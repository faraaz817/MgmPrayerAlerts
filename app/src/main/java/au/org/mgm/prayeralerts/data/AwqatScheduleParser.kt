package au.org.mgm.prayeralerts.data

import java.time.LocalDate

internal data class IqamaConfig(
    val offsetsMinutes: List<Int?>,
    val fixedTimes: List<String?>
)

internal object AwqatScheduleParser {
    val defaultJumuah = listOf("12:30", "13:30", "14:15")

    fun looksLikeWtimes(raw: String): Boolean =
        raw.contains("~~~~~") && !looksLikeHtmlChallenge(raw)

    fun looksLikeIqama(raw: String): Boolean =
        raw.contains("FIXED_IQAMA_TIMES") && !looksLikeHtmlChallenge(raw)

    fun looksLikeHtmlChallenge(raw: String): Boolean {
        val trimmed = raw.trimStart()
        return trimmed.startsWith("<") ||
            trimmed.contains("One moment, please", ignoreCase = true) ||
            trimmed.contains("request is being verified", ignoreCase = true)
    }

    fun parseWtimesForDate(raw: String, date: LocalDate): List<String> {
        val key = String.format("%02d-%02d", date.monthValue, date.dayOfMonth)
        val match = Regex("\"$key~~~~~([^\"]+)\"").find(raw)
            ?: error("No wtimes entry for $key")
        val parts = match.groupValues[1].split("|")
        require(parts.size >= 6) { "Incomplete wtimes row for $key" }
        return parts.take(6)
    }

    fun parseIqamaConfig(js: String): IqamaConfig {
        val fixed = Regex("""FIXED_IQAMA_TIMES\s*=\s*\[([^\]]*)\]""")
            .find(js)
            ?.groupValues
            ?.get(1)
            ?.split(",")
            ?.map { token ->
                val cleaned = token.trim().trim('\'', '"')
                cleaned.ifBlank { null }
            }
            ?: List(6) { null }

        val offsetsRaw = Regex("""JS_IQAMA_TIME\s*=\s*\[([^\]]*)\]""")
            .find(js)
            ?.groupValues
            ?.get(1)
            ?: "0,30,,30,5,"

        val offsets = offsetsRaw.split(",").map { token ->
            val cleaned = token.trim()
            if (cleaned.isEmpty()) null else cleaned.toIntOrNull()
        }

        return IqamaConfig(
            offsetsMinutes = offsets,
            fixedTimes = fixed
        )
    }

    fun buildPairs(azanRaw: List<String>, config: IqamaConfig): List<PrayerPair> {
        val azanTimes = azanRaw.map { TimeParse.parseFlexible(it) }
        // Indices in FIXED/JS arrays: 1=Fajr, 2=Dohr, 3=Asr, 4=Maghrib, 5=Isha
        val shoroqOffset = 15

        fun jamaatFor(azanIndex: Int, configIndex: Int, defaultOffset: Int): String {
            val azan = azanTimes[azanIndex]
            val fixed = config.fixedTimes.getOrNull(configIndex)
            val jamaatTime = if (!fixed.isNullOrBlank()) {
                TimeParse.parseFlexible(fixed)
            } else {
                val offset = config.offsetsMinutes.getOrNull(configIndex) ?: defaultOffset
                TimeParse.addMinutes(azan, offset)
            }
            return TimeParse.formatStorage(jamaatTime)
        }

        return listOf(
            PrayerPair(TimeParse.formatStorage(azanTimes[0]), jamaatFor(0, 1, 30)),
            PrayerPair(
                TimeParse.formatStorage(azanTimes[1]),
                TimeParse.formatStorage(TimeParse.addMinutes(azanTimes[1], shoroqOffset))
            ),
            PrayerPair(TimeParse.formatStorage(azanTimes[2]), jamaatFor(2, 2, 15)),
            PrayerPair(TimeParse.formatStorage(azanTimes[3]), jamaatFor(3, 3, 30)),
            PrayerPair(TimeParse.formatStorage(azanTimes[4]), jamaatFor(4, 4, 5)),
            PrayerPair(TimeParse.formatStorage(azanTimes[5]), jamaatFor(5, 5, 10))
        )
    }

    fun parseJumuah(html: String): List<String> {
        val regex = Regex(
            """JUMU'?AH\s+(\d{1,2}:\d{2}\s*[AP]M)\s*&\s*(\d{1,2}:\d{2}\s*[AP]M)\s*&\s*(\d{1,2}:\d{2}\s*[AP]M)""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(html.replace("&amp;", "&")) ?: return emptyList()
        return match.groupValues.drop(1).map {
            TimeParse.formatStorage(TimeParse.parseFlexible(it))
        }
    }

    fun toSchedule(
        wtimes: String,
        iqamaJs: String,
        pageHtml: String,
        today: LocalDate,
        fetchedAtEpochMs: Long = System.currentTimeMillis()
    ): DaySchedule {
        val pairs = buildPairs(parseWtimesForDate(wtimes, today), parseIqamaConfig(iqamaJs))
        val jumuah = parseJumuah(pageHtml).ifEmpty { defaultJumuah }
        return DaySchedule(
            dateKey = today.toString(),
            fajr = pairs[0],
            sunrise = pairs[1],
            dhuhr = pairs[2],
            asr = pairs[3],
            maghrib = pairs[4],
            isha = pairs[5],
            jumuah = jumuah.map { TimeParse.formatStorage(TimeParse.parseFlexible(it)) },
            fetchedAtEpochMs = fetchedAtEpochMs
        )
    }
}

data class AwqatFetchResult(
    val schedule: DaySchedule,
    val liveIqamaJs: String? = null
)
