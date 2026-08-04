package au.org.mgm.prayeralerts.data

import android.content.Context
import au.org.mgm.prayeralerts.notify.PrayerScheduler

object ScheduleSync {
    fun refreshAndReschedule(context: Context): DaySchedule {
        val repository = AwqatPrayerRepository()
        val prefs = AppPreferences(context)
        val schedule = repository.fetchToday()
        prefs.saveSchedule(schedule)
        PrayerScheduler.clearAndReschedule(context, schedule)
        return schedule
    }
}
