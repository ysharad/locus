package com.bitchat.android.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import org.prebid.mobile.RewardedVideoAdUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Which perk the viewer is watching for. Each maps to its own AdMob unit so revenue is attributable. */
enum class RewardedPlacement { REVEAL, BOOST }

/**
 * Rewarded ads, kept warm per placement. Locus never gates content behind money — the only
 * "premium" currency is a few seconds of attention. Watch a short video, unlock a perk (see who
 * likes you, jump to the front of nearby decks). One ad per placement is preloaded and immediately
 * replenished after it's shown, so the perk buttons feel instant.
 *
 * Unit ids come from the same remotely-controlled [AdConfig] as the banner, so they can be swapped
 * without an app release, and either an AdMob id or an Ad Manager/AdX path works — see
 * [isAdManagerUnit]. Whichever it is, the unit must be of the **Rewarded** format: a
 * Rewarded-Interstitial or a banner unit will not load through [RewardedAd.load], regardless of
 * what the unit happens to be named.
 */
object RewardedAds {

    private const val TAG = "RewardedAds"

    /** Extra grace on top of Prebid's own timeout before the watchdog forces the GAM load. */
    private const val WATCHDOG_GRACE_MS = 2_000L

    private val ads = ConcurrentHashMap<RewardedPlacement, RewardedAd>()
    private val loading = ConcurrentHashMap<RewardedPlacement, Boolean>()

    /** In-flight Prebid auctions, held so the ad unit survives until fetchDemand calls back. */
    private val auctions = ConcurrentHashMap<RewardedPlacement, RewardedVideoAdUnit>()

    /** Ad Manager/AdX units are network paths ("/22128176844/adx-rewarded-reveal"). */
    private fun isAdManagerUnit(unit: String) = unit.startsWith("/")

    /** Warm every placement. Safe to call repeatedly. */
    fun preload(context: Context) {
        RewardedPlacement.values().forEach { preload(context, it) }
    }

    /** Kick off a load for one placement if we don't already have one in hand. */
    fun preload(context: Context, placement: RewardedPlacement) {
        if (ads[placement] != null || loading[placement] == true) return
        loading[placement] = true
        val appCtx = context.applicationContext
        // RewardedAd.load must run on the main thread; config load is suspend, so hop through a scope.
        CoroutineScope(Dispatchers.Main).launch {
            val cfg = try {
                AdConfigLoader.load(appCtx)
            } catch (_: Exception) {
                null
            }
            val unit = when (placement) {
                RewardedPlacement.REVEAL -> cfg?.rewardedUnit ?: AdConfig.LIVE_ADX_REWARDED
                RewardedPlacement.BOOST -> cfg?.rewardedBoostUnit ?: AdConfig.LIVE_ADX_REWARDED_BOOST
            }
            val callback = object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    ads[placement] = loaded
                    loading[placement] = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded load failed ($placement, unit=$unit): ${error.message}")
                    ads.remove(placement)
                    loading[placement] = false
                }
            }
            // Ad Manager/AdX unit paths look like /<network>/<unit>; AdMob ids look like
            // ca-app-pub-<pub>/<unit>. The two take different request types and the SDK picks
            // the overload statically, so branch here rather than passing a common supertype:
            // a GAM path requested with a plain AdRequest simply never fills.
            if (!isAdManagerUnit(unit)) {
                RewardedAd.load(appCtx, unit, AdRequest.Builder().build(), callback)
                return@launch
            }

            val request = AdManagerAdRequest.Builder().build()
            val prebidConfigId = cfg?.prebidConfigIdFor(placement)
            if (prebidConfigId == null) {
                RewardedAd.load(appCtx, unit, request, callback)
                return@launch
            }

            // Header bidding is strictly additive. fetchDemand only *adds* targeting key-values to
            // this same request; it never replaces or cancels it. So whatever comes back — a
            // winning bid, NO_BIDS, TIMEOUT, a server error — the identical request still goes to
            // GAM, which runs its usual auction and lets AdX serve exactly as it does with Prebid
            // switched off. Adding demand to the Prebid server later starts competing against AdX
            // with no further app change.
            AdsBootstrap.ensurePrebid(appCtx, cfg)
            if (!AdsBootstrap.prebidReady()) {
                // Init is asynchronous, so the first preload after launch generally lands here and
                // goes straight to GAM. That is the correct trade: never make a user wait on the
                // bidder. Each placement re-preloads as soon as its ad is consumed, and those
                // loads pick Prebid up, so the bypass is limited to the first fill per placement.
                Log.d(TAG, "Prebid not ready yet ($placement); loading $unit from GAM")
                RewardedAd.load(appCtx, unit, request, callback)
                return@launch
            }
            fetchPrebidThenLoad(appCtx, placement, unit, prebidConfigId, request, callback)
        }
    }

    /**
     * Run the Prebid auction, then load the GAM unit **exactly once**, whatever happened.
     *
     * The bid (if any) rides along as targeting on [request]. The only job here is to guarantee
     * the GAM load still happens, so a silent Prebid server — no demand yet, unreachable, hung —
     * can never cost an AdX impression.
     */
    private fun fetchPrebidThenLoad(
        appCtx: Context,
        placement: RewardedPlacement,
        unit: String,
        configId: String,
        request: AdManagerAdRequest,
        callback: RewardedAdLoadCallback,
    ) {
        val adUnit = RewardedVideoAdUnit(configId)
        // Hold a reference for the duration of the auction so it cannot be collected mid-fetch.
        auctions[placement] = adUnit
        val done = AtomicBoolean(false)
        fun loadGam(reason: String) {
            if (!done.compareAndSet(false, true)) return
            auctions.remove(placement)
            Log.d(TAG, "Prebid $placement -> $reason; loading $unit")
            RewardedAd.load(appCtx, unit, request, callback)
        }
        try {
            adUnit.fetchDemand(request) { code -> loadGam(code.name) }
        } catch (t: Throwable) {
            loadGam("fetchDemand threw: ${t.message}")
            return
        }
        // Prebid's own cap should fire first; this only covers a callback that never arrives.
        Handler(Looper.getMainLooper()).postDelayed(
            { loadGam("watchdog") },
            AdsBootstrap.PREBID_AUCTION_TIMEOUT_MS + WATCHDOG_GRACE_MS,
        )
    }

    fun isReady(placement: RewardedPlacement = RewardedPlacement.REVEAL): Boolean =
        ads[placement] != null

    /**
     * Show the ad for [placement]. [onReward] fires only if the viewer actually earns the reward
     * (watched enough); [onUnavailable] fires when there's no ad ready or it failed to present, so
     * the caller can fail open or tell the user to try again. A fresh ad is preloaded either way.
     */
    fun show(
        activity: Activity,
        placement: RewardedPlacement = RewardedPlacement.REVEAL,
        onReward: () -> Unit,
        onUnavailable: () -> Unit = {},
    ) {
        val current = ads.remove(placement) // consume; a replacement is loaded on dismissal
        if (current == null) {
            onUnavailable()
            preload(activity, placement)
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity, placement)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded show failed ($placement): ${error.message}")
                preload(activity, placement)
                onUnavailable()
            }
        }
        // Reward at EARN time, not dismissal: a creative with a broken close button (seen with
        // Google's test unit) must never be able to eat a reward the user already earned.
        current.show(activity) { onReward() }
    }
}
