package au.org.mgm.prayeralerts.data

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AwqatScheduleParserTest {
    private val oct10 = """
        var JS_TIMES = [
        "10-10~~~~~05:10|06:41|13:10|16:48|19:38|20:47",
        ]
    """.trimIndent()

    private val iqamaJs = """
        var FIXED_IQAMA_TIMES = ['','','13:45','','','21:00'];
        var JS_IQAMA_TIME = [0,30,,30,5,];
    """.trimIndent()

    @Test
    fun oct10MatchesMosqueBoard() {
        val schedule = AwqatScheduleParser.toSchedule(
            wtimes = oct10,
            iqamaJs = iqamaJs,
            pageHtml = "JUMU'AH 12:30PM & 1:30PM & 2:15PM",
            today = LocalDate.of(2026, 10, 10)
        )
        assertEquals("5:10", schedule.fajr.azan)
        assertEquals("5:40", schedule.fajr.jamaat)
        assertEquals("6:41", schedule.sunrise.azan)
        assertEquals("6:56", schedule.sunrise.jamaat)
        assertEquals("13:10", schedule.dhuhr.azan)
        assertEquals("13:45", schedule.dhuhr.jamaat)
        assertEquals("16:48", schedule.asr.azan)
        assertEquals("17:18", schedule.asr.jamaat)
        assertEquals("19:38", schedule.maghrib.azan)
        assertEquals("19:43", schedule.maghrib.jamaat)
        assertEquals("20:47", schedule.isha.azan)
        assertEquals("21:00", schedule.isha.jamaat)
        assertEquals(listOf("12:30", "13:30", "14:15"), schedule.jumuah)
    }

    @Test
    fun htmlChallengeFallsBackToBundledTimes() {
        val html = """
            <!DOCTYPE html>
            <html><head><title>One moment, please...</title></head>
            <body>Please wait while your request is being verified...</body></html>
        """.trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(html.toResponseBody("text/html".toMediaType()))
                    .build()
            })
            .build()

        val result = AwqatPrayerRepository(
            client = client,
            bundledWtimes = oct10,
            bundledIqama = iqamaJs
        ).fetchToday(today = LocalDate.of(2026, 10, 10))

        assertEquals("5:10", result.schedule.fajr.azan)
        assertEquals("13:45", result.schedule.dhuhr.jamaat)
        assertEquals("21:00", result.schedule.isha.jamaat)
        assertNull(result.liveIqamaJs)
    }
}
