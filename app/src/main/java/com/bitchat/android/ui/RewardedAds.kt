package com.bitchat.android.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A single rewarded ad, kept warm. Locus never gates content behind money — the only "premium"
 * currency is a few seconds of attention. Watch a short video, unlock a perk (see who likes you,
 * jump to the front of nearby decks). One ad is preloaded at a time and immediately replenished
 * after it's shown, so the perk buttons feel instant.
 *
 * The unit id comes from the same remotely-controlled [AdConfig] as the banner, so it can be
 * swapped without an app release; it falls back to Google's public test rewarded unit.
 */
object RewardedAds {

    private const val TAG = "RewardedAds"

    @Volatile private var ad: RewardedAd? = null
    @Volatile private var loading = false

    /** Kick off a load if we don't already have one in hand. Safe to call repeatedly. */
    fun preload(context: Context) {
        if (ad != null || loading) return
        loading = true
        val appCtx = context.applicationContext
        // RewardedAd.load must run on the main thread; config load is suspend, so hop through a scope.
        CoroutineScope(Dispatchers.Main).launch {
            val unit = try {
                AdConfigLoader.load(appCtx).rewardedUnit
            } catch (_: Exception) {
                AdConfig.TEST_ADMOB_REWARDED
            }
            RewardedAd.load(
                appCtx,
                unit,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(loaded: RewardedAd) {
                        ad = loaded
                        loading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Rewarded load failed: ${error.message}")
                        ad = null
                        loading = false
                    }
                },
            )
        }
    }

    fun isReady(): Boolean = ad != null

    /**
     * Show the ad. [onReward] fires only if the viewer actually earns the reward (watched enough);
     * [onUnavailable] fires when there's no ad ready or it failed to present, so the caller can tell
     * the user to try again in a moment. A fresh ad is preloaded either way.
     */
    fun show(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit = {}) {
        val current = ad
        if (current == null) {
            onUnavailable()
            preload(activity)
            return
        }
        ad = null // consume; a replacement is loaded on dismissal
        var earned = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity)
                if (earned) onReward()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded show failed: ${error.message}")
                preload(activity)
                onUnavailable()
            }
        }
        current.show(activity) { earned = true }
    }
}
