package au.org.mgm.prayeralerts.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import au.org.mgm.prayeralerts.R
import au.org.mgm.prayeralerts.data.AppPreferences
import au.org.mgm.prayeralerts.data.DaySchedule
import au.org.mgm.prayeralerts.data.PrayerTarget
import au.org.mgm.prayeralerts.data.ScheduleSync
import au.org.mgm.prayeralerts.data.TimeParse
import au.org.mgm.prayeralerts.databinding.ActivityMainBinding
import au.org.mgm.prayeralerts.databinding.ItemNotificationSettingBinding
import au.org.mgm.prayeralerts.databinding.ItemPrayerTimeBinding
import au.org.mgm.prayeralerts.notify.NotificationHelper
import au.org.mgm.prayeralerts.notify.PrayerScheduler
import au.org.mgm.prayeralerts.notify.ScheduleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private var soundTarget: PrayerTarget? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_LONG).show()
            }
        }

    private val soundPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val target = soundTarget ?: return@registerForActivityResult
            if (uri == null) return@registerForActivityResult
            runCatching {
                val name = queryDisplayName(uri) ?: "Custom sound"
                prefs.setCustomSound(target, uri, this, name)
                NotificationHelper.ensureTargetChannel(this, target)
                applyScheduleResult(PrayerScheduler.clearAndReschedule(this))
                renderSettings()
                Toast.makeText(this, "Sound saved for ${target.displayName}", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, it.message ?: "Failed to save sound", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        requestNeededPermissions()
        binding.refreshButton.setOnClickListener { refreshTimes(forceNetwork = true) }
        renderCachedOrEmpty()
        refreshTimes(forceNetwork = true)
    }

    override fun onResume() {
        super.onResume()
        val today = LocalDate.now(ZoneId.of("Australia/Melbourne")).toString()
        val schedule = prefs.loadSchedule()
        if (schedule != null && schedule.isForDate(today)) {
            renderSchedule(schedule)
        }
        warnPermissionsInStatus()
    }

    private fun warnPermissionsInStatus() {
        val extras = buildString {
            if (!NotificationHelper.notificationsAllowed(this@MainActivity)) {
                append("\n").append(getString(R.string.notifications_blocked))
            }
            if (!PrayerScheduler.canScheduleExact(this@MainActivity)) {
                append("\n").append(getString(R.string.exact_alarm_blocked))
            }
        }
        if (extras.isNotBlank() && !binding.statusText.text.contains(extras.trim())) {
            binding.statusText.append(extras)
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

    private fun renderCachedOrEmpty() {
        val schedule = prefs.loadSchedule()
        val today = LocalDate.now(ZoneId.of("Australia/Melbourne")).toString()
        if (schedule != null && schedule.isForDate(today)) {
            renderSchedule(schedule)
            applyScheduleResult(PrayerScheduler.clearAndReschedule(this, schedule))
        } else if (schedule != null) {
            binding.statusText.text = getString(R.string.stale_schedule) +
                "\n(Saved date: ${schedule.dateKey})"
        }
        renderSettings()
        warnPermissionsInStatus()
    }

    private fun refreshTimes(forceNetwork: Boolean) {
        binding.statusText.text = "Refreshing…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (forceNetwork) {
                        ScheduleSync.refreshAndReschedule(applicationContext)
                    } else {
                        val today = LocalDate.now(ZoneId.of("Australia/Melbourne")).toString()
                        val cached = prefs.loadSchedule()
                        if (cached != null && cached.isForDate(today)) {
                            cached
                        } else {
                            ScheduleSync.refreshAndReschedule(applicationContext)
                        }
                    }
                }
            }
            result.onSuccess { schedule ->
                renderSchedule(schedule)
                applyScheduleResult(PrayerScheduler.clearAndReschedule(this@MainActivity, schedule))
                renderSettings()
                warnPermissionsInStatus()
            }.onFailure { error ->
                val today = LocalDate.now(ZoneId.of("Australia/Melbourne")).toString()
                val cached = prefs.loadSchedule()
                if (cached != null && cached.isForDate(today)) {
                    renderSchedule(cached)
                    applyScheduleResult(PrayerScheduler.clearAndReschedule(this@MainActivity, cached))
                    binding.statusText.text =
                        getString(R.string.fetch_failed) + "\n${error.message}"
                } else {
                    binding.statusText.text =
                        getString(R.string.fetch_failed) + "\n" +
                            getString(R.string.stale_schedule) + "\n${error.message}"
                }
                renderSettings()
                warnPermissionsInStatus()
            }
        }
    }

    private fun applyScheduleResult(result: ScheduleResult) {
        when {
            !result.exactAlarmsAllowed -> {
                Toast.makeText(this, getString(R.string.exact_alarm_blocked), Toast.LENGTH_LONG).show()
            }
            !result.usedTodaySchedule -> {
                result.reasonIfSkipped?.let {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                // Keep quiet unless useful
                if (result.scheduledCount >= 0) {
                    binding.statusText.append(
                        "\n" + getString(R.string.scheduled_count, result.scheduledCount)
                    )
                }
            }
        }
    }

    private fun renderSchedule(schedule: DaySchedule) {
        val zone = ZoneId.of("Australia/Melbourne")
        val isFriday = LocalDate.now(zone).dayOfWeek.value == 5
        val fetched = Instant.ofEpochMilli(schedule.fetchedAtEpochMs)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"))

        binding.statusText.text = getString(R.string.last_fetch, fetched) +
            "\nSchedule date: ${schedule.dateKey}"

        binding.timesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        schedule.displayRows(isFriday).forEach { (name, pair) ->
            val row = ItemPrayerTimeBinding.inflate(inflater, binding.timesContainer, false)
            row.prayerName.text = name
            val azan = TimeParse.formatDisplay(pair.azan)
            val jamaat = TimeParse.formatDisplay(pair.jamaat)
            row.prayerTimes.text = "Azaan $azan  ·  Jamaat $jamaat"
            binding.timesContainer.addView(row.root)
        }
    }

    private fun renderSettings() {
        binding.settingsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        PrayerTarget.entries.forEach { target ->
            val row = ItemNotificationSettingBinding.inflate(inflater, binding.settingsContainer, false)
            row.targetName.text = target.displayName
            row.enabledSwitch.setOnCheckedChangeListener(null)
            row.enabledSwitch.isChecked = prefs.isEnabled(target)
            row.enabledSwitch.setOnCheckedChangeListener { _, checked ->
                prefs.setEnabled(target, checked)
                applyScheduleResult(PrayerScheduler.clearAndReschedule(this))
                Toast.makeText(
                    this,
                    if (checked) "${target.displayName} enabled" else "${target.displayName} disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }

            val soundName = prefs.customSoundLabel(target)
            row.soundLabel.text = if (soundName.isNullOrBlank()) {
                getString(R.string.default_sound)
            } else {
                "Custom: $soundName"
            }

            row.pickSoundButton.setOnClickListener {
                soundTarget = target
                soundPicker.launch("audio/*")
            }
            row.clearSoundButton.setOnClickListener {
                prefs.clearCustomSound(target)
                NotificationHelper.ensureTargetChannel(this, target)
                applyScheduleResult(PrayerScheduler.clearAndReschedule(this))
                renderSettings()
            }
            row.testButton.setOnClickListener {
                if (!NotificationHelper.notificationsAllowed(this)) {
                    Toast.makeText(this, getString(R.string.notifications_blocked), Toast.LENGTH_LONG).show()
                    NotificationHelper.openAppNotificationSettings(this)
                    return@setOnClickListener
                }
                val schedule = prefs.loadSchedule()
                val pair = schedule?.pairFor(target)
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

            binding.settingsContainer.addView(row.root)
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
}
