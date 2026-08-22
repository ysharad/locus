package com.bitchat.android.connect.ui

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.ui.RewardedAds

/**
 * Locus+ — the perks hub. Locus never sells anything; the only way in is a few seconds of a
 * rewarded video. Two perks today: reveal who likes you (a day at a time) and Boost (re-air your
 * card so nearby decks resort you to the front). Both are honest — no locked messages, no paywalls.
 */
@Composable
fun LocusPlusScreen(onClose: () -> Unit, onWhoLiked: () -> Unit = {}) {
    val activity = LocalContext.current as? Activity
    val likesReceived by ConnectManager.likesReceived.collectAsState()
    val revealedLikes by ConnectManager.revealedLikes.collectAsState()
    val boostUntil by ConnectManager.boostUntil.collectAsState()
    val visible by ConnectManager.visible.collectAsState()
    val now = System.currentTimeMillis()
    val hiddenCount = (likesReceived - revealedLikes).size
    val boosted = now < boostUntil

    var watching by remember { mutableStateOf(false) }
    var boostHint by remember { mutableStateOf<String?>(null) }
    var showTray by remember { mutableStateOf(false) }
    var revealing by remember { mutableStateOf(false) }
    val nearby by ConnectManager.nearby.collectAsState()

    fun watchThen(onReward: () -> Unit) {
        val act = activity ?: return
        watching = true
        RewardedAds.show(
            act,
            onReward = { watching = false; onReward() },
            onUnavailable = { watching = false; boostHint = "No video ready — try again in a moment." }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "←",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text("Locus+", style = TitleStyle.copy(fontSize = 28.sp), color = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            "Everything in Locus is free. Perks run on attention, not payments — watch a short video, unlock something useful.",
            style = BodyStyle.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        // Reveal perk — per-wave, permanent. One video unblurs everyone hidden right now,
        // forever; new admirers arrive hidden. No timers, no rent.
        PerkCard(
            glyph = "+",
            title = "See who likes you",
            body = "One video reveals everyone who swiped you first — forever. New admirers arrive hidden.",
            statusText = when {
                hiddenCount > 0 -> "$hiddenCount HIDDEN RIGHT NOW"
                likesReceived.isNotEmpty() -> "ALL REVEALED"
                else -> "NO ONE YET — STAY DISCOVERABLE"
            },
            statusColor = if (hiddenCount > 0) Copper else Jade,
            buttonLabel = "See who ⚡",
            watching = false,
            onWatch = { onWhoLiked() }
        )
        Spacer(Modifier.height(14.dp))

        // Boost perk
        PerkCard(
            glyph = "◎",
            title = "Boost",
            body = "Put your profile back on the air so nearby decks resort you to the front, right now.",
            statusText = when {
                boosted -> "BOOSTED · AT THE FRONT"
                !visible -> "YOU'RE INVISIBLE — TURN ON VISIBLE FIRST"
                else -> null
            },
            statusColor = if (boosted) Jade else Rust,
            buttonLabel = if (boosted) "Watch to boost again" else "Watch & boost",
            watching = watching,
            onWatch = {
                boostHint = null
                watchThen {
                    if (!ConnectManager.boostNow()) {
                        boostHint = "Turn on “Visible in the room” (in You) to boost."
                    }
                }
            }
        )
        boostHint?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = BodyStyle.copy(fontSize = 13.sp), color = Rust)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "No subscriptions. No in-app purchases. Ever.",
            style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.08.em),
            color = Slate,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showTray) {
        TraySheet(
            peerIDs = likesReceived,
            nearby = nearby,
            revealed = revealedLikes,
            revealing = revealing,
            onReveal = {
                val batch = likesReceived - revealedLikes
                val act = activity
                if (act == null) {
                    ConnectManager.revealLikers(batch)
                } else {
                    revealing = true
                    RewardedAds.show(
                        act,
                        onReward = { ConnectManager.revealLikers(batch); revealing = false },
                        // Fail-open: no fill never blocks a reveal.
                        onUnavailable = { ConnectManager.revealLikers(batch); revealing = false }
                    )
                }
            },
            onDismiss = { showTray = false }
        )
    }
}

@Composable
private fun PerkCard(
    glyph: String,
    title: String,
    body: String,
    statusText: String?,
    statusColor: androidx.compose.ui.graphics.Color,
    buttonLabel: String,
    watching: Boolean,
    onWatch: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(glyph, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = LocusGold)
            Spacer(Modifier.width(12.dp))
            Text(title, style = ListNameStyle.copy(fontSize = 19.sp), color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Text(body, style = BodyStyle.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (statusText != null) {
            Spacer(Modifier.height(12.dp))
            Text(statusText, style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.1.em), color = statusColor)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(50))
                .background(if (watching) MaterialTheme.colorScheme.surface else Copper)
                .border(1.dp, if (watching) MaterialTheme.colorScheme.outline else Copper, RoundedCornerShape(50))
                .clickable(enabled = !watching) { onWatch() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▶", fontSize = 12.sp, color = if (watching) Slate else OnCopper)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (watching) "LOADING…" else buttonLabel.uppercase(),
                    style = EyebrowStyle.copy(fontSize = 12.sp, letterSpacing = 0.1.em),
                    color = if (watching) Slate else OnCopper
                )
            }
        }
    }
}

