package com.bitchat.android.connect

import org.json.JSONArray
import org.json.JSONObject

/**
 * A Locus profile card that peers broadcast over the mesh.
 *
 * Kept deliberately tiny: the JSON form rides inside a public mesh message and
 * should stay in the one-or-two-fragment range (< ~500 bytes).
 */
data class ConnectProfile(
    val peerID: String = "",
    val name: String = "",
    val age: Int? = null,
    val emoji: String = "🜂",
    val bio: String = "",
    val vibes: List<String> = emptyList(),
    val hereTo: String = "",
    val updatedAt: Long = 0L
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("n", name.take(MAX_NAME))
        age?.let { o.put("a", it) }
        o.put("e", emoji.take(8))
        if (bio.isNotBlank()) o.put("b", bio.take(MAX_BIO))
        if (vibes.isNotEmpty()) o.put("t", JSONArray(vibes.take(MAX_VIBES).map { it.take(24) }))
        if (hereTo.isNotBlank()) o.put("h", hereTo.take(48))
        return o.toString()
    }

    companion object {
        const val MAX_NAME = 32
        const val MAX_BIO = 160
        const val MAX_VIBES = 5

        fun fromJson(json: String, senderPeerID: String, receivedAt: Long): ConnectProfile? = try {
            val o = JSONObject(json)
            val name = o.optString("n").trim()
            if (name.isEmpty()) null else ConnectProfile(
                peerID = senderPeerID,
                name = name.take(MAX_NAME),
                age = if (o.has("a")) o.optInt("a").takeIf { it in 18..120 } else null,
                emoji = o.optString("e", "🜂").take(8),
                bio = o.optString("b").take(MAX_BIO),
                vibes = o.optJSONArray("t")?.let { arr ->
                    (0 until minOf(arr.length(), MAX_VIBES)).mapNotNull { i ->
                        arr.optString(i).takeIf { it.isNotBlank() }?.take(24)
                    }
                } ?: emptyList(),
                hereTo = o.optString("h").take(48),
                updatedAt = receivedAt
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Wire format for Locus control messages carried over bitchat's transports.
 *
 * All control content starts with [PREFIX] so [com.bitchat.android.services.IncomingMessageAdmission]
 * can swallow it before it reaches timelines, notifications, or unread counters.
 *
 *  - `BCX1|P|<json>`  profile card, sent as a public broadcast message
 *  - `BCX1|L|`        "want to connect" signal, sent as a Noise-encrypted private message
 */
object ConnectSignal {
    const val PREFIX = "BCX1|"
    private const val PROFILE = "${PREFIX}P|"
    private const val LIKE = "${PREFIX}L|"
    private const val WAVE = "${PREFIX}W|"
    private const val REACT = "${PREFIX}R|"
    private const val TYPING = "${PREFIX}T|"

    fun isConnectContent(content: String): Boolean = content.startsWith(PREFIX)

    fun encodeProfile(profile: ConnectProfile): String = PROFILE + profile.toJson()

    fun encodeLike(): String = LIKE

    /** A wave — one packet, no message. */
    fun encodeWave(): String = WAVE
    fun isWave(content: String): Boolean = content.startsWith(WAVE)

    /**
     * A reaction on a specific message: `BCX1|R|<messageId>|<emoji>`. An empty emoji clears the
     * sender's reaction on that message. The messageId is a UUID (no `|`), so a limit-2 split is safe.
     */
    fun encodeReaction(messageId: String, emoji: String): String = "$REACT$messageId|$emoji"
    fun isReaction(content: String): Boolean = content.startsWith(REACT)
    fun parseReaction(content: String): Pair<String, String>? {
        if (!content.startsWith(REACT)) return null
        val parts = content.removePrefix(REACT).split("|", limit = 2)
        val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        return id to (parts.getOrNull(1) ?: "")
    }

    /** A transient "typing now" ping. No explicit stop — the receiver expires it on a short timer. */
    fun encodeTyping(): String = "${TYPING}1"
    fun isTyping(content: String): Boolean = content.startsWith(TYPING)

    fun decodeProfile(content: String, senderPeerID: String, receivedAt: Long): ConnectProfile? =
        if (content.startsWith(PROFILE)) {
            ConnectProfile.fromJson(content.removePrefix(PROFILE), senderPeerID, receivedAt)
        } else null

    fun isLike(content: String): Boolean = content.startsWith(LIKE)
}
