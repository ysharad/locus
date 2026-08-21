package com.bitchat.android.connect

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Calls the Locus backend to generate opener suggestions for a connection.
 *
 * The Anthropic API key never touches the app — the `generateIcebreakers` Cloud
 * Function holds it as a secret, and the client only sends the two public profile
 * cards. Requires online + Firebase anonymous auth (established by [CloudSync]);
 * fully offline the app just doesn't offer this.
 */
object Icebreakers {

    private const val TAG = "Icebreakers"

    private fun cardMap(p: ConnectProfile): Map<String, Any?> = mapOf(
        "name" to p.name,
        "bio" to p.bio,
        "hereTo" to p.hereTo,
        "vibes" to p.vibes
    )

    /** Returns up to 3 opener suggestions, or an empty list on any failure. */
    suspend fun suggest(me: ConnectProfile?, them: ConnectProfile): List<String> {
        return try {
            val payload = hashMapOf(
                "me" to me?.let { cardMap(it) },
                "them" to cardMap(them)
            )
            val result = FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("generateIcebreakers")
                .call(payload)
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            (data?.get("openers") as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
                ?.take(3)
                ?: emptyList()
        } catch (e: Exception) {
            Log.i(TAG, "Icebreaker request failed: ${e.message}")
            emptyList()
        }
    }
}
