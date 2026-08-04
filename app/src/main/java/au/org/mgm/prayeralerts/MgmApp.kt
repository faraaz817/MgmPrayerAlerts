package au.org.mgm.prayeralerts

import android.app.Application
import au.org.mgm.prayeralerts.notify.NotificationHelper
import au.org.mgm.prayeralerts.notify.PrayerScheduler

class MgmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureBaseChannel(this)
        PrayerScheduler.ensureDailyRefreshScheduled(this)
    }
}
