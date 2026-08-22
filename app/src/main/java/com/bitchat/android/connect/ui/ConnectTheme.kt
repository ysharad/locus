@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.bitchat.android.connect.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The "Locus field" design voice — an instrument, not a flyer.
 *
 * Three type voices, on purpose:
 *  - [ConnectDisplay] (Archivo, variable width): the match moment, names, the wordmark. Wide-cut,
 *    the confidence of a record sleeve.
 *  - [ConnectSans] (Geist): bios and body copy. The human voice. Never above 20sp.
 *  - [ConnectMono] (Azeret Mono): signal state, key fingerprints, section eyebrows, privacy lines.
 *    The machine voice — if a human wrote it, it isn't mono.
 */
internal val ConnectDisplay = FontFamily(
    // Title / cardName voice — normal width, semibold.
    Font(
        R.font.archivo_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.width(100f))
    ),
    // Hero / wordmark voice — wide-cut, bold.
    Font(
        R.font.archivo_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.width(112f))
    )
)

internal val ConnectSans = FontFamily(
    Font(R.font.geist_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.geist_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.geist_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
)

internal val ConnectMono = FontFamily(
    Font(R.font.azeret_mono_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.azeret_mono_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
)

// ---- Locus colour tokens (dark identity; used directly on the always-dark crowd surfaces
// and as accents elsewhere). Ground / text / outline semantics come from MaterialTheme. ----

/** Fired copper — the one warm action colour. Primary, connect, celebration. */
internal val Copper = Color(0xFFCF7A45)
internal val OnCopper = Color(0xFF180E07)
/** Jade — live signal only: dots, bars, in-range, safe. Never a fill or heading. */
internal val Jade = Color(0xFF63C39D)
internal val OnJade = Color(0xFF06170F)
/** Rust — block, report, destructive. Used nowhere else so it is unmistakable. */
internal val Rust = Color(0xFFDB5B4A)
internal val Bone = Color(0xFFEDE7DA)
internal val Sage = Color(0xFF9AA79F)
internal val Slate = Color(0xFF8A958C)
/** The well behind a glyph avatar — a hair lighter than the card. */
internal val AvatarWell = Color(0xFF22302A)
/** Copper-tint fill for the selected chip / card / tray. */
internal val CopperTint = Color(0xFF1E1710)

/** Locus+ mark. The one gold in the app — it means "a perk", nothing else. */
internal val LocusGold = Color(0xFFE8B341)

/** Glyph tints a profile can choose. Copper first = the system default. */
internal val GlyphColors = listOf(
    Color(0xFFCF7A45), // copper
    Color(0xFF63C39D), // jade
    Color(0xFFE8B341), // gold
    Color(0xFFDB5B4A), // rust
    Color(0xFF7FA8D9), // ice
    Color(0xFFB98BD9), // orchid
    Color(0xFFEDE7DA), // bone
    Color(0xFF8A958C), // slate
)

// Back-compat aliases for call sites still naming the old palette. Signal == the action
// colour (copper), the match strobe is also copper, the "secondary" accent is jade.
internal val Signal = Copper
internal val Strobe = Copper
internal val Ultraviolet = Jade

/** The five contour strokes of the Locus field, inner (brightest) to outer. */
internal val ContourStrokes = listOf(
    Color(0xFF5C7266),
    Color(0xFF4A5C53),
    Color(0xFF3E4E46),
    Color(0xFF334239),
    Color(0xFF2B3830)
)

/** A flat copper sheen for the rare place a gradient still reads better than a solid. */
internal val SignalGradient = Brush.linearGradient(listOf(Copper, Color(0xFFB4642F)))

// ---- Type scale (Locus) ----

/** Match + wordmark only. Archivo 40/700 wide, tight tracking. */
internal val DisplayStyle = TextStyle(
    fontFamily = ConnectDisplay,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.025).em
)

/** Screen titles. Archivo 28/600. */
internal val TitleStyle = TextStyle(
    fontFamily = ConnectDisplay,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.015).em
)

