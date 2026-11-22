package com.bitchat.android.connect

import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Proves possession of the mesh identity key to the server. Must match `functions/identity.js`. */
internal object IdentityProof {

    private const val CONTEXT = "locus-profile-claim-v1"
    private const val KEY_BYTES = 32

    fun fingerprint(publicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(publicKey).joinToString("") { "%02x".format(it) }

    /** The answer to [challenge], or null if the challenge is not a usable key. */
    fun answer(privateKey: ByteArray, publicKey: ByteArray, uid: String, challenge: String): String? {
        val serverKey = try {
            Base64.getDecoder().decode(challenge)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (serverKey.size != KEY_BYTES || privateKey.size != KEY_BYTES) return null

        val shared = ByteArray(KEY_BYTES)
        if (!X25519.calculateAgreement(privateKey, 0, serverKey, 0, shared, 0)) return null
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(shared, "HmacSHA256")) }
        val message = "$CONTEXT|$uid|${fingerprint(publicKey)}|$challenge"
        return Base64.getEncoder().encodeToString(mac.doFinal(message.toByteArray()))
    }
}
