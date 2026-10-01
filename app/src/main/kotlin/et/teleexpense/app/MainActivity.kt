package et.teleexpense.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Green = Color(0xFF8DC63F); val Blue = Color(0xFF0088D1)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val tracker = Tracker(this)
        setContent { AppTheme { Root(tracker) } }
    }
}

@Composable fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Green, background = Color(0xFF101410), surface = Color(0xFF1B211B))
                  else lightColorScheme(primary = Green, background = Color(0xFFF6F6F6), surface = Color.White),
    content = content)

@Composable fun Root(tracker: Tracker) {
    val scope = rememberCoroutineScope()
    var setUp by remember { mutableStateOf(tracker.prefs.isSetUp) }
    var summary by remember { mutableStateOf<Tracker.ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(true) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok ->
        if (ok.values.all { it }) { busy = true; scope.launch {
            summary = withContext(Dispatchers.IO) { tracker.initialScan(month) }; setUp = true; busy = false } }
    }
    if (!setUp) Welcome(busy) { m -> month = m; ask.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }
    else Main(tracker, summary)
}

@Composable fun Welcome(busy: Boolean, onChoose: (Boolean) -> Unit) = Column(
    Modifier.fillMaxSize().background(Color(0xFF0B3D2E)).padding(28.dp), verticalArrangement = Arrangement.Center) {
    Text("Welcome to Tele Expense", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text("Your telecom spending,\nautomatically tracked.\nEverything stays on your phone.", color = Green)
    Spacer(Modifier.height(16.dp))
    Text("Tele Expense needs SMS access to detect telecom purchases and recharges automatically. Your messages are processed locally on your phone.",
        color = Color.White, fontSize = 13.sp)
    Spacer(Modifier.height(24.dp))
    if (busy) Text("Analyzing telecom messages...", color = Color.White)
    else {
        Button({ onChoose(true) }, Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(Green, Color.Black), shape = RoundedCornerShape(12.dp)) { Text("Scan This Month") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton({ onChoose(false) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) { Text("Start From Today", color = Color.White) }
    }
}

@Composable fun Main(tracker: Tracker, summary: Tracker.ScanResult?) {
    val dao = remember { AppDb.get(tracker.ctx).dao() }
    val txs by dao.observeTx().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scan: () -> Unit = { scope.launch {
        val r = withContext(Dispatchers.IO) { tracker.scanNew() }
        msg = "Scanned: ${r.relevantSms} new relevant message(s)" } }
    val tabs = listOf("🏠" to "Home", "🧾" to "Transactions", "📅" to "Calendar", "📊" to "Analytics", "⚙️" to "Settings")
    Scaffold(bottomBar = { NavigationBar { tabs.forEachIndexed { i, (e, l) ->
        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(e) }, label = { Text(l, fontSize = 10.sp, maxLines = 1) }) } } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (tab) {
                0 -> HomeScreen(txs, summary, msg, scan, { tab = it }, { open = it })
                1 -> TransactionsScreen(txs) { open = it }
                2 -> CalendarScreen(txs) { open = it }
                3 -> AnalyticsScreen(txs)
                else -> SettingsScreen(tracker, msg, scan)
            }
        }
    }
    txs.firstOrNull { it.key == open }?.let { DetailDialog(it, dao) { open = null } }
}
