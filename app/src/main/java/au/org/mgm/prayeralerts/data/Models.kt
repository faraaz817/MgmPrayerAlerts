package au.org.mgm.prayeralerts.data

enum class PrayerTarget(
    val id: String,
    val displayName: String,
    val isJumuah: Boolean = false
) {
    FAJR("fajr", "Fajr"),
    DHUHR("dhuhr", "Dhuhr"),
    ASR("asr", "Asr"),
    MAGHRIB("maghrib", "Maghrib"),
    ISHA("isha", "Isha"),
    JUMUAH_1("jumuah_1", "Jumu'ah #1", isJumuah = true),
    JUMUAH_2("jumuah_2", "Jumu'ah #2", isJumuah = true),
    JUMUAH_3("jumuah_3", "Jumu'ah #3", isJumuah = true);

    companion object {
        fun fromId(id: String): PrayerTarget? = entries.find { it.id == id }
    }
}

data class PrayerPair(
    val azan: String,
    val jamaat: String
)

data class DaySchedule(
    val dateKey: String,
    val fajr: PrayerPair,
    val sunrise: PrayerPair,
    val dhuhr: PrayerPair,
    val asr: PrayerPair,
    val maghrib: PrayerPair,
    val isha: PrayerPair,
    val jumuah: List<String>,
    val fetchedAtEpochMs: Long
) {
    fun isForDate(dateKey: String): Boolean = this.dateKey == dateKey

    fun pairFor(target: PrayerTarget): PrayerPair? = when (target) {
        PrayerTarget.FAJR -> fajr
        PrayerTarget.DHUHR -> dhuhr
        PrayerTarget.ASR -> asr
        PrayerTarget.MAGHRIB -> maghrib
        PrayerTarget.ISHA -> isha
        PrayerTarget.JUMUAH_1 -> jumuah.getOrNull(0)?.let { PrayerPair(it, it) }
        PrayerTarget.JUMUAH_2 -> jumuah.getOrNull(1)?.let { PrayerPair(it, it) }
        PrayerTarget.JUMUAH_3 -> jumuah.getOrNull(2)?.let { PrayerPair(it, it) }
    }

    fun displayRows(isFriday: Boolean): List<Pair<String, PrayerPair>> {
        val rows = mutableListOf(
            "Fajr" to fajr,
            "Dhuhr" to dhuhr,
            "Asr" to asr,
            "Maghrib" to maghrib,
            "Isha" to isha
        )
        if (isFriday) {
            jumuah.forEachIndexed { index, time ->
                rows.add("Jumu'ah #${index + 1}" to PrayerPair(time, time))
            }
        }
        return rows
    }
}
