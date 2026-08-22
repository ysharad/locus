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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectProfile
import com.bitchat.android.model.BitchatMessage

/** A bottom-anchored sheet over a scrim — the app's one sheet shape (28dp top corners). */
@Composable
internal fun BottomSheet(
    onDismiss: () -> Unit,
    topBorder: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xB8060A08))
                .clickable(indication = null, interactionSource = noRippleSource()) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        if (topBorder != null)
                            Modifier.border(1.dp, topBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        else Modifier
                    )
                    .clickable(indication = null, interactionSource = noRippleSource()) {}
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                content()
            }
        }
    }
}

@Composable
private fun noRippleSource() = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

/** "⚡ N for you" — blurred until one short video reveals the current wave, forever. */
@Composable
internal fun TraySheet(
    peerIDs: Set<String>,
    nearby: Map<String, ConnectProfile>,
    revealed: Set<String>,
    revealing: Boolean,
    onReveal: () -> Unit,
    onDismiss: () -> Unit
) {
    BottomSheet(onDismiss = onDismiss, topBorder = Copper) {
        Text("⚡ ${peerIDs.size} FOR YOU", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Copper)
        Spacer(Modifier.height(12.dp))
        Text("They swiped you first.", style = TitleStyle.copy(fontSize = 24.sp), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))
        val ordered = peerIDs.sortedByDescending { it in revealed }
        ordered.take(6).forEach { id ->
            val p = nearby[id]
            val isRevealed = id in revealed
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, if (isRevealed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = isRevealed) { ConnectManager.like(id); onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiAvatar(if (isRevealed) (p?.emoji ?: "❍") else "❍", size = 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isRevealed) (p?.name ?: "Nearby") else "• • • • •",
                        style = ListNameStyle.copy(fontSize = 16.sp),
                        color = if (isRevealed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isRevealed) (if (p != null) "in range" else "seen earlier") else "hidden",
                        style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.08.em),
                        color = if (isRevealed && p != null) Jade else Slate
                    )
                }
                if (isRevealed) {
                    Text("⚡ CONNECT", style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.08.em), color = Copper)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val hiddenCount = peerIDs.count { it !in revealed }
        if (hiddenCount > 0) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onReveal,
                enabled = !revealing,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (revealing) "Loading…" else "Reveal — watch a short video",
                    style = EyebrowStyle.copy(fontSize = 13.sp, letterSpacing = 0.1.em),
                    color = OnCopper
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "One video reveals everyone here — forever. New admirers arrive hidden.",
                style = BodyStyle.copy(fontSize = 12.sp),
                color = Slate
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun ConnectManagerLike(id: String) = com.bitchat.android.connect.ConnectManager.like(id)

/**
 * The reveal gate: how many like you, and one copper button to trade a short video for a day of
 * seeing who. No prices, no tiers — attention is the only currency Locus asks for. [watching] shows
 * a patient state while the ad is fetched or presented.
 */
@Composable
internal fun RevealGateSheet(count: Int, watching: Boolean, onWatch: () -> Unit, onDismiss: () -> Unit) {
    BottomSheet(onDismiss = onDismiss, topBorder = Copper) {
        Text("⚡ $count LIKE YOU", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Copper)
        Spacer(Modifier.height(12.dp))
        Text("See who's into you", style = TitleStyle.copy(fontSize = 24.sp), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Watch a short video and everyone who swiped you first is revealed — unlocked for the next 24 hours.",
            style = BodyStyle.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onWatch,
            enabled = !watching,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                if (watching) "Loading…" else "Watch & reveal",
                style = EyebrowStyle.copy(fontSize = 13.sp, letterSpacing = 0.1.em),
                color = OnCopper
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Locus is free. A few seconds of attention, not your wallet.",
            style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.06.em),
            color = Slate,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * A peer's card raised as a sheet — used from radar taps. Full deck anatomy + connect, or — for
 * someone you're already connected with — a straight line into the chat.
 */
@Composable
internal fun PeerSheet(
    profile: ConnectProfile,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
    isConnection: Boolean = false,
    alreadySent: Boolean = false,
    onChat: () -> Unit = {}
) {
    // The ⚡ is silent on their side by design — so YOUR side must say what happened.
    // A like already sent STAYS sent across reopenings; the sheet only excuses itself
    // right after the tap, not when you reopen someone you liked earlier.
    val sentState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(alreadySent) }
    val justSent = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(justSent.value) {
        if (justSent.value) {
            kotlinx.coroutines.delay(1600)
            onDismiss()
        }
    }
    BottomSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalBars(bars = 4)
            Spacer(Modifier.width(9.dp))
            Text(
                if (isConnection) "CONNECTED · IN RANGE" else "ARM'S REACH",
                style = EyebrowStyle.copy(letterSpacing = 0.14.em),
                color = if (isConnection) Copper else Jade
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmojiAvatar(profile.emoji, size = 92)
            Spacer(Modifier.height(16.dp))
            Text(
                profile.name + (profile.age?.let { ", $it" } ?: ""),
                style = CardNameStyle.copy(fontSize = 29.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            TrustBadge(profile, Modifier.padding(top = 8.dp))
            if (profile.hereTo.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("HERE TO ${profile.hereTo}".uppercase(), style = EyebrowStyle.copy(letterSpacing = 0.14.em), color = Copper)
            }
            if (profile.bio.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(profile.bio, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            if (profile.vibes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                VibeChips(profile.vibes)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) { Text("✕", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(
                onClick = {
                    when {
                        isConnection -> onChat()
                        !sentState.value -> { onConnect(); sentState.value = true; justSent.value = true }
                    }
                },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sentState.value) Jade else Copper,
                    contentColor = if (sentState.value) androidx.compose.ui.graphics.Color(0xFF0C1210) else OnCopper
                )
            ) {
                Text(
                    when {
                        isConnection -> "✶  Chat"
                        sentState.value && !justSent.value -> "✓  Sent — waiting on them"
                        sentState.value -> "✓  Sent — if they tap ⚡ too, it's a match"
                        // Deliberately NOT hinting that this person already liked you: that is
                        // exactly what the ⚡ tray sells. The match simply happens when you tap.
                        else -> "⚡  Connect"
                    },
                    style = BodyStyle.copy(fontSize = if (sentState.value) 14.sp else 17.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Long-press actions on a message in a 1:1 chat (the design's ovMsg): a react bar across the top,
 * then Reply / Copy / and — on someone else's message — Report & block. Reactions send to
 * [chatPeer]; the row highlights the emoji you already gave this message.
 */
@Composable
internal fun LocusMessageActions(
    message: BitchatMessage,
    chatPeer: String,
    isMine: Boolean,
    onReply: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val myKey = ConnectManager.myFingerprint()
    val mine = ConnectManager.reactions.value[message.id]?.get(myKey)
    BottomSheet(onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("❤️", "🔥", "😂", "👍", "⚡").forEach { emoji ->
                val active = mine == emoji
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) Copper.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (active) Copper else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable {
                            ConnectManager.sendReaction(chatPeer, message.id, emoji)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 22.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        ActionLine("Reply", MaterialTheme.colorScheme.onSurface) { onReply(); onDismiss() }
        ActionLine("Copy", MaterialTheme.colorScheme.onSurface) {
            clipboard.setText(AnnotatedString(message.content)); onDismiss()
        }
        if (!isMine) {
            ActionLine("Report & block", Rust) { onReport(); onDismiss() }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ActionLine(label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = BodyStyle.copy(fontSize = 16.sp), color = color, modifier = Modifier.weight(1f))
    }
}
