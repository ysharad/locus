package com.bitchat.android.connect

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions

/**
 * The "anonymous but accountable" identity anchor. A Google account is LINKED to the existing
 * anonymous Firebase user — same uid, so profile/mailbox ownership is untouched and the public
 * card stays exactly as anonymous as before. What changes: bans can follow the account instead
 * of the throwaway fingerprint, and the server will accept a verification claim
 * ([claimVerified] checks the Google identity inside the auth token, never a client assertion).
 */
object GoogleAnchor {

    private const val TAG = "GoogleAnchor"

    /**
     * The OAuth web client id the google-services plugin generates from google-services.json.
     * Referenced DIRECTLY (not via getIdentifier): a reflective lookup let release builds'
     * shrinkResources strip the string, which silently hid the Google button on Play builds.
     */
    fun webClientId(context: Context): String? =
        runCatching { context.getString(com.bitchat.android.R.string.default_web_client_id) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    fun signInIntent(context: Context): Intent? {
        val id = webClientId(context) ?: return null
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(id)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context.applicationContext, gso).signInIntent
    }

    /**
     * Handle the sign-in activity result: link the Google credential to the current anonymous
     * user (keeping the uid), turn on keep-chats, then ask the server to accept the verified
     * claim. [onResult] is invoked on the main thread with (success, userFacingMessage).
     */
    fun handleResult(data: Intent?, onResult: (Boolean, String?) -> Unit) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) {
                onResult(false, "Google didn't return a token — try again.")
                return
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            val task = if (user != null) user.linkWithCredential(credential)
            else auth.signInWithCredential(credential)
            task
                .addOnSuccessListener {
                    // Anchored: chats should survive, and the badge claim can go out.
                    ConnectManager.setKeepChats(true)
                    claimVerifiedAfterLink(onResult)
                }
                .addOnFailureListener { e ->
                    if (e is FirebaseAuthUserCollisionException) {
                        // This Google account already owns a Locus account — the person is
                        // coming BACK (typically after a reinstall). Signing in, rather than
                        // linking, adopts that account: verification, bans and saved chats
                        // follow it. This is the recovery path, not an error.
                        auth.signInWithCredential(credential)
                            .addOnSuccessListener {
                                ConnectManager.setKeepChats(true)
                                claimVerifiedAfterLink(onResult)
                            }
                            .addOnFailureListener { e2 ->
                                Log.i(TAG, "adopt sign-in failed: ${e2.message}")
                                onResult(false, e2.message)
                            }
                    } else {
                        Log.i(TAG, "link failed: ${e.message}")
                        onResult(false, e.message)
                    }
                }
        } catch (e: ApiException) {
            // Includes the user simply backing out of the account picker.
            onResult(false, if (e.statusCode == 12501) null else "Google sign-in failed (${e.statusCode}).")
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }

    /**
     * Log out of the anchor. Local chats stay on the phone; the relay stops; the badge comes
     * off the card — it belongs to the account, and logging back in adopts it all back.
     */
    fun logout() {
        try {
            ConnectManager.setKeepChats(false)
            val auth = FirebaseAuth.getInstance()
            auth.signOut()
            auth.signInAnonymously()
            ConnectManager.demoteVerified()
        } catch (_: Exception) { }
    }

    /**
     * Heal a missed badge. The claim can lose a race on first sign-in (the profile doc may not
     * have synced yet, so the server rightly refuses) — so any time we notice the session is
     * anchored but the local profile isn't verified, quietly claim again.
     */
    fun retryClaimIfNeeded() {
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return
            val anchored = user.providerData.any {
                it.providerId == "google.com" || it.providerId == "phone"
            }
            if (!anchored) return
            if (ConnectManager.myProfile.value?.verified == true) return
            claimVerifiedAfterLink { _, _ -> }
        } catch (_: Exception) { }
    }

    /** Shared by every anchor (Google, phone): ask the server to accept the verified claim. */
    internal fun claimVerifiedAfterLink(onResult: (Boolean, String?) -> Unit) {
        val fp = ConnectManager.myFingerprint()
        if (fp.isNullOrBlank()) {
            // Signed in fine; the badge will be claimable once the mesh identity exists.
            onResult(true, null)
            return
        }
        FirebaseFunctions.getInstance()
            .getHttpsCallable("claimVerified")
            .call(mapOf("fingerprint" to fp))
            .addOnSuccessListener {
                ConnectManager.markVerified()
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.i(TAG, "claimVerified failed: ${e.message}")
                // Signed in — that part worked; the badge just didn't land yet.
                onResult(true, null)
            }
    }
}
