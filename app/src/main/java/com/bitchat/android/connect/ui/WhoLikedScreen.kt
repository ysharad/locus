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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.ui.RewardedAds

/**
 * Who liked you — a page, not a sheet. Each admirer is a wide card: hidden ones show a masked
 * medallion with a Reveal button on the right; once revealed (one video unlocks the whole
 * current wave, permanently) the row becomes their ordinary card with a Connect action.
 */
@Composable
fun WhoLikedScreen(onClose: () -> Unit, onConnected: () -> Unit = {}) {
    val likesReceived by ConnectManager.likesReceived.collectAsState()
    val revealed by ConnectManager.revealedLikes.collectAsState()
    val nearby by ConnectManager.nearby.collectAsState()
    val activity = LocalContext.current as? Activity
    var revealing by remember { mutableStateOf(false) }

    val hidden = likesReceived - revealed

    fun reveal() {
        val batch = likesReceived - ConnectManager.revealedLikes.value
        if (batch.isEmpty()) return
        val act = activity
        if (act == null) {
            ConnectManager.revealLikers(batch)
        } else {
            revealing = true
            RewardedAds.show(
                act,
                onReward = { ConnectManager.revealLikers(batch); revealing = false },
                // Fail-open: no ad available must never cost someone their reveal.
                onUnavailable = { ConnectManager.revealLikers(batch); revealing = false }
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text("Who liked you", style = TitleStyle.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground)
        }

        if (likesReceived.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No one yet", style = TitleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Stay discoverable and they'll turn up here.",
                        style = BodyStyle.copy(fontSize = 13.sp),
                        color = Slate,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Column
        }

        Text(
            if (hidden.isEmpty()) "${likesReceived.size} in total" else "${hidden.size} still hidden",
            style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.1.em),
            color = if (hidden.isEmpty()) Jade else Copper
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(likesReceived.sortedByDescending { it in revealed }, key = { it }) { id ->
                val isRevealed = id in revealed
                val p = nearby[id]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            1.dp,
                            if (isRevealed) MaterialTheme.colorScheme.outline else Copper.copy(alpha = 0.5f),
                            RoundedCornerShape(20.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmojiAvatar(
                        if (isRevealed) (p?.emoji ?: "❍") else "❍",
                        size = 52,
                        ring = if (isRevealed) Jade else MaterialTheme.colorScheme.outline,
                        glyphTint = if (isRevealed) p?.glyphTintOrNull() else null
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isRevealed) (p?.name ?: "Nearby") else "• • • • •",
                            style = ListNameStyle.copy(fontSize = 17.sp),
                            color = if (isRevealed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                !isRevealed -> "Hidden"
                                p == null -> "Seen earlier"
                                p.bio.isNotBlank() -> p.bio
                                p.hereTo.isNotBlank() -> "Here to ${p.hereTo}"
                                else -> "In range"
                            },
                            style = BodyStyle.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    // The action lives on the right of every row: reveal while hidden,
                    // connect once you can see who it is.
                    if (isRevealed) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Copper)
                                .clickable { ConnectManager.like(id); onConnected() }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("Connect", style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.08.em), color = OnCopper)
                        }
                    } else {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, Copper, RoundedCornerShape(50))
                                .clickable(enabled = !revealing) { reveal() }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                if (revealing) "…" else "Reveal",
                                style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.08.em),
                                color = Copper
                            )
                        }
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "One video reveals everyone hidden right now — for good.",
                        style = BodyStyle.copy(fontSize = 12.sp),
                        color = Slate
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
