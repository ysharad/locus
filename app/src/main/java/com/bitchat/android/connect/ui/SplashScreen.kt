@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.bitchat.android.connect.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The field, surveyed. The three contour rings stroke on from the inside out, then the origin dot
 * lands and the wordmark fades up — the app's whole idea stated in ~1.3 seconds, on ink.
 */
@Composable
fun LocusSplash(onDone: () -> Unit) {
    val prog = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        prog.animateTo(1f, tween(1300, easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)))
        delay(180)
        onDone()
    }
    val p = prog.value

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0C1210)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(132.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val base = size.minDimension / 2f
                val radii = listOf(base * 0.34f, base * 0.60f, base * 0.88f)
                val colors = listOf(Copper, Color(0xFF5C7266), Color(0xFF3E4E46))
                val seeds = listOf(0.7f, 2.3f, 4.1f)
                val widths = listOf(4f, 3f, 2.5f)
                val startFracs = listOf(0.0f, 0.16f, 0.32f)
                for (i in 0 until 3) {
                    val local = ((p - startFracs[i]) / 0.6f).coerceIn(0f, 1f)
                    if (local > 0f) {
                        val full = contourRing(cx, cy, radii[i], base * 0.05f, seeds[i], points = 24)
                        val drawn = if (local >= 1f) full else partialPath(full, local)
                        drawPath(drawn, colors[i], style = Stroke(width = widths[i].dp.toPx()))
                    }
                }
                val dotAlpha = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
                if (dotAlpha > 0f) drawCircle(Copper.copy(alpha = dotAlpha), radius = base * 0.085f, center = Offset(cx, cy))
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "LOCUS",
                modifier = Modifier.alpha(((p - 0.8f) / 0.2f).coerceIn(0f, 1f)),
                fontFamily = ConnectDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = 0.34.em,
                color = Bone
            )
        }
        Text(
            "NO ACCOUNT · NO SERVER",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).alpha(((p - 0.85f) / 0.15f).coerceIn(0f, 1f)),
            style = EyebrowStyle.copy(fontSize = 10.sp),
            color = Slate
        )
    }
}

/** Trim a path to the first [fraction] of its length — the "drawing on" effect. */
private fun partialPath(full: Path, fraction: Float): Path {
    val pm = PathMeasure()
    pm.setPath(full, false)
    val out = Path()
    pm.getSegment(0f, pm.length * fraction, out, startWithMoveTo = true)
    return out
}
