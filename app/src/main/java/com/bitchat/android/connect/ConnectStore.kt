package com.bitchat.android.connect

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * SharedPreferences-backed persistence for the Locus layer.
 *
 * Everything is keyed by mesh peerID, which on this fork is the first 16 hex chars
 * of the peer's stable identity fingerprint, so keys survive reconnects.
 */
class ConnectStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bitconnect_store", Context.MODE_PRIVATE)

    // MARK: - My profile

    fun loadMyProfile(): ConnectProfile? =
        prefs.getString(KEY_MY_PROFILE, null)?.let {
            ConnectProfile.fromJson(it, senderPeerID = "", receivedAt = 0L)
        }

    fun saveMyProfile(profile: ConnectProfile) {
        prefs.edit().putString(KEY_MY_PROFILE, profile.toJson()).apply()
    }

    // MARK: - Swipe state

    fun likedPeers(): Set<String> = prefs.getStringSet(KEY_LIKED, emptySet()) ?: emptySet()
    fun passedPeers(): Set<String> = prefs.getStringSet(KEY_PASSED, emptySet()) ?: emptySet()
    fun likesReceived(): Set<String> = prefs.getStringSet(KEY_LIKES_RECEIVED, emptySet()) ?: emptySet()
    fun deliveredLikes(): Set<String> = prefs.getStringSet(KEY_LIKES_DELIVERED, emptySet()) ?: emptySet()

    fun addLiked(peerID: String) = addToSet(KEY_LIKED, peerID)
    fun addPassed(peerID: String) = addToSet(KEY_PASSED, peerID)
    fun addLikeReceived(peerID: String) = addToSet(KEY_LIKES_RECEIVED, peerID)
    fun addLikeDelivered(peerID: String) = addToSet(KEY_LIKES_DELIVERED, peerID)

    fun removePassed(peerID: String) {
        val next = passedPeers().toMutableSet().apply { remove(peerID) }
        prefs.edit().putStringSet(KEY_PASSED, next).apply()
    }

    fun removeLiked(peerID: String) {
        val next = likedPeers().toMutableSet().apply { remove(peerID) }
        prefs.edit().putStringSet(KEY_LIKED, next).apply()
    }

    fun removeLikeReceived(peerID: String) {
        val next = likesReceived().toMutableSet().apply { remove(peerID) }
        prefs.edit().putStringSet(KEY_LIKES_RECEIVED, next).apply()
    }

    // MARK: - Safety

    fun blockedPeers(): Set<String> = prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
    fun addBlocked(peerID: String) = addToSet(KEY_BLOCKED, peerID)

    // MARK: - Age gate

    fun isAgeConfirmed(): Boolean = prefs.getBoolean(KEY_AGE_CONFIRMED, false)
    fun setAgeConfirmed() = prefs.edit().putBoolean(KEY_AGE_CONFIRMED, true).apply()

    /** Whether the one-time sign-in screen has been shown (skipped or completed). */
    fun isIdentitySeen(): Boolean = prefs.getBoolean(KEY_IDENTITY_SEEN, false)
    fun setIdentitySeen() = prefs.edit().putBoolean(KEY_IDENTITY_SEEN, true).apply()

    /** Opt-in: back up and relay chats through the encrypted backend when online. */
    fun isKeepChats(): Boolean = prefs.getBoolean(KEY_KEEP_CHATS, false)
    fun setKeepChats(on: Boolean) = prefs.edit().putBoolean(KEY_KEEP_CHATS, on).apply()

    // Likers the user has revealed (by watching an ad, or fail-open when no ad had fill).
    // Permanent: a reveal is never un-earned. New admirers arrive blurred.
    fun revealedLikes(): Set<String> = prefs.getStringSet("revealed_likes", emptySet()) ?: emptySet()
    fun addRevealedLikes(ids: Set<String>) {
        val next = revealedLikes().toMutableSet().apply { addAll(ids) }
        prefs.edit().putStringSet("revealed_likes", next).apply()
    }

    // One-time "keep this chat?" nudge, fired the first time a chat with a real reply is closed.
    fun isKeepChatsNudged(): Boolean = prefs.getBoolean("keepchats_nudged", false)
    fun setKeepChatsNudged() = prefs.edit().putBoolean("keepchats_nudged", true).apply()

    /** Presence: whether your card is broadcast. Default visible. */
    fun isVisible(): Boolean = prefs.getBoolean(KEY_VISIBLE, true)
    fun setVisible(on: Boolean) = prefs.edit().putBoolean(KEY_VISIBLE, on).apply()

    /** Our stable mesh fingerprint, cached so the background push service can address our mailbox. */
    fun myFingerprint(): String? = prefs.getString(KEY_MY_FP, null)
    fun setMyFingerprint(fp: String) = prefs.edit().putString(KEY_MY_FP, fp).apply()

    // MARK: - Notification preferences (default on unless noted)
    fun isNotifyNearby(): Boolean = prefs.getBoolean(KEY_NOTIFY_NEARBY, true)
    fun setNotifyNearby(on: Boolean) = prefs.edit().putBoolean(KEY_NOTIFY_NEARBY, on).apply()
    fun isNotifyMessages(): Boolean = prefs.getBoolean(KEY_NOTIFY_MSGS, true)
    fun setNotifyMessages(on: Boolean) = prefs.edit().putBoolean(KEY_NOTIFY_MSGS, on).apply()

    fun str(key: String, default: String = ""): String = prefs.getString(key, default) ?: default
    fun setStr(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun bool(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun setBool(key: String, on: Boolean) = prefs.edit().putBoolean(key, on).apply()

    fun long(key: String, default: Long): Long = prefs.getLong(key, default)
    fun setLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    /** Master + quiet-hours gate used before raising any notification. */
    fun notificationsAllowedNow(hourOfDay: Int): Boolean {
        if (!bool(KEY_NOTIFY_ALL, true)) return false
        if (bool(KEY_QUIET_HOURS, false)) {
            // Quiet 23:00–09:00
            if (hourOfDay >= 23 || hourOfDay < 9) return false
        }
        return true
    }

    fun removeBlocked(key: String) {
        val next = blockedPeers().toMutableSet().apply { remove(key) }
        prefs.edit().putStringSet(KEY_BLOCKED, next).apply()
    }

    // MARK: - Discovery filters (client-side; everyone in range still gets your card)
    fun filterIntents(): Set<String> = prefs.getStringSet(KEY_F_INTENTS, emptySet()) ?: emptySet()
    fun filterVibes(): Set<String> = prefs.getStringSet(KEY_F_VIBES, emptySet()) ?: emptySet()
    fun filterAgeMin(): Int = prefs.getInt(KEY_F_AGE_MIN, 18)
    fun filterAgeMax(): Int = prefs.getInt(KEY_F_AGE_MAX, 99)
    fun saveFilters(intents: Set<String>, vibes: Set<String>, ageMin: Int, ageMax: Int) {
        prefs.edit()
            .putStringSet(KEY_F_INTENTS, intents)
            .putStringSet(KEY_F_VIBES, vibes)
            .putInt(KEY_F_AGE_MIN, ageMin)
            .putInt(KEY_F_AGE_MAX, ageMax)
            .apply()
    }

    /** Erase everything Locus stored on this device (card, matches, blocks, opt-ins). */
    fun wipe() = prefs.edit().clear().apply()

    // MARK: - Matches

    /** peerID -> (profile snapshot, matchedAt) */
    fun loadMatches(): Map<String, ConnectMatch> {
        val raw = prefs.getString(KEY_MATCHES, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap {
                o.keys().forEach { peerID ->
                    val entry = o.getJSONObject(peerID)
                    val profile = ConnectProfile.fromJson(
                        entry.getString("p"), peerID, entry.optLong("at")
                    ) ?: return@forEach
                    put(peerID, ConnectMatch(peerID, profile, entry.optLong("at")))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveMatches(matches: Map<String, ConnectMatch>) {
        val o = JSONObject()
        matches.forEach { (peerID, match) ->
            o.put(peerID, JSONObject().apply {
                put("p", match.profile.toJson())
                put("at", match.matchedAt)
            })
        }
        prefs.edit().putString(KEY_MATCHES, o.toString()).apply()
    }

    private fun addToSet(key: String, value: String) {
        val next = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        next.add(value)
        prefs.edit().putStringSet(key, next).apply()
    }

    companion object {
        private const val KEY_IDENTITY_SEEN = "identity_seen"
        private const val KEY_KEEP_CHATS = "keep_chats"
        private const val KEY_VISIBLE = "visible"
        private const val KEY_MY_FP = "my_fingerprint"
        private const val KEY_NOTIFY_NEARBY = "notify_nearby"
        private const val KEY_NOTIFY_MSGS = "notify_messages"
        private const val KEY_F_INTENTS = "filter_intents"
        private const val KEY_F_VIBES = "filter_vibes"
        private const val KEY_F_AGE_MIN = "filter_age_min"
        private const val KEY_F_AGE_MAX = "filter_age_max"

        // Settings toggles referenced by the settings sub-screens (defaults noted at call sites).
        const val KEY_NOTIFY_ALL = "notify_all"
        const val KEY_QUIET_HOURS = "quiet_hours"
        const val KEY_NOTIFY_CONNECTION = "notify_connection"
        const val KEY_NOTIFY_MENTIONED = "notify_mentioned"
        const val KEY_NOTIFY_WAKE = "notify_wake"
        const val KEY_READ_RECEIPTS = "read_receipts"
        const val KEY_TYPING = "typing_indicator"
        const val KEY_LAST_SEEN = "last_seen"
        const val KEY_CONNECTIONS_ONLY = "connections_only"
        private const val KEY_MY_PROFILE = "my_profile"
        private const val KEY_LIKED = "liked_peers"
        private const val KEY_PASSED = "passed_peers"
        private const val KEY_LIKES_RECEIVED = "likes_received"
        private const val KEY_LIKES_DELIVERED = "likes_delivered"
        private const val KEY_MATCHES = "matches"
        private const val KEY_BLOCKED = "blocked_peers"
        private const val KEY_AGE_CONFIRMED = "age_confirmed"
    }
}

data class ConnectMatch(
    val peerID: String,
    val profile: ConnectProfile,
    val matchedAt: Long
)
