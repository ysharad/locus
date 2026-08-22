package com.bitchat.android.connect.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectMatch
import com.bitchat.android.connect.ConnectProfile
import com.bitchat.android.ui.ChatViewModel
import com.bitchat.android.ui.RewardedAds
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * The discovery surface — CARDS (a swipe deck) and RADAR (the field, browsable). Fully offline:
 * everything here is built from profile cards heard over the mesh.
 */
@Composable
fun DiscoverScreen(viewModel: ChatViewModel, onInvite: () -> Unit = {}, onFilters: () -> Unit = {}, onOpenChat: (String) -> Unit = {}, onWhoLiked: () -> Unit = {}) {
    val nearby by ConnectManager.nearby.collectAsState()
    val liked by ConnectManager.liked.collectAsState()
    val passed by ConnectManager.passed.collectAsState()
    val matches by ConnectManager.matches.collectAsState()
    val likesReceived by ConnectManager.likesReceived.collectAsState()
    val filters by ConnectManager.filters.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()

    val myPeerID = viewModel.myPeerID
    val connectedSet = connectedPeers.toSet()

    val deck = remember(nearby, liked, passed, matches, connectedSet, filters) {
        nearby.values
            .filter {
                it.peerID != myPeerID &&
                    it.peerID !in liked &&
                    it.peerID !in passed &&
                    it.peerID !in matches &&
                    ConnectManager.passesFilters(it)
            }
            .sortedWith(
                compareByDescending<ConnectProfile> { it.peerID in connectedSet }
                    .thenByDescending { it.updatedAt }
            )
    }

    // People you've already connected with who are in the room right now — shown on radar too, with
    // a distinct look, so the spatial view is "everyone here", not only strangers left to swipe.
    val connectionsInRange = remember(matches, connectedSet) {
        matches.values.filter { it.peerID in connectedSet }.map { it.profile }
    }

    // People the radio can hear who haven't aired a card yet. Still marked on the radar —
    // someone physically in the room must never be invisible (hyperlocal north star).
    val unknownPresent = remember(connectedSet, nearby, matches) {
        connectedPeers.filter { it != myPeerID && it !in nearby && it !in matches }
    }

    // Cards you've already swiped (liked or passed) whose owner is still around. The deck is
    // done with them, but the RADAR is a map of the room, not a to-do list — they stay on it,
    // dimmed. Tapping re-opens the card (an undo for a hasty pass, a reminder for a like).
    val seenPresent = remember(nearby, liked, passed, matches) {
        nearby.values.filter {
            (it.peerID in liked || it.peerID in passed) && it.peerID !in matches && it.peerID != myPeerID
        }
    }

    var radar by rememberSaveableBool()
    var tray by remember { mutableStateOf(false) }
    var peerSheet by remember { mutableStateOf<ConnectProfile?>(null) }

    // Locus+ : the tray always opens; unrevealed admirers show blurred with one reveal button
    // inside (per-batch, permanent — see TraySheet). No time windows, no separate gate sheet.
    val revealedLikes by ConnectManager.revealedLikes.collectAsState()
    var revealing by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    val openTray = { onWhoLiked() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LocusField(
            Modifier.fillMaxSize(),
            animated = true,
            peerRings = if (radar) deck.indices.map { it % 5 } else emptyList(),
            originDot = true,
            searching = deck.isEmpty() && connectionsInRange.isEmpty() && unknownPresent.isEmpty() && seenPresent.isEmpty()
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            DiscoverHeader(
                radar = radar,
                onCards = { radar = false },
                onRadar = { radar = true },
                inRange = connectedPeers.size,
                admirers = likesReceived.size,
                filterCount = filters.activeCount,
                onTray = openTray,
                onFilters = onFilters
            )
            Spacer(Modifier.height(14.dp))

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    // An empty radar gets the same full empty state as the deck — title, one
                    // line, and the Invite / Wake buttons — not a bare sentence.
                    radar && deck.isEmpty() && connectionsInRange.isEmpty() && unknownPresent.isEmpty() && seenPresent.isEmpty() -> EmptyDeck(
                        peersInRange = connectedPeers.size,
                        hasPassed = passed.isNotEmpty(),
                        admirers = likesReceived.size,
                        onInvite = onInvite,
                        onTray = openTray
                    )
                    radar -> RadarView(deck = deck, connections = connectionsInRange, connectedSet = connectedSet, unknowns = unknownPresent, seen = seenPresent, onTap = { peerSheet = it })
                    deck.isEmpty() -> EmptyDeck(
                        peersInRange = connectedPeers.size,
                        hasPassed = passed.isNotEmpty(),
                        admirers = likesReceived.size,
                        onInvite = onInvite,
                        onTray = openTray
                    )
                    else -> CardDeck(deck = deck, connectedSet = connectedSet)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    if (tray) {
        TraySheet(
            peerIDs = likesReceived,
            nearby = nearby,
            revealed = revealedLikes,
            revealing = revealing,
            onReveal = {
                val batch = likesReceived - revealedLikes
                val act = activity
                if (act == null) {
                    // No activity to host an ad — never punish the user for our plumbing.
                    ConnectManager.revealLikers(batch)
                } else {
                    revealing = true
                    RewardedAds.show(
                        act,
                        onReward = { ConnectManager.revealLikers(batch); revealing = false },
                        // No fill / failed to show → reveal anyway (fail-open by decision).
                        onUnavailable = { ConnectManager.revealLikers(batch); revealing = false }
                    )
                }
            },
            onDismiss = { tray = false }
        )
    }
    peerSheet?.let { p ->
        PeerSheet(
            profile = p,
            // No instant close: the sheet shows "✓ Sent" for a beat, then dismisses itself.
            onConnect = { ConnectManager.like(p.peerID) },
            onDismiss = { peerSheet = null },
            isConnection = p.peerID in matches,
            alreadySent = p.peerID in liked,
            onChat = { peerSheet = null; onOpenChat(p.peerID) }
        )
    }
}

@Composable
// Radar (the field) is the default view; Cards is a tap away.
private fun rememberSaveableBool() = remember { mutableStateOf(true) }

@Composable
private fun DiscoverHeader(
    radar: Boolean,
    onCards: () -> Unit,
    onRadar: () -> Unit,
    inRange: Int,
    admirers: Int,
    filterCount: Int,
    onTray: () -> Unit,
    onFilters: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // CARDS / RADAR toggle
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                .padding(3.dp)
        ) {
            SegLabel("RADAR", active = radar, onClick = onRadar)
            SegLabel("CARDS", active = !radar, onClick = onCards)
        }
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (inRange == 0) MaterialTheme.colorScheme.outlineVariant else Jade, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                if (inRange == 0) "LISTENING" else "$inRange IN RANGE",
                style = EyebrowStyle.copy(letterSpacing = 0.12.em),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Filters button (shows active count)
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (filterCount > 0) CopperTint else MaterialTheme.colorScheme.surface)
                .border(1.dp, if (filterCount > 0) Copper else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                .clickable { onFilters() }
                .padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Text(
                if (filterCount > 0) "≡ $filterCount" else "≡",
                style = EyebrowStyle.copy(letterSpacing = 0.06.em),
                color = if (filterCount > 0) Copper else Slate
            )
        }
        if (admirers > 0) {
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CopperTint)
                    .border(1.dp, Copper, RoundedCornerShape(50))
                    .clickable { onTray() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ $admirers", style = EyebrowStyle.copy(letterSpacing = 0.08.em), color = Copper)
            }
        }
    }
}

