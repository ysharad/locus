package com.bitchat.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// Standard UI semantics live in Material so stock components and custom Bitchat composables
// share one source of truth. LocalBitchatPalette below only supplies app-specific extra colors.
//
// "Locus field" palette — a receiver drawn as an instrument. Near-black charcoal-green ground,
// a single fired-copper action colour, and jade reserved for live-signal semantics only
// (dots, bars, presence). No magenta / violet / saturated blue anywhere: the loudest thing
// in the system is brass instrumentation, not LED. See DESIGN_BRIEF.md / the Locus design doc.
internal val DarkBitchatColorScheme = darkColorScheme(
    primary = Color(0xFFCF7A45),           // locusCopper — primary, connect, celebration
    onPrimary = Color(0xFF180E07),          // ON-COPPER
    primaryContainer = Color(0xFF1E1710),   // copper-tint fill (selected chips, tray)
    onPrimaryContainer = Color(0xFFCF7A45),
    secondary = Color(0xFF63C39D),          // locusJade — live signal, in range, safe
    onSecondary = Color(0xFF06170F),        // ON-JADE
    secondaryContainer = Color(0xFF14231C),
    onSecondaryContainer = Color(0xFF63C39D),
    tertiary = Color(0xFFCF7A45),           // match moment is copper, not a third hue
    onTertiary = Color(0xFF180E07),
    background = Color(0xFF0C1210),          // locusInk
    onBackground = Color(0xFFEDE7DA),        // locusBone — text primary
    surface = Color(0xFF131C18),             // locusSurface — sheets, fields
    onSurface = Color(0xFFEDE7DA),
    surfaceVariant = Color(0xFF1A241F),      // locusCard — elevated card
    onSurfaceVariant = Color(0xFF9AA79F),    // locusSage — text secondary
    outline = Color(0xFF2B3A33),             // locusOutline
    outlineVariant = Color(0xFF45564D),      // locusOutlineStrong — focus, icon strokes
    error = Color(0xFFDB5B4A),               // locusRust — block, report, destructive
    onError = Color(0xFF1A0603)
)

internal val LightBitchatColorScheme = lightColorScheme(
    primary = Color(0xFF9C4E23),            // locusCopper (daylight) — clears 4.5:1 on paper
    onPrimary = Color(0xFFFFF6EE),          // ON-COPPER
    primaryContainer = Color(0xFFF6E9DF),   // warm copper-tint fill
    onPrimaryContainer = Color(0xFF9C4E23),
    secondary = Color(0xFF146B4F),          // locusJade (daylight)
    onSecondary = Color(0xFFF2FBF6),        // ON-JADE
    secondaryContainer = Color(0xFFE1EFE8),
    onSecondaryContainer = Color(0xFF146B4F),
    tertiary = Color(0xFF9C4E23),
    onTertiary = Color(0xFFFFF6EE),
    background = Color(0xFFEFECE2),          // locusPaper
    onBackground = Color(0xFF131C18),        // locusInk — text primary
    surface = Color(0xFFF7F5EE),             // locusSurface (daylight)
    onSurface = Color(0xFF131C18),
    surfaceVariant = Color(0xFFFFFFFF),      // locusCard (daylight)
    onSurfaceVariant = Color(0xFF4E5A53),    // locusSage (daylight)
    outline = Color(0xFFD5D1C2),             // locusOutline (daylight)
    outlineVariant = Color(0xFFA8A392),      // locusOutlineStrong (daylight)
    error = Color(0xFFA83420),               // locusRust (daylight)
    onError = Color(0xFFFFF6EE)
)

@Composable
fun BitchatTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkBitchatColorScheme else LightBitchatColorScheme
    val palette = if (shouldUseDark) DarkBitchatPalette else LightBitchatPalette

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(LocalBitchatPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
