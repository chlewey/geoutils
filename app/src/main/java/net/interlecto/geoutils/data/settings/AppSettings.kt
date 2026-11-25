package net.interlecto.geoutils.data.settings

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
)
