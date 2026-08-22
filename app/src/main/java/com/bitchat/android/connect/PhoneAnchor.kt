package com.bitchat.android.connect

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Phone-number identity anchor — the second door to the ✓ badge, and in practice a stronger
 * one than Google here: a fresh Gmail is free, a fresh phone number isn't. Same contract as
 * [GoogleAnchor]: the credential is LINKED to the existing anonymous user (uid unchanged, the
 * public card stays anonymous, the number is never shown to anyone), then the server-side
 * claim runs. SMS is region-locked to IN in the project config to keep pumping fraud out.
 */
object PhoneAnchor {

    /**
     * Firebase's auth errors are written for developers ("The format of the phone number
     * provided is incorrect..."). Users get one short sentence they can act on.
     */
    fun friendly(raw: String?): String {
        val m = raw.orEmpty().lowercase()
        return when {
            m.contains("format of the phone number") || m.contains("invalid_phone_number") ||
                m.contains("invalid format") -> "That number doesn't look right."
            m.contains("verification code") && m.contains("invalid") -> "That code isn't right."
            m.contains("expired") || m.contains("session") -> "That code expired — send a new one."
            m.contains("too many") || m.contains("quota") || m.contains("blocked") ->
                "Too many tries. Give it a few minutes."
            m.contains("network") || m.contains("timeout") || m.contains("unavailable") ->
                "No connection. Try again."
            m.contains("not authorized") || m.contains("integrity") ->
                "Couldn't verify this app. Try again in a moment."
            m.isBlank() -> "Something went wrong. Try again."
            else -> "Couldn't verify that number. Try again."
        }
    }

    /**
     * Begin OTP verification for [phoneE164] (e.g. "+919876543210"). [onCodeSent] fires with
     * the verification id when the SMS goes out; [onDone] fires on instant/auto verification
     * (some devices verify without the user typing the code) or on failure.
     */
    /** Kept so "Resend code" reuses the same verification session rather than starting over. */
    @Volatile private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun start(
        activity: Activity,
        phoneE164: String,
        resend: Boolean = false,
        onCodeSent: (String) -> Unit,
        onDone: (Boolean, String?) -> Unit
    ) {
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phoneE164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .apply { if (resend) resendToken?.let { setForceResendingToken(it) } }
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    link(credential, onDone)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    onDone(false, friendly(e.message))
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    resendToken = token
                    onCodeSent(verificationId)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** The user typed the SMS code. */
    fun confirmCode(verificationId: String, code: String, onDone: (Boolean, String?) -> Unit) {
        link(PhoneAuthProvider.getCredential(verificationId, code), onDone)
    }

    private fun link(credential: PhoneAuthCredential, onDone: (Boolean, String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val task = if (user != null) user.linkWithCredential(credential)
        else auth.signInWithCredential(credential)
        task
            .addOnSuccessListener {
                ConnectManager.setKeepChats(true)
                GoogleAnchor.claimVerifiedAfterLink(onDone)
            }
            .addOnFailureListener { e ->
                if (e is FirebaseAuthUserCollisionException) {
                    // Same recovery rule as GoogleAnchor: an already-used number means the
                    // person is coming back — adopt that account instead of erroring.
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener {
                            ConnectManager.setKeepChats(true)
                            GoogleAnchor.claimVerifiedAfterLink(onDone)
                        }
                        .addOnFailureListener { e2 -> onDone(false, e2.message) }
                } else {
                    onDone(false, friendly(e.message))
                }
            }
    }
}
