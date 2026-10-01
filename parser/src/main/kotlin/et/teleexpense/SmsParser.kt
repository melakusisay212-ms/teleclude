package et.teleexpense

import et.teleexpense.Classification.*
import java.time.LocalDateTime

/** Modular rule. Add new SMS formats by adding a Rule; the engine never changes. */
fun interface Rule { fun apply(t: String, received: LocalDateTime, c: Ctx): Candidate? }

class Ctx(val provider: String, val txDate: LocalDateTime?) {
    fun cand(cl: Classification, cat: String, why: String, rcv: LocalDateTime, amount: Double? = null,
             dir: String = "outgoing", pkg: String? = null, rec: String? = null,
             tx: String? = null, tr: String? = null, conf: Double = 0.9) =
        Candidate(cl, provider, cat, amount, dir, pkg, rec, tx, tr, txDate ?: rcv, rcv, txDate != null, why, conf)
}

object ProviderDetector {
    fun detect(sender: String?, t: String): String {
        val s = t.lowercase()
        return when {
            "ebirr" in s -> "eBirr"
            "you have paid etb" in s || "telebirr" in s || "you have transferred etb" in s -> "telebirr"
            "ethio telecom" in s || "prepaid account" in s || "service offer" in s -> "Ethio telecom"
            sender?.contains("bank", true) == true || sender?.contains("cbe", true) == true -> "Bank"
            else -> "Unknown"
        }
    }
}

object SmsParser {
    private const val NUM = """(\d+(?:,\d{3})*(?:\.\d+)?)"""
    private const val ETB = """ETB\s?$NUM"""
    private fun amt(s: String) = s.replace(",", "").toDouble()
    private val transferId = Regex("""Transfer ID:\s*(\d+)""")
    private val txnNo = Regex("""transaction number is\s+([A-Z0-9]{6,})""", RegexOption.IGNORE_CASE)
    private val failed = Regex("""\b(failed|reversed|unsuccessful|declined)\b""", RegexOption.IGNORE_CASE)
    private val free = Regex("""\b(free gift|free|complimentary|promotional|promotion)\b""", RegexOption.IGNORE_CASE)

    private val rules: List<Rule> = listOf(
        Rule { t, r, c -> if ("You have transferred ETB" in t)
            c.cand(IGNORE, "person_to_person_transfer", "P2P transfer; fee/VAT not telecom", r) else null },
        Rule { t, r, c -> Regex("""received airtime top-up of $ETB""").find(t)?.let {
            c.cand(IGNORE, "airtime", "incoming airtime", r, amt(it.groupValues[1]), "incoming",
                tr = transferId.find(t)?.groupValues?.get(1)) } },
        Rule { t, r, c -> Regex("""sent airtime top-up of $ETB to (\d+)""").find(t)?.let {
            c.cand(COUNT, "airtime", "outgoing airtime top-up", r, amt(it.groupValues[1]),
                rec = it.groupValues[2], tr = transferId.find(t)?.groupValues?.get(1)) } },
        Rule { t, r, c -> Regex("""You have recharged $ETB to (\d+)""").find(t)?.let {
            c.cand(REFERENCE, "airtime_recharge", "recharge confirmation; balance ignored", r,
                amt(it.groupValues[1]), rec = it.groupValues[2]) } },
        Rule { t, r, c -> Regex("""Recharged balance is $NUM Birr""", RegexOption.IGNORE_CASE).find(t)?.let {
            c.cand(COUNT, "airtime_recharge", "prepaid recharge; 'Your balance' ignored", r, amt(it.groupValues[1])) } },
        Rule { t, r, c -> Regex("""You have paid $ETB for (?:package )?(.+?) (?:purchase made for (\d+)|to (\d+))""").find(t)?.let {
            val name = it.groupValues[2].trim()
            c.cand(COUNT, categoryOf(name), "paid package", r, amt(it.groupValues[1]), pkg = name,
                rec = (it.groupValues[3] + it.groupValues[4]).ifEmpty { null }, tx = txnNo.find(t)?.groupValues?.get(1)) } },
        // Free rule runs AFTER paid rules, so "night bonus" inside a paid pack is not misread.
        Rule { t, r, c -> if (free.containsMatchIn(t)) c.cand(IGNORE, "free_package", "free/promotional", r, 0.0) else null },
        Rule { t, r, c -> Regex("""successfully sent (.+?) to (\d{9,12})""").find(t)?.let {
            c.cand(REFERENCE, categoryOf(it.groupValues[1]), "package sent, price unknown", r,
                pkg = it.groupValues[1].trim(), rec = it.groupValues[2]) } },
        Rule { t, r, c -> Regex("""service offer (.+?) from \w+ to expire.*?service number (\d+)""").find(t)?.let {
            c.cand(REFERENCE, "package_activation", "activation, no price", r,
                pkg = it.groupValues[1].trim(), rec = it.groupValues[2]) } },
    )

    fun categoryOf(name: String): String {
        val n = name.lowercase()
        val d = Regex("""\d\s?(gb|mb)""").containsMatchIn(n) || "internet" in n || "data" in n
        val s = "sms" in n
        val v = Regex("""\bmin\b|voice""").containsMatchIn(n)
        return when {
            listOf(d, s, v).count { it } > 1 -> "mixed_package"
            d -> "data_package"; s -> "sms_package"; v -> "voice_package"; else -> "other"
        }
    }

    fun parse(sender: String?, body: String, received: LocalDateTime): Candidate {
        val t = body.replace(Regex("\\s+"), " ").trim()
        val ctx = Ctx(ProviderDetector.detect(sender, t), DateExtractor.extract(t))
        if (failed.containsMatchIn(t)) return ctx.cand(IGNORE, "other", "failed/reversed", received)
        for (rule in rules) rule.apply(t, received, ctx)?.let { return it }
        return ctx.cand(REFERENCE, "other", "unrecognized; conservative", received, conf = 0.2)
    }
}
