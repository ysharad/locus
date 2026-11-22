package com.bitchat.android.connect

import android.content.Context
import android.util.Log
import com.bitchat.android.identity.SecureIdentityStateManager
import com.google.firebase.functions.FirebaseFunctions
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Claims profiles/{fingerprint} for the current account once per process. Callers always continue:
 * without a connection they behave as before, and the rules still refuse writes to another
 * account's profile.
 */
internal object IdentityClaim {

    private const val TAG = "IdentityClaim"
    private const val TIMEOUT_SECONDS = 10L

    private val lock = Any()
    private val claimed = mutableSetOf<String>()
    private val waiting = mutableMapOf<String, MutableList<() -> Unit>>()

    fun ensure(context: Context, uid: String, fingerprint: String, then: () -> Unit) {
        val id = "$uid:$fingerprint"
        val alreadyClaimed = synchronized(lock) {
            if (id in claimed) {
                true
            } else {
                waiting[id]?.let { it += then; return }
                waiting[id] = mutableListOf(then)
                false
            }
        }
        if (alreadyClaimed) {
            then()
            return
        }
        claim(context, uid, fingerprint) { success ->
            val callbacks = synchronized(lock) {
                if (success) claimed += id
                waiting.remove(id).orEmpty()
            }
            callbacks.forEach { it() }
        }
    }

    private fun claim(context: Context, uid: String, fingerprint: String, done: (Boolean) -> Unit) {
        try {
            val (privateKey, publicKey) = SecureIdentityStateManager(context).loadStaticKey()
                ?: return done(false)
            if (IdentityProof.fingerprint(publicKey) != fingerprint) return done(false)

            callable("profileChallenge").call()
                .addOnFailureListener {
                    Log.i(TAG, "challenge failed: ${it.message}")
                    done(false)
                }
                .addOnSuccessListener { result ->
                    val challenge = (result.getData() as? Map<*, *>)?.get("challenge") as? String
                    val proof = challenge?.let { IdentityProof.answer(privateKey, publicKey, uid, it) }
                    if (proof == null) {
                        done(false)
                        return@addOnSuccessListener
                    }
                    val request = mapOf(
                        "publicKey" to Base64.getEncoder().encodeToString(publicKey),
                        "proof" to proof,
                    )
                    callable("proveProfile").call(request).addOnCompleteListener {
                        if (!it.isSuccessful) Log.i(TAG, "claim failed: ${it.exception?.message}")
                        done(it.isSuccessful)
                    }
                }
        } catch (e: Exception) {
            Log.i(TAG, "claim unavailable: ${e.message}")
            done(false)
        }
    }

    private fun callable(name: String) =
        FirebaseFunctions.getInstance().getHttpsCallable(name).withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
}
