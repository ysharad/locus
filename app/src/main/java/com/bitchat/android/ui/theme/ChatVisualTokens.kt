@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.bitchat.android.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R

/**
 * The Locus type system, defined app-wide so EVERY screen — including the inherited bitchat
 * chat/onboarding surfaces that read [BitchatFontFamily] and the global [Typography] — renders in
 * the design fonts, not the old terminal mono. Three voices: Archivo (display), Geist (body),
 * Azeret Mono (machine facts only). Bundled in the APK so it works fully offline.
 */
internal val LocusDisplayFamily = FontFamily(
    Font(R.font.archivo_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.width(100f))),
    Font(R.font.archivo_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.width(110f))),
)
internal val LocusBodyFamily = FontFamily(
    Font(R.font.geist_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.geist_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.geist_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.geist_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
internal val LocusMonoFamily = FontFamily(
    Font(R.font.azeret_mono_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.azeret_mono_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

/**
 * The app-wide default family. Now Geist (proportional) — repointed from the old Geist Mono so the
 * chat, onboarding and every stock sheet inherit the design body face instead of terminal mono.
 */
internal val BitchatFontFamily = LocusBodyFamily

/** Exact typography, spacing, and opacity values exported for the chat transcript. */
internal object ChatVisualTokens {
    val MessageBodyFontSize: TextUnit = 14.sp
    val MessageBodyLineHeight: TextUnit = 20.sp
    val SenderFontSize: TextUnit = 14.sp
    val SenderLineHeight: TextUnit = 16.sp
    val SystemActionFontSize: TextUnit = 12.sp
    val SystemActionLineHeight: TextUnit = 16.sp
    val SystemTimeFontSize: TextUnit = 10.sp

    val MessageItemSpacing: Dp = 11.dp
    val SenderTopPadding: Dp = 8.dp
    val SenderToBodySpacing: Dp = 4.dp

    // MARK: - Bubble geometry (ChatUiMode.Bubbles)

    /** Rounded corner on the three "free" corners of a message bubble. Softer = lighter. */
    val BubbleCornerRadius: Dp = 20.dp

    /** Tightened corner on the speaker's own side, giving the bubble a subtle tail. */
    val BubbleTailRadius: Dp = 5.dp

    /** Padding inside a bubble, around the text. */
    val BubblePaddingHorizontal: Dp = 14.dp
    val BubblePaddingVertical: Dp = 9.dp

    /** A bubble never grows past this fraction of the list width, so long lines still wrap. */
    const val BubbleMaxWidthFraction: Float = 0.80f

    /**
     * Author-colour wash inside a bubble. Matches the mention-chip treatment so a tinted
     * bubble stays legible on both the near-black and near-white chat surfaces.
     */
    const val BubbleBackgroundAlpha: Float = 0.16f

    /** Author-colour hairline around a bubble; kept soft so bubbles feel light, not boxed-in. */
    const val BubbleBorderAlpha: Float = 0.22f

    const val SenderSuffixAlpha: Float = 0.60f
    const val HighlightAlpha: Float = 0.20f
    const val MutedTextAlpha: Float = 0.50f

    val MessageBodyStyle = TextStyle(
        fontFamily = BitchatFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = MessageBodyFontSize,
        lineHeight = MessageBodyLineHeight,
    )

    val SenderStyle = TextStyle(
        fontFamily = BitchatFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = SenderFontSize,
        lineHeight = SenderLineHeight,
    )

    val SystemActionStyle = TextStyle(
        fontFamily = BitchatFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = SystemActionFontSize,
        lineHeight = SystemActionLineHeight,
    )
}
