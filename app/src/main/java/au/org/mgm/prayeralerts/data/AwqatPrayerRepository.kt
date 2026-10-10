package au.org.mgm.prayeralerts.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class AwqatPrayerRepository(
    private val client: OkHttpClient = defaultClient(),
    private val zoneId: ZoneId = ZoneId.of("Australia/Melbourne"),
    private val bundledWtimes: String,
    private val bundledIqama: String
) {
    companion object {
        private const val BASE = "https://www.awqat.com.au"
        const val PAGE_URL = "$BASE/mgm/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

        private val WTIMES_URLS = listOf(
            "$BASE/www/data/wtimes-AU.MELBOURNE.ini",
            "https://awqat.com.au/www/data/wtimes-AU.MELBOURNE.ini"
        )
        private val IQAMA_URLS = listOf(
            "$BASE/mgm/iqamafixed.js",
            "https://awqat.com.au/mgm/iqamafixed.js"
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-AU,en;q=0.9")
                    .header("Referer", PAGE_URL)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun fetchToday(
        today: LocalDate = LocalDate.now(zoneId),
        cachedIqamaJs: String? = null
    ): AwqatFetchResult {
        val remoteWtimes = firstMatching(WTIMES_URLS, AwqatScheduleParser::looksLikeWtimes)
        val remoteIqama = firstMatching(IQAMA_URLS, AwqatScheduleParser::looksLikeIqama)
        val pageHtml = runCatching { httpGet(PAGE_URL) }.getOrDefault("")
            .takeIf { !AwqatScheduleParser.looksLikeHtmlChallenge(it) }
            .orEmpty()

        val wtimes = remoteWtimes
            ?: bundledWtimes.takeIf { AwqatScheduleParser.looksLikeWtimes(it) }
            ?: error("Could not load Melbourne prayer times from Awqat")
        val iqamaJs = sequenceOf(remoteIqama, cachedIqamaJs, bundledIqama)
            .firstOrNull { it != null && AwqatScheduleParser.looksLikeIqama(it) }
            ?: error("Could not load MGM iqama times from Awqat")

        return AwqatFetchResult(
            schedule = AwqatScheduleParser.toSchedule(wtimes, iqamaJs, pageHtml, today),
            liveIqamaJs = remoteIqama
        )
    }

    private fun firstMatching(urls: List<String>, accept: (String) -> Boolean): String? {
        urls.forEach { url ->
            val body = runCatching { httpGet(url) }.getOrNull() ?: return@forEach
            if (accept(body)) return body
        }
        return null
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
}