@Composable
private fun SegLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Copper else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = EyebrowStyle.copy(letterSpacing = 0.08.em),
            color = if (active) OnCopper else Slate
        )
    }
}

@Composable
private fun CardDeck(deck: List<ConnectProfile>, connectedSet: Set<String>) {
    val top = deck.first()
    var safetyFor by remember { mutableStateOf<ConnectProfile?>(null) }
    var iceFor by remember { mutableStateOf<ConnectProfile?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val offsetX = remember(top.peerID) { Animatable(0f) }
    val offsetY = remember(top.peerID) { Animatable(0f) }
    val flingDistance = with(density) { 460.dp.toPx() }
    val threshold = with(density) { 110.dp.toPx() }

    fun settle(likeIt: Boolean?) {
        scope.launch {
            when (likeIt) {
                null -> {
                    launch { offsetX.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 380f)) }
                    launch { offsetY.animateTo(0f, spring()) }
                }
                else -> {
                    val target = if (likeIt) flingDistance else -flingDistance
                    offsetX.animateTo(target, tween(300))
                    if (likeIt) ConnectManager.like(top.peerID) else ConnectManager.pass(top.peerID)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            deck.getOrNull(1)?.let { under ->
                ProfileCardFace(
                    profile = under,
                    isNearbyNow = under.peerID in connectedSet,
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp).scale(0.965f).alpha(0.5f)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = kotlin.math.abs(offsetX.value) * 0.06f
                        rotationZ = (offsetX.value / 24f).coerceIn(-12f, 12f)
                    }
                    .pointerInput(top.peerID) {
                        var dragX = 0f
                        detectDragGestures(
                            onDragStart = { dragX = 0f },
                            onDrag = { change, drag ->
                                change.consume()
                                dragX += drag.x
                                val x = dragX
                                scope.launch { offsetX.snapTo(x) }
                            },
                            onDragEnd = {
                                when {
                                    dragX > threshold -> settle(true)
                                    dragX < -threshold -> settle(false)
                                    else -> settle(null)
                                }
                            },
                            onDragCancel = { settle(null) }
                        )
                    }
            ) {
                ProfileCardFace(
                    profile = top,
                    isNearbyNow = top.peerID in connectedSet,
                    modifier = Modifier.fillMaxSize(),
                    onReport = { safetyFor = top }
                )
                SwipeStamp("CONNECT", Jade, (offsetX.value / threshold).coerceIn(0f, 1f), -11f,
                    Modifier.align(Alignment.TopStart).padding(28.dp))
                SwipeStamp("PASS", MaterialTheme.colorScheme.error, (-offsetX.value / threshold).coerceIn(0f, 1f), 11f,
                    Modifier.align(Alignment.TopEnd).padding(28.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction("✕", 66, outline = true) { settle(false) }
            ConnectAction { settle(true) }
            RoundAction("✦", 66, outline = true, tint = Copper) { iceFor = top }
        }
    }

    safetyFor?.let { target ->
        SafetyDialog(
            name = target.name,
            onBlockReport = { ConnectManager.blockAndReport(target.peerID, report = true); safetyFor = null },
            onBlockOnly = { ConnectManager.blockAndReport(target.peerID, report = false); safetyFor = null },
            onDismiss = { safetyFor = null }
        )
    }
    iceFor?.let { target ->
        IcebreakerSheet(
            match = ConnectMatch(target.peerID, target, target.updatedAt),
            onUse = { iceFor = null },
            onDismiss = { iceFor = null }
        )
    }
}

@Composable
private fun RoundAction(glyph: String, size: Int, outline: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(if (outline) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier)
            .background(if (outline) Color.Transparent else Copper)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = (size * 0.34f).sp, color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectAction(onClick: () -> Unit) {
    Box(
        Modifier
            .size(84.dp)
            .shadow(10.dp, CircleShape, spotColor = Copper)
            .clip(CircleShape)
            .background(Copper)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("⚡", fontSize = 30.sp, color = OnCopper)
    }
}

@Composable
private fun SwipeStamp(text: String, color: Color, alpha: Float, rotation: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .graphicsLayer { rotationZ = rotation }
            .border(2.5.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = color, style = EyebrowStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em))
    }
}

@Composable
private fun RadarView(
    deck: List<ConnectProfile>,
    connections: List<ConnectProfile>,
    connectedSet: Set<String>,
    unknowns: List<String> = emptyList(),
    seen: List<ConnectProfile> = emptyList(),
    onTap: (ConnectProfile) -> Unit
) {
    // Nobody here at all — a calm message; the field behind is already scanning (searching=true).
    if (deck.isEmpty() && connections.isEmpty() && unknowns.isEmpty() && seen.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // One line. The breathing field already says "scanning"; every extra word here
            // was one more thing between a person and the room.
            Text("No one in range yet", style = TitleStyle.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    // Peers present — plot them on their rings. The rings ARE the distance legend; no floating labels.
    val ping by rememberInfiniteTransition(label = "radar").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "ping"
    )
    // Connections first (always plotted, distinct), then fresh discoverables, then cards you've
    // already swiped but whose owner is still in the room (dim, steady — the room map is honest).
    val blips = remember(connections, deck, seen) {
        (connections.map { it to 0 } + deck.map { it to 1 } + seen.map { it to 2 }).take(8)
    }
    Box(Modifier.fillMaxSize()) {
        blips.forEachIndexed { i, (p, kind) ->
            val isConnection = kind == 0
            val near = isConnection || p.peerID in connectedSet
            val angle = (i * 51f % 360f)
            val ringFrac = 0.30f + (i % 4) * 0.16f
            val rad = Math.toRadians(angle.toDouble())
            val xDp = (kotlin.math.cos(rad) * 150 * ringFrac).dp
            val yDp = -(kotlin.math.sin(rad).coerceAtLeast(0.05) * 320 * ringFrac).dp
            val ringColor = when {
                isConnection -> Copper // a known connection — steady copper, clearly distinct from jade
                kind == 2 -> MaterialTheme.colorScheme.outline // already swiped — quiet, no signal color
                near -> Jade
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                Modifier.fillMaxSize().padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Fresh discoverables near you pulse (jade); connections and seen cards sit steady.
                if (kind == 1 && near) {
                    Box(
                        Modifier
                            .offset(x = xDp, y = yDp)
                            .size(44.dp)
                            .graphicsLayer {
                                val s = 1f + 1.1f * ping
                                scaleX = s; scaleY = s; alpha = 0.7f * (1f - ping)
                            }
                            .border(1.5.dp, Jade, CircleShape)
                    )
                }
                Box(
                    Modifier
                        .offset(x = xDp, y = yDp)
                        .size(if (near) 46.dp else 40.dp)
                        .alpha(if (kind == 2) 0.6f else 1f)
                        .clip(CircleShape)
                        .background(if (isConnection) CopperTint else AvatarWell)
                        .border(if (isConnection) 2.5.dp else 1.5.dp, ringColor, CircleShape)
                        .clickable { onTap(p) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.emoji, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        // Present but cardless — the radio hears them, they just haven't aired a profile.
        // Shown as faint breathing marks: nobody physically in the room is ever invisible.
        unknowns.take((8 - blips.size).coerceAtLeast(0)).forEachIndexed { j, _ ->
            val i = blips.size + j
            val angle = (i * 51f % 360f)
            val ringFrac = 0.30f + (i % 4) * 0.16f
            val rad = Math.toRadians(angle.toDouble())
            val xDp = (kotlin.math.cos(rad) * 150 * ringFrac).dp
            val yDp = -(kotlin.math.sin(rad).coerceAtLeast(0.05) * 320 * ringFrac).dp
            Box(
                Modifier.fillMaxSize().padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier
                        .offset(x = xDp, y = yDp)
                        .size(44.dp)
                        .graphicsLayer {
                            val s = 1f + 1.1f * ping
                            scaleX = s; scaleY = s; alpha = 0.4f * (1f - ping)
                        }
                        .border(1.5.dp, Jade, CircleShape)
                )
                Box(
                    Modifier
                        .offset(x = xDp, y = yDp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AvatarWell)
                        .border(1.5.dp, Jade.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("◌", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // No legend line — the field explains itself, and every word here was one more thing
        // between a person and the room. (North star: discovery stays effortless.)
    }
}

@Composable
private fun EmptyDeck(peersInRange: Int, hasPassed: Boolean, admirers: Int, onInvite: () -> Unit, onTray: () -> Unit) {
    val cleared = peersInRange > 0
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (cleared) Jade else Copper, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(if (cleared) "ALL SEEN" else "MESH · LISTENING", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(40.dp))
        Text(
            if (cleared) "That's everyone,\nfor now." else "No one in range yet",
            style = TitleStyle.copy(fontSize = 28.sp, lineHeight = 33.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (cleared) "Everyone in range is already yours — they're on the radar and in Chats." else "Locus is listening nearby — leave it open.",
            style = BodyStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = if (cleared && admirers > 0) onTray else onInvite,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)
            ) {
                Text(
                    if (cleared && admirers > 0) "⚡ $admirers for you" else "Invite",
                    style = BodyStyle.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            val wakeCtx = LocalContext.current
            OutlinedButton(
                onClick = {
                    if (hasPassed) ConnectManager.resetPasses()
                    else {
                        val sent = ConnectManager.wakeRoom()
                        android.widget.Toast.makeText(
                            wakeCtx,
                            if (sent) "Wake sent — sleeping phones nearby will hear it."
                            else "Room already woken — try again in a few minutes.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(if (hasPassed) "Review passes" else "Wake the room", style = BodyStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        // A quiet way to see the whole flow with nobody around — the first thing anyone
        // alone in a room needs, reviewers included. Demo cards are local-only and never
        // touch the mesh or disk.
        if (!cleared) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Try a demo deck",
                style = BodyStyle.copy(fontSize = 14.sp),
                color = Slate,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { ConnectManager.seedDemoProfiles() }
                    .padding(vertical = 10.dp)
            )
        }
    }
}
