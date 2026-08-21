package com.bitchat.android.connect

import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import com.google.crypto.tink.subtle.XChaCha20Poly1305

/**
 * End-to-end sealed-box encryption for the opt-in online chat relay. The backend only ever stores
 * the output of [seal] — ciphertext addressed to the recipient's public key, which nobody but the
 * holder of the matching private key can [open]. Built from Tink's low-level primitives (the same
 * ones the mesh's Nostr layer already uses), so there is no dependency on a keyset format.
 *
 * Wire format of a sealed message: ephemeralPublicKey(32) ‖ XChaCha20Poly1305(nonce24 ‖ ct ‖ tag16).
 * A fresh ephemeral key per message gives forward secrecy against later compromise of the sender.
 */
object ChatCrypto {

    /** A fresh X25519 identity for cloud chat. Returns (privateKey, publicKey), 32 bytes each. */
    fun generateKeypair(): Pair<ByteArray, ByteArray> {
        val priv = X25519.generatePrivateKey()
        val pub = X25519.publicFromPrivate(priv)
        return priv to pub
    }

    /** Seal [plaintext] to [recipientPub] (their 32-byte X25519 public key). */
    fun seal(recipientPub: ByteArray, plaintext: ByteArray): ByteArray {
        val esk = X25519.generatePrivateKey()
        val epk = X25519.publicFromPrivate(esk)
        val shared = X25519.computeSharedSecret(esk, recipientPub)
        val key = Hkdf.computeHkdf("HMACSHA256", shared, null, epk + recipientPub, 32)
        val ct = XChaCha20Poly1305(key).encrypt(plaintext, null)
        return epk + ct
    }

    /** Open a sealed message with my keypair. Throws on tamper / wrong key. */
    fun open(myPriv: ByteArray, myPub: ByteArray, sealed: ByteArray): ByteArray {
        require(sealed.size > 32) { "sealed message too short" }
        val epk = sealed.copyOfRange(0, 32)
        val ct = sealed.copyOfRange(32, sealed.size)
        val shared = X25519.computeSharedSecret(myPriv, epk)
        val key = Hkdf.computeHkdf("HMACSHA256", shared, null, epk + myPub, 32)
        return XChaCha20Poly1305(key).decrypt(ct, null)
    }
}
