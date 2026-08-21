package com.bitchat.android.connect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.bitchat.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * "People radar": posts a notification when someone with Locus comes into
 * radio range. Detection is driven by profile cards arriving over the mesh, so this
 * only ever fires while Bluetooth scanning is actually running.
 *
 * Burst behavior: walking into a venue can surface a dozen cards in seconds. New
 * sightings are debounced for [DEBOUNCE_MS] and posted as one grouped stack —
 * a few individual (silent) children under a single alerting summary. Beyond
 * [MAX_CHILD_NOTIFICATIONS] people, only the summary is posted.
 *
 * Politeness rules: nothing is posted while the app is visible (the deck already
 * shows arrivals live), and a given person re-triggers at most once per
 * [PER_PERSON_COOLDOWN_MS].
 */
object ConnectNotifier {

    private const val TAG = "ConnectNotifier"
    private const val CHANNEL_ID = "bitconnect_nearby"
    private const val GROUP_KEY = "com.bitconnect.NEARBY"
    private const val SUMMARY_ID = 7001
    private const val CHILD_ID_BASE = 7100
    private const val DEBOUNCE_MS = 8_000L
    private const val PER_PERSON_COOLDOWN_MS = 2 * 60 * 60_000L
    private const val MAX_CHILD_NOTIFICATIONS = 4

    private data class Sighting(
        val peerID: String,
        val name: String,
        val emoji: String,
        val hereTo: String,
        val isConnection: Boolean
    )

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastNotifiedAt = ConcurrentHashMap<String, Long>()
    private val pending = LinkedHashMap<String, Sighting>()
    private var flushJob: Job? = null
    private val lock = Any()

    fun init(context: Context) {
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "People nearby",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Someone with Locus came into range"
            }
        )
    }

    /** Report a newly-heard person. Safe to call from transport worker threads. */
    fun personDetected(profile: ConnectProfile, isConnection: Boolean) {
        val now = System.currentTimeMillis()
        val last = lastNotifiedAt[profile.peerID] ?: 0L
        if (now - last < PER_PERSON_COOLDOWN_MS) return
        lastNotifiedAt[profile.peerID] = now

        synchronized(lock) {
            pending[profile.peerID] = Sighting(
                peerID = profile.peerID,
                name = profile.name,
                emoji = profile.emoji,
                hereTo = profile.hereTo,
                isConnection = isConnection
            )
            if (flushJob?.isActive != true) {
                flushJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    flush()
                }
            }
        }
    }

    private fun flush() {
        val batch: List<Sighting>
        synchronized(lock) {
            batch = pending.values.toList()
            pending.clear()
        }
        if (batch.isEmpty()) return

        // The deck already shows arrivals live while the user is looking at it
        if (appVisible()) return

        val context = appContext ?: return
        val notifier = NotificationManagerCompat.from(context)
        if (!notifier.areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (batch.size == 1) {
                val s = batch.first()
                notifier.notify(childId(s.peerID), childNotification(context, s, contentIntent, alert = true))
                return
            }

            // Children stay silent; the summary carries the single alert.
            if (batch.size <= MAX_CHILD_NOTIFICATIONS) {
                batch.forEach { s ->
                    notifier.notify(childId(s.peerID), childNotification(context, s, contentIntent, alert = false))
                }
            }

            val connections = batch.count { it.isConnection }
            val title = when {
                connections > 0 && batch.size == connections ->
                    "$connections of your connections just arrived ⚡"
                else -> "${batch.size} people nearby with Locus"
            }
            val names = batch.joinToString(", ", limit = 5, truncated = "…") {
                "${it.emoji} ${it.name}"
            }
            val style = NotificationCompat.InboxStyle().also { inbox ->
                batch.take(6).forEach { inbox.addLine(lineFor(it)) }
                if (batch.size > 6) inbox.setSummaryText("+${batch.size - 6} more")
            }
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(names)
                .setStyle(style)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .build()
            notifier.notify(SUMMARY_ID, summary)
        } catch (e: SecurityException) {
            Log.i(TAG, "Notifications not permitted: ${e.message}")
        }
    }

    private fun childNotification(
        context: Context,
        s: Sighting,
        contentIntent: PendingIntent,
        alert: Boolean
    ): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (s.isConnection) "${s.emoji} ${s.name} is here — you're connected ⚡"
                else "${s.emoji} ${s.name} is nearby"
            )
            .setContentText(
                if (s.hereTo.isNotBlank()) "here to ${s.hereTo}" else "just came into range"
            )
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(
                if (alert) NotificationCompat.GROUP_ALERT_ALL
                else NotificationCompat.GROUP_ALERT_SUMMARY
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

    private fun lineFor(s: Sighting): String {
        val suffix = when {
            s.isConnection -> " · your connection ⚡"
            s.hereTo.isNotBlank() -> " · here to ${s.hereTo}"
            else -> ""
        }
        return "${s.emoji} ${s.name}$suffix"
    }

    private fun childId(peerID: String): Int = CHILD_ID_BASE + (peerID.hashCode() and 0xFFFF)

    private fun appVisible(): Boolean = try {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    } catch (_: Exception) {
        false
    }
}