/** Deck + match card name. Archivo 31/600. */
internal val CardNameStyle = TextStyle(
    fontFamily = ConnectDisplay,
    fontWeight = FontWeight.SemiBold,
    fontSize = 31.sp,
    lineHeight = 35.sp,
    letterSpacing = (-0.02).em
)

/** Rows, chat headers. Archivo 17/600. */
internal val ListNameStyle = TextStyle(
    fontFamily = ConnectDisplay,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 22.sp
)

/** Machine facts + section eyebrows. Azeret Mono 11/500, wide-tracked, uppercased at call site. */
internal val EyebrowStyle = TextStyle(
    fontFamily = ConnectMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.em
)

/** Body / UI copy. Geist 15/400. Never below 15sp. */
internal val BodyStyle = TextStyle(
    fontFamily = ConnectSans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 23.sp,
    letterSpacing = 0.005.em
)

// ---- The signature: the Locus field ----

/**
 * Irregular contour rings centred on you, bottom-centre — proximity drawn as topography.
 * Five closed paths, stroked only, no fills / no blur / no shadow. Each ring's alpha breathes
 * on its own slow cycle so the field never pulses in unison. Cheaper than a single radial glow.
 *
 * @param peerRings ring indices (0 = closest) to plot a jade signal dot on.
 */
