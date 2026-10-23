package com.erswatcher.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.erswatcher.app.R

private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

private val ErsTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = SpaceGrotesk),
        displayMedium = t.displayMedium.copy(fontFamily = SpaceGrotesk),
        displaySmall = t.displaySmall.copy(fontFamily = SpaceGrotesk),
        headlineLarge = t.headlineLarge.copy(fontFamily = SpaceGrotesk),
        headlineMedium = t.headlineMedium.copy(fontFamily = SpaceGrotesk),
        headlineSmall = t.headlineSmall.copy(fontFamily = SpaceGrotesk),
        titleLarge = t.titleLarge.copy(fontFamily = SpaceGrotesk),
        titleMedium = t.titleMedium.copy(fontFamily = SpaceGrotesk),
        titleSmall = t.titleSmall.copy(fontFamily = SpaceGrotesk),
        bodyLarge = t.bodyLarge.copy(fontFamily = SpaceGrotesk),
        bodyMedium = t.bodyMedium.copy(fontFamily = SpaceGrotesk),
        bodySmall = t.bodySmall.copy(fontFamily = SpaceGrotesk),
        labelLarge = t.labelLarge.copy(fontFamily = SpaceGrotesk),
        labelMedium = t.labelMedium.copy(fontFamily = SpaceGrotesk),
        labelSmall = t.labelSmall.copy(fontFamily = SpaceGrotesk)
    )
}

enum class Accent(val label: String, val primary: Long, val onPrimary: Long) {
    VIOLET("Violet", 0xFFB875FF, 0xFF1C0A2E),
    CYAN("Cyan", 0xFF5AD7FF, 0xFF04202B),
    AMBER("Amber", 0xFFFFBE55, 0xFF2E1A00),
    MONO("Mono", 0xFFEDE9F2, 0xFF0A0810)
}

val LocalAccent = staticCompositionLocalOf { Accent.VIOLET }

/** Fixed brand colours that stay the same across accents. */
object Eye {
    val White = Color(0xFFF8F3E8)
    val Cream = Color(0xFFEDE5D6)
    val Orange = Color(0xFFFFAE2B)
    val OrangeDeep = Color(0xFFF08A0C)
    val Ink = Color(0xFF160F05)
    val Violet = Color(0xFFB875FF)
    val Cyan = Color(0xFF58D7FF)
    val Magenta = Color(0xFFF25CFF)
    val Green = Color(0xFF71E0AC)
    val Red = Color(0xFFFF7B86)
}

fun accentOf(name: String?): Accent = try {
    Accent.valueOf(name ?: "VIOLET")
} catch (_: Exception) {
    Accent.VIOLET
}

@Composable
fun ErsTheme(accent: Accent, content: @Composable () -> Unit) {
    val p = Color(accent.primary)
    val onP = Color(accent.onPrimary)
    val scheme = darkColorScheme(
        primary = p,
        onPrimary = onP,
        primaryContainer = Color(0xFF2A173E),
        onPrimaryContainer = Color(0xFFF3E8FF),
        secondary = Color(0xFFD7C9E5),
        onSecondary = Color(0xFF1A1222),
        secondaryContainer = Color(0xFF241C2D),
        onSecondaryContainer = Color(0xFFE7DAF4),
        tertiary = Eye.Cyan,
        onTertiary = Color(0xFF04202A),
        tertiaryContainer = Color(0xFF07313E),
        onTertiaryContainer = Color(0xFFCFF5FF),
        background = Color(0xFF060509),
        onBackground = Color(0xFFF2EDF7),
        surface = Color(0xFF0A0810),
        onSurface = Color(0xFFF2EDF7),
        surfaceVariant = Color(0xFF15121D),
        onSurfaceVariant = Color(0xFFA79EAF),
        surfaceContainerLowest = Color(0xFF050407),
        surfaceContainerLow = Color(0xFF09070D),
        surfaceContainer = Color(0xFF100D16),
        surfaceContainerHigh = Color(0xFF17131F),
        surfaceContainerHighest = Color(0xFF211B2B),
        outline = Color(0xFF352C42),
        outlineVariant = Color(0xFF241E2E),
        error = Eye.Red,
        onError = Color(0xFF36070B),
        errorContainer = Color(0xFF4A1016),
        onErrorContainer = Color(0xFFFFDADD),
        scrim = Color(0xFF000000),
        surfaceTint = p,
        inverseSurface = Color(0xFFF2EDF7),
        inverseOnSurface = Color(0xFF1A151F),
        inversePrimary = Color(0xFF6E35A5),
        surfaceBright = Color(0xFF261F31),
        surfaceDim = Color(0xFF050407)
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ErsTypography,
            content = content
        )
    }
}
