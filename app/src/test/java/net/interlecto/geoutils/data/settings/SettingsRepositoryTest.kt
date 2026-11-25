package net.interlecto.geoutils.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.interlecto.geoutils.ui.theme.isDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun withRepository(block: suspend (SettingsRepository) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("settings.preferences_pb") }
            runBlocking { block(SettingsRepository(store)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaultsToFollowingTheSystem() = withRepository { repo ->
        assertEquals(AppSettings(ThemeMode.SYSTEM), repo.settings.first())
    }

    // One write per test: on Windows DataStore can't rename over a file it just read, in a second write.
    @Test
    fun savesDarkMode() = withRepository { repo ->
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.settings.first().themeMode)
    }

    @Test
    fun savesLightMode() = withRepository { repo ->
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.settings.first().themeMode)
    }

    @Test
    fun unknownKeysMeanSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("sepia"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        for (mode in ThemeMode.entries) assertEquals(mode, ThemeMode.fromKey(mode.key))
    }

    @Test
    fun explicitModesIgnoreTheSystem() {
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }
}
