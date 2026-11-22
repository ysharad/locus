package com.bitchat.android.connect

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DataEraserTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val phone get() =
        shadowOf(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)

    @Test
    fun `delete everything clears all app storage once the cloud copy is gone`() {
        var failed = false
        DataEraser.eraseEverything(context, { done -> done(true) }, { failed = true })
        assertTrue(phone.isApplicationUserDataCleared)
        assertFalse(failed)
    }

    @Test
    fun `cloud failure is reported and keeps the session needed to retry`() {
        var failures = 0
        DataEraser.eraseEverything(context, { done -> done(false) }, { failures++ })
        assertEquals(1, failures)
        assertFalse(phone.isApplicationUserDataCleared)
    }

    @Test
    fun `the phone can still be erased without a connection`() {
        DataEraser.erasePhone(context)
        assertTrue(phone.isApplicationUserDataCleared)
    }
}
