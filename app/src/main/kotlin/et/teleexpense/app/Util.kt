package et.teleexpense.app

import et.teleexpense.*
import java.time.*

fun etb(v: Double) = if (v % 1.0 == 0.0) "%,.0f".format(v) else "%,.2f".format(v)
fun catEmoji(c: String) = when {
    c.startsWith("data") -> "📶"; c.startsWith("airtime") -> "📞"; c.startsWith("sms") -> "💬"
    c.startsWith("voice") -> "🎙️"; c.startsWith("mixed") -> "🎁"; else -> "💳" }
fun catGroup(c: String) = when {
    c.startsWith("data") -> "Data"; c.startsWith("airtime") -> "Airtime"; c.startsWith("sms") -> "SMS"; else -> "Other" }
fun TxRow.dt(): LocalDateTime = LocalDateTime.parse(dateTimeIso)
fun TxRow.title() = packageName ?: when (category) {
    "airtime" -> "Airtime top-up"; "airtime_recharge" -> "Airtime recharge"
    else -> category.replace('_', ' ').replaceFirstChar { it.uppercase() } }
fun TxRow.eth() = EthDate(ethYear, ethMonth, ethDay)

enum class Period(val label: String) { Today("Today"), Week("This Week"), Month("This Month"), Year("This Year"), Custom("Custom") }
data class Range(val from: LocalDate, val to: LocalDate, val title: String)

private fun monthStart(y: Int, m: Int) = EthiopianCalendar.toGregorian(EthDate(y, m, 1))
private fun nextMonthStart(y: Int, m: Int) = if (m == 13) monthStart(y + 1, 1) else monthStart(y, m + 1)

/** offset shifts the period back/forward (days, weeks, months or years). Custom = pick any Ethiopian month. */
fun rangeOf(p: Period, offset: Int, today: LocalDate = LocalDate.now()): Range = when (p) {
    Period.Today -> today.plusDays(offset.toLong()).let { Range(it, it, EthiopianCalendar.fromGregorian(it).toString()) }
    Period.Week -> {
        val s = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).plusWeeks(offset.toLong())
        val a = EthiopianCalendar.fromGregorian(s); val b = EthiopianCalendar.fromGregorian(s.plusDays(6))
        Range(s, s.plusDays(6), "${a.monthName} ${a.day} – ${b.monthName} ${b.day}, ${b.year}")
    }
    Period.Month, Period.Custom -> {
        val e = EthiopianCalendar.fromGregorian(today)
        val idx = e.year * 13 + (e.month - 1) + offset
        val y = Math.floorDiv(idx, 13); val m = Math.floorMod(idx, 13) + 1
        Range(monthStart(y, m), nextMonthStart(y, m).minusDays(1), "${EthDate.MONTHS[m - 1]} $y")
    }
    Period.Year -> {
        val y = EthiopianCalendar.fromGregorian(today).year + offset
        Range(monthStart(y, 1), monthStart(y + 1, 1).minusDays(1), "$y")
    }
}

fun inRange(t: List<TxRow>, r: Range) = t.filter { val d = it.dt().toLocalDate(); !d.isBefore(r.from) && !d.isAfter(r.to) }
