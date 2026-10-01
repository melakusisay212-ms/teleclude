package et.teleexpense.app

import android.content.Context
import android.net.Uri
import et.teleexpense.*
import java.time.*

class Prefs(c: Context) {
    private val p = c.getSharedPreferences("tracker", Context.MODE_PRIVATE)
    var startedAt: Long get() = p.getLong("startedAt", 0); set(v) = p.edit().putLong("startedAt", v).apply()
    var lastProcessed: Long get() = p.getLong("last", 0); set(v) = p.edit().putLong("last", v).apply()
    val isSetUp get() = startedAt != 0L
}

class Tracker(val ctx: Context) {
    val ctx0 = ctx
    private val dao = AppDb.get(ctx).dao()
    val prefs = Prefs(ctx)
    private val zone = ZoneId.systemDefault()

    /** Start of the current Ethiopian month, as epoch millis (first scan window). */
    fun currentEthMonthStart(): Long {
        val e = EthiopianCalendar.fromGregorian(LocalDate.now())
        return EthiopianCalendar.toGregorian(EthDate(e.year, e.month, 1)).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Result numbers for the first-run "Found: …" screen. */
    data class ScanResult(val relevantSms: Int, val events: Int, val expenses: Int)

    suspend fun initialScan(thisMonth: Boolean): ScanResult {
        val now = System.currentTimeMillis()
        prefs.startedAt = now
        if (!thisMonth) { prefs.lastProcessed = now; return ScanResult(0, 0, 0) }
        return scanSince(currentEthMonthStart())
    }

    /** "Scan New Messages": only after last processed timestamp. */
    suspend fun scanNew() = scanSince(maxOf(prefs.lastProcessed, prefs.startedAt))

    private suspend fun scanSince(sinceMillis: Long): ScanResult {
        var read = 0; var kept = 0; var newest = sinceMillis
        ctx.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"),
            "date > ?", arrayOf(sinceMillis.toString()), "date ASC")?.use { c ->
            while (c.moveToNext()) {
                read++
                val ts = c.getLong(2); newest = maxOf(newest, ts)
                if (ingest(c.getString(0), c.getString(1), ts)) kept++
            }
        }
        prefs.lastProcessed = newest
        regroup()
        val n = dao.allTx().count { !it.deleted }
        return ScanResult(kept, kept, n)
    }

    /** Called by scan and by the live receiver. Returns true if SMS was telecom-relevant. */
    suspend fun ingest(sender: String?, body: String, receivedMillis: Long): Boolean {
        val rcv = LocalDateTime.ofInstant(Instant.ofEpochMilli(receivedMillis), zone)
        val c = SmsParser.parse(sender, body, rcv)
        if (c.provider == "Unknown" && c.confidence < 0.5) return false // unrelated SMS: not stored
        dao.insertCandidate(CandidateRow(0, (body.trim() + receivedMillis).hashCode(), c.classification.name, c.provider,
            c.category, c.amount, c.direction, c.packageName, c.recipient, c.transactionId, c.transferId,
            c.dateTime.toString(), c.receivedAt.toString(), c.dateIsExplicit, c.reason, c.confidence))
        return true
    }

    suspend fun regroup() {
        val cands = dao.allCandidates().map {
            Candidate(Classification.valueOf(it.classification), it.provider, it.category, it.amount, it.direction,
                it.packageName, it.recipient, it.transactionId, it.transferId, LocalDateTime.parse(it.dateTimeIso),
                LocalDateTime.parse(it.receivedIso), it.explicit, it.reason, it.confidence)
        }
        val existing = dao.allTx().associateBy { it.key }
        for (t in TransactionGrouper().group(cands)) {
            val p = t.primary
            val key = p.transactionId ?: p.transferId ?: "${p.category}|${p.amount}|${p.recipient}|${p.receivedAt}"
            if (existing[key]?.userEdited == true) continue // never overwrite user corrections
            val e = p.ethDate
            dao.upsert(TxRow(key, p.provider, p.category, t.amount, p.packageName, p.recipient, p.transactionId,
                p.dateTime.toString(), e.year, e.month, e.day, t.smsCount, p.confidence))
        }
    }
}
