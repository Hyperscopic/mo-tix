package com.hyperscopic.motix.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where the car is, and which service said so. */
data class CarPosition(val lat: Double, val lon: Double, val source: Source, val atMillis: Long) {
    enum class Source { PLEOS_FUSED, ANDROID_GPS, ANDROID_NETWORK, LAST_KNOWN }
}

/**
 * The position of the car.
 *
 * First the Pleos FusedLocation SDK (the fused position from the car: GNSS with dead reckoning). The SDK policy page
 * still lists FusedLocation as "in preparation" on some builds, so when the service is not supported, the permission is
 * refused or it stays silent, the Android [LocationManager] answers instead. Nothing here invents a position: with no
 * fix at all, [position] stays null and the app says so.
 */
class CarLocation(private val context: Context) {
    private val _position = MutableStateFlow<CarPosition?>(null)
    val position: StateFlow<CarPosition?> = _position

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var fused: FusedLocation? = null
    private var fusedListening = false
    private var androidListening = false

    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            if (location.latitude == 0.0 && location.longitude == 0.0) return
            offer(CarPosition(location.latitude, location.longitude, CarPosition.Source.PLEOS_FUSED, location.timestampMillis))
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "FusedLocation: ${exception.message}")
        }
    }

    private val androidListener = LocationListener { l -> offer(l.toPosition()) }

    val fusedPermission: Boolean get() = granted(PERMISSION_FUSED)
    val androidPermission: Boolean get() = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Call once the permissions have been answered (and again later: it only adds what is missing). */
    fun start() {
        startFused()
        startAndroid()
    }

    private fun startFused() {
        if (fusedListening || !fusedPermission) return
        runCatching {
            val f = FusedLocation(context.applicationContext)
            if (!f.isSupported()) {
                Log.i(TAG, "Pleos FusedLocation is not supported on this build; using LocationManager")
                return
            }
            f.initialize()
            f.getLastKnownLocation(fusedListener)
            f.registerFusedLocationCallback(fusedListener)
            fused = f
            fusedListening = true
            Log.i(TAG, "Pleos FusedLocation started")
        }.onFailure { Log.w(TAG, "Pleos FusedLocation unavailable: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun startAndroid() {
        if (androidListening || !androidPermission) return
        runCatching {
            val providers = manager.getProviders(true)
            // The freshest fix the system already has, so the first screen does not wait for a new one.
            providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }?.let { offer(it.toPosition(lastKnown = true)) }
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (p in providers) manager.requestLocationUpdates(p, 5_000L, 50f, androidListener, Looper.getMainLooper())
            }
            androidListening = true
        }.onFailure { Log.w(TAG, "LocationManager unavailable: ${it.message}") }
    }

    /** The fused position wins while it is fresh; otherwise the newest fix does. */
    private fun offer(p: CarPosition) {
        val now = _position.value
        val fusedIsFresh = now?.source == CarPosition.Source.PLEOS_FUSED && System.currentTimeMillis() - now.atMillis < 30_000
        if (fusedIsFresh && p.source != CarPosition.Source.PLEOS_FUSED) return
        _position.value = p
    }

    fun stop() {
        if (fusedListening) runCatching {
            fused?.unregisterFusedLocationCallback(fusedListener)
            fused?.release()
        }
        fused = null
        fusedListening = false
        if (androidListening) runCatching { manager.removeUpdates(androidListener) }
        androidListening = false
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toPosition(lastKnown: Boolean = false) = CarPosition(
        latitude, longitude,
        when {
            lastKnown -> CarPosition.Source.LAST_KNOWN
            provider == LocationManager.NETWORK_PROVIDER -> CarPosition.Source.ANDROID_NETWORK
            else -> CarPosition.Source.ANDROID_GPS
        },
        time,
    )

    companion object {
        private const val TAG = "MoTixLocation"
        const val PERMISSION_FUSED = "pleos.car.permission.FUSED_LOCATION"
        val PERMISSIONS = arrayOf(PERMISSION_FUSED, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
