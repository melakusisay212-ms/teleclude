package et.teleexpense.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import et.teleexpense.*
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------- shared ----------
@Composable fun PeriodBar(p: Period, off: Int, title: String, fg: Color, onPeriod: (Period) -> Unit, onOff: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Period.values().forEach { q ->
            val sel = q == p
            Text(q.label, Modifier.clip(RoundedCornerShape(50)).background(if (sel) Green else fg.copy(alpha = .12f))
                .clickable { onPeriod(q) }.padding(horizontal = 14.dp, vertical = 7.dp),
                color = if (sel) Color.Black else fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", Modifier.clickable { onOff(off - 1) }.padding(12.dp), color = fg, fontSize = 22.sp)
        Text(title, Modifier.weight(1f), color = fg, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text("›", Modifier.clickable { if (off < 0) onOff(off + 1) }.padding(12.dp), color = if (off < 0) fg else fg.copy(alpha = .25f), fontSize = 22.sp)
    }
}

@Composable fun TxCard(t: TxRow, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }, shape = RoundedCornerShape(14.dp)) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(catEmoji(t.category), fontSize = 26.sp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title(), fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text("${t.eth()} · ${t.dt().format(DateTimeFormatter.ofPattern("HH:mm"))}", fontSize = 12.sp, color = Color.Gray)
            Text(t.provider, fontSize = 12.sp, color = Color.Gray)
        }
        Text("${etb(t.amount)} ETB", fontWeight = FontWeight.Bold)
    }
}

@Composable fun Bars(data: List<Pair<String, Double>>) {
    val max = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        data.forEach { (l, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().height(if (v > 0) (80 * (v / max)).dp.coerceAtLeast(3.dp) else 0.dp).background(Green, RoundedCornerShape(4.dp)))
                Text(l, fontSize = 8.sp, maxLines = 1, color = Color.Gray)
            }
        }
    }
}

@Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp)) {
    Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); content() }
}

// ---------- home ----------
@Composable fun HomeScreen(txs: List<TxRow>, summary: Tracker.ScanResult?, msg: String?, onScan: () -> Unit, onNav: (Int) -> Unit, onOpen: (String) -> Unit) {
    var p by remember { mutableStateOf(Period.Month) }; var off by remember { mutableStateOf(0) }
    val r = rangeOf(p, off); val list = inRange(txs, r); val total = list.sumOf { it.amount }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF0B3D2E)).padding(20.dp)) {
            Text("Tele Expense", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Where is your money going?", color = Green, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            PeriodBar(p, off, r.title, Color.White, { p = it; off = 0 }) { off = it }
            Text("ETB ${etb(total)}", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text("${list.size} transaction(s)", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            summary?.let { Text("Found ${it.relevantSms} relevant messages · ${it.expenses} expenses", color = Green, fontSize = 12.sp) }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("🧾" to "Transactions", "📅" to "Calendar", "📊" to "Analytics", "🔄" to "Scan New").forEachIndexed { i, (e, l) ->
                Card(Modifier.weight(1f).clickable { if (i == 3) onScan() else onNav(i + 1) }, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(e, fontSize = 22.sp); Text(l, fontSize = 11.sp, maxLines = 1) } }
            }
        }
        msg?.let { Text(it, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = Color.Gray) }
        val groups = listOf("Data" to "📶", "Airtime" to "📞", "SMS" to "💬", "Other" to "💳")
        val sums = groups.map { (g, _) -> list.filter { catGroup(it.category) == g }.sumOf { it.amount } }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEachIndexed { i, (g, e) ->
                val top = sums[i] > 0 && sums[i] == sums.max()
                Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), border = if (top) BorderStroke(2.dp, Green) else null) {
                    Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(e, fontSize = 20.sp); Text(g, fontSize = 12.sp); Text(etb(sums[i]), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }
        }
        Text("Recent", Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), fontWeight = FontWeight.Bold)
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (list.isEmpty()) Text("No telecom expenses in this period yet.", color = Color.Gray)
            list.take(5).forEach { TxCard(it) { onOpen(it.key) } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- transactions ----------
@Composable fun TransactionsScreen(txs: List<TxRow>, onOpen: (String) -> Unit) {
    var p by remember { mutableStateOf(Period.Month) }; var off by remember { mutableStateOf(0) }
    val r = rangeOf(p, off); val list = inRange(txs, r)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PeriodBar(p, off, r.title, MaterialTheme.colorScheme.onBackground, { p = it; off = 0 }) { off = it }
        Text("${list.size} transactions · ETB ${etb(list.sumOf { it.amount })}", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyColumn {
            if (list.isEmpty()) item { Text("Nothing here yet.", Modifier.padding(24.dp), color = Color.Gray) }
            items(list.size) { i -> TxCard(list[i]) { onOpen(list[i].key) } }
        }
    }
}

// ---------- ethiopian calendar ----------
@Composable fun CalendarScreen(txs: List<TxRow>, onOpen: (String) -> Unit) {
    var off by remember { mutableStateOf(0) }; var sel by remember { mutableStateOf<Int?>(null) }
    val r = rangeOf(Period.Month, off); val month = inRange(txs, r)
    val days = (ChronoUnit.DAYS.between(r.from, r.to) + 1).toInt()
    val startCol = r.from.dayOfWeek.value % 7 // Sunday = 0
    val byDay = month.groupBy { it.ethDay }
    val today = EthiopianCalendar.fromGregorian(LocalDate.now())
    val maxDay = byDay.values.maxOfOrNull { l -> l.sumOf { it.amount } } ?: 1.0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", Modifier.clickable { off--; sel = null }.padding(12.dp), fontSize = 24.sp)
            Text(r.title, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("›", Modifier.clickable { off++; sel = null }.padding(12.dp), fontSize = 24.sp)
        }
        Text("ETB ${etb(month.sumOf { it.amount })} this month", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row { listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp) } }
        val cells = startCol + days
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.padding(vertical = 2.dp)) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - startCol + 1
                    if (day < 1 || day > days) { Spacer(Modifier.weight(1f)) ; continue }
                    val spend = byDay[day]?.sumOf { it.amount } ?: 0.0
                    val isToday = off == 0 && today.day == day
                    Column(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (spend > 0) Green.copy(alpha = .2f + .5f * (spend / maxDay).toFloat()) else Color.Transparent)
                        .border(if (sel == day) 2.dp else if (isToday) 1.dp else 0.dp, if (sel == day) Green else Color.Gray, RoundedCornerShape(10.dp))
                        .clickable { sel = day }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$day", fontSize = 14.sp, fontWeight = if (spend > 0) FontWeight.Bold else FontWeight.Normal)
                        Text(if (spend > 0) "●" else " ", fontSize = 8.sp, color = Green)
                    }
                }
            }
        }
        sel?.let { d ->
            val list = byDay[d].orEmpty()
            Spacer(Modifier.height(14.dp))
            Text("${EthDate.MONTHS[EthiopianCalendar.fromGregorian(r.from).month - 1]} $d, ${EthiopianCalendar.fromGregorian(r.from).year}", fontWeight = FontWeight.Bold)
            Text("Total: ETB ${etb(list.sumOf { it.amount })}", color = Color.Gray)
            if (list.isEmpty()) Text("No telecom spending", Modifier.padding(top = 8.dp), color = Color.Gray)
            list.forEach { TxCard(it) { onOpen(it.key) } }
        }
    }
}

