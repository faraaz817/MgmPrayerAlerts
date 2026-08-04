package au.org.mgm.prayeralerts.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TimeParseTest {
    @Test
    fun storageRoundTripKeepsAfternoonTimes() {
        val maghrib = LocalTime.of(17, 40)
        val stored = TimeParse.formatStorage(maghrib)
        assertEquals("17:40", stored)
        assertEquals(maghrib, TimeParse.parseFlexible(stored))
    }

    @Test
    fun storageRoundTripKeepsMorningTimes() {
        val fajr = LocalTime.of(5, 46)
        val stored = TimeParse.formatStorage(fajr)
        assertEquals("5:46", stored)
        assertEquals(fajr, TimeParse.parseFlexible(stored))
    }

    @Test
    fun displayShowsMeridian() {
        assertEquals("5:40 PM", TimeParse.formatDisplay(LocalTime.of(17, 40)))
        assertEquals("5:46 AM", TimeParse.formatDisplay(LocalTime.of(5, 46)))
        assertEquals("12:27 PM", TimeParse.formatDisplay(LocalTime.of(12, 27)))
    }

    @Test
    fun parsesAmPmAnd24Hour() {
        assertEquals(LocalTime.of(13, 30), TimeParse.parseFlexible("1:30PM"))
        assertEquals(LocalTime.of(14, 15), TimeParse.parseFlexible("2:15 PM"))
        assertEquals(LocalTime.of(18, 49), TimeParse.parseFlexible("18:49"))
    }

    @Test
    fun jumuahDefaultsStayAfternoonAfterStorage() {
        val times = listOf("12:30", "13:30", "14:15").map {
            TimeParse.formatStorage(TimeParse.parseFlexible(it))
        }
        assertEquals(listOf("12:30", "13:30", "14:15"), times)
        assertEquals(LocalTime.of(13, 30), TimeParse.parseFlexible(times[1]))
        assertEquals(LocalTime.of(14, 15), TimeParse.parseFlexible(times[2]))
    }
}
