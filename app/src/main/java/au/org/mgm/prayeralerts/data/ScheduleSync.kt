package au.org.mgm.prayeralerts.data

import android.content.Context
import au.org.mgm.prayeralerts.notify.PrayerScheduler

object ScheduleSync {
    fun refreshAndReschedule(context: Context): DaySchedule {
        val prefs = AppPreferences(context)
        val repository = AwqatPrayerRepository(
            bundledWtimes = readAsset(context, "awqat/wtimes-AU.MELBOURNE.ini"),
            bundledIqama = readAsset(context, "awqat/iqamafixed.js")
        )
        val result = repository.fetchToday(cachedIqamaJs = prefs.loadLiveIqamaJs())
        result.liveIqamaJs?.let { prefs.saveLiveIqamaJs(it) }
        prefs.saveSchedule(result.schedule)
        PrayerScheduler.clearAndReschedule(context, result.schedule)
        return result.schedule
    }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
