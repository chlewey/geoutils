package net.interlecto.geoutils.data.settings

import net.interlecto.geoutils.geometry.GeoPoint

/** How the app picks its light or dark colors. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /** The mode saved under [key]; anything unknown (or missing) means follow the system. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** What the user can change on the settings page. More settings are added here as they land. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Points A and B the tools start with while the user has no points of their own saved. */
    val defaultPoints: PointPair = DEFAULT_POINTS,
) {
    companion object {
        /** Bogotá and London, until the user chooses others. */
        val DEFAULT_POINTS = PointPair(GeoPoint(-74.0721, 4.7110), GeoPoint(-0.1278, 51.5074))
    }
}
