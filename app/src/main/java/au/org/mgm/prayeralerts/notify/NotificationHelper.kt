package au.org.mgm.prayeralerts.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import au.org.mgm.prayeralerts.R
import au.org.mgm.prayeralerts.data.AppPreferences
import au.org.mgm.prayeralerts.data.PrayerTarget
import au.org.mgm.prayeralerts.data.TimeParse
import au.org.mgm.prayeralerts.ui.MainActivity

object NotificationHelper {
    private const val BASE_CHANNEL = "mgm_general"

    fun notificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun ensureBaseChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            BASE_CHANNEL,
            "General",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    fun channelIdFor(context: Context, target: PrayerTarget): String {
        val prefs = AppPreferences(context)
        val version = prefs.soundVersion(target)
        return "mgm_${target.id}_$version"
    }

    fun soundUriFor(context: Context, target: PrayerTarget): Uri? {
        val prefs = AppPreferences(context)
        val soundFile = prefs.customSoundFile(target) ?: return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            soundFile
        )
    }

    fun ensureTargetChannel(context: Context, target: PrayerTarget) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = channelIdFor(context, target)

        manager.notificationChannels
            .filter { it.id.startsWith("mgm_${target.id}_") && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }

        if (manager.getNotificationChannel(channelId) != null) return

        val channel = NotificationChannel(
            channelId,
            target.displayName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Azaan alerts for ${target.displayName}"
            enableVibration(true)
            val sound = soundUriFor(context, target)
            if (sound != null) {
                // Broaden grants beyond systemui — OEM notification hosts vary.
                listOf(
                    "com.android.systemui",
                    "com.android.phone",
                    "com.google.android.permissioncontroller"
                ).forEach { pkg ->
                    runCatching {
                        context.grantUriPermission(
                            pkg,
                            sound,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(sound, attrs)
            }
        }
        manager.createNotificationChannel(channel)
    }

    fun showPrayerNotification(
        context: Context,
        target: PrayerTarget,
        azan: String,
        jamaat: String,
        test: Boolean = false
    ): Boolean {
        if (!notificationsAllowed(context)) return false

        ensureTargetChannel(context, target)
        val channelId = channelIdFor(context, target)
        val sound = soundUriFor(context, target)

        val openIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            target.ordinal,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val azanDisplay = runCatching { TimeParse.formatDisplay(azan) }.getOrDefault(azan)
        val jamaatDisplay = runCatching { TimeParse.formatDisplay(jamaat) }.getOrDefault(jamaat)

        val title = if (test) {
            "Test · ${target.displayName}"
        } else if (target.isJumuah) {
            target.displayName
        } else {
            context.getString(R.string.notification_title, target.displayName)
        }

        val body = if (target.isJumuah) {
            context.getString(R.string.jumuah_body, azanDisplay)
        } else {
            context.getString(R.string.notification_body, azanDisplay, jamaatDisplay)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        if (sound != null) {
            builder.setSound(sound)
        }

        NotificationManagerCompat.from(context)
            .notify(if (test) 9000 + target.ordinal else target.ordinal + 100, builder.build())
        return true
    }

    fun openExactAlarmSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
