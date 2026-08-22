package com.bitchat.android.connect.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectProfile

/**
 * A conversation that exists without a match behind it — the other side reset their identity,
 * or a thread arrived before any card was exchanged. If it can ring a notification, it must
 * have a row here, or the notification is the only door back in.
 */
data class StrayThread(
    val peerID: String,
    val name: String,
    val here: Boolean,
    val unread: Boolean
)

private val STRAY_GLYPHS = listOf("◈", "☾", "✦", "❍", "◇", "⬡", "✷", "◐", "⟡", "△", "◉")
private fun strayGlyph(name: String): String = STRAY_GLYPHS[(name.hashCode() and 0x7fffffff) % STRAY_GLYPHS.size]

/** One row on either tab, whatever it was born from. */
private data class PersonRow(
    val peerID: String,
    val name: String,
    val emoji: String,
    val profile: ConnectProfile?,   // null = stray thread, no card yet
    val here: Boolean,
    val unread: Boolean,
    val subtitle: String,
    val isConversation: Boolean,    // taps open the chat; otherwise the deck
    val sortKey: Long
)

/**
 * The Chats page, two tabs: CHATS is every conversation (matched or not), in-range first,
 * rows deliberately showing the person's bio rather than message previews; PEOPLE is
 * everyone the radio knows about — connections and strangers alike — split by presence.
 * Tap a conversation to chat, tap a stranger to go meet them in Discover. Hold to block.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchesScreen(
    onOpenChat: (String) -> Unit,
    inRange: Set<String> = emptySet(),
    strays: List<StrayThread> = emptyList(),
    unreadStems: Set<String> = emptySet(),
    onDiscover: () -> Unit = {}
) {
    val matches by ConnectManager.matches.collectAsState()
    val nearby by ConnectManager.nearby.collectAsState()
    var peopleTab by rememberSaveable { mutableStateOf(false) }
    var safety by remember { mutableStateOf<Pair<String, String>?>(null) } // peerID to name

    val chatRows = remember(matches, strays, inRange, unreadStems) {
        val matchRows = matches.values.map { m ->
            PersonRow(
                peerID = m.peerID,
                name = m.profile.name,
                emoji = m.profile.emoji,
                profile = m.profile,
                here = m.peerID in inRange,
                unread = m.peerID.take(16) in unreadStems,
                subtitle = m.profile.bio.ifBlank { m.profile.hereTo },
                isConversation = true,
                sortKey = m.matchedAt
            )
        }
        val strayRows = strays.map { s ->
            PersonRow(
                peerID = s.peerID,
                name = s.name,
                emoji = strayGlyph(s.name),
                profile = null,
                here = s.here,
                unread = s.unread,
                subtitle = "no profile yet",
                isConversation = true,
                sortKey = 0L
            )
        }
        (matchRows + strayRows).sortedWith(
            compareByDescending<PersonRow> { it.unread }
                .thenByDescending { it.here }
                .thenByDescending { it.sortKey }
        )
    }

    val peopleRows = remember(chatRows, nearby, inRange) {
        val known = chatRows.map { it.peerID.take(16) }.toSet()
        val strangers = nearby.values
            .filter { it.peerID.take(16) !in known }
            .map { p ->
                PersonRow(
                    peerID = p.peerID,
                    name = p.name,
                    emoji = p.emoji,
                    profile = p,
                    here = p.peerID in inRange,
                    unread = false,
                    subtitle = p.hereTo.ifBlank { p.bio },
                    isConversation = false,
                    sortKey = p.updatedAt
                )
            }
        (chatRows + strangers).sortedWith(
            compareByDescending<PersonRow> { it.here }.thenByDescending { it.sortKey }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Chats", style = TitleStyle.copy(fontSize = 28.sp), color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabPill("CHATS", active = !peopleTab) { peopleTab = false }
            TabPill("PEOPLE", active = peopleTab) { peopleTab = true }
        }
        Spacer(Modifier.height(14.dp))

        val rows = if (peopleTab) peopleRows else chatRows
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (peopleTab) "No one in range yet." else "Two taps of ⚡ make a chat.",
                        style = TitleStyle.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (peopleTab) "The radio is listening — people appear as they arrive."
                        else "When you and someone nearby both say yes, they land here.",
                        style = BodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn {
                val hereRows = rows.filter { it.here }
                val awayRows = rows.filterNot { it.here }
                if (peopleTab && hereRows.isNotEmpty()) {
                    item { SectionLabel("IN RANGE") }
                }
                items(hereRows, key = { (if (peopleTab) "p_" else "c_") + it.peerID }) { r ->
                    PersonRowView(r, onOpenChat, onDiscover) { safety = it }
                }
                if (peopleTab && awayRows.isNotEmpty()) {
                    item { SectionLabel("AWAY") }
                }
                items(awayRows, key = { (if (peopleTab) "pa_" else "ca_") + it.peerID }) { r ->
                    PersonRowView(r, onOpenChat, onDiscover) { safety = it }
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

    safety?.let { (peerID, name) ->
        SafetyDialog(
            name = name,
            onBlockReport = { ConnectManager.blockAndReport(peerID, report = true); safety = null },
            onBlockOnly = { ConnectManager.blockAndReport(peerID, report = false); safety = null },
            onDismiss = { safety = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonRowView(
    r: PersonRow,
    onOpenChat: (String) -> Unit,
    onDiscover: () -> Unit,
    onSafety: (Pair<String, String>) -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (r.isConversation) onOpenChat(r.peerID) else onDiscover() },
                    onLongClick = { onSafety(r.peerID to r.name) }
                )
                .padding(vertical = 14.dp)
                .alpha(if (r.here) 1f else 0.55f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiAvatar(
                r.emoji,
                size = 52,
                ring = if (r.here) Jade else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.name,
                        style = ListNameStyle,
                        color = if (r.here) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (r.here) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(6.dp).background(Jade, CircleShape))
                    }
                    if (r.unread) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(6.dp).background(Copper, CircleShape))
                    }
                    r.profile?.let { TrustBadge(it, Modifier.padding(start = 8.dp)) }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    r.subtitle.ifBlank { if (r.here) "in range now" else "out of range" },
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.1.em),
        color = Slate,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun TabPill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, if (active) Jade else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .background(
                if (active) androidx.compose.ui.graphics.Color(0xFF17211C) else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.1.em), color = if (active) Jade else Slate)
    }
}
