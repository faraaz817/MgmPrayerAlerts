package au.org.mgm.prayeralerts.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import au.org.mgm.prayeralerts.data.PrayerTarget

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val targetId = intent.getStringExtra(EXTRA_TARGET) ?: return
        val target = PrayerTarget.fromId(targetId) ?: return
        val azan = intent.getStringExtra(EXTRA_AZAN).orEmpty()
        val jamaat = intent.getStringExtra(EXTRA_JAMAAT).orEmpty()
        NotificationHelper.showPrayerNotification(context, target, azan, jamaat)
    }

    companion object {
        const val EXTRA_TARGET = "target"
        const val EXTRA_AZAN = "azan"
        const val EXTRA_JAMAAT = "jamaat"
    }
}
