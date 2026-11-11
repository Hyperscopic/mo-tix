package com.hyperscopic.motix.platform

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the car allows right now.
 *
 * Pleos policy is "standard first", so this is the AAOS Car API and nothing else:
 *  - [CarUxRestrictionsManager] is the authority. The platform works out the restrictions from the gear, the speed
 *    and the region, and the app applies what it is told (Pleos driving-restriction development guide).
 *  - The gear ([VehiclePropertyIds.GEAR_SELECTION]) only ever makes Mo-Tix stricter: video and payment need Park.
 */
data class DrivingState(
    /** The platform asks for a distraction-optimized screen (the car is idling or moving). */
    val restricted: Boolean = false,
    /** `UX_RESTRICTIONS_NO_VIDEO` is active. */
    val noVideo: Boolean = false,
    /** The gear, when the car reports it: true in Park. Null when unknown. */
    val inPark: Boolean? = null,
    /** False when the Car API is missing (not a car): nothing is restricted. */
    val carConnected: Boolean = false,
) {
    /** Parked: no restriction is active and the gear, when known, is Park. */
    val parked: Boolean get() = !restricted && inPark != false

    /** Trailers play only while parked and never under NO_VIDEO. */
    val videoAllowed: Boolean get() = parked && !noVideo

    /** Booking and payment are parked-only. */
    val paymentAllowed: Boolean get() = parked
}

class DrivingMonitor(private val context: Context) {
    private val _state = MutableStateFlow(DrivingState())
    val state: StateFlow<DrivingState> = _state

    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null
    private var props: CarPropertyManager? = null

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { apply(it) }

    private val gearListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val gear = value.value as? Int ?: return
            _state.value = _state.value.copy(inPark = gear == VehicleGear.GEAR_PARK)
        }

        override fun onErrorEvent(propId: Int, zone: Int) = Unit
    }

    fun start() {
        if (car != null) return
        if (!context.packageManager.hasSystemFeature("android.hardware.type.automotive")) return
        runCatching {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) connect(c) else disconnect()
            }
        }.onFailure { Log.w(TAG, "Car API unavailable", it) }
    }

    private fun connect(c: Car) {
        runCatching {
            val manager = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            ux = manager
            manager.registerListener(uxListener)
            _state.value = _state.value.copy(carConnected = true)
            manager.currentCarUxRestrictions?.let { apply(it) }
        }.onFailure { Log.w(TAG, "CarUxRestrictionsManager unavailable", it) }
        runCatching {
            val manager = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            props = manager
            manager.registerCallback(gearListener, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            (manager.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)?.value)?.let {
                _state.value = _state.value.copy(inPark = it == VehicleGear.GEAR_PARK)
            }
        }.onFailure { Log.i(TAG, "Gear not readable; CarUxRestrictions alone decides: ${it.message}") }
    }

    private fun disconnect() {
        ux = null
        props = null
        _state.value = DrivingState()
    }

    private fun apply(r: CarUxRestrictions) {
        val active = r.activeRestrictions
        _state.value = _state.value.copy(
            carConnected = true,
            restricted = r.isRequiresDistractionOptimization,
            noVideo = r.isRequiresDistractionOptimization && (active and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0,
        )
        Log.i(TAG, "UX restrictions: requiresDO=${r.isRequiresDistractionOptimization} active=0x${active.toString(16)}")
    }

    fun stop() {
        runCatching { ux?.unregisterListener() }
        runCatching { props?.unregisterCallback(gearListener) }
        runCatching { car?.disconnect() }
        car = null
        ux = null
        props = null
    }

    private companion object {
        const val TAG = "MoTixDriving"
    }
}
