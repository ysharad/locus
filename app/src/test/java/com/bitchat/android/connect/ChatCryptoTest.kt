package com.bitchat.android.connect

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChatCryptoTest {

    @Test
    fun `seal then open round-trips`() {
        val (priv, pub) = ChatCrypto.generateKeypair()
        val msg = "by the sound desk, black jacket 🜂".toByteArray()
        val sealed = ChatCrypto.seal(pub, msg)
        val opened = ChatCrypto.open(priv, pub, sealed)
        assertArrayEquals(msg, opened)
    }

    @Test
    fun `each seal is unique (fresh ephemeral key)`() {
        val (_, pub) = ChatCrypto.generateKeypair()
        val msg = "same message".toByteArray()
        val a = ChatCrypto.seal(pub, msg)
        val b = ChatCrypto.seal(pub, msg)
        assertTrue("ciphertexts must differ", !a.contentEquals(b))
    }

    @Test
    fun `wrong recipient cannot open`() {
        val (_, pubA) = ChatCrypto.generateKeypair()
        val (privB, pubB) = ChatCrypto.generateKeypair()
        val sealed = ChatCrypto.seal(pubA, "secret".toByteArray())
        try {
            ChatCrypto.open(privB, pubB, sealed)
            fail("wrong key must not decrypt")
        } catch (e: Exception) {
            // expected: AEAD tag check fails
        }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val (priv, pub) = ChatCrypto.generateKeypair()
        val sealed = ChatCrypto.seal(pub, "trust me".toByteArray()).copyOf()
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        try {
            ChatCrypto.open(priv, pub, sealed)
            fail("tampered message must be rejected")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `two real parties exchange (A seals to B, B opens)`() {
        val (_, pubA) = ChatCrypto.generateKeypair()
        val (privB, pubB) = ChatCrypto.generateKeypair()
        // A sends to B
        val plaintext = "moving to the roof, come up".toByteArray()
        val sealed = ChatCrypto.seal(pubB, plaintext)
        val opened = ChatCrypto.open(privB, pubB, sealed)
        assertEquals(String(plaintext), String(opened))
    }
}
