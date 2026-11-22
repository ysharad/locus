package com.bitchat.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class IdentityProofTest {
    private val privateKey = ByteArray(32) { 0x11 }
    private val publicKey = Base64.getDecoder().decode("e06Qm75//kTEZaIgA31gjuNYl9Me+XLwf3SJLLD3PxM=")
    private val challenge = "D6poTtKIZ7l/Smot7l34zpdOdrcBjj8iocTPJnhXDyA="

    // Golden vector computed with the server's crypto (Node), from synthetic keys.
    @Test
    fun `answer matches the server golden vector`() {
        assertEquals("e9uEfML+0XnSw6vbsKb/Dtbp+wK39qqXTzZrGUaNFUc=", IdentityProof.answer(privateKey, publicKey, "test-uid", challenge))
    }

    @Test
    fun `a malformed challenge is refused`() {
        assertNull(IdentityProof.answer(privateKey, publicKey, "test-uid", "AAAA"))
        assertNull(IdentityProof.answer(privateKey, publicKey, "test-uid", "not base64!"))
    }

    @Test
    fun `an all-zero challenge is refused rather than answered`() {
        val zero = Base64.getEncoder().encodeToString(ByteArray(32))
        assertNull(IdentityProof.answer(privateKey, publicKey, "test-uid", zero))
    }
}
