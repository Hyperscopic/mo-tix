package com.hyperscopic.motix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hyperscopic.motix.platform.CarLocation
import com.hyperscopic.motix.ui.MoTixRoot

/**
 * Mo-Tix's one activity. It connects the car-facing services (driving restrictions, position, navigation) and hands
 * the window to Compose; everything else is in [MoTixViewModel] and ui/.
 */
class MainActivity : ComponentActivity() {
    private val app get() = application as MoTixApp
    private val vm: MoTixViewModel by viewModels()

    private var locationGranted by mutableStateOf(false)
    private var locationDenied by mutableStateOf(false)

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermission()
        locationDenied = !locationGranted
        if (locationGranted) app.location.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MoTix) // the splash theme hands over to the app theme
        super.onCreate(savedInstanceState)
        app.driving.start()
        app.navigator.connect()
        refreshPermission()
        setContent {
            MoTixRoot(vm, locationGranted, locationDenied, onAskLocation = { askLocation.launch(CarLocation.PERMISSIONS) })
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPermission()
        // The position is only followed while Mo-Tix is on screen.
        if (locationGranted) app.location.start()
    }

    override fun onStop() {
        app.location.stop()
        super.onStop()
    }

    private fun refreshPermission() {
        locationGranted = app.location.fusedPermission || app.location.androidPermission
    }
}
