package et.teleexpense

import java.time.LocalDateTime

enum class Classification { COUNT, IGNORE, REFERENCE }

/** One parsed SMS. The raw body is never stored. */
data class Candidate(
    val classification: Classification,
    val provider: String,
    val category: String,
    val amount: Double? = null,
    val direction: String = "outgoing",
    val packageName: String? = null,
    val recipient: String? = null,
    val transactionId: String? = null,
    val transferId: String? = null,
    val dateTime: LocalDateTime,      // explicit transaction date, else received time
    val receivedAt: LocalDateTime,
    val dateIsExplicit: Boolean,
    val reason: String,               // explainability
    val confidence: Double = 0.9,
) { val ethDate get() = EthiopianCalendar.fromGregorian(dateTime.toLocalDate()) }

data class TelecomTransaction(val primary: Candidate, val related: MutableList<Candidate> = mutableListOf()) {
    val amount get() = primary.amount!!
    val smsCount get() = 1 + related.size
}
