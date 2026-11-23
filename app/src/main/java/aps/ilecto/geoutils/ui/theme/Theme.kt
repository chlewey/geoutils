package net.interlecto.geoutils.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ocean = Color(0xFF0B3D66)
private val OceanLight = Color(0xFF9CC8F0)
private val Land = Color(0xFF5FA36A)
private val LandLight = Color(0xFFA5D6AC)

private val LightColors = lightColorScheme(
    primary = Ocean,
    secondary = Land,
)

private val DarkColors = darkColorScheme(
    primary = OceanLight,
    secondary = LandLight,
)

@Composable
fun GeoUtilsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
