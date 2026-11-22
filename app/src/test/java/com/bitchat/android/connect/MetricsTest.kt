package com.bitchat.android.connect

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MetricsTest {

    @Test
    fun `params become a string bundle`() {
        val b = Metrics.bundleOf(mapOf("peak" to "2-3"))
        assertEquals(setOf("peak"), b.keySet())
        assertEquals("2-3", b.getString("peak"))
    }

    @Test
    fun `empty params give an empty bundle`() {
        assertEquals(0, Metrics.bundleOf(emptyMap()).size())
    }
}
