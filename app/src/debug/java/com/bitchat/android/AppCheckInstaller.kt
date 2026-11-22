package com.bitchat.android

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds cannot pass Play Integrity (unsigned by Play, often on an emulator), so they use
 * the debug provider. It prints a debug secret to logcat under the tag "DebugAppCheckProvider";
 * that token has to be registered in Firebase Console > App Check > Apps > Manage debug tokens
 * before a local build can reach Firebase once enforcement is switched on.
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
