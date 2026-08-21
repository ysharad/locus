package com.bitchat.android.connect.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectMatch

/**
 * Everyone you've mutually connected with. Presence is the only sort key: in-range first,
 * full opacity; away rows drop back and say what's queued. Tap a row to chat, hold to block.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchesScreen(onOpenChat: (String) -> Unit, inRange: Set<String> = emptySet()) {
    val matches by ConnectManager.matches.collectAsState()
    val sorted = matches.values.sortedWith(
        compareByDescending<ConnectMatch> { it.peerID in inRange }.thenByDescending { it.matchedAt }
    )
    val inRangeCount = matches.values.count { it.peerID in inRange }
    var safetyFor by remember { mutableStateOf<ConnectMatch?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Chats", style = TitleStyle.copy(fontSize = 28.sp), color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOTHING MUTUAL YET", style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Two taps of ⚡ make a connection.",
                        style = TitleStyle.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "When you and someone nearby both say yes, they land here.",
                        style = BodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Presence filter pills.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("IN RANGE $inRangeCount", active = true)
                FilterPill("AWAY ${sorted.size - inRangeCount}", active = false)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(sorted, key = { it.peerID }) { match ->
                    val here = match.peerID in inRange
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenChat(match.peerID) },
                                    onLongClick = { safetyFor = match }
                                )
                                .padding(vertical = 14.dp)
                                .alpha(if (here) 1f else 0.55f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EmojiAvatar(
                                match.profile.emoji,
                                size = 52,
                                ring = if (here) Jade else MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        match.profile.name,
                                        style = ListNameStyle,
                                        color = if (here) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (here) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.size(6.dp).background(Jade, CircleShape))
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (here) match.profile.bio.ifBlank { "in range now" } else "out of range",
                                    style = BodyStyle.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    }
                }
                item {
                    Text(
                        "HOLD A ROW TO BLOCK OR REPORT.",
                        style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.08.em),
                        color = Slate,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
        }
    }

    safetyFor?.let { target ->
        SafetyDialog(
            name = target.profile.name,
            onBlockReport = { ConnectManager.blockAndReport(target.peerID, report = true); safetyFor = null },
            onBlockOnly = { ConnectManager.blockAndReport(target.peerID, report = false); safetyFor = null },
            onDismiss = { safetyFor = null }
        )
    }
}

@Composable
private fun FilterPill(text: String, active: Boolean) {
    Box(
        Modifier
            .border(1.dp, if (active) Jade else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .background(if (active) androidx.compose.ui.graphics.Color(0xFF17211C) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.1.em), color = if (active) Jade else Slate)
    }
}
