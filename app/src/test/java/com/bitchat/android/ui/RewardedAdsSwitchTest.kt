package com.bitchat.android.ui

import android.app.Activity
import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RewardedAdsSwitchTest {

    private fun remoteConfig(config: AdConfig?) =
        ReflectionHelpers.setField(AdConfigLoader, "memory", config)

    @After
    fun tearDown() = remoteConfig(null)

    @Test
    fun `switching ads off grants the perk without an ad`() {
        remoteConfig(AdConfig(adsEnabled = false))
        var rewarded = 0
        var unavailable = 0
        RewardedAds.show(
            Robolectric.buildActivity(Activity::class.java).get(),
            onReward = { rewarded++ },
            onUnavailable = { unavailable++ },
        )
        assertEquals(1, rewarded)
        assertEquals(0, unavailable)
    }

    @Test
    fun `switching ads off leaves no rewarded unit to load`() {
        val off = AdConfig(adsEnabled = false)
        RewardedPlacement.values().forEach { assertEquals(null, off.rewardedUnitFor(it)) }
    }

    @Test
    fun `ads on keeps each placement on its own unit`() {
        val on = AdConfig(rewardedUnit = "reveal", rewardedBoostUnit = "boost")
        assertEquals("reveal", on.rewardedUnitFor(RewardedPlacement.REVEAL))
        assertEquals("boost", on.rewardedUnitFor(RewardedPlacement.BOOST))
    }
}
