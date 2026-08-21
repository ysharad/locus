package com.bitchat.android.connect

import android.content.Context
import android.util.Base64
import android.util.Log
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.services.AppStateStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Date

/**
 * The opt-in online chat relay. When two people have both chosen "keep my chats", a message that
 * can't be delivered over the Bluetooth mesh is sealed to the recipient's public key (see
 * [ChatCrypto]) and dropped in their Firestore mailbox; their device pulls it, decrypts it, drops
 * it into the same conversation the mesh uses, and deletes it. The server only ever holds ciphertext.
 *
 * Keyed on the STABLE mesh fingerprint (64-hex), so a message lands in `contact_<fingerprint>` — the
 * exact conversation id the mesh chat uses — regardless of whether the peer is currently in range.
 */
object ChatRelay {

    private const val TAG = "ChatRelay"
    @Volatile private var registration: ListenerRegistration? = null
    @Volatile private var appContext: Context? = null

    private fun db() = FirebaseFirestore.getInstance()

    private fun ensureAuth(block: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) { block(); return }
        auth.signInAnonymously()
            .addOnSuccessListener { block() }
            .addOnFailureListener { Log.i(TAG, "relay auth failed: ${it.message}") }
    }

    /**
     * Seal [msg] to [recipientFingerprint] and drop it in their mailbox. Fire-and-forget and
     * fully offline-safe: any failure (no network, recipient not opted-in, no published key) is
     * swallowed — the mesh remains the primary transport.
     */
    fun send(context: Context, myFingerprint: String, senderName: String, recipientFingerprint: String, msg: BitchatMessage) {
        if (recipientFingerprint.isBlank() || myFingerprint.isBlank()) return
        val ctx = context.applicationContext
        val plaintext = msg.content.toByteArray()
        ensureAuth {
            // Look up the recipient's published chat public key.
            db().collection("profiles").document(recipientFingerprint).get()
                .addOnSuccessListener { doc ->
                    val pubB64 = doc.getString("chatPubKey") ?: return@addOnSuccessListener
                    try {
                        val recipientPub = Base64.decode(pubB64, Base64.NO_WRAP)
                        val sealed = ChatCrypto.seal(recipientPub, plaintext)
                        val payload = mapOf(
                            "from" to myFingerprint,
                            "fromName" to senderName,
                            "ct" to Base64.encodeToString(sealed, Base64.NO_WRAP),
                            "id" to msg.id,
                            "ts" to (msg.timestamp.time),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        db().collection("mailbox").document(recipientFingerprint)
                            .collection("messages").document(msg.id)
                            .set(payload)
                            .addOnFailureListener { Log.i(TAG, "mailbox write failed: ${it.message}") }
                    } catch (e: Exception) {
                        Log.i(TAG, "seal failed: ${e.message}")
                    }
                }
                .addOnFailureListener { Log.i(TAG, "recipient key lookup failed: ${it.message}") }
            // Keep the on-file public key fresh for others sealing to us.
            db().collection("profiles").document(myFingerprint)
                .set(mapOf("chatPubKey" to ChatKeys.publicKeyB64(ctx)), com.google.firebase.firestore.SetOptions.merge())
        }
    }

    /** Start listening on my mailbox; decrypt + inject + delete each arriving message. */
    /**
     * Set by ChatViewModel to the shared NotificationManager's private-message notifier so a
     * relayed message rings exactly like a mesh one (same settings, mute, and focused-chat
     * suppression). Without this, a relay arrival while the app is background-but-alive was
     * silent: the listener drained the mailbox before the FCM wake could, and no one notified.
     * Args: canonical conversation id ("contact_<fp>"), sender nickname, message preview.
     */
    @Volatile var notifier: ((String, String, String) -> Unit)? = null

    fun startListening(context: Context, myFingerprint: String, myNickname: String, scope: CoroutineScope) {
        if (myFingerprint.isBlank()) return
        val ctx = context.applicationContext
        appContext = ctx
        stopListening()
        ChatKeys.ensure(ctx)
        ensureAuth {
            // Make sure our public key is on file so people can reach us.
            db().collection("profiles").document(myFingerprint)
                .set(mapOf("chatPubKey" to ChatKeys.publicKeyB64(ctx)), com.google.firebase.firestore.SetOptions.merge())
            registration = db().collection("mailbox").document(myFingerprint)
                .collection("messages")
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) return@addSnapshotListener
                    for (change in snap.documentChanges) {
                        if (change.type != DocumentChange.Type.ADDED) continue
                        val d = change.document
                        val ctB64 = d.getString("ct") ?: continue
                        val from = d.getString("from") ?: continue
                        // Blocked senders stay silent on the online path too — discard their
                        // ciphertext outright so it never lands in the thread or re-delivers.
                        if (from in ConnectManager.blocked.value) { d.reference.delete(); continue }
                        val fromName = d.getString("fromName") ?: "Someone"
                        val id = d.getString("id") ?: d.id
                        val ts = d.getLong("ts") ?: System.currentTimeMillis()
                        try {
                            val plaintext = String(
                                ChatCrypto.open(
                                    ChatKeys.privateKey(ctx),
                                    ChatKeys.publicKey(ctx),
                                    Base64.decode(ctB64, Base64.NO_WRAP)
                                )
                            )
                            val message = BitchatMessage(
                                id = id,
                                sender = fromName,
                                content = plaintext,
                                timestamp = Date(ts),
                                isPrivate = true,
                                recipientNickname = myNickname,
                                senderPeerID = from.take(16),
                                deliveryStatus = DeliveryStatus.Delivered(to = myNickname, at = Date())
                            )
                            scope.launch {
                                // "contact_<fingerprint>" is the canonical conversation id — lands
                                // in the same thread as mesh messages from this person.
                                val ok = AppStateStore.addPrivateMessageDurably("contact_$from", message)
                                // Only remove the server copy once it's safely persisted (or already
                                // present). A transient failure (DB not ready, writes suspended)
                                // keeps the ciphertext so a later drain can retry — never lose it.
                                if (ok) {
                                    d.reference.delete()
                                    notifier?.invoke("contact_$from", fromName, plaintext)
                                }
                            }
                        } catch (e: Exception) {
                            Log.i(TAG, "relay decrypt failed (${d.id}): ${e.message}")
                        }
                    }
                }
        }
    }

    fun stopListening() {
        registration?.remove()
        registration = null
    }

    /** Publish this device's current FCM token so the Cloud Function can wake us for new mail. */
    fun publishFcmToken(myFingerprint: String) {
        if (myFingerprint.isBlank()) return
        ensureAuth {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> publishToken(myFingerprint, token) }
                .addOnFailureListener { Log.i(TAG, "fcm token fetch failed: ${it.message}") }
        }
    }

    fun publishToken(myFingerprint: String, token: String) {
        if (myFingerprint.isBlank() || token.isBlank()) return
        ensureAuth {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@ensureAuth
            // Push tokens live in an owner-only `tokens/{fp}` doc — never in the (readable) profile.
            // No client can read it; only the Cloud Function (admin SDK) does, to send the push.
            db().collection("tokens").document(myFingerprint)
                .set(mapOf("token" to token, "uid" to uid), com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener { Log.i(TAG, "fcm token publish failed: ${it.message}") }
        }
    }

    /**
     * One-shot mailbox drain for a background push wake (app closed, no live listener). Blocks the
     * calling background thread until done. Returns (senderName, plaintext) for notifications.
     */
    fun drainOnce(context: Context, myFingerprint: String, myNickname: String): List<Pair<String, String>> {
        val ctx = context.applicationContext
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) com.google.android.gms.tasks.Tasks.await(auth.signInAnonymously())
            ChatKeys.ensure(ctx)
            val snap = com.google.android.gms.tasks.Tasks.await(
                db().collection("mailbox").document(myFingerprint).collection("messages").get()
            )
            val delivered = mutableListOf<Pair<String, String>>()
            for (d in snap.documents) {
                val ctB64 = d.getString("ct") ?: continue
                val from = d.getString("from") ?: continue
                // Same silence rule as the live listener: a blocked sender's mail is discarded.
                if (from in ConnectManager.blocked.value) { d.reference.delete(); continue }
                val fromName = d.getString("fromName") ?: "Someone"
                val id = d.getString("id") ?: d.id
                val ts = d.getLong("ts") ?: System.currentTimeMillis()
                try {
                    val plaintext = String(
                        ChatCrypto.open(ChatKeys.privateKey(ctx), ChatKeys.publicKey(ctx), Base64.decode(ctB64, Base64.NO_WRAP))
                    )
                    val message = BitchatMessage(
                        id = id, sender = fromName, content = plaintext, timestamp = Date(ts),
                        isPrivate = true, recipientNickname = myNickname, senderPeerID = from.take(16),
                        deliveryStatus = DeliveryStatus.Delivered(to = myNickname, at = Date())
                    )
                    val ok = kotlinx.coroutines.runBlocking { AppStateStore.addPrivateMessageDurably("contact_$from", message) }
                    // Delete + notify only when it actually persisted (or was already present).
                    // A transient failure keeps the ciphertext for the next drain, and a dedupe
                    // (already delivered over mesh) won't raise a second notification.
                    if (ok) {
                        com.google.android.gms.tasks.Tasks.await(d.reference.delete())
                        delivered.add(fromName to plaintext)
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "drain decrypt failed (${d.id}): ${e.message}")
                }
            }
            delivered
        } catch (e: Exception) {
            Log.i(TAG, "drain failed: ${e.message}")
            emptyList()
        }
    }
}
