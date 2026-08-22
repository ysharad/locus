package com.bitchat.android.connect

import android.content.Context
import android.location.LocationManager
import com.bitchat.android.geohash.Geohash
import java.security.MessageDigest

/**
 * The room's shareable name: four characters derived locally from venue + hour
 * (geohash block + hour bucket). Everyone standing in the same place in the same hour
 * derives the same code with no server holding a room list, and codes expire on their
 * own when the hour turns. It's the one Locus object that works outside the mesh —
 * paste it in a group chat, print it on a flyer — because arriving with the code and
 * being in range IS joining the room.
 */
object RoomCode {

    // No I/L/O/0/1 — codes get read aloud across loud rooms.
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** Current code for here-and-now, or null when no location is available yet. */
    fun current(context: Context): String? {
        val loc = lastKnownLocation(context) ?: return null
        return try {
            val block = Geohash.encode(loc.first, loc.second, 6)
            val hour = System.currentTimeMillis() / 3_600_000L
            val digest = MessageDigest.getInstance("SHA-256").digest("locus|$block|$hour".toByteArray())
            val c = (0 until 4).map { ALPHABET[(digest[it].toInt() and 0xFF) % ALPHABET.length] }
            "${c[0]}${c[1]}-${c[2]}${c[3]}"
        } catch (_: Exception) {
            null
        }
    }

    private fun lastKnownLocation(context: Context): Pair<Double, Double>? = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.allProviders
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    } catch (_: Exception) {
        null
    }
}
