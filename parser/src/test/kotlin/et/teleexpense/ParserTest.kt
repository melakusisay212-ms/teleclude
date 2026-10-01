package et.teleexpense

import et.teleexpense.Classification.*
import java.time.LocalDateTime
import kotlin.test.*

class ParserTest {
    private fun at(m: Int, d: Int, h: Int, mi: Int, s: Int = 0) = LocalDateTime.of(2026, m, d, h, mi, s)
    private val a = "[-EBIRR-KAAFI-] Transfer ID: 802520883630, You have successfuly sent airtime top-up of ETB5 to 251981801919"
    private val b = "[-EBIRR-KAAFI-] Transfer ID: 802520883630, You have received airtime top-up of ETB5 from 251981801919"
    private val c = "[-EBIRR-KAAFI-] You have recharged ETB5 to 251981801919, your balance is ETB3.6"
    private val pkg1 = "Dear MELAKU You have paid ETB 35.00 for package Monthly student pack 1.2GB + 120SMS plus 1.2GB night bonus purchase made for 981801919 on 11/09/2026 15:52:15. Your transaction number is DIB1N45Z9Z."
    private val pkg2 = pkg1.replace("11/09/2026 15:52:15", "17/09/2026 08:38:04").replace("DIB1N45Z9Z", "DIH5SLOKQ1")
    private val p2p = "You have transferred ETB 10.00 to RUTA TAKELE (2519****3230) on 11/09/2026 21:21:46. The service fee is ETB 0.87 and 15% VAT on the service fee is ETB 0.13."
    private val act = "As per your request the new service offer Monthly student pack 1.2GB + 120SMS plus 1.2GB night bonus from telebirr to expire after 30 days is added to your service number 0981801919. The service offer is effective as of Sep 17, 2026 8:38:04 AM."
    private val bday = "Ethio telecom wishes you a Happy birthday! Please enjoy 1GB internet, 20 Min and 20 SMS package Free gift, valid for 24-hours."
    private val sent300 = "You have successfully sent Daily internet Package 300 MB to 0943178701."

    private fun p(s: String, t: LocalDateTime = at(9, 17, 8, 38, 10)) = SmsParser.parse(null, s, t)

    @Test fun exampleA() = p(a).run { assertEquals(COUNT, classification); assertEquals(5.0, amount); assertEquals("802520883630", transferId) }
    @Test fun exampleB() = p(b).run { assertEquals(IGNORE, classification); assertEquals("incoming", direction) }
    @Test fun exampleC() = p(c).run { assertEquals(REFERENCE, classification); assertEquals(5.0, amount); assertEquals("airtime_recharge", category) }
    @Test fun telebirrPackage() = p(pkg1).run {
        assertEquals(COUNT, classification); assertEquals(35.0, amount); assertEquals("DIB1N45Z9Z", transactionId)
        assertEquals("Monthly student pack 1.2GB + 120SMS plus 1.2GB night bonus", packageName)
        assertEquals(at(9, 11, 15, 52, 15), dateTime); assertTrue(dateIsExplicit)
        assertEquals(EthDate(2019, 1, 1), ethDate)
    }
    @Test fun p2pIgnored() = assertEquals(IGNORE, p(p2p).classification)
    @Test fun activationIsReference() = p(act).run {
        assertEquals(REFERENCE, classification); assertNull(amount); assertEquals(at(9, 17, 8, 38, 4), dateTime)
    }
    @Test fun freeIgnored() = assertEquals(IGNORE, p(bday).classification)
    @Test fun prepaidBalanceNotDoubleCounted() {
        val x = p("Your prepaid account has been recharged successfully. Your Recharged balance is 100.00 Birr. Your balance is 100.00 Birr.")
        assertEquals(COUNT, x.classification); assertEquals(100.0, x.amount)
    }
    @Test fun noDateFallsBackToReceived() = p(a, at(9, 20, 1, 1)).run { assertFalse(dateIsExplicit); assertEquals(at(9, 20, 1, 1), dateTime) }
}

