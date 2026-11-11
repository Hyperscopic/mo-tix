package com.hyperscopic.motix.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where a "Get route" request stands. */
sealed interface RouteState {
    data object Idle : RouteState

    /** Sent to the car navigation; waiting for it to find the route. */
    data class Requesting(val destination: String) : RouteState

    /** The car navigation is guiding. Distance in metres and time in seconds, when it reported them. */
    data class Guiding(val destination: String, val distanceM: Int? = null, val durationS: Int? = null) : RouteState

    data class Failed(val destination: String, val reason: Reason) : RouteState

    enum class Reason { UNAVAILABLE, TOO_CLOSE, TOO_FAR, LOW_BATTERY, SEARCH_FAILED }
}

/**
 * "Get route" on Pleos Connect: the destination goes to the native navigation through the NaviHelper SDK
 * ([NaviHelper.requestRoute]), and the navigation is brought to the front
 * ([NaviHelper.requestNavigationScreen] with [NavigationScreenRequest.Foreground]). Mo-Tix draws no map and computes
 * no route of its own: routing, traffic and guidance belong to the car.
 */
class CarNavigator(private val context: Context) {
    private val _state = MutableStateFlow<RouteState>(RouteState.Idle)
    val state: StateFlow<RouteState> = _state

    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    @Volatile private var connected = false
    private var pending: Request? = null

    private class Request(val lat: Double, val lon: Double, val name: String, val address: String)

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "onRouteStarted $routeStartInfo")
            val name = destinationName() ?: routeStartInfo.name
            _state.value = RouteState.Guiding(name, routeStartInfo.distance.takeIf { it > 0 }, routeStartInfo.duration.takeIf { it > 0 })
            showNavigation()
        }

        override fun onRoutingCompleted() {
            Log.i(TAG, "onRoutingCompleted")
            val s = _state.value
            if (s is RouteState.Requesting) _state.value = RouteState.Guiding(s.destination)
            showNavigation()
            runCatching { helper?.getDestinationInfo() }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            Log.i(TAG, "onDestinationInfo $destinationInfo")
            val s = _state.value
            if (s is RouteState.Guiding) {
                _state.value = s.copy(
                    distanceM = destinationInfo.remainDistance.takeIf { it > 0 } ?: s.distanceM,
                    durationS = destinationInfo.remainTime.takeIf { it > 0 } ?: s.durationS,
                )
            }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.i(TAG, "onDestinationArrived")
            _state.value = RouteState.Idle
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "onRouteCancelled")
            _state.value = RouteState.Idle
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "onError $errorCode")
            val name = destinationName() ?: return
            _state.value = RouteState.Failed(
                name,
                when (errorCode) {
                    NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> RouteState.Reason.TOO_CLOSE
                    NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> RouteState.Reason.TOO_FAR
                    NaviErrorCode.CAUSE_LOW_BATTERY -> RouteState.Reason.LOW_BATTERY
                    else -> RouteState.Reason.SEARCH_FAILED
                },
            )
        }
    }

    private fun destinationName(): String? = when (val s = _state.value) {
        is RouteState.Requesting -> s.destination
        is RouteState.Guiding -> s.destination
        is RouteState.Failed -> s.destination
        RouteState.Idle -> null
    }

    /** Connect to the Pleos navigation service. Safe to call again. */
    fun connect() {
        if (helper != null) return
        runCatching {
            val h = NaviHelper(context.applicationContext)
            helper = h
            h.initialize(
                onConnected = {
                    Log.i(TAG, "NaviHelper connected")
                    connected = true
                    runCatching { h.addListener(listener) }
                    pending?.let { p -> pending = null; main.post { send(p) } }
                },
                onFailed = { t ->
                    Log.w(TAG, "NaviHelper failed to connect: ${t.message}")
                    connected = false
                    helper = null
                    pending?.let { p -> pending = null; _state.value = RouteState.Failed(p.name, RouteState.Reason.UNAVAILABLE) }
                },
            )
        }.onFailure {
            Log.w(TAG, "NaviHelper unavailable: ${it.message}")
            helper = null
        }
    }

    /** One tap: set the destination in the car navigation and bring the navigation forward. */
    fun routeTo(lat: Double, lon: Double, name: String, address: String) {
        val request = Request(lat, lon, name, address)
        _state.value = RouteState.Requesting(name)
        if (helper == null) connect()
        when {
            helper == null -> _state.value = RouteState.Failed(name, RouteState.Reason.UNAVAILABLE)
            connected -> send(request)
            else -> pending = request // sent from onConnected
        }
        main.postDelayed({
            // Still waiting: the navigation did not answer. Say so instead of spinning forever.
            val s = _state.value
            if (s is RouteState.Requesting && s.destination == name) {
                pending = null
                _state.value = RouteState.Failed(name, if (connected) RouteState.Reason.SEARCH_FAILED else RouteState.Reason.UNAVAILABLE)
            }
        }, TIMEOUT_MS)
    }

    private fun send(r: Request) {
        runCatching {
            helper!!.requestRoute(
                RouteInfo(latitude = r.lat, longitude = r.lon, poiName = r.name, poiId = "", address = r.address, poiSubId = ""),
            )
            Log.i(TAG, "requestRoute sent: ${r.name}")
        }.onFailure {
            Log.w(TAG, "requestRoute failed: ${it.message}")
            _state.value = RouteState.Failed(r.name, RouteState.Reason.UNAVAILABLE)
        }
    }

    /** Bring the navigation app to the front (it takes the window next to Mo-Tix). */
    fun showNavigation() {
        runCatching { helper?.requestNavigationScreen(NavigationScreenRequest.Foreground) }
            .onFailure { Log.w(TAG, "requestNavigationScreen failed: ${it.message}") }
    }

    fun clear() {
        if (_state.value is RouteState.Failed) _state.value = RouteState.Idle
    }

    fun release() {
        runCatching {
            helper?.removeListener(listener)
            helper?.release()
        }
        helper = null
        connected = false
    }

    private companion object {
        const val TAG = "MoTixNavi"
        const val TIMEOUT_MS = 20_000L
    }
}
