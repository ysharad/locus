package com.bitchat.android.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.InitializationStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Consent + SDK bootstrap. Call from MainActivity.onCreate. Gathers UMP consent
 * (required for EEA/UK ad serving) and then initializes the Google Mobile Ads SDK
 * off the main thread. Prebid is initialized lazily, only when the remote config
 * selects prebid mode.
 */
object AdsBootstrap {

    private const val TAG = "AdsBootstrap"

    /** Prebid's own auction cap. The GAM request goes out regardless once this elapses. */
    const val PREBID_AUCTION_TIMEOUT_MS = 3_000

    private val mobileAdsStarted = AtomicBoolean(false)
    private val prebidStarted = AtomicBoolean(false)
    private val prebidUsable = AtomicBoolean(false)

    /** Held from [init] so Settings can query and re-open the consent form. */
    @Volatile
    private var consentInformation: ConsentInformation? = null

    /**
     * Whether this user must be offered a way to change or withdraw ad consent. UMP answers true
     * only where the consent programme demands it (EEA/UK/Switzerland), so the settings entry
     * stays hidden everywhere else instead of advertising a control that does nothing.
     */
    fun privacyOptionsRequired(): Boolean =
        consentInformation?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Re-present the consent message so the user can revoke or change their choice — required by
     * the Privacy & messaging consent programme, which is not satisfied by showing the form once
     * at first launch.
     */
    fun showPrivacyOptions(activity: Activity, onError: (String) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form: ${formError.message}")
                onError(formError.message)
            } else if (consentInformation?.canRequestAds() == true) {
                // Consent may have just been granted; nothing is loaded until it is.
                startMobileAds(activity)
            }
        }
    }

    fun init(activity: Activity) {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = consentInfo
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
        CoroutineScope(Dispatchers.IO).launch {
            if (AdConfigLoader.load(appContext).adsEnabled) {
                MobileAds.initialize(appContext) { RewardedAds.preload(appContext) }
            } else {
                mobileAdsStarted.set(false)
            }
        }
    }

    /**
     * True once Prebid can be asked for demand. Bidding is *additive*: when this is false the
     * ad request simply goes to GAM unchanged and AdX serves as it does today, so nothing here
     * is ever allowed to block a load.
     */
    fun prebidReady(): Boolean = prebidUsable.get()

    fun ensurePrebid(context: Context, config: AdConfig) {
        if (!prebidStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        // Prebid requires main-thread initialization.
        Handler(Looper.getMainLooper()).post {
            // On PSP this is the Xandr member id; on vanilla Prebid Server, the account id.
            PrebidMobile.setPrebidServerAccountId(config.prebidAccountId)
            // Cap the auction well inside the user's patience; on timeout we load without bids.
            PrebidMobile.setTimeoutMillis(PREBID_AUCTION_TIMEOUT_MS)
            // Health check lives at a server-specific path (PSP: ib.adnxs.com/status). Getting it
            // wrong only costs a SERVER_STATUS_WARNING, but that warning is worth keeping honest.
            config.prebidStatusEndpoint.takeIf { it.isNotBlank() }?.let {
                PrebidMobile.setCustomStatusEndpoint(it)
            }
            try {
                PrebidMobile.checkGoogleMobileAdsCompatibility(MobileAds.getVersion().toString())
            } catch (t: Throwable) {
                Log.w(TAG, "Prebid/GMA compatibility check: ${t.message}")
            }
            PrebidMobile.initializeSdk(appContext, config.prebidServerUrl) { status ->
                // SERVER_STATUS_WARNING means the status endpoint was unhappy but bidding still
                // works, so only a hard FAILED takes Prebid out of the path.
                val usable = status != InitializationStatus.FAILED
                prebidUsable.set(usable)
                if (usable) {
                    Log.i(TAG, "Prebid ready ($status)")
                } else {
                    Log.w(TAG, "Prebid init failed (${status.description}); GAM/AdX serves alone")
                }
            }
        }
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