// ---------- analytics ----------
@Composable fun AnalyticsScreen(txs: List<TxRow>) {
    var p by remember { mutableStateOf(Period.Month) }; var off by remember { mutableStateOf(0) }
    val r = rangeOf(p, off); val list = inRange(txs, r); val total = list.sumOf { it.amount }
    val days = (ChronoUnit.DAYS.between(r.from, r.to) + 1).toInt()
    val elapsed = (ChronoUnit.DAYS.between(r.from, minOf(r.to, LocalDate.now())) + 1).toInt().coerceIn(1, days)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Where is my money going?", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            PeriodBar(p, off, r.title, MaterialTheme.colorScheme.onBackground, { p = it; off = 0 }) { off = it }
        }
        Section("Total spending") {
            Text("ETB ${etb(total)}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row { listOf("Transactions" to "${list.size}", "Avg / day" to etb(total / elapsed),
                "Avg / tx" to if (list.isEmpty()) "–" else etb(total / list.size)).forEach { (k, v) ->
                Column(Modifier.weight(1f)) { Text(v, fontWeight = FontWeight.Bold); Text(k, fontSize = 11.sp, color = Color.Gray) } } }
        }
        Section("By category") {
            if (total == 0.0) Text("No data for this period.", color = Color.Gray)
            listOf("Data", "Airtime", "SMS", "Other").forEach { g ->
                val v = list.filter { catGroup(it.category) == g }.sumOf { it.amount }
                if (v > 0) {
                    val f = (v / total).toFloat()
                    Row(Modifier.fillMaxWidth()) { Text(g, Modifier.weight(1f)); Text("${(f * 100).toInt()}% · ${etb(v)} ETB", fontSize = 13.sp) }
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(8.dp).background(Color.Gray.copy(alpha = .2f), RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Green, RoundedCornerShape(4.dp))) }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        if (days <= 31) Section("Daily spending") {
            Bars((0 until days).map { i -> val d = r.from.plusDays(i.toLong())
                "${EthiopianCalendar.fromGregorian(d).day}" to list.filter { it.dt().toLocalDate() == d }.sumOf { it.amount } })
        }
        Section("Weekly spending") {
            Bars((0 until (days + 6) / 7).map { w -> val a = r.from.plusDays(w * 7L); val b = a.plusDays(6)
                "W${w + 1}" to list.filter { val d = it.dt().toLocalDate(); !d.isBefore(a) && !d.isAfter(b) }.sumOf { it.amount } }.takeLast(12))
        }
        Section("Monthly spending · ${EthiopianCalendar.fromGregorian(r.from).year}") {
            val y = EthiopianCalendar.fromGregorian(r.from).year
            Bars((1..13).map { m -> EthDate.MONTHS[m - 1].take(3) to txs.filter { it.ethYear == y && it.ethMonth == m }.sumOf { it.amount } })
        }
        Text("Trends and savings insights appear only once there is enough history.", Modifier.padding(16.dp), fontSize = 11.sp, color = Color.Gray)
    }
}

