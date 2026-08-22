package com.bitchat.android.net

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TorPreferenceManager {
    private const val PREFS_NAME = "bitchat_settings"
    private const val KEY_TOR_MODE = "tor_mode"

    // Tor is severed in Locus (out-of-mesh traffic goes over the Firebase relay). OFF is forced:
    // a legacy saved "ON" from older builds must not resurrect the bootstrap/battery drain.
    private val _modeFlow = MutableStateFlow(TorMode.OFF)
    val modeFlow: StateFlow<TorMode> = _modeFlow

    fun init(context: Context) {
        _modeFlow.value = TorMode.OFF
    }

    fun set(context: Context, mode: TorMode) {
        // Ignore requests to enable; keep the stored value pinned OFF.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOR_MODE, TorMode.OFF.name).apply()
        _modeFlow.value = TorMode.OFF
    }

    fun get(context: Context): TorMode = TorMode.OFF
}
