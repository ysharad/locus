package com.bitchat.android.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Remotely controlled ad stack configuration.
 *
 * Fetched from the goapps.store config repo on every launch (5s budget), cached in
 * SharedPreferences so the previous config applies offline, and falling back to
 * Google/Prebid test IDs on first run. Editing the JSON in the repo switches the
 * live ad mode for all installs on their next launch — no app release needed.
 *
 * mode precedence ladder: "admob" (plain AdMob) -> "gam" (GAM + AdX) -> "prebid"
 * (Prebid Mobile header bidding rendered through GAM).
 */
data class AdConfig(
    val adsEnabled: Boolean = true,
    val mode: String = MODE_ADMOB,
    val admobBannerUnit: String = TEST_ADMOB_BANNER,
    val rewardedUnit: String = LIVE_ADX_REWARDED,
    val rewardedBoostUnit: String = LIVE_ADX_REWARDED_BOOST,
    val gamBannerUnit: String = TEST_GAM_BANNER,
    val prebidServerUrl: String = TEST_PREBID_SERVER,
    val prebidAccountId: String = TEST_PREBID_ACCOUNT,
    // Prebid Server's health endpoint. Blank leaves the SDK default, which is wrong for PSP and
    // surfaces as InitializationStatus.SERVER_STATUS_WARNING — bidding still works, but the
    // warning hides real problems, so set it when pointing at a non-default server.
    val prebidStatusEndpoint: String = "",
    val prebidBannerConfigId: String = TEST_PREBID_CONFIG_ID,
    val prebidGamAdUnit: String = TEST_PREBID_GAM_UNIT,
    val prebidBannerWidth: Int = 320,
    val prebidBannerHeight: Int = 50,
    // Prebid config ids for the two rewarded placements. Blank = no header bidding on that
    // placement; it loads straight from GAM as before. There is deliberately no test default:
    // a made-up config id would fetch no demand and look like a broken integration.
    val prebidRewardedConfigId: String = "",
    val prebidRewardedBoostConfigId: String = "",
) {
    /** Prebid bids into the existing GAM rewarded unit; both a mode and a config id are required. */
    fun prebidConfigIdFor(placement: RewardedPlacement): String? {
        if (mode != MODE_PREBID) return null
        val id = when (placement) {
            RewardedPlacement.REVEAL -> prebidRewardedConfigId
            RewardedPlacement.BOOST -> prebidRewardedBoostConfigId
        }
        return id.ifBlank { null }
    }

    /** The unit to load, or null when ads are switched off. */
    fun rewardedUnitFor(placement: RewardedPlacement): String? = when {
        !adsEnabled -> null
        placement == RewardedPlacement.REVEAL -> rewardedUnit
        else -> rewardedBoostUnit
    }

    companion object {
        const val MODE_ADMOB = "admob"
        const val MODE_GAM = "gam"
        const val MODE_PREBID = "prebid"

        // Previous AdMob units (Locus, Mags Apps), superseded by the AdX units below but kept as
        // the rollback target: dropping either id into rewarded_unit / rewarded_boost_unit in the
        // remote JSON moves demand back to AdMob on the next launch, no release needed.
        // Unlike a GAM path these are format-bound and must be *Rewarded* units — RewardedAds.kt
        // calls RewardedAd.load(), and a Rewarded-Interstitial will not load through it.
        // `locus_rewarded_reveal_peer` (…/8788147896) is a rewarded interstitial and is
        // deliberately NOT used; `locus_rewarded_boost` (…/1376561734) is superseded.
        const val LIVE_ADMOB_REWARDED = "ca-app-pub-8045188034659976/1783755845"       // rewarded_reveal_card
        const val LIVE_ADMOB_REWARDED_BOOST = "ca-app-pub-8045188034659976/9571631007" // rewarded_boost_peer

        // Live Google Ad Manager / AdX units (network 22128176844) — the current rewarded demand
        // source. RewardedAds.kt spots the leading "/" and requests these with AdManagerAdRequest
        // rather than AdRequest, which is the only difference the SDK cares about; a GAM ad unit
        // is format-agnostic, so the format is decided by the load call, not by the unit.
        const val LIVE_ADX_REWARDED = "/22128176844/adx-rewarded-reveal"
        const val LIVE_ADX_REWARDED_BOOST = "/22128176844/adx-rewarded-boost"

        // Google's public test units and Prebid's public demo server/config.
        const val TEST_ADMOB_BANNER = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_ADMOB_REWARDED = "ca-app-pub-3940256099942544/5224354917"
        const val TEST_GAM_BANNER = "/21775744923/example/adaptive-banner"
        const val TEST_PREBID_SERVER = "https://prebid-server-test-j.prebid.org/openrtb2/auction"
        const val TEST_PREBID_ACCOUNT = "0689a263-318d-448b-a3d4-b02e8a709d9d"
        const val TEST_PREBID_CONFIG_ID = "prebid-demo-banner-320-50"
        const val TEST_PREBID_GAM_UNIT = "/21808260008/prebid_demo_app_original_api_banner"

        // Microsoft Monetize / Xandr Prebid Server Premium. PSP is served by the ordinary Prebid
        // Mobile SDK, so switching to it is purely config:
        //   server_url    -> PSP_SERVER_URL
        //   status_endpoint -> PSP_STATUS_ENDPOINT
        //   account_id    -> the Xandr **member id**
        //   *_config_id   -> the Xandr **placement id** (PSP's name for Prebid's config id)
        // Note the PSP docs still show Host.CUSTOM + setPrebidServerHost(); that setter no longer
        // exists in Prebid Mobile 3.x, where the host is the URL argument to initializeSdk().
        const val PSP_SERVER_URL = "https://ib.adnxs.com/openrtb2/prebid"
        const val PSP_STATUS_ENDPOINT = "https://ib.adnxs.com/status"

        fun fromJson(json: JSONObject): AdConfig {
            val defaults = AdConfig()
            val prebid = json.optJSONObject("prebid")
            return AdConfig(
                adsEnabled = json.optBoolean("ads_enabled", defaults.adsEnabled),
                mode = json.optString("mode", defaults.mode).lowercase(),
                admobBannerUnit = json.optString("admob_banner_unit", defaults.admobBannerUnit),
                rewardedUnit = json.optString("rewarded_unit", defaults.rewardedUnit),
                rewardedBoostUnit = json.optString("rewarded_boost_unit", defaults.rewardedBoostUnit),
                gamBannerUnit = json.optString("gam_banner_unit", defaults.gamBannerUnit),
                prebidServerUrl = prebid?.optString("server_url", defaults.prebidServerUrl)
                    ?: defaults.prebidServerUrl,
                prebidAccountId = prebid?.optString("account_id", defaults.prebidAccountId)
                    ?: defaults.prebidAccountId,
                prebidStatusEndpoint = prebid?.optString(
                    "status_endpoint", defaults.prebidStatusEndpoint
                ) ?: defaults.prebidStatusEndpoint,
                prebidBannerConfigId = prebid?.optString("banner_config_id", defaults.prebidBannerConfigId)
                    ?: defaults.prebidBannerConfigId,
                prebidGamAdUnit = prebid?.optString("gam_ad_unit", defaults.prebidGamAdUnit)
                    ?: defaults.prebidGamAdUnit,
                prebidBannerWidth = prebid?.optInt("width", defaults.prebidBannerWidth)
                    ?: defaults.prebidBannerWidth,
                prebidBannerHeight = prebid?.optInt("height", defaults.prebidBannerHeight)
                    ?: defaults.prebidBannerHeight,
                prebidRewardedConfigId = prebid?.optString(
                    "rewarded_config_id", defaults.prebidRewardedConfigId
                ) ?: defaults.prebidRewardedConfigId,
                prebidRewardedBoostConfigId = prebid?.optString(
                    "rewarded_boost_config_id", defaults.prebidRewardedBoostConfigId
                ) ?: defaults.prebidRewardedBoostConfigId,
            )
        }
    }
}

