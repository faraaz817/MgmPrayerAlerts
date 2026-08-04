package au.org.mgm.prayeralerts.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Storage/scheduling times are always unambiguous 24-hour `H:mm` (e.g. `17:40`).
 * UI may use [formatDisplay] for 12-hour with AM/PM.
 */
object TimeParse {
    private val hHmm = DateTimeFormatter.ofPattern("H:mm")
    private val hHmmSs = DateTimeFormatter.ofPattern("H:mm:ss")

    fun parseFlexible(raw: String): LocalTime {
        val cleaned = raw.trim()
            .uppercase(Locale.US)
            .replace(".", ":")
            .replace(" ", "")

        val ampm = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?(AM|PM)$""")
            .matchEntire(cleaned)
        if (ampm != null) {
            var hour = ampm.groupValues[1].toInt()
            val minute = ampm.groupValues[2].toInt()
            val second = ampm.groupValues[3].ifBlank { "0" }.toInt()
            val meridian = ampm.groupValues[4]
            if (meridian == "PM" && hour < 12) hour += 12
            if (meridian == "AM" && hour == 12) hour = 0
            return LocalTime.of(hour, minute, second)
        }

        return try {
            LocalTime.parse(cleaned, hHmmSs)
        } catch (_: Exception) {
            LocalTime.parse(cleaned, hHmm)
        }
    }

    /** Unambiguous storage / alarm trigger format, e.g. `5:40` for 05:40 and `17:40` for Maghrib. */
    fun formatStorage(time: LocalTime): String = time.format(hHmm)

    fun formatDisplay(time: LocalTime): String {
        val hour12 = when {
            time.hour == 0 -> 12
            time.hour > 12 -> time.hour - 12
            else -> time.hour
        }
        val meridian = if (time.hour < 12) "AM" else "PM"
        return String.format(Locale.US, "%d:%02d %s", hour12, time.minute, meridian)
    }

    fun formatDisplay(rawStorageOrFlexible: String): String =
        formatDisplay(parseFlexible(rawStorageOrFlexible))

    fun addMinutes(time: LocalTime, minutes: Int): LocalTime = time.plusMinutes(minutes.toLong())

    /** @deprecated Use [formatStorage] — kept name alias during migration. */
    fun formatShort(time: LocalTime): String = formatStorage(time)
}
