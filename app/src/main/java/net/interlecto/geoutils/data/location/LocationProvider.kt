package net.interlecto.geoutils.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import net.interlecto.geoutils.geometry.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The device's approximate position, from the platform [LocationManager] (no
 * Google Play services needed). Coarse location is enough to center a globe.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * A recent last-known fix if there is one, otherwise a fresh fix within
     * [timeoutMs]. Null without permission, with location off, or on timeout.
     */
    @SuppressLint("MissingPermission") // Checked by hasPermission().
    suspend fun current(timeoutMs: Long = 10_000): GeoPoint? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null

        val now = System.currentTimeMillis()
        val lastKnown = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && now - lastKnown.time < MAX_AGE_MS) return lastKnown.toGeoPoint()

        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) }
            ?: return lastKnown?.toGeoPoint()
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location: Location? -> continuation.resume(location) }
            }
        }
        return (fresh ?: lastKnown)?.toGeoPoint()
    }

    private fun Location.toGeoPoint() = GeoPoint(longitude, latitude)

    private companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
    }
}