object AdConfigLoader {

    // Apex directly: the ysharad.github.io path 301s here, and following that costs an extra
    // round trip on every cold start of the one mechanism that changes ad config without a release.
    private const val CONFIG_URL = "https://goapps.store/adconfig/locus.json"
    private const val PREFS = "ad_config_cache"
    private const val KEY_JSON = "config_json"

    @Volatile
    private var memory: AdConfig? = null

    /** The config already loaded this session, if any. */
    fun current(): AdConfig? = memory

    suspend fun load(context: Context): AdConfig {
        memory?.let { return it }
        val appContext = context.applicationContext
        val fetched = withContext(Dispatchers.IO) { fetchRemote(appContext) }
        val config = fetched ?: readCache(appContext) ?: AdConfig()
        memory = config
        return config
    }

    private fun fetchRemote(context: Context): AdConfig? = try {
        val connection = URL(CONFIG_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", "locus/ad-config (Android)")
        connection.inputStream.bufferedReader().use { reader ->
            val body = reader.readText()
            val config = AdConfig.fromJson(JSONObject(body))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_JSON, body).apply()
            config
        }
    } catch (_: Exception) {
        null
    }

    private fun readCache(context: Context): AdConfig? = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)
            ?.let { AdConfig.fromJson(JSONObject(it)) }
    } catch (_: Exception) {
        null
    }
}
