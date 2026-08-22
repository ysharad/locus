package com.bitchat.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Bitchat-specific color tokens that do not have a faithful Material 3 semantic role.
 *
 * Standard backgrounds, surfaces, text, outlines, primary/secondary accents, and errors belong
 * to [androidx.compose.material3.MaterialTheme.colorScheme]. Keeping only the extra app semantics
 * here lets Material components inherit correct defaults without losing Bitchat's identity.
 */
@Immutable
data class BitchatPalette(
    // MARK: - Form controls
    /**
     * Resting border for text inputs. Deliberately a neutral grey rather than the green-tinted
     * Material outline: the composer is the one surface the user stares at while typing.
     */
    val inputOutline: Color,
    /** Border for a focused text input. A step brighter, still neutral. */
    val inputOutlineFocused: Color,
    /**
     * Fill for text inputs. Near-black / near-white and completely untinted, for the same reason
     * as [inputOutline] — and because the composer sits on top of a green-tinted scrim, so any
     * tint of its own compounds into something muddy.
     */
    val inputSurface: Color,
    /** Fill for a focused text input. A barely perceptible lift. */
    val inputSurfaceFocused: Color,
    /** Resting disc behind the composer's action glyphs. Neutral grey. */
    val inputButton: Color,

    // MARK: - Extra semantics
    /** Timestamps, placeholders, section labels, disabled states. */
    val textTertiary: Color,
    /** Self, mentions targeting you, unread DMs. */
    val accentOrange: Color,
    /** Legacy slot (was Nostr reachability) — now a quiet slate. */
    val accentPurple: Color,
    /** Live-signal / sound-state: verified, in range, delivered in the room. */
    val accentJade: Color,
    /** Destructive / failed: block, report, a handshake that did not hold. */
    val accentRust: Color,

    // MARK: - Deterministic peer colors
    /**
     * Saturation/value applied after deriving a peer's stable hue. Swap this when adding a
     * new theme — see [PeerColorStyle] for contrast guidelines.
     */
    val peerColors: PeerColorStyle,
)

val DarkBitchatPalette = BitchatPalette(
    inputOutline = Color(0xFF26302B),
    inputOutlineFocused = Color(0xFFCF7A45),
    inputSurface = Color(0xFF101612),
    inputSurfaceFocused = Color(0xFF16201A),
    inputButton = Color(0xFF1E2823),
    textTertiary = Color(0xFF8A958C),
    // Copper — the app's one accent (self bubbles, mentions, unread). Was amber; the Locus
    // system has no amber, so this lines the chat up with the rest of the app.
    accentOrange = Color(0xFFCF7A45),
    accentPurple = Color(0xFF8A958C),
    accentJade = Color(0xFF63C39D),
    accentRust = Color(0xFFDB5B4A),
    peerColors = PeerColorStyle.Dark,
)

val LightBitchatPalette = BitchatPalette(
    inputOutline = Color(0xFFCFD3D1),
    inputOutlineFocused = Color(0xFF8E9490),
    inputSurface = Color(0xFFFAFAFA),
    inputSurfaceFocused = Color(0xFFF2F2F2),
    inputButton = Color(0xFFE8E8E8),
    textTertiary = Color(0xFF6B756E),
    accentOrange = Color(0xFF9C4E23),
    accentPurple = Color(0xFF6B756E),
    accentJade = Color(0xFF146B4F),
    accentRust = Color(0xFFA63B2E),
    peerColors = PeerColorStyle.Light,
)

val LocalBitchatPalette = staticCompositionLocalOf { DarkBitchatPalette }

/**
 * Motion tokens. The redesign leans on short, snappy transitions: long durations read as
 * sluggish on a chat surface where the user is scanning quickly.
 */
object BitchatMotion {
    /** Icon tints, text colors, small fills. */
    const val QUICK_MS = 120

    /** Tab indicators, pill growth, chip reveals. */
    const val STANDARD_MS = 180

    /** Sheet-level fades and scroll-driven top bars. */
    const val EMPHASIZED_MS = 240
}
