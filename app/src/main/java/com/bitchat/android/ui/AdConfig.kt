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
    val rewardedUnit: String = TEST_ADMOB_REWARDED,
    val gamBannerUnit: String = TEST_GAM_BANNER,
    val prebidServerUrl: String = TEST_PREBID_SERVER,
    val prebidAccountId: String = TEST_PREBID_ACCOUNT,
    val prebidBannerConfigId: String = TEST_PREBID_CONFIG_ID,
    val prebidGamAdUnit: String = TEST_PREBID_GAM_UNIT,
    val prebidBannerWidth: Int = 320,
    val prebidBannerHeight: Int = 50,
) {
    companion object {
        const val MODE_ADMOB = "admob"
        const val MODE_GAM = "gam"
        const val MODE_PREBID = "prebid"

        // Google's public test units and Prebid's public demo server/config.
        const val TEST_ADMOB_BANNER = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_ADMOB_REWARDED = "ca-app-pub-3940256099942544/5224354917"
        const val TEST_GAM_BANNER = "/21775744923/example/adaptive-banner"
        const val TEST_PREBID_SERVER = "https://prebid-server-test-j.prebid.org/openrtb2/auction"
        const val TEST_PREBID_ACCOUNT = "0689a263-318d-448b-a3d4-b02e8a709d9d"
        const val TEST_PREBID_CONFIG_ID = "prebid-demo-banner-320-50"
        const val TEST_PREBID_GAM_UNIT = "/21808260008/prebid_demo_app_original_api_banner"

        fun fromJson(json: JSONObject): AdConfig {
            val defaults = AdConfig()
            val prebid = json.optJSONObject("prebid")
            return AdConfig(
                adsEnabled = json.optBoolean("ads_enabled", defaults.adsEnabled),
                mode = json.optString("mode", defaults.mode).lowercase(),
                admobBannerUnit = json.optString("admob_banner_unit", defaults.admobBannerUnit),
                rewardedUnit = json.optString("rewarded_unit", defaults.rewardedUnit),
                gamBannerUnit = json.optString("gam_banner_unit", defaults.gamBannerUnit),
                prebidServerUrl = prebid?.optString("server_url", defaults.prebidServerUrl)
                    ?: defaults.prebidServerUrl,
                prebidAccountId = prebid?.optString("account_id", defaults.prebidAccountId)
                    ?: defaults.prebidAccountId,
                prebidBannerConfigId = prebid?.optString("banner_config_id", defaults.prebidBannerConfigId)
                    ?: defaults.prebidBannerConfigId,
                prebidGamAdUnit = prebid?.optString("gam_ad_unit", defaults.prebidGamAdUnit)
                    ?: defaults.prebidGamAdUnit,
                prebidBannerWidth = prebid?.optInt("width", defaults.prebidBannerWidth)
                    ?: defaults.prebidBannerWidth,
                prebidBannerHeight = prebid?.optInt("height", defaults.prebidBannerHeight)
                    ?: defaults.prebidBannerHeight,
            )
        }
    }
}

object AdConfigLoader {

    private const val CONFIG_URL = "https://ysharad.github.io/goapps/adconfig/locus.json"
    private const val PREFS = "ad_config_cache"
    private const val KEY_JSON = "config_json"

    @Volatile
    private var memory: AdConfig? = null

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
