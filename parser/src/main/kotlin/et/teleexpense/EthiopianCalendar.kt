package et.teleexpense

import java.time.LocalDate

data class EthDate(val year: Int, val month: Int, val day: Int) {
    val monthName get() = MONTHS[month - 1]
    override fun toString() = "$monthName $day, $year"
    companion object {
        val MONTHS = listOf("Meskerem","Tikimt","Hidar","Tahsas","Tir","Yekatit",
            "Megabit","Miazia","Ginbot","Sene","Hamle","Nehase","Pagume")
    }
}

/** JDN-based conversion. Handles Pagume (13th month) and Ethiopian leap years (year % 4 == 3). */
object EthiopianCalendar {
    private const val ETH_EPOCH_JDN = 1723856L
    private const val UNIX_EPOCH_JDN = 2440588L

    fun fromGregorian(d: LocalDate): EthDate {
        val days = d.toEpochDay() + UNIX_EPOCH_JDN - ETH_EPOCH_JDN
        val r = Math.floorMod(days, 1461L)
        val q = Math.floorDiv(days, 1461L)
        val n = r % 365 + 365 * (r / 1460)
        val year = 4 * q + r / 365 - r / 1460
        return EthDate(year.toInt(), (n / 30 + 1).toInt(), (n % 30 + 1).toInt())
    }

    fun toGregorian(e: EthDate): LocalDate {
        val jdn = ETH_EPOCH_JDN + 365L * (e.year - 1) + Math.floorDiv(e.year, 4) + 30L * (e.month - 1) + e.day - 1
        return LocalDate.ofEpochDay(jdn - UNIX_EPOCH_JDN)
    }
}
