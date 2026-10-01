package et.teleexpense.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val tracker = Tracker(ctx)
        if (!tracker.prefs.isSetUp) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = msgs.firstOrNull()?.originatingAddress
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val ts = msgs.first().timestampMillis
        if (ts < tracker.prefs.startedAt) return // only messages after tracking began
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { if (tracker.ingest(sender, body, ts)) tracker.regroup(); tracker.prefs.lastProcessed = ts }
            finally { pending.finish() }
        }
    }
}
