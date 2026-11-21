package com.bitchat.android.connect

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * The funnel, and nothing else. 104 accounts authenticated and 24 finished a profile before this
 * existed, and the drop-off point was invisible — a profile is only written on save, so the 80
 * who quit left no trace of where. Four events answer that question; screen views, sessions and
 * retention come free with the SDK. No message content, no peer identifiers, no location ever
 * goes into an event.
 *
 * Every call is fire-and-forget and swallow-on-failure: measurement must never be a reason the
 * app misbehaves.
 */
object Metrics {

    /** First-run gate passed (18+ attestation + identity screen dismissed). */
    const val IDENTITY_DONE = "identity_done"

    /** A profile card was saved — the end of the funnel the 80 never reached. */
    const val PROFILE_SAVED = "profile_saved"

    /** A connection became mutual (fires on both sides). */
    const val MATCH_MADE = "match_made"

    /** A 1:1 chat was opened. */
    const val CHAT_OPENED = "chat_opened"

    /** A bitchat device was nearby this session; param `peak` is a bucket ("1", "2-3", "4-9", "10+"). */
    const val BITCHAT_NEARBY = "bitchat_nearby"

    /** The mesh scan ran this session (denominator for [BITCHAT_NEARBY]). */
    const val BITCHAT_SCAN_SESSION = "bitchat_scan_session"

    @Volatile private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        try {
            analytics = FirebaseAnalytics.getInstance(context.applicationContext)
        } catch (_: Exception) { }
    }

    fun log(event: String) {
        try {
            analytics?.logEvent(event, null)
        } catch (_: Exception) { }
    }
}
