package net.interlecto.geoutils.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.interlecto.geoutils.data.settings.ThemeMode

// Complete Material 3 schemes derived from the palette (docs/identity/README.md).
// No dynamic color: the brand palette is the identity.

private val LightColors = lightColorScheme(
    primary = Palette.Ocean,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3F4),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF3E7D4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E8CB),
    onSecondaryContainer = Color(0xFF00210C),
    tertiary = Color(0xFF00638E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCBE6FF),
    onTertiaryContainer = Color(0xFF001E30),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF14202B),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF14202B),
    surfaceVariant = Color(0xFFDEE3EA),
    onSurfaceVariant = Color(0xFF424C57),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8FB),
    surfaceContainer = Color(0xFFEEF2F7),
    surfaceContainerHigh = Color(0xFFE8EDF2),
    surfaceContainerHighest = Color(0xFFE2E8EE),
    outline = Color(0xFF727D89),
    outlineVariant = Color(0xFFC2C9D1),
)

private val DarkColors = darkColorScheme(
    primary = Palette.OceanLight,
    onPrimary = Color(0xFF0B2030),
    primaryContainer = Color(0xFF23405C),
    onPrimaryContainer = Color(0xFFD2E3F4),
    secondary = Palette.LandLight,
    onSecondary = Color(0xFF0F3A1A),
    secondaryContainer = Color(0xFF2A5233),
    onSecondaryContainer = Color(0xFFC8E8CB),
    tertiary = Color(0xFF7CC4FF),
    onTertiary = Color(0xFF003450),
    tertiaryContainer = Color(0xFF004B70),
    onTertiaryContainer = Color(0xFFCBE6FF),
    background = Color(0xFF0F151C),
    onBackground = Color(0xFFE4EBF2),
    surface = Color(0xFF0F151C),
    onSurface = Color(0xFFE4EBF2),
    surfaceVariant = Color(0xFF2A3745),
    onSurfaceVariant = Color(0xFFBAC5D0),
    surfaceContainerLowest = Color(0xFF0A0F14),
    surfaceContainerLow = Color(0xFF141B22),
    surfaceContainer = Color(0xFF18212B),
    surfaceContainerHigh = Color(0xFF222C37),
    surfaceContainerHighest = Color(0xFF2D3844),
    outline = Color(0xFF85909B),
    outlineVariant = Color(0xFF3A4856),
)

/** Whether the app is dark for [mode], given whether the system is. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun GeoUtilsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.isDark(isSystemInDarkTheme())) DarkColors else LightColors,
        typography = GeoUtilsTypography,
        shapes = GeoUtilsShapes,
        content = content,
    )
}
