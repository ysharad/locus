package com.bitchat.android.connect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager

/** The You tab: your card as the room sees it, the visibility switch, and your key as an object. */
@Composable
fun YouScreen(myPeerID: String, onEdit: () -> Unit, onInvite: () -> Unit, onKeepChats: () -> Unit = {}, onSettings: () -> Unit = {}, onQr: () -> Unit = {}, onPlus: () -> Unit = {}) {
    val profile by ConnectManager.myProfile.collectAsState()
    val keepChats by ConnectManager.keepChats.collectAsState()
    val visible by ConnectManager.visible.collectAsState()
    val p = profile ?: return

    val keyHex = remember(myPeerID) {
        myPeerID.uppercase().padEnd(16, '0').take(16).chunked(4).joinToString(" · ")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("THIS IS WHAT THE ROOM SEES", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate)
        Spacer(Modifier.height(12.dp))

        // Card preview
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmojiAvatar(p.emoji, size = 88)
            Spacer(Modifier.height(16.dp))
            Text(
                p.name + (p.age?.let { ", $it" } ?: ""),
                style = CardNameStyle.copy(fontSize = 26.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (p.hereTo.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("HERE TO ${p.hereTo}".uppercase(), style = EyebrowStyle.copy(letterSpacing = 0.14.em), color = Copper, textAlign = TextAlign.Center)
            }
            if (p.bio.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(p.bio, style = BodyStyle.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
            if (p.vibes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                VibeChips(p.vibes)
            }
        }
        Spacer(Modifier.height(6.dp))

        // Visibility switch
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Visible in the room", style = BodyStyle.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (visible) "Go quiet without leaving" else "Hidden — you're off the radar",
                    style = BodyStyle.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Toggle(on = visible, onToggle = { ConnectManager.setVisible(!visible) })
        }
        RowDivider()
        // Locus+ — free perks, unlocked by attention (rewarded video), never by paying.
        Row(
            Modifier.fillMaxWidth().clickable { onPlus() }.padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡", fontSize = 18.sp, color = Copper)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Locus+", style = BodyStyle.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text("Free perks — watch, unlock", style = BodyStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", style = BodyStyle.copy(fontSize = 16.sp), color = Copper)
        }
        RowDivider()
        NavRow("Edit your profile", onEdit)
        RowDivider()
        NavRow("Invite people nearby", onInvite)
        RowDivider()
        NavRow("Connect in person", onQr)
        RowDivider()
        NavRow("Settings", onSettings)
        RowDivider()
        // Keep-your-chats opt-in status
        Row(
            Modifier.fillMaxWidth().clickable { onKeepChats() }.padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Keep your chats", style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (keepChats) "On — backed up, encrypted" else "Off — chats stay on this phone",
                    style = BodyStyle.copy(fontSize = 13.sp),
                    color = if (keepChats) Jade else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(if (keepChats) "✓" else "→", style = BodyStyle.copy(fontSize = 16.sp), color = if (keepChats) Jade else Slate)
        }
        RowDivider()

        // Key
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
            Text("Your key", style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(keyHex, style = EyebrowStyle.copy(fontSize = 13.sp, letterSpacing = 0.1.em), color = Jade)
            Spacer(Modifier.height(6.dp))
            Text("Made on this phone. Never leaves it.", style = BodyStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RowDivider()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text("→", style = BodyStyle.copy(fontSize = 16.sp), color = Slate)
    }
}

@Composable
private fun Toggle(on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (on) Jade else MaterialTheme.colorScheme.outline)
            .clickable { onToggle() },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (on) OnJade else MaterialTheme.colorScheme.surface)
        )
    }
}
