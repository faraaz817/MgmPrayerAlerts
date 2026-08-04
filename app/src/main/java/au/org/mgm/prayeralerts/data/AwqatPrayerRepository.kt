package au.org.mgm.prayeralerts.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class AwqatPrayerRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val zoneId: ZoneId = ZoneId.of("Australia/Melbourne")
) {
    companion object {
        private const val WTIMES_URL =
            "https://awqat.com.au/www/data/wtimes-AU.MELBOURNE.ini"
        private const val IQAMA_URL = "https://awqat.com.au/mgm/iqamafixed.js"
        private const val PAGE_URL = "https://awqat.com.au/mgm/"
        private val DEFAULT_JUMUAH = listOf("12:30", "13:30", "14:15")
    }

    fun fetchToday(): DaySchedule {
        val today = LocalDate.now(zoneId)
        val wtimes = httpGet(WTIMES_URL)
        val iqamaJs = httpGet(IQAMA_URL)
        val pageHtml = runCatching { httpGet(PAGE_URL) }.getOrDefault("")

        val azan = parseWtimesForDate(wtimes, today)
        val iqamaConfig = parseIqamaConfig(iqamaJs)
        val pairs = buildPairs(azan, iqamaConfig)
        val jumuah = parseJumuah(pageHtml).ifEmpty { DEFAULT_JUMUAH }

        return DaySchedule(
            dateKey = today.toString(),
            fajr = pairs[0],
            sunrise = pairs[1],
            dhuhr = pairs[2],
            asr = pairs[3],
            maghrib = pairs[4],
            isha = pairs[5],
            jumuah = jumuah.map { TimeParse.formatStorage(TimeParse.parseFlexible(it)) },
            fetchedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} for $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseWtimesForDate(raw: String, date: LocalDate): List<String> {
        val key = String.format("%02d-%02d", date.monthValue, date.dayOfMonth)
        val lineRegex = Regex("\"$key~~~~~([^\"]+)\"")
        val match = lineRegex.find(raw) ?: error("No wtimes entry for $key")
        val parts = match.groupValues[1].split("|")
        require(parts.size >= 6) { "Incomplete wtimes row for $key" }
        return parts.take(6)
    }

    private data class IqamaConfig(
        val offsetsMinutes: List<Int?>,
        val fixedTimes: List<String?>
    )

    private fun parseIqamaConfig(js: String): IqamaConfig {
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

    private fun buildPairs(azanRaw: List<String>, config: IqamaConfig): List<PrayerPair> {
        val azanTimes = azanRaw.map { TimeParse.parseFlexible(it) }
        // Indices in FIXED/JS arrays: 1=Fajr, 2=Dohr, 3=Asr, 4=Maghrib, 5=Isha
        // Shoroq iqama offset defaults to +15 in awqat page.
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
            // Sunrise is not a prayer; shown for reference only (+15 on awqat).
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

    private fun parseJumuah(html: String): List<String> {
        val regex = Regex(
            """JUMU'?AH\s+(\d{1,2}:\d{2}\s*[AP]M)\s*&\s*(\d{1,2}:\d{2}\s*[AP]M)\s*&\s*(\d{1,2}:\d{2}\s*[AP]M)""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(html.replace("&amp;", "&")) ?: return emptyList()
        return match.groupValues.drop(1).map {
            TimeParse.formatStorage(TimeParse.parseFlexible(it))
        }
    }
}
