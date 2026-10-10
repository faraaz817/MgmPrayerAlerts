package au.org.mgm.prayeralerts.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val filesDir = context.filesDir

    fun isEnabled(target: PrayerTarget): Boolean =
        prefs.getBoolean(enabledKey(target), true)

    fun setEnabled(target: PrayerTarget, enabled: Boolean) {
        prefs.edit().putBoolean(enabledKey(target), enabled).apply()
    }

    fun customSoundFile(target: PrayerTarget): File? {
        val path = prefs.getString(soundKey(target), null) ?: return null
        val file = File(path)
        return file.takeIf { it.exists() }
    }

    fun customSoundLabel(target: PrayerTarget): String? =
        prefs.getString(soundLabelKey(target), null)

    fun setCustomSound(target: PrayerTarget, source: Uri, context: Context, displayName: String) {
        // App-specific external storage is more readable by notification hosts on many OEMs.
        val base = context.getExternalFilesDir("sounds") ?: File(filesDir, "sounds")
        if (!base.exists()) base.mkdirs()
        val dest = File(base, "${target.id}.audio")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected audio")
        prefs.edit()
            .putString(soundKey(target), dest.absolutePath)
            .putString(soundLabelKey(target), displayName)
            .putLong(soundVersionKey(target), System.currentTimeMillis())
            .apply()
    }

    fun clearCustomSound(target: PrayerTarget) {
        prefs.getString(soundKey(target), null)?.let { File(it).delete() }
        prefs.edit()
            .remove(soundKey(target))
            .remove(soundLabelKey(target))
            .putLong(soundVersionKey(target), System.currentTimeMillis())
            .apply()
    }

    fun soundVersion(target: PrayerTarget): Long =
        prefs.getLong(soundVersionKey(target), 0L)

    fun saveSchedule(schedule: DaySchedule) {
        val json = JSONObject()
            .put("dateKey", schedule.dateKey)
            .put("fetchedAtEpochMs", schedule.fetchedAtEpochMs)
            .put("fajrAzan", schedule.fajr.azan)
            .put("fajrJamaat", schedule.fajr.jamaat)
            .put("sunriseAzan", schedule.sunrise.azan)
            .put("sunriseJamaat", schedule.sunrise.jamaat)
            .put("dhuhrAzan", schedule.dhuhr.azan)
            .put("dhuhrJamaat", schedule.dhuhr.jamaat)
            .put("asrAzan", schedule.asr.azan)
            .put("asrJamaat", schedule.asr.jamaat)
            .put("maghribAzan", schedule.maghrib.azan)
            .put("maghribJamaat", schedule.maghrib.jamaat)
            .put("ishaAzan", schedule.isha.azan)
            .put("ishaJamaat", schedule.isha.jamaat)
            .put("jumuah", schedule.jumuah.joinToString("|"))
        prefs.edit().putString(KEY_SCHEDULE, json.toString()).apply()
    }

    fun saveLiveIqamaJs(js: String) {
        prefs.edit().putString(KEY_LIVE_IQAMA, js).apply()
    }

    fun loadLiveIqamaJs(): String? = prefs.getString(KEY_LIVE_IQAMA, null)

    fun loadSchedule(): DaySchedule? {
        val raw = prefs.getString(KEY_SCHEDULE, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            DaySchedule(
                dateKey = json.getString("dateKey"),
                fajr = PrayerPair(json.getString("fajrAzan"), json.getString("fajrJamaat")),
                sunrise = PrayerPair(json.getString("sunriseAzan"), json.getString("sunriseJamaat")),
                dhuhr = PrayerPair(json.getString("dhuhrAzan"), json.getString("dhuhrJamaat")),
                asr = PrayerPair(json.getString("asrAzan"), json.getString("asrJamaat")),
                maghrib = PrayerPair(json.getString("maghribAzan"), json.getString("maghribJamaat")),
                isha = PrayerPair(json.getString("ishaAzan"), json.getString("ishaJamaat")),
                jumuah = json.optString("jumuah").split("|").filter { it.isNotBlank() },
                fetchedAtEpochMs = json.getLong("fetchedAtEpochMs")
            )
        }.getOrNull()
    }

    private fun enabledKey(target: PrayerTarget) = "enabled_${target.id}"
    private fun soundKey(target: PrayerTarget) = "sound_${target.id}"
    private fun soundLabelKey(target: PrayerTarget) = "sound_label_${target.id}"
    private fun soundVersionKey(target: PrayerTarget) = "sound_ver_${target.id}"

    companion object {
        private const val PREFS = "mgm_prefs"
        private const val KEY_SCHEDULE = "day_schedule"
        private const val KEY_LIVE_IQAMA = "live_iqama_js"
    }
}
