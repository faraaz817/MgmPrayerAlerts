package au.org.mgm.prayeralerts.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import au.org.mgm.prayeralerts.data.AppPreferences
import au.org.mgm.prayeralerts.data.ScheduleSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class DailyRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext
            try {
                runCatching { ScheduleSync.refreshAndReschedule(app) }
                    .onFailure {
                        // Keep today's alarms if we already have a valid today schedule.
                        val prefs = AppPreferences(app)
                        val cached = prefs.loadSchedule()
                        val today = LocalDate.now(ZoneId.of("Australia/Melbourne")).toString()
                        if (cached != null && cached.isForDate(today)) {
                            PrayerScheduler.clearAndReschedule(app, cached)
                        }
                    }
            } finally {
                // Always re-arm 3 AM — the previous alarm is one-shot.
                PrayerScheduler.ensureDailyRefreshScheduled(app)
                pendingResult.finish()
            }
        }
    }
}
