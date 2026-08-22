package com.bitchat.android.connect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectProfile
import com.bitchat.android.ui.ChatViewModel

private const val TONIGHT_QUESTION = "What's the best set you've seen in this building?"

/**
 * "Tonight" — the here-and-now view of the room: who's around, a light Wave to say hello without
 * a message, and a prompt of the night. A companion to the Room's chat noticeboard.
 */
@Composable
fun TonightScreen(viewModel: ChatViewModel, onOpenChat: (String) -> Unit, onEditCard: () -> Unit, onRoom: () -> Unit, onClose: () -> Unit) {
    val nearby by ConnectManager.nearby.collectAsState()
    val matches by ConnectManager.matches.collectAsState()
    val waved by ConnectManager.wavedAt.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val connectedSet = connectedPeers.toSet()

    val people = nearby.values
        .filter { it.peerID != viewModel.myPeerID }
        .sortedWith(compareByDescending<ConnectProfile> { it.peerID in matches }.thenByDescending { it.peerID in connectedSet })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(6.dp))
            Text("Tonight", style = TitleStyle.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Text(
                "THE ROOM", style = EyebrowStyle.copy(fontSize = 12.sp), color = Jade,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)).clickable { onRoom() }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Row(Modifier.padding(start = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (connectedPeers.isEmpty()) MaterialTheme.colorScheme.outlineVariant else Jade, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${connectedPeers.size} IN RANGE · ${matches.size} CONNECTED", style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.14.em), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item { Spacer(Modifier.height(18.dp)) }
            if (matches.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Color(0xFF2A3F35), RoundedCornerShape(20.dp)).background(Color(0xFF0F1714)).padding(20.dp)) {
                        Text("YOUR NIGHT", style = EyebrowStyle.copy(letterSpacing = 0.14.em), color = Jade)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "You've connected with ${matches.size} ${if (matches.size == 1) "person" else "people"} in this room.",
                            style = TitleStyle.copy(fontSize = 21.sp, lineHeight = 28.sp), color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Copper, RoundedCornerShape(20.dp)).background(Color(0xFF12100C)).padding(20.dp)) {
                    Text("TONIGHT'S QUESTION", style = EyebrowStyle.copy(letterSpacing = 0.14.em), color = Copper)
                    Spacer(Modifier.height(10.dp))
                    Text(TONIGHT_QUESTION, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onEditCard, modifier = Modifier.height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)) {
                        Text("Answer on my profile", style = BodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("IN THE ROOM RIGHT NOW", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate)
            }
            if (people.isEmpty()) {
                item {
                    Text("No one in range yet.", style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                }
            }
            items(people, key = { it.peerID }) { p ->
                val connected = p.peerID in matches
                val here = p.peerID in connectedSet
                val hasWaved = p.peerID in waved
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    EmojiAvatar(p.emoji, size = 46, ring = if (connected || here) Jade else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = ListNameStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                connected -> "CONNECTED"
                                here -> "IN RANGE"
                                else -> "SEEN NEARBY"
                            },
                            style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.08.em),
                            color = if (connected || here) Jade else Slate
                        )
                    }
                    when {
                        connected -> ActionPill("CHAT", filled = true) { onOpenChat(p.peerID) }
                        hasWaved -> ActionPill("WAVED", filled = false, muted = true) {}
                        else -> ActionPill("WAVE", filled = false) { ConnectManager.sendWave(p.peerID) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
            }
            item {
                Text("A WAVE IS ONE PACKET. NO MESSAGE, NO OBLIGATION.", style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.06.em), color = Slate, modifier = Modifier.padding(vertical = 18.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun ActionPill(label: String, filled: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(Copper) else Modifier.border(1.dp, if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)))
            .clickable(enabled = !muted) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, style = EyebrowStyle.copy(fontSize = 11.sp), color = if (filled) OnCopper else if (muted) Slate else MaterialTheme.colorScheme.onSurface)
    }
}
