package com.bitchat.android

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest with Play Integrity — Google vouches that the request came from a genuine,
 * Play-installed, unmodified Locus on a real device.
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
