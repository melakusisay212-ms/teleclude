package et.teleexpense

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateExtractor {
    private val numeric = Regex("""(\d{1,2})/(\d{1,2})/(\d{2,4})(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
    private val iso = Regex("""(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?""")
    private val textual = Regex("""[A-Z][a-z]{2} \d{1,2}, \d{4} \d{1,2}:\d{2}:\d{2} [AP]M""")
    private val textFmt = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a", Locale.ENGLISH)

    /** First explicit date in text (dd/MM/yyyy per Ethiopian SMS convention), or null. Never invents one. */
    fun extract(t: String): LocalDateTime? {
        textual.find(t)?.let { return runCatching { LocalDateTime.parse(it.value, textFmt) }.getOrNull() }
        numeric.find(t)?.let { m ->
            val g = m.groupValues
            val y = if (g[3].length == 2) 2000 + g[3].toInt() else g[3].toInt()
            return runCatching { LocalDateTime.of(y, g[2].toInt(), g[1].toInt(),
                g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0) }.getOrNull()
        }
        iso.find(t)?.let { m ->
            val g = m.groupValues
            return runCatching { LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(),
                g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0) }.getOrNull()
        }
        return null
    }
}