@Composable
internal fun LocusField(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    peerRings: List<Int> = emptyList(),
    originDot: Boolean = false,
    searching: Boolean = false
) {
    val cycles = listOf(3.5f, 4.5f, 6f, 7.5f, 9f)
    val transition = rememberInfiniteTransition(label = "locusField")
    val breaths = cycles.map { secs ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween((secs * 1000).toInt()), RepeatMode.Reverse),
            label = "breathe"
        )
    }
    // The whole field slowly drifts (~6dp) and scales (~1.2%) so it's always breathing — the one
    // ambient motion in the app. Per the design system's "scanning ambient" spec.
    val drift = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse), label = "drift"
    )
    val fieldScale = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6500), RepeatMode.Reverse), label = "scale"
    )
    // "Searching" scan: two staggered contour rings expand outward from you, on a loop.
    val scanA = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600)), label = "scanA")
    val scanB = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, delayMillis = 1300)), label = "scanB")
    // Alpha ranges: inner brighter, outer fainter (per spec).
    val alphaBands = listOf(0.34f to 0.62f, 0.30f to 0.54f, 0.24f to 0.44f, 0.16f to 0.32f, 0.10f to 0.26f)

    // Precompute the 14 polar samples per ring, radius modulated by three summed sines.
    val samples = remember {
        List(5) { ring ->
            val seed = ring * 1.7f + 0.4f
            List(14) { i ->
                val t = i / 14f
                val ang = t * 2f * PI.toFloat()
                val amp = 6f + ring * 2.25f // 6 -> ~15dp
                val m = (sin(ang * 3f + seed) + 0.6f * sin(ang * 5f + seed * 2f) + 0.4f * sin(ang * 2f - seed)) / 2f
                ang to (amp * m)
            }
        }
    }

    Canvas(modifier) {
        val cx = size.width * 0.5f
        val driftPx = if (animated) -10.dp.toPx() * drift.value else 0f
        val scale = if (animated) 1f + 0.02f * fieldScale.value else 1f
        val cy = size.height * 0.92f + driftPx
        val baseRadii = listOf(64f, 122f, 180f, 238f, 296f).map { (it * scale).dp.toPx() }
        val strokeW = listOf(1.4f, 1.3f, 1.2f, 1.1f, 1.0f)

        for (ring in 0 until 5) {
            val pts = samples[ring].map { (ang, mod) ->
                val r = baseRadii[ring] + mod.dp.toPx()
                Offset(cx + r * cos(ang), cy + r * sin(ang) * 0.92f)
            }
            val path = closedCatmullRom(pts)
            val a = if (animated) {
                val (lo, hi) = alphaBands[ring]
                lo + (hi - lo) * breaths[ring].value
            } else (alphaBands[ring].first + alphaBands[ring].second) / 2f
            drawPath(
                path = path,
                color = ContourStrokes[ring].copy(alpha = (a * 0.6f).coerceIn(0f, 1f)),
                style = Stroke(width = strokeW[ring].dp.toPx())
            )
            // Jade signal dot on the ring, at a fixed pleasant angle per ring index.
            if (ring in peerRings) {
                val ang = (-0.35f - ring * 0.5f)
                val r = baseRadii[ring]
                val p = Offset(cx + r * cos(ang), cy + r * sin(ang) * 0.92f)
                drawCircle(Jade, radius = 5.dp.toPx(), center = p)
            }
        }
        if (searching && animated) {
            listOf(scanA.value, scanB.value).forEach { s ->
                val r = 30.dp.toPx() + s * 300.dp.toPx()
                drawPath(
                    path = contourRing(cx, cy, r, r * 0.045f, 1.3f, points = 26, yScale = 0.92f),
                    color = Jade.copy(alpha = (1f - s) * 0.4f),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
        }
        if (originDot) {
            drawCircle(Copper.copy(alpha = 0.22f), radius = 14.dp.toPx(), center = Offset(cx, cy))
            drawCircle(Copper, radius = 5.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

/**
 * A single irregular contour ring — a hand-surveyed circle, not a geometric one. The radius is
 * modulated by three summed sines with a per-ring [seed] so no two rings wobble alike, then
 * smoothed. This is the shape that carries the "cartography" identity; nothing in the app draws
 * a perfect circle for a ring.
 */
internal fun contourRing(
    cx: Float,
    cy: Float,
    rPx: Float,
    ampPx: Float,
    seed: Float,
    points: Int = 20,
    yScale: Float = 1f
): Path {
    val pts = List(points) { i ->
        val ang = i.toFloat() / points * 2f * PI.toFloat()
        val m = (sin(ang * 3f + seed) + 0.55f * sin(ang * 5f + seed * 2.1f) + 0.5f * sin(ang * 2f - seed * 1.3f)) / 2.05f
        val r = rPx + ampPx * m
        Offset(cx + r * cos(ang), cy + r * sin(ang) * yScale)
    }
    return closedCatmullRom(pts)
}

/** Build a closed smooth path through [pts] using a Catmull-Rom → cubic Bézier conversion. */
private fun closedCatmullRom(pts: List<Offset>): Path {
    val path = Path()
    val n = pts.size
    if (n < 3) return path
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until n) {
        val p0 = pts[(i - 1 + n) % n]
        val p1 = pts[i]
        val p2 = pts[(i + 1) % n]
        val p3 = pts[(i + 2) % n]
        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    path.close()
    return path
}

/**
 * The mark, at any size: three nested contour arcs (copper → sage → outline) and a copper
 * origin dot. Below 48dp callers should pass rings = 2 so the third ring doesn't turn to mud.
 */
@Composable
internal fun ContourMark(sizeDp: Int, modifier: Modifier = Modifier, rings: Int = 3) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = size.minDimension / 2f
        // Inner (copper) to outer (faint) — the same order as the field, cropped to a glyph.
        val ringColors = listOf(Copper, Color(0xFF5C7266), outline)
        val ringWidth = listOf(4f, 3f, 2.5f)
        val radii = listOf(base * 0.34f, base * 0.60f, base * 0.88f)
        val seeds = listOf(0.7f, 2.3f, 4.1f)
        for (i in 0 until rings.coerceIn(2, 3)) {
            drawPath(
                path = contourRing(cx, cy, radii[i], base * 0.05f, seeds[i], points = 22),
                color = ringColors[i],
                style = Stroke(width = ringWidth[i].dp.toPx())
            )
        }
        drawCircle(Copper, radius = (base * 0.085f), center = Offset(cx, cy))
    }
}
