package com.bitchat.android.connect.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager

/** Live Bluetooth on/off, updated from the adapter's state-change broadcast. */
@Composable
private fun rememberBluetoothEnabled(): State<Boolean> {
    val context = LocalContext.current
    val adapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    val enabled = remember { mutableStateOf(adapter?.isEnabled == true) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { enabled.value = adapter?.isEnabled == true }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        // Re-read once on attach in case it changed while we weren't listening.
        enabled.value = adapter?.isEnabled == true
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }
    return enabled
}

/**
 * A slim banner at the top of a 1:1 chat that tells the honest truth about whether this conversation
 * can actually go through:
 *  • Bluetooth off + not signed in → you can't reach them at all. Offer Bluetooth + sign-in.
 *  • Bluetooth on + not signed in → a gentle, dismissible nudge to sign in so the chat survives when
 *    you drift out of Bluetooth range.
 *  • Signed in + Bluetooth off → informational: messages will go online.
 * Nothing shows when Bluetooth is on and you're signed in — the happy path stays quiet.
 */
@Composable
internal fun ChatConnectivityBanner() {
    val keepChats by ConnectManager.keepChats.collectAsState()
    val btEnabled by rememberBluetoothEnabled()
    val context = LocalContext.current

    if (keepChats && btEnabled) return // all good — no nagging

    val hardBlock = !keepChats && !btEnabled
    // The soft nudge / info line can be dismissed for the session; the hard block cannot.
    var dismissed by remember(keepChats, btEnabled) { mutableStateOf(false) }
    if (dismissed && !hardBlock) return

    val accent = when {
        hardBlock -> Rust
        !keepChats -> Copper
        else -> Slate
    }
    val title = when {
        hardBlock -> "Can't reach them right now"
        !keepChats -> "Sign in to keep this chat"
        else -> "Bluetooth is off"
    }
    val body = when {
        hardBlock -> "Bluetooth is off and you're not signed in. Turn Bluetooth on, or sign in to keep chatting online."
        !keepChats -> "You're talking over Bluetooth. Sign in so it continues online when you go out of range."
        else -> "You're signed in — messages will send online when there's a connection."
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.09f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                title.uppercase(),
                style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.08.em),
                color = accent,
                modifier = Modifier.weight(1f)
            )
            if (!hardBlock) {
                Text(
                    "✕",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).clickable { dismissed = true }.padding(4.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(body, style = BodyStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!btEnabled) {
                BannerButton("Turn on Bluetooth", filled = false, accent = accent) {
                    try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) {}
                }
            }
            if (!keepChats) {
                BannerButton("Sign in", filled = true, accent = accent) { ConnectManager.requestSignIn() }
            }
        }
    }
}

@Composable
private fun BannerButton(label: String, filled: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(accent) else Modifier.border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50)))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.06.em),
            color = if (filled) OnCopper else accent
        )
    }
}