class GroupingTest {
    private fun cands(vararg s: Pair<String, LocalDateTime>) = s.map { SmsParser.parse(null, it.first, it.second) }
    private val t0 = LocalDateTime.of(2026, 9, 17, 8, 38, 0)
    private val a = "Transfer ID: 802520883630, You have successfuly sent airtime top-up of ETB5 to 251981801919"
    private val b = "Transfer ID: 802520883630, You have received airtime top-up of ETB5 from 251981801919"
    private val c = "You have recharged ETB5 to 251981801919, your balance is ETB3.6"
    private val pkg = "You have paid ETB 35.00 for package Monthly student pack 1.2GB + 120SMS plus 1.2GB night bonus purchase made for 981801919 on 17/09/2026 08:38:04. Your transaction number is DIH5SLOKQ1."

    @Test fun threeEbirrSmsIsOneFiveBirrExpense() {
        val tx = TransactionGrouper().group(cands(a to t0, b to t0.plusSeconds(2), c to t0.plusSeconds(3)))
        assertEquals(1, tx.size); assertEquals(5.0, tx[0].amount); assertEquals(3, tx[0].smsCount)
    }
    @Test fun twoPackagePurchasesAreTwoExpenses() {
        val p2 = pkg.replace("17/09", "11/09").replace("DIH5SLOKQ1", "DIB1N45Z9Z")
        val tx = TransactionGrouper().group(cands(p2 to t0.minusDays(6), pkg to t0))
        assertEquals(2, tx.size); assertTrue(tx.all { it.amount == 35.0 })
    }
    @Test fun duplicateSmsSameIdCountedOnce() =
        assertEquals(1, TransactionGrouper().group(cands(pkg to t0, pkg to t0.plusSeconds(30))).size)
    @Test fun p2pAndFreeGiveZero() {
        val p2p = "You have transferred ETB 10.00 to RUTA (2519****3230) on 11/09/2026 21:21:46. The service fee is ETB 0.87"
        val free = "Happy birthday! Please enjoy 1GB internet Free gift, valid for 24-hours."
        assertEquals(0, TransactionGrouper().group(cands(p2p to t0, free to t0)).size)
    }
    @Test fun unpricedPackageIsZeroUntilPaidMatch() {
        val sent = "You have successfully sent Daily internet Package 300 MB to 0943178701."
        assertEquals(0, TransactionGrouper().group(cands(sent to t0)).size)
        val paid = "You have paid ETB 10.00 for Daily internet Package 300 MB to 0943178701 on 17/09/2026 08:37:00. Your transaction number is ABC123XYZ."
        val tx = TransactionGrouper().group(cands(sent to t0, paid to t0))
        assertEquals(1, tx.size); assertEquals(10.0, tx[0].amount); assertEquals(2, tx[0].smsCount)
    }
    @Test fun activationLinksToPayment() {
        val act = "As per your request the new service offer Monthly student pack 1.2GB + 120SMS plus 1.2GB night bonus from telebirr to expire after 30 days is added to your service number 0981801919. The service offer is effective as of Sep 17, 2026 8:38:04 AM."
        val tx = TransactionGrouper().group(cands(pkg to t0, act to t0.plusSeconds(5)))
        assertEquals(1, tx.size); assertEquals(2, tx[0].smsCount)
    }
}

class CalendarTest {
    private fun e(y: Int, m: Int, d: Int) = EthiopianCalendar.fromGregorian(java.time.LocalDate.of(y, m, d))
    @Test fun knownDates() {
        assertEquals(EthDate(2019, 1, 7), e(2026, 9, 17))
        assertEquals(EthDate(2019, 1, 1), e(2026, 9, 11))
        assertEquals(EthDate(2018, 13, 5), e(2026, 9, 10))
        assertEquals(EthDate(2016, 1, 1), e(2023, 9, 12))  // year before Gregorian leap
        assertEquals(EthDate(2015, 13, 6), e(2023, 9, 11)) // Ethiopian leap year has Pagume 6
    }
    @Test fun roundTrip() {
        var d = java.time.LocalDate.of(2020, 1, 1)
        repeat(3000) { assertEquals(d, EthiopianCalendar.toGregorian(EthiopianCalendar.fromGregorian(d))); d = d.plusDays(1) }
    }
}
