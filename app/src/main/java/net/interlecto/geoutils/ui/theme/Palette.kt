package net.interlecto.geoutils.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The brand colors, in one place. The visual identity redesign changes these
 * values (and the color schemes in Theme.kt), not the call sites. The same
 * colors appear in `docs/identity/` and in `res/values/colors.xml`.
 */
object Palette {
    val Ocean = Color(0xFF0B3D66)
    val OceanLight = Color(0xFF9CC8F0)
    val Land = Color(0xFF5FA36A)
    val LandLight = Color(0xFFA5D6AC)
    val Ice = Color(0xFFEAF2F8)

    /** Default background of the map viewer; it does not follow the light/dark theme. */
    val Night = Color(0xFF000814)
}