// ---------- settings ----------
@Composable fun SettingsScreen(tracker: Tracker, msg: String?, onScan: () -> Unit) {
    val started = java.time.Instant.ofEpochMilli(tracker.prefs.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        Text("Settings", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Section("Tracking") {
            Text("Tracking since ${EthiopianCalendar.fromGregorian(started)} ($started)")
            Spacer(Modifier.height(10.dp))
            Button(onScan, Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(Blue), shape = RoundedCornerShape(12.dp)) { Text("Scan New Messages") }
            msg?.let { Text(it, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp)) }
        }
        Section("Privacy") {
            Text("Everything is processed on this phone. No account, no server, no internet needed. Full SMS text is never saved — only extracted amounts, dates and IDs.", fontSize = 13.sp)
        }
        Section("About") { Text("Tele Expense · v0.1 · rule-based parser, Ethiopian calendar first", fontSize = 13.sp) }
    }
}

// ---------- details + edit ----------
private val CATS = listOf("data_package", "airtime", "airtime_recharge", "sms_package", "voice_package", "mixed_package", "other")

@Composable fun DetailDialog(t: TxRow, dao: Dao, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf(t.category) }
    var amt by remember { mutableStateOf(etb(t.amount).replace(",", "")) }
    var y by remember { mutableStateOf("${t.ethYear}") }; var m by remember { mutableStateOf("${t.ethMonth}") }; var d by remember { mutableStateOf("${t.ethDay}") }
    val fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy · hh:mm a", Locale.ENGLISH)
    AlertDialog(onDismissRequest = onClose,
        title = { Text("ETB ${etb(t.amount)}", fontWeight = FontWeight.Bold, fontSize = 26.sp) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!editing) {
                Field("Category", "${catEmoji(t.category)} ${catGroup(t.category)} · ${t.category.replace('_', ' ')}")
                t.packageName?.let { Field("Package", it) }
                Field("Ethiopian date", "${t.eth()}")
                Field("Gregorian", t.dt().format(fmt))
                Field("Provider", t.provider)
                t.recipient?.let { Field("Recipient", it) }
                t.transactionId?.let { Field("Transaction", it) }
                Field("Detected by", "SMS rules · ${(t.confidence * 100).toInt()}% confidence" + if (t.userEdited) " · edited by you" else "")
                Field("Grouped SMS", "${t.smsCount} related")
            } else {
                Text("Category", fontSize = 12.sp, color = Color.Gray)
                Column { CATS.forEach { c -> Text("${catEmoji(c)} ${c.replace('_', ' ')}", Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (cat == c) Green.copy(alpha = .35f) else Color.Transparent).clickable { cat = c }.padding(8.dp)) } }
                OutlinedTextField(amt, { amt = it }, label = { Text("Amount (ETB)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("Ethiopian date", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(y, { y = it }, label = { Text("Year") }, singleLine = true, modifier = Modifier.weight(1.3f))
                    OutlinedTextField(m, { m = it }, label = { Text("Month") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(d, { d = it }, label = { Text("Day") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton({ scope.launch { dao.softDelete(t.key); onClose() } }) { Text("Not an expense / delete", color = Color(0xFFD32F2F)) }
        } },
        confirmButton = {
            if (editing) TextButton({
                val a = amt.toDoubleOrNull(); val ey = y.toIntOrNull(); val em = m.toIntOrNull(); val ed = d.toIntOrNull()
                if (a != null && a > 0 && ey != null && em in 1..13 && ed in 1..30) {
                    val g = EthiopianCalendar.toGregorian(EthDate(ey, em!!, ed!!)).atTime(t.dt().toLocalTime())
                    val e = EthiopianCalendar.fromGregorian(g.toLocalDate())
                    scope.launch { dao.edit(t.key, cat, a, g.toString(), e.year, e.month, e.day); onClose() }
                }
            }) { Text("Save") } else TextButton({ editing = true }) { Text("Edit") }
        },
        dismissButton = { TextButton(onClose) { Text("Close") } })
}

@Composable private fun Field(k: String, v: String) = Column(Modifier.padding(vertical = 4.dp)) {
    Text(k, fontSize = 11.sp, color = Color.Gray); Text(v, fontSize = 15.sp) }
