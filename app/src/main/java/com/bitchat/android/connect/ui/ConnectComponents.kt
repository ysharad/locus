package com.bitchat.android.connect.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bitchat.android.connect.ConnectMatch
import com.bitchat.android.connect.ConnectProfile

/** The curated vibe tags a card can carry. */
internal val VIBE_OPTIONS = listOf(
    "music", "dancing", "chill", "adventurous", "foodie", "artsy",
    "sporty", "gamer", "festival head", "night owl", "deep talks", "traveler"
)

/** What someone is at this place for — deliberately open-ended. */
internal val HERE_TO_OPTIONS = listOf(
    "meet new people", "find my crew", "see where the night goes", "just vibing"
)

/**
 * The medallion: a single copper contour arc around a dark well — the person is the dark shape,
 * the arc is the ring of the field they sit on. Replaces the old gradient glow halo.
 */
@Composable
internal fun EmojiAvatar(
    emoji: String,
    size: Int,
    modifier: Modifier = Modifier,
    ring: Color = Copper
) {
    // A per-person seed so each medallion's contour wobbles a little differently.
    val seed = remember(emoji) { (emoji.hashCode() % 100) / 12f + 0.6f }
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f - 1.5.dp.toPx()
            drawPath(
                path = contourRing(this.size.width / 2f, this.size.height / 2f, r, r * 0.05f, seed, points = 28),
                color = ring,
                style = Stroke(width = (size / 62f).coerceIn(1.3f, 2f).dp.toPx())
            )
        }
        Box(
            Modifier
                .size((size * 0.73f).dp)
                .clip(CircleShape)
                .background(AvatarWell),
            contentAlignment = Alignment.Center
        ) {
            Text(
                emoji,
                fontSize = (size * 0.38f).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Four-bar signal glyph — the deck card's "how near" indicator. [bars] 0..4 filled in jade. */
@Composable
internal fun SignalBars(bars: Int, modifier: Modifier = Modifier) {
    val on = Jade
    val off = MaterialTheme.colorScheme.outline
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val heights = listOf(6, 9, 12, 15)
        heights.forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < bars) on else off)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VibeChips(vibes: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        vibes.forEach { vibe ->
            Box(
                Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    vibe,
                    style = BodyStyle.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The full discovery card face, shared by the deck and the "wants to connect" list. */
@Composable
internal fun ProfileCardFace(
    profile: ConnectProfile,
    isNearbyNow: Boolean,
    modifier: Modifier = Modifier,
    onReport: (() -> Unit)? = null
) {
    val hair = MaterialTheme.colorScheme.outline
    Card(
        modifier = modifier.border(1.dp, hair, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top status band: signal + IN RANGE NOW left, ⚑ REPORT right, hairline under.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SignalBars(bars = if (isNearbyNow) 3 else 1)
                Spacer(Modifier.width(9.dp))
                Text(
                    if (isNearbyNow) "IN RANGE NOW" else "SEEN NEARBY",
                    style = EyebrowStyle.copy(letterSpacing = 0.14.em),
                    color = if (isNearbyNow) Jade else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (onReport != null) {
                    Text(
                        "⚑ REPORT",
                        style = EyebrowStyle.copy(letterSpacing = 0.12.em),
                        color = Slate,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onReport() }
                            .padding(4.dp)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
            // Center block
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmojiAvatar(profile.emoji, size = 104)
                Spacer(Modifier.height(22.dp))
                Text(
                    profile.name + (profile.age?.let { ", $it" } ?: ""),
                    style = CardNameStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.hereTo.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "HERE TO ${profile.hereTo}".uppercase(),
                        style = EyebrowStyle.copy(letterSpacing = 0.14.em),
                        color = Copper,
                        textAlign = TextAlign.Center
                    )
                }
                if (profile.bio.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        profile.bio,
                        style = BodyStyle.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (profile.vibes.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                VibeChips(
                    profile.vibes,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                )
            }
        }
    }
}

/**
 * Full-screen "you're both in" moment — the one place the design is allowed to be exuberant.
 * Converging copper rings + a soft flare, two overlapping glyph medallions, then it stops moving.
 */
@Composable
internal fun MatchCelebration(
    match: ConnectMatch?,
    myEmoji: String = "🜂",
    onSayHi: () -> Unit,
    onDismiss: () -> Unit
) {
    if (match == null) return
    val t = rememberInfiniteTransition(label = "match")
    // A flare that expands and fades on a loop (0→1, restart), plus a breathing ring set + slow spin.
    val flareT by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.LinearEasing)), label = "flare")
    val ringBreath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "ringBreath")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(30000, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    // One-shot entry: the medallions rise and scale in.
    val rise = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        rise.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 380f))
    }
    val flareAlpha = kotlin.math.sin(flareT * kotlin.math.PI.toFloat()) * 0.9f

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        // Concentric copper rings (breathing + slowly rotating) + an expanding flare pulse.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.4f)
            // steady ambient glow
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Copper.copy(alpha = 0.14f), Color.Transparent), center = c, radius = 240.dp.toPx()
                ),
                radius = 240.dp.toPx(), center = c
            )
            // expanding flare ring
            val flareR = (120f + 150f * flareT).dp.toPx()
            drawCircle(Copper.copy(alpha = flareAlpha.coerceIn(0f, 1f) * 0.5f), flareR, c, style = Stroke(2.dp.toPx()))
            rotate(spin * 360f, c) {
                val b = 1f + 0.06f * ringBreath
                listOf(72f to 0.7f, 112f to 0.5f, 156f to 0.32f, 200f to 0.18f).forEach { (r, a) ->
                    drawCircle(Copper.copy(alpha = a * 0.55f), r.dp.toPx() * b, c, style = Stroke(1.2.dp.toPx()))
                }
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Two overlapping medallions — rise + scale in on entry.
            Box(
                Modifier.graphicsLayer {
                    val s = 0.7f + 0.3f * rise.value
                    scaleX = s; scaleY = s
                    translationY = (1f - rise.value) * 40.dp.toPx()
                    alpha = rise.value
                }
            ) {
                Row {
                    EmojiAvatar(myEmoji, size = 96, modifier = Modifier.zIndex(1f))
                    Spacer(Modifier.width((-18).dp))
                    EmojiAvatar(match.profile.emoji, size = 96, ring = Jade, modifier = Modifier.zIndex(2f))
                }
            }
            Spacer(Modifier.height(30.dp))
            Text("MUTUAL", style = EyebrowStyle.copy(letterSpacing = 0.28.em), color = Copper)
            Spacer(Modifier.height(14.dp))
            Text(
                "You're both in.",
                style = DisplayStyle,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${match.profile.name} is in this room, right now.",
                style = BodyStyle.copy(fontSize = 16.sp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onSayHi,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)
            ) {
                Text("Say something", style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButtonRow("Keep discovering", onDismiss)
            Spacer(Modifier.height(22.dp))
            Text(
                "ENCRYPTED · KEYS STAY ON YOUR PHONES",
                style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.06.em),
                color = Slate,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OutlinedButtonRow(label: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(label, style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Block/report dialog, reachable from every surface that shows a stranger's card. */
@Composable
internal fun SafetyDialog(
    name: String,
    onBlockReport: () -> Unit,
    onBlockOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Block $name?",
                    style = TitleStyle.copy(fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "They vanish from your deck. No one is told.",
                    style = BodyStyle.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Text("ALSO REPORT FOR…", style = EyebrowStyle.copy(letterSpacing = 0.14.em), color = Slate)
                Spacer(Modifier.height(10.dp))
                ReportReasonChips()
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onBlockReport,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Block & report", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onBlockOnly,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Block only", style = BodyStyle)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportReasonChips() {
    val reasons = listOf("harassment", "inappropriate", "under 18", "something else")
    val selected = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(-1) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reasons.forEachIndexed { i, r ->
            val on = selected.value == i
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, if (on) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .background(if (on) Color(0xFF2A1512) else Color.Transparent)
                    .clickable { selected.value = if (on) -1 else i }
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text(r, style = BodyStyle.copy(fontSize = 13.sp), color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Shared screen header: mono telemetry eyebrow over an Archivo title. */
@Composable
internal fun ConnectHeader(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            eyebrow.uppercase(),
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = TitleStyle,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
