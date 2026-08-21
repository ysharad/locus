package com.bitchat.android.connect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Wakes the app for a new relayed message when it's closed. The push carries no content — only a
 * nudge; the device then drains its own encrypted mailbox ([ChatRelay.drainOnce]) and decrypts
 * locally, so plaintext never touches the push channel.
 */
class LocusMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val store = ConnectStore(applicationContext)
        val fp = store.myFingerprint()
        if (store.isKeepChats() && fp != null) ChatRelay.publishToken(fp, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val store = ConnectStore(applicationContext)
        if (!store.isKeepChats()) return
        val fp = store.myFingerprint() ?: return
        val nickname = store.loadMyProfile()?.name ?: "You"
        val delivered = ChatRelay.drainOnce(applicationContext, fp, nickname)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (delivered.isNotEmpty() && store.isNotifyMessages() && store.notificationsAllowedNow(hour)) {
            notify(applicationContext, delivered)
        }
    }

    private fun notify(context: Context, messages: List<Pair<String, String>>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "New messages from your connections" }
            )
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // One notification per sender (latest line as the preview).
        messages.groupBy { it.first }.forEach { (from, lines) ->
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.bitchat.android.R.drawable.ic_launcher_monochrome)
                .setContentTitle(from)
                .setContentText(lines.last().second)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            nm.notify(("chat_" + from).hashCode(), n)
        }
    }

    companion object {
        private const val CHANNEL = "locus_chat"
    }
}
