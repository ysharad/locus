package com.bitchat.android.connect

import android.app.ActivityManager
import android.content.Context

/**
 * "Delete everything". Asks the OS to clear the app's storage instead of listing stores by hand:
 * that list is how chats and keys used to survive a wipe. The OS also closes the app.
 */
internal object DataEraser {

    fun eraseEverything(
        context: Context,
        deleteCloud: ((Boolean) -> Unit) -> Unit,
        onCloudFailed: () -> Unit,
    ) {
        // Cloud first: clearing storage destroys the session needed to delete it.
        deleteCloud { deleted -> if (deleted) erasePhone(context) else onCloudFailed() }
    }

    fun erasePhone(context: Context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
    }
}
