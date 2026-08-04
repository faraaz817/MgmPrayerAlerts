package au.org.mgm.prayeralerts.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import au.org.mgm.prayeralerts.data.AppPreferences
import au.org.mgm.prayeralerts.data.DaySchedule
import au.org.mgm.prayeralerts.data.PrayerTarget
import au.org.mgm.prayeralerts.data.TimeParse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

data class ScheduleResult(
    val scheduledCount: Int,
    val exactAlarmsAllowed: Boolean,
    val usedTodaySchedule: Boolean,
    val reasonIfSkipped: String? = null
)

object PrayerScheduler {
    private val zone: ZoneId = ZoneId.of("Australia/Melbourne")

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    fun ensureDailyRefreshScheduled(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, DailyRefreshReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            77,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3:00 AM device-local time per FR
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        setExact(alarmManager, calendar.timeInMillis, pending)
    }

    fun clearAndReschedule(context: Context, schedule: DaySchedule? = null): ScheduleResult {
        val prefs = AppPreferences(context)
        val day = schedule ?: prefs.loadSchedule()
        clearAll(context)

        if (day == null) {
            ensureDailyRefreshScheduled(context)
            return ScheduleResult(0, canScheduleExact(context), false, "No saved schedule")
        }

        val today = LocalDate.now(zone)
        val todayKey = today.toString()
        if (!day.isForDate(todayKey)) {
            ensureDailyRefreshScheduled(context)
            return ScheduleResult(
                scheduledCount = 0,
                exactAlarmsAllowed = canScheduleExact(context),
                usedTodaySchedule = false,
                reasonIfSkipped = "Saved schedule is for ${day.dateKey}, not $todayKey"
            )
        }

        val exactOk = canScheduleExact(context)
        if (!exactOk) {
            ensureDailyRefreshScheduled(context)
            return ScheduleResult(0, false, true, "Exact alarms not permitted")
        }

        val isFriday = today.dayOfWeek.value == 5
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        var scheduled = 0

        PrayerTarget.entries.forEach { target ->
            if (!prefs.isEnabled(target)) return@forEach
            if (target.isJumuah && !isFriday) return@forEach

            val pair = day.pairFor(target) ?: return@forEach
            val trigger = LocalDateTime.of(today, TimeParse.parseFlexible(pair.azan))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

            if (trigger <= System.currentTimeMillis()) return@forEach

            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                putExtra(PrayerAlarmReceiver.EXTRA_TARGET, target.id)
                putExtra(PrayerAlarmReceiver.EXTRA_AZAN, pair.azan)
                putExtra(PrayerAlarmReceiver.EXTRA_JAMAAT, pair.jamaat)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(target),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setExact(alarmManager, trigger, pending)
            NotificationHelper.ensureTargetChannel(context, target)
            scheduled++
        }

        ensureDailyRefreshScheduled(context)
        return ScheduleResult(scheduled, true, true, null)
    }

    fun clearAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        PrayerTarget.entries.forEach { target ->
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(target),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)
        }
    }

    private fun requestCode(target: PrayerTarget): Int = 200 + target.ordinal

    private fun setExact(alarmManager: AlarmManager, triggerAt: Long, pending: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }
}
