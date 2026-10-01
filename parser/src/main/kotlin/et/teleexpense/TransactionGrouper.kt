package et.teleexpense

import et.teleexpense.Classification.*
import java.time.Duration

/** Explainable grouping: strong IDs first, then amount + recipient/package + time proximity. */
class TransactionGrouper(private val window: Duration = Duration.ofMinutes(15)) {

    fun group(input: List<Candidate>): List<TelecomTransaction> {
        val txs = mutableListOf<TelecomTransaction>()
        val sorted = input.sortedBy { it.receivedAt }
        for (c in sorted.filter { it.classification == COUNT }) {
            val dup = txs.firstOrNull { sameId(it.primary, c) ||
                (noIds(it.primary, c) && it.primary.amount == c.amount && it.primary.category == c.category &&
                    it.primary.recipient == c.recipient && near(it.primary, c, Duration.ofMinutes(2))) }
            if (dup != null) dup.related += c else txs += TelecomTransaction(c)
        }
        for (c in sorted.filter { it.classification != COUNT })
            txs.firstOrNull { supports(it.primary, c) }?.related?.add(c)
        return txs // only COUNT-anchored groups become expenses
    }

    private fun sameId(a: Candidate, b: Candidate) =
        (a.transferId != null && a.transferId == b.transferId) ||
        (a.transactionId != null && a.transactionId == b.transactionId)
    private fun noIds(a: Candidate, b: Candidate) =
        listOf(a.transferId, a.transactionId, b.transferId, b.transactionId).all { it == null }
    private fun near(a: Candidate, b: Candidate, w: Duration) =
        Duration.between(a.receivedAt, b.receivedAt).abs() <= w ||
        Duration.between(a.dateTime, b.dateTime).abs() <= w

    private fun supports(p: Candidate, c: Candidate): Boolean {
        if (sameId(p, c)) return true
        if (!near(p, c, window)) return false
        val amountOk = c.amount == null || c.amount == 0.0 || c.amount == p.amount
        val pkgOk = c.packageName != null && c.packageName == p.packageName
        val recOk = c.recipient != null && p.recipient != null && c.recipient.takeLast(9) == p.recipient.takeLast(9)
        val catOk = c.category == p.category ||
            (c.category == "airtime_recharge" && p.category == "airtime") ||
            (c.category == "package_activation" && p.category.endsWith("package"))
        return catOk && amountOk && (pkgOk || recOk)
    }
}
