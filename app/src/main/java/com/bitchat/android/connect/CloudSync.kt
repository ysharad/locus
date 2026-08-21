package com.bitchat.android.connect

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Phase-2 backend bridge. Strictly additive: the mesh app is the product and works
 * with zero internet; this mirrors state to Firebase when a network happens to exist.
 *
 * Identity model (no login): the mesh keypair IS the account. The full identity
 * fingerprint (SHA-256 of the Noise static public key) is the document ID. On first
 * contact we sign in with Firebase Anonymous Auth and stamp that uid into the profile
 * doc; security rules then let only that uid touch the doc. This binds fingerprint→uid
 * on first-write. (Upgrade path: replace anonymous auth with a Cloud Function that
 * verifies a signature challenge and mints a custom token for the fingerprint.)
 *
 * Every call is fire-and-forget and swallows failures: no cloud outage, missing
 * config, or airplane mode may ever affect the offline experience.
 */
object CloudSync {

    private const val TAG = "CloudSync"

    @Volatile private var available = false
    @Volatile private var fingerprint: String? = null

    fun init(context: Context) {
        available = try {
            FirebaseApp.initializeApp(context) != null || FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            Log.i(TAG, "Firebase not configured, cloud sync off: ${e.message}")
            false
        }
    }

    /** Associate this device's stable mesh identity; safe to call repeatedly. */
    fun bind(identityFingerprint: String) {
        if (identityFingerprint.isBlank()) return
        fingerprint = identityFingerprint
        signedIn { /* warm the session so later writes are instant */ }
    }

    fun syncProfile(profile: ConnectProfile) {
        val fp = fingerprint ?: return
        signedIn { uid ->
            // "Last seen" off → stop refreshing the timestamp and remove any existing one.
            val lastSeen = if (ConnectManager.lastSeenEnabled()) FieldValue.serverTimestamp() else FieldValue.delete()
            val doc = mapOf(
                "uid" to uid,
                "name" to profile.name,
                "age" to profile.age,
                "emoji" to profile.emoji,
                "bio" to profile.bio,
                "vibes" to profile.vibes,
                "hereTo" to profile.hereTo,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to lastSeen
            )
            FirebaseFirestore.getInstance()
                .collection("profiles").document(fp)
                .set(doc, SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "Profile synced") }
                .addOnFailureListener { Log.i(TAG, "Profile sync failed: ${it.message}") }
        }
    }

    /** Cheap liveness stamp, called from the broadcast tick when peers are around. */
    fun heartbeat() {
        val fp = fingerprint ?: return
        if (!ConnectManager.lastSeenEnabled()) return // last-seen off → no liveness stamp
        signedIn { uid ->
            FirebaseFirestore.getInstance()
                .collection("profiles").document(fp)
                .set(
                    mapOf("uid" to uid, "lastSeenAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                )
                .addOnFailureListener { Log.i(TAG, "Heartbeat failed: ${it.message}") }
        }
    }

    /** File an abuse report for review. Create-only server-side; nobody can read them back. */
    fun report(reportedPeerID: String, reportedProfile: ConnectProfile?) {
        val fp = fingerprint
        signedIn { uid ->
            val doc = mapOf(
                "reporterUid" to uid,
                "reporterFingerprint" to fp,
                "reportedPeerID" to reportedPeerID,
                "reportedName" to (reportedProfile?.name ?: ""),
                "reportedCard" to (reportedProfile?.toJson() ?: ""),
                "createdAt" to FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance()
                .collection("reports").document()
                .set(doc)
                .addOnFailureListener { Log.i(TAG, "Report upload failed: ${it.message}") }
        }
    }

    /** True once the anonymous identity has been upgraded to a permanent (email) account. */
    fun hasPermanentAccount(): Boolean = try {
        available && FirebaseAuth.getInstance().currentUser?.isAnonymous == false
    } catch (e: Exception) {
        false
    }

    /**
     * Turn the device's anonymous identity into a permanent account by linking an email — the
     * SAME uid is kept, so the fingerprint→uid ownership of the profile (and mailbox) is preserved
     * and chats can be backed up / relayed. Cross-device restore (same email, new phone) is a
     * later step (needs a server-side ownership migration); this only links the current identity.
     */
    fun linkEmail(email: String, password: String, onResult: (ok: Boolean, error: String?) -> Unit) {
        if (!available) { onResult(false, "Backend unavailable"); return }
        try {
            val auth = FirebaseAuth.getInstance()
            val cred = com.google.firebase.auth.EmailAuthProvider.getCredential(email.trim(), password)
            val doLink = {
                val user = auth.currentUser
                if (user == null) {
                    onResult(false, "Not signed in")
                } else if (!user.isAnonymous) {
                    onResult(true, null) // already permanent
                } else {
                    user.linkWithCredential(cred)
                        .addOnSuccessListener { onResult(true, null) }
                        .addOnFailureListener { e -> onResult(false, e.message ?: "Couldn't save your account") }
                }
            }
            if (auth.currentUser != null) doLink()
            else auth.signInAnonymously()
                .addOnSuccessListener { doLink() }
                .addOnFailureListener { e -> onResult(false, e.message ?: "Couldn't reach the backend") }
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }

    /** Best-effort delete of the cloud profile (card, published key, push token). */
    fun deleteProfile(fingerprint: String?) {
        val fp = fingerprint ?: this.fingerprint ?: return
        signedIn {
            FirebaseFirestore.getInstance().collection("profiles").document(fp).delete()
                .addOnFailureListener { Log.i(TAG, "profile delete failed: ${it.message}") }
        }
    }

    private fun signedIn(block: (uid: String) -> Unit) {
        if (!available) return
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) {
                block(user.uid)
                return
            }
            auth.signInAnonymously()
                .addOnSuccessListener { result -> result.user?.uid?.let(block) }
                .addOnFailureListener { Log.i(TAG, "Anonymous sign-in failed: ${it.message}") }
        } catch (e: Exception) {
            Log.i(TAG, "Auth unavailable: ${e.message}")
        }
    }
}
