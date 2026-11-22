package com.bitchat.android

import android.app.Application
import com.bitchat.android.ui.theme.ThemePreferenceManager

/**
 * Main application class for bitchat Android
 */
class BitchatApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // App Check must be installed before anything touches Auth/Firestore/Functions, so it
        // goes first. Our API key ships in every APK; this is what proves a request actually came
        // from a genuine Locus install rather than someone replaying the key.
        initAppCheck()

        // Start the single process-wide power policy before transport components are constructed.
        com.bitchat.android.mesh.PowerManager.getInstance(this).start()

        // Nostr/Tor deliberately NOT started (severed 2026-08-21): Locus's out-of-mesh path is
        // the Firebase relay. The inherited Tor bootstrap + relay reconnect loops were pure
        // battery drain here. Code stays in-tree for now; nothing initializes or routes to it.

        // Measurement first, so the funnel sees everything after it (init is cheap and safe).
        try { com.bitchat.android.connect.Metrics.init(this) } catch (_: Exception) { }
        registerCensusFlush()

        // Initialize the Locus layer before transports can deliver control traffic
        try { com.bitchat.android.connect.ConnectManager.init(this) } catch (_: Exception) { }

        // If the account is anchored (Google/phone) but the badge claim was missed — e.g. the
        // first claim raced the profile's very first sync — land it now.
        try { com.bitchat.android.connect.GoogleAnchor.retryClaimIfNeeded() } catch (_: Exception) { }

        // Initialize favorites persistence early so MessageRouter/NostrTransport can use it on startup
        try {
            com.bitchat.android.favorites.FavoritesPersistenceService.initialize(this)
        } catch (_: Exception) { }

        // Restore private conversations before background transports can deliver new messages.
        // AppStateStore merges any in-flight arrivals by message ID, so startup cannot replace
        // newer transport state with an older database snapshot.
        try {
            com.bitchat.android.services.AppStateStore.initializeConversationPersistence(this)
        } catch (_: Exception) { }

        // Initialize theme preference
        ThemePreferenceManager.init(this)

        // Initialize chat UI mode (matrix transcript vs bubbles)
        com.bitchat.android.ui.theme.ChatUiModeManager.init(this)

        // Initialize debug preference manager (persists debug toggles)
        try { com.bitchat.android.ui.debug.DebugPreferenceManager.init(this) } catch (_: Exception) { }

        // Initialize Wi‑Fi Aware controller with persisted default
        try {
            val enabled = com.bitchat.android.ui.debug.DebugPreferenceManager.getWifiAwareEnabled(false)
            com.bitchat.android.wifiaware.WifiAwareController.initialize(this, enabled)
        } catch (_: Exception) { }

        // Initialize Geohash Registries for persistence
        try {
            com.bitchat.android.nostr.GeohashAliasRegistry.initialize(this)
            com.bitchat.android.nostr.GeohashConversationRegistry.initialize(this)
        } catch (_: Exception) { }

        // Initialize mesh service preferences
        try { com.bitchat.android.service.MeshServicePreferences.init(this) } catch (_: Exception) { }

        // Proactively start the foreground service to keep mesh alive
        try { com.bitchat.android.service.MeshForegroundService.start(this) } catch (_: Exception) { }
    }

    /**
     * Register the App Check provider. The provider itself is chosen per build type in
     * src/release and src/debug — the debug factory is not on the release classpath, and a
     * reflective lookup would be the same trap that once stripped the Google sign-in button
     * under R8.
     *
     * Failure is swallowed on purpose: App Check is a hardening layer, and a provider that cannot
     * initialize must never be the reason the app fails to start.
     */
    private fun initAppCheck() {
        try {
            AppCheckInstaller.install()
        } catch (e: Exception) {
            android.util.Log.w("BitchatApplication", "App Check init failed: ${e.message}")
        }
    }

    /** Count nearby devices per foreground session; log the bucket when the app leaves the foreground. */
    private fun registerCensusFlush() {
        try {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : androidx.lifecycle.DefaultLifecycleObserver {
                    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                        try { com.bitchat.android.connect.BitchatCensus.beginSession() } catch (_: Exception) { }
                    }

                    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                        try {
                            val census = com.bitchat.android.connect.BitchatCensus
                            census.events(census.flush()).forEach { (event, params) ->
                                com.bitchat.android.connect.Metrics.log(event, params)
                            }
                        } catch (_: Exception) { }
                    }
                }
            )
        } catch (_: Exception) { }
    }
}
