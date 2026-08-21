package com.bitchat.android.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.PrebidMobile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Consent + SDK bootstrap. Call from MainActivity.onCreate. Gathers UMP consent
 * (required for EEA/UK ad serving) and then initializes the Google Mobile Ads SDK
 * off the main thread. Prebid is initialized lazily, only when the remote config
 * selects prebid mode.
 */
object AdsBootstrap {

    private val mobileAdsStarted = AtomicBoolean(false)
    private val prebidStarted = AtomicBoolean(false)

    fun init(activity: Activity) {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consentInfo.canRequestAds()) startMobileAds(activity)
                }
            },
            {
                // Consent update failed (e.g. offline): serve with whatever consent state exists.
                if (consentInfo.canRequestAds()) startMobileAds(activity)
            },
        )
        // Consent may already be gathered from a previous session.
        if (consentInfo.canRequestAds()) startMobileAds(activity)
    }

    private fun startMobileAds(context: Context) {
        if (!mobileAdsStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        thread { MobileAds.initialize(appContext) { RewardedAds.preload(appContext) } }
    }

    fun ensurePrebid(context: Context, config: AdConfig) {
        if (!prebidStarted.compareAndSet(false, true)) return
        PrebidMobile.setPrebidServerAccountId(config.prebidAccountId)
        PrebidMobile.initializeSdk(context.applicationContext, config.prebidServerUrl, null)
    }
}

/**
 * The one banner slot every screen uses. Which stack fills it — plain AdMob,
 * GAM (AdX), or Prebid header bidding into GAM — is decided by the remote
 * AdConfig at launch. ads_enabled=false collapses the slot entirely.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config by produceState<AdConfig?>(initialValue = null) {
        value = AdConfigLoader.load(context)
    }
    val cfg = config ?: return
    if (!cfg.adsEnabled) return

    key(cfg.mode, cfg.admobBannerUnit, cfg.gamBannerUnit, cfg.prebidGamAdUnit) {
        when (cfg.mode) {
            AdConfig.MODE_GAM -> GamBanner(cfg, modifier)
            AdConfig.MODE_PREBID -> PrebidBanner(cfg, modifier)
            else -> AdmobBanner(cfg, modifier)
        }
    }
}

@Composable
private fun AdmobBanner(config: AdConfig, modifier: Modifier) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
                )
                adUnitId = config.admobBannerUnit
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}

@Composable
private fun GamBanner(config: AdConfig, modifier: Modifier) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdManagerAdView(context).apply {
                setAdSizes(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
                )
                adUnitId = config.gamBannerUnit
                loadAd(AdManagerAdRequest.Builder().build())
            }
        },
    )
}

@Composable
private fun PrebidBanner(config: AdConfig, modifier: Modifier) {
    val context = LocalContext.current
    val adUnit = remember(config.prebidBannerConfigId) {
        AdsBootstrap.ensurePrebid(context, config)
        BannerAdUnit(
            config.prebidBannerConfigId,
            config.prebidBannerWidth,
            config.prebidBannerHeight,
        )
    }
    DisposableEffect(adUnit) {
        onDispose { adUnit.stopAutoRefresh() }
    }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdManagerAdView(ctx).apply {
                setAdSizes(AdSize(config.prebidBannerWidth, config.prebidBannerHeight))
                adUnitId = config.prebidGamAdUnit
                val request = AdManagerAdRequest.Builder().build()
                // Prebid auction first; its key-values ride on the GAM request.
                adUnit.fetchDemand(request) { _ -> loadAd(request) }
            }
        },
    )
}
