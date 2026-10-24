package net.interlecto.geoutils.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.interlecto.geoutils.geometry.GeoPoint
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Two points the user entered, A and B. */
data class PointPair(val a: GeoPoint, val b: GeoPoint)

/** The last points used for each tool, so their fields come back filled in. */
data class SavedPoints(val equator: PointPair? = null, val route: PointPair? = null)

/** Remembers the last points used for the oblique equator and for routes, across app restarts. */
@Singleton
class SavedPointsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val points: Flow<SavedPoints> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> SavedPoints(equator = prefs.pair(EQUATOR), route = prefs.pair(ROUTE)) }

    suspend fun saveEquator(points: PointPair) = save(EQUATOR, points)

    suspend fun saveRoute(points: PointPair) = save(ROUTE, points)

    private suspend fun save(prefix: String, points: PointPair) {
        store.edit { prefs ->
            prefs[key(prefix, "a_lon")] = points.a.lon
            prefs[key(prefix, "a_lat")] = points.a.lat
            prefs[key(prefix, "b_lon")] = points.b.lon
            prefs[key(prefix, "b_lat")] = points.b.lat
        }
    }

    private fun Preferences.pair(prefix: String): PointPair? {
        val aLon = this[key(prefix, "a_lon")] ?: return null
        val aLat = this[key(prefix, "a_lat")] ?: return null
        val bLon = this[key(prefix, "b_lon")] ?: return null
        val bLat = this[key(prefix, "b_lat")] ?: return null
        return runCatching { PointPair(GeoPoint(aLon, aLat), GeoPoint(bLon, bLat)) }.getOrNull()
    }

    private fun key(prefix: String, name: String) = doublePreferencesKey("${prefix}_$name")

    private companion object {
        const val EQUATOR = "equator"
        const val ROUTE = "route"
    }
}
