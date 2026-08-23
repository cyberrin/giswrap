package com.cyberrin.giswrap.data.local

import com.cyberrin.giswrap.domain.model.WeatherError
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.cyberrin.giswrap.domain.model.Place
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LocationDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun currentPlace(): Place {
        val location = currentLocation()
        val name = localityName(location)
            ?: throw NoFix(WeatherError.LocationNoName)
        return Place(name, location.latitude, location.longitude)
    }

    private suspend fun currentLocation(): Location {
        if (!hasPermission()) throw NoFix(WeatherError.LocationDenied)

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw NoFix(WeatherError.LocationNoProvider)

        lastKnown(manager)?.let { return it }

        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: throw NoFix(WeatherError.LocationOff)

        return awaitFix(manager, provider)
    }

    // Safe: every caller goes through hasPermission() first.
    @Suppress("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .asSequence()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    @Suppress("MissingPermission")
    private suspend fun awaitFix(manager: LocationManager, provider: String): Location =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(
                    provider,
                    signal,
                    context.mainExecutor,
                ) { location ->
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        continuation.resumeWithException(
                            NoFix(WeatherError.LocationUnavailable)
                        )
                    }
                }
            } else {
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    @Deprecated("Required by the pre-30 interface")
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit

                    override fun onProviderDisabled(provider: String) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                NoFix(WeatherError.LocationOff)
                            )
                        }
                    }
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION") // the pre-R equivalent of getCurrentLocation
                manager.requestSingleUpdate(provider, listener, context.mainLooper)
            }
        }

    private fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

    private suspend fun localityName(location: Location): String? {
        if (!Geocoder.isPresent()) return null

        val geocoder = Geocoder(context, Locale.forLanguageTag("ru"))
        val addresses = runCatching { geocoder.lookup(location) }.getOrNull().orEmpty()
        return addresses.firstNotNullOfOrNull { address ->
            address.locality
                ?: address.subAdminArea
                ?: address.adminArea
        }?.takeIf { it.isNotBlank() }
    }

    private suspend fun Geocoder.lookup(location: Location): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    if (continuation.isActive) continuation.resume(addresses)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION") // the pre-33 blocking form, hence the IO dispatcher
                getFromLocation(location.latitude, location.longitude, 1).orEmpty()
            }
        }
}

class NoFix(val error: WeatherError, cause: Throwable? = null) : Exception(error.toString(), cause)
