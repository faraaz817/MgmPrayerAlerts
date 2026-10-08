package au.org.mgm.prayeralerts.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import au.org.mgm.prayeralerts.R
import au.org.mgm.prayeralerts.data.AppPreferences
import au.org.mgm.prayeralerts.data.DaySchedule
import au.org.mgm.prayeralerts.data.PrayerTarget
import au.org.mgm.prayeralerts.data.ScheduleSync
import au.org.mgm.prayeralerts.data.TimeParse
import au.org.mgm.prayeralerts.databinding.ActivityMainBinding
import au.org.mgm.prayeralerts.databinding.ItemAlertGroupHeaderBinding
import au.org.mgm.prayeralerts.databinding.ItemJumuahSessionBinding
import au.org.mgm.prayeralerts.databinding.ItemNotificationSettingBinding
import au.org.mgm.prayeralerts.databinding.ItemPrayerTimeBinding
import au.org.mgm.prayeralerts.databinding.ItemStatusBannerBinding
import au.org.mgm.prayeralerts.notify.NotificationHelper
import au.org.mgm.prayeralerts.notify.PrayerScheduler
import au.org.mgm.prayeralerts.notify.ScheduleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private var soundTarget: PrayerTarget? = null

    private var schedule: DaySchedule? = null
    private var lastResult: ScheduleResult? = null
    private var fetchError: String? = null
    private var refreshing = false
    private var expandedTarget: PrayerTarget? = null
    private val settingRows = mutableMapOf<PrayerTarget, ItemNotificationSettingBinding>()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // If declined, the banner explains what is missing.
            renderBanners()
            renderToday()
        }

    private val soundPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val target = soundTarget ?: return@registerForActivityResult
            if (uri == null) return@registerForActivityResult
            runCatching {
                val name = queryDisplayName(uri) ?: "Custom sound"
                prefs.setCustomSound(target, uri, this, name)
                NotificationHelper.ensureTargetChannel(this, target)
                lastResult = PrayerScheduler.clearAndReschedule(this)
                renderSettings()
                renderStatus()
                Toast.makeText(
                    this,
                    getString(R.string.sound_saved, target.displayName),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    this,
                    it.message ?: getString(R.string.sound_save_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The screen is always dark, so system bar icons stay light regardless of system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)
        applySystemBarInsets()
        // Keeps row ripples inside the card's rounded corners.
        binding.settingsContainer.clipToOutline = true

        requestNeededPermissions()
        binding.refreshButton.setOnClickListener { refreshTimes() }

        schedule = prefs.loadSchedule()
        todaySchedule()?.let { lastResult = PrayerScheduler.clearAndReschedule(this, it) }
        renderAll()
        refreshTimes()

        // Keeps the countdown and the "next prayer" highlight current while visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    renderToday()
                    delay(CLOCK_TICK_MS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may be returning from a permission screen opened by a banner.
        val result = lastResult
        if (result != null && !result.exactAlarmsAllowed && PrayerScheduler.canScheduleExact(this)) {
            todaySchedule()?.let { lastResult = PrayerScheduler.clearAndReschedule(this, it) }
        }
        renderStatus()
        renderBanners()
    }

    private fun applySystemBarInsets() {
        val baseScrollBottom = binding.scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.scroll.updatePadding(bottom = baseScrollBottom + bars.bottom)
            insets
        }
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!PrayerScheduler.canScheduleExact(this)) {
            Toast.makeText(this, getString(R.string.exact_alarm_needed), Toast.LENGTH_LONG).show()
            NotificationHelper.openExactAlarmSettings(this)
        }
    }

    private fun refreshTimes() {
        if (refreshing) return
        refreshing = true
        binding.refreshProgress.show()
        renderStatus()
        renderBanners()
        renderToday()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ScheduleSync.refreshAndReschedule(applicationContext) }
            }
            refreshing = false
            binding.refreshProgress.hide()
            result.onSuccess { fresh ->
                schedule = fresh
                fetchError = null
                lastResult = PrayerScheduler.clearAndReschedule(this@MainActivity, fresh)
            }.onFailure { error ->
                schedule = prefs.loadSchedule()
                fetchError = error.message ?: error.javaClass.simpleName
                todaySchedule()?.let {
                    lastResult = PrayerScheduler.clearAndReschedule(this@MainActivity, it)
                }
            }
            renderAll()
        }
    }

    private fun todaySchedule(): DaySchedule? =
        schedule?.takeIf { it.isForDate(LocalDate.now(ZONE).toString()) }

    private fun renderAll() {
        renderStatus()
        renderBanners()
        renderToday()
        renderSettings()
    }

    private fun renderStatus() {
        val day = todaySchedule()
        binding.statusText.text = when {
            refreshing -> getString(R.string.status_refreshing)
            day == null -> getString(R.string.status_no_schedule)
            else -> {
                val updatedAt = Instant.ofEpochMilli(day.fetchedAtEpochMs).atZone(ZONE).toLocalTime()
                val updated = getString(R.string.status_updated, TimeParse.formatDisplay(updatedAt))
                val count = lastResult
                    ?.takeIf { it.usedTodaySchedule && it.exactAlarmsAllowed }
                    ?.scheduledCount
                when (count) {
                    null -> updated
                    0 -> getString(R.string.status_joined, updated, getString(R.string.status_alerts_none))
                    else -> getString(
                        R.string.status_joined,
                        updated,
                        resources.getQuantityString(R.plurals.alerts_scheduled, count, count)
                    )
                }
            }
        }
    }

    private fun renderBanners() {
        binding.bannerContainer.removeAllViews()
        if (!NotificationHelper.notificationsAllowed(this)) {
            addBanner(
                R.string.banner_notifications_title,
                getString(R.string.banner_notifications_body),
                R.string.banner_notifications_action
            ) { NotificationHelper.openAppNotificationSettings(this) }
        }
        if (!PrayerScheduler.canScheduleExact(this)) {
            addBanner(
                R.string.banner_exact_title,
                getString(R.string.banner_exact_body),
                R.string.banner_exact_action
            ) { NotificationHelper.openExactAlarmSettings(this) }
        }
        val error = fetchError
        if (error != null && !refreshing) {
            val summary = getString(
                if (todaySchedule() != null) R.string.banner_fetch_body_cached
                else R.string.banner_fetch_body_empty
            )
            addBanner(
                R.string.banner_fetch_title,
                "$summary\n$error",
                R.string.banner_fetch_action
            ) { refreshTimes() }
        }
    }

    private fun addBanner(
        @StringRes title: Int,
        body: CharSequence,
        @StringRes action: Int,
        onAction: () -> Unit
    ) {
        val banner = ItemStatusBannerBinding.inflate(layoutInflater, binding.bannerContainer, false)
        banner.bannerTitle.setText(title)
        banner.bannerBody.text = body
        banner.bannerAction.setText(action)
        banner.bannerAction.setOnClickListener { onAction() }
        binding.bannerContainer.addView(banner.root)
    }

    private fun renderToday() {
        val now = LocalDateTime.now(ZONE)
        binding.dateText.text = now.format(DATE_FORMAT)

        val day = todaySchedule()
        val isFriday = now.dayOfWeek == DayOfWeek.FRIDAY
        val next = day
            ?.let { events(it, isFriday) }
            ?.firstOrNull { it.azaan.isAfter(now.toLocalTime()) }

        renderHero(day, next, now.toLocalTime())
        renderTimes(day, next?.target, now.toLocalTime())
        renderJumuah(day?.takeIf { isFriday }, next?.target, now.toLocalTime())
    }

    private fun renderHero(day: DaySchedule?, next: PrayerEvent?, now: LocalTime) {
        if (next == null) {
            val (title, body) = when {
                day != null -> R.string.hero_done_title to R.string.hero_done_body
                refreshing -> R.string.hero_loading_title to R.string.hero_loading_body
                else -> R.string.hero_empty_title to R.string.hero_empty_body
            }
            binding.heroLabel.setText(R.string.hero_today)
            binding.heroAlertPill.isVisible = false
            binding.heroName.setText(title)
            binding.heroTime.isVisible = false
            binding.heroSubtitle.setText(body)
            return
        }

        val alertActive = prefs.isEnabled(next.target) &&
            NotificationHelper.notificationsAllowed(this) &&
            PrayerScheduler.canScheduleExact(this)
        binding.heroLabel.setText(R.string.next_prayer)
        binding.heroAlertPill.isVisible = true
        binding.heroAlertIcon.setImageResource(
            if (alertActive) R.drawable.ic_bell else R.drawable.ic_bell_off
        )
        binding.heroAlertText.setText(if (alertActive) R.string.alert_on else R.string.alert_off)
        binding.heroName.text = next.target.displayName
        binding.heroTime.isVisible = true
        binding.heroTime.text = largeTime(next.azaan)

        val countdown = countdown(Duration.between(now, next.azaan))
        binding.heroSubtitle.text = next.jamaat
            ?.let { getString(R.string.hero_countdown_jamaat, countdown, TimeParse.formatDisplay(it)) }
            ?: countdown
    }

    private fun renderTimes(day: DaySchedule?, next: PrayerTarget?, now: LocalTime) {
        binding.timesLabel.isVisible = day != null
        binding.timesCard.isVisible = day != null
        binding.timesContainer.removeAllViews()
        if (day == null) return

        GRID_PRAYERS.forEach { target ->
            val pair = day.pairFor(target) ?: return@forEach
            val row = ItemPrayerTimeBinding.inflate(layoutInflater, binding.timesContainer, false)
            row.prayerName.text = target.displayName
            row.azaanTime.text = displayTime(pair.azan)
            row.jamaatTime.text = displayTime(pair.jamaat)

            // A prayer only reads as finished once its Jamaat has started.
            val finished = parseTime(pair.jamaat)?.let { !it.isAfter(now) } ?: false
            val color = getColor(
                when {
                    target == next -> R.color.mgm_gold
                    finished -> R.color.mgm_muted
                    else -> R.color.mgm_text
                }
            )
            row.prayerName.setTextColor(color)
            row.azaanTime.setTextColor(color)
            row.jamaatTime.setTextColor(color)
            if (target == next) row.root.setBackgroundResource(R.drawable.bg_row_highlight)
            binding.timesContainer.addView(row.root)
        }
    }

    private fun renderJumuah(fridaySchedule: DaySchedule?, next: PrayerTarget?, now: LocalTime) {
        val sessions = fridaySchedule
            ?.let { day -> JUMUAH_SESSIONS.mapNotNull { target -> day.pairFor(target)?.let { target to it.azan } } }
            .orEmpty()
        binding.jumuahLabel.isVisible = sessions.isNotEmpty()
        binding.jumuahContainer.isVisible = sessions.isNotEmpty()
        binding.jumuahContainer.removeAllViews()

        sessions.forEachIndexed { index, (target, azan) ->
            val cell = ItemJumuahSessionBinding.inflate(layoutInflater, binding.jumuahContainer, false)
            cell.sessionLabel.text = getString(R.string.jumuah_session, index + 1)
            cell.sessionTime.text = displayTime(azan)
            val started = parseTime(azan)?.let { !it.isAfter(now) } ?: false
            when {
                target == next -> {
                    cell.root.setBackgroundResource(R.drawable.bg_row_highlight)
                    cell.sessionLabel.setTextColor(getColor(R.color.mgm_gold))
                    cell.sessionTime.setTextColor(getColor(R.color.mgm_gold))
                }
                started -> cell.sessionTime.setTextColor(getColor(R.color.mgm_muted))
            }
            binding.jumuahContainer.addView(cell.root)
        }
    }

    private fun renderSettings() {
        val container = binding.settingsContainer
        container.removeAllViews()
        settingRows.clear()
        val day = todaySchedule()

        var previous: PrayerTarget? = null
        PrayerTarget.entries.forEach { target ->
            val startsGroup = previous == null || previous?.isJumuah != target.isJumuah
            if (startsGroup && target.isJumuah) {
                ItemAlertGroupHeaderBinding.inflate(layoutInflater, container, true)
            }
            val row = ItemNotificationSettingBinding.inflate(layoutInflater, container, false)
            row.divider.isVisible = !startsGroup
            bindSettingRow(row, target, day)
            settingRows[target] = row
            container.addView(row.root)
            previous = target
        }
    }

    private fun bindSettingRow(
        row: ItemNotificationSettingBinding,
        target: PrayerTarget,
        day: DaySchedule?
    ) {
        val customSound = prefs.customSoundLabel(target)?.takeIf { it.isNotBlank() }
        val sound = customSound ?: getString(R.string.default_sound)
        val azaan = day?.pairFor(target)?.azan?.let { displayTime(it) }
        row.targetName.text = target.displayName
        row.soundLabel.text = azaan?.let { getString(R.string.setting_subtitle, it, sound) } ?: sound

        val enabled = prefs.isEnabled(target)
        row.targetName.setTextColor(getColor(if (enabled) R.color.mgm_text else R.color.mgm_muted))
        row.enabledSwitch.contentDescription =
            getString(R.string.alert_switch_description, target.displayName)
        row.enabledSwitch.isChecked = enabled
        row.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.setEnabled(target, checked)
            lastResult = PrayerScheduler.clearAndReschedule(this)
            row.targetName.setTextColor(getColor(if (checked) R.color.mgm_text else R.color.mgm_muted))
            renderStatus()
            renderToday()
        }

        setExpanded(row, expandedTarget == target, animate = false)
        row.headerRow.setOnClickListener { toggleExpanded(target) }

        row.pickSoundButton.setOnClickListener {
            soundTarget = target
            soundPicker.launch("audio/*")
        }
        row.clearSoundButton.isVisible = customSound != null
        row.clearSoundButton.setOnClickListener {
            prefs.clearCustomSound(target)
            NotificationHelper.ensureTargetChannel(this, target)
            lastResult = PrayerScheduler.clearAndReschedule(this)
            renderSettings()
            renderStatus()
        }
        row.testButton.setOnClickListener { sendTestNotification(target) }
    }

    private fun toggleExpanded(target: PrayerTarget) {
        val previous = expandedTarget
        expandedTarget = if (previous == target) null else target
        TransitionManager.beginDelayedTransition(
            binding.content,
            AutoTransition().setOrdering(TransitionSet.ORDERING_TOGETHER).setDuration(EXPAND_ANIM_MS)
        )
        previous?.let { settingRows[it] }?.let { setExpanded(it, expanded = false, animate = true) }
        expandedTarget?.let { settingRows[it] }?.let { setExpanded(it, expanded = true, animate = true) }
    }

    private fun setExpanded(row: ItemNotificationSettingBinding, expanded: Boolean, animate: Boolean) {
        row.detailsPanel.isVisible = expanded
        val rotation = if (expanded) 180f else 0f
        if (animate) {
            row.expandIcon.animate().rotation(rotation).setDuration(EXPAND_ANIM_MS).start()
        } else {
            row.expandIcon.rotation = rotation
        }
    }

    private fun sendTestNotification(target: PrayerTarget) {
        if (!NotificationHelper.notificationsAllowed(this)) {
            Toast.makeText(this, getString(R.string.notifications_blocked), Toast.LENGTH_LONG).show()
            NotificationHelper.openAppNotificationSettings(this)
            return
        }
        val pair = prefs.loadSchedule()?.pairFor(target)
        val ok = NotificationHelper.showPrayerNotification(
            this,
            target,
            pair?.azan ?: "0:00",
            pair?.jamaat ?: "0:00",
            test = true
        )
        if (!ok) {
            Toast.makeText(this, getString(R.string.notifications_blocked), Toast.LENGTH_LONG).show()
        }
    }

    private data class PrayerEvent(
        val target: PrayerTarget,
        val azaan: LocalTime,
        val jamaat: LocalTime?
    )

    /** Today's notifiable events in time order; Jumu'ah sessions only count on Fridays. */
    private fun events(day: DaySchedule, isFriday: Boolean): List<PrayerEvent> =
        PrayerTarget.entries
            .filter { isFriday || !it.isJumuah }
            .mapNotNull { target ->
                val pair = day.pairFor(target) ?: return@mapNotNull null
                val azaan = parseTime(pair.azan) ?: return@mapNotNull null
                val jamaat = if (target.isJumuah) null else parseTime(pair.jamaat)
                PrayerEvent(target, azaan, jamaat)
            }
            .sortedBy { it.azaan }

    private fun parseTime(raw: String): LocalTime? =
        runCatching { TimeParse.parseFlexible(raw) }.getOrNull()

    private fun displayTime(raw: String): String =
        runCatching { TimeParse.formatDisplay(raw) }.getOrDefault(raw)

    /** "4:56 PM" with a smaller meridian, for the next-prayer card. */
    private fun largeTime(time: LocalTime): CharSequence {
        val text = TimeParse.formatDisplay(time)
        val meridianStart = text.lastIndexOf(' ')
        if (meridianStart < 0) return text
        return SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.4f), meridianStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun countdown(remaining: Duration): String {
        // Round up so the last minute reads "in 1 min", never "in 0 min".
        val totalMinutes = (remaining.seconds + 59) / 60
        val hours = (totalMinutes / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()
        return when {
            hours == 0 -> getString(R.string.countdown_minutes, minutes)
            minutes == 0 -> getString(R.string.countdown_hours, hours)
            else -> getString(R.string.countdown_hours_minutes, hours, minutes)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Australia/Melbourne")
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
        private val GRID_PRAYERS = PrayerTarget.entries.filterNot { it.isJumuah }
        private val JUMUAH_SESSIONS = PrayerTarget.entries.filter { it.isJumuah }
        private const val CLOCK_TICK_MS = 30_000L
        private const val EXPAND_ANIM_MS = 200L
    }
}
