package net.interlecto.geoutils.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.interlecto.geoutils.geometry.GeoPoint
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The user's settings, kept in the preferences DataStore (map content stays in Room). */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                themeMode = ThemeMode.fromKey(prefs[THEME_MODE]),
                defaultPoints = prefs.defaultPoints() ?: AppSettings.DEFAULT_POINTS,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.key }
    }

    suspend fun setDefaultPoints(points: PointPair) {
        store.edit {
            it[A_LON] = points.a.lon
            it[A_LAT] = points.a.lat
            it[B_LON] = points.b.lon
            it[B_LAT] = points.b.lat
        }
    }

    private fun Preferences.defaultPoints(): PointPair? {
        val aLon = this[A_LON] ?: return null
        val aLat = this[A_LAT] ?: return null
        val bLon = this[B_LON] ?: return null
        val bLat = this[B_LAT] ?: return null
        return runCatching { PointPair(GeoPoint(aLon, aLat), GeoPoint(bLon, bLat)) }.getOrNull()
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val A_LON = doublePreferencesKey("default_a_lon")
        val A_LAT = doublePreferencesKey("default_a_lat")
        val B_LON = doublePreferencesKey("default_b_lon")
        val B_LAT = doublePreferencesKey("default_b_lat")
    }
}
