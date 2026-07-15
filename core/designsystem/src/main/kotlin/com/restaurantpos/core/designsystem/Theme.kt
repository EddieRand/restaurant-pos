package com.restaurantpos.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Light color scheme — dynamic color is intentionally disabled (SUNMI brand requirement)
private val PosLightColorScheme: ColorScheme = lightColorScheme(
    primary = SunmiOrange,
    onPrimary = OnSunmiOrange,
    primaryContainer = SunmiOrangeContainer,
    onPrimaryContainer = OnSunmiOrangeContainer,
    secondary = SunmiOrangeDark,
    onSecondary = OnSunmiOrange,
    background = PosBackground,
    onBackground = PosOnSurface,
    surface = PosSurface,
    onSurface = PosOnSurface,
    surfaceVariant = PosSurfaceVariant,
    onSurfaceVariant = PosOnSurfaceVariant,
    outline = PosOutline,
    error = PosError,
    errorContainer = PosErrorContainer,
)

// Dark palette — named constants for readability
private val DarkBackground = androidx.compose.ui.graphics.Color(0xFF1C1917)
private val DarkSurface = androidx.compose.ui.graphics.Color(0xFF241E1B)
private val DarkOnSurface = androidx.compose.ui.graphics.Color(0xFFEDE0D9)
private val DarkError = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
private val DarkErrorContainer = androidx.compose.ui.graphics.Color(0xFF93000A)

private val PosDarkColorScheme: ColorScheme = darkColorScheme(
    primary = SunmiOrangeLight,
    onPrimary = SunmiOrangeDark,
    primaryContainer = SunmiOrangeDark,
    onPrimaryContainer = SunmiOrangeContainer,
    secondary = SunmiOrange,
    onSecondary = OnSunmiOrange,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    error = DarkError,
    errorContainer = DarkErrorContainer,
)

@Composable
fun PosTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) PosDarkColorScheme else PosLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PosTypography,
        shapes = PosShapes,
        content = content,
    )
}
