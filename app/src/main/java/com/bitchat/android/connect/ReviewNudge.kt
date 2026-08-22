package com.bitchat.android.connect

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * The Play in-app review ask, fired only at moments of finished delight — dismissing a match
 * celebration (second match onward) or closing a chat that became a real conversation. Never
 * mid-task, never on a timer. Our own throttle keeps asks rare; on top of that the Play API
 * has an opaque quota and silently decides whether anything is actually shown, so callers
 * must treat this as fire-and-forget.
 */
object ReviewNudge {

    private const val TAG = "ReviewNudge"
    private const val KEY_LAST_ASK = "review_last_ask"
    private const val MIN_GAP_MS = 45L * 24 * 60 * 60 * 1000 // at most one ask per 45 days

    fun maybeAsk(activity: Activity?) {
        activity ?: return
        try {
            val store = ConnectStore(activity.applicationContext)
            val now = System.currentTimeMillis()
            if (now - store.long(KEY_LAST_ASK, 0L) < MIN_GAP_MS) return
            store.setLong(KEY_LAST_ASK, now)

            val manager = ReviewManagerFactory.create(activity.applicationContext)
            manager.requestReviewFlow().addOnSuccessListener { info ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    manager.launchReviewFlow(activity, info)
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "review ask skipped: ${e.message}")
        }
    }
}
