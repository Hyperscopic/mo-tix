package com.hyperscopic.motix

import android.app.Application
import com.hyperscopic.motix.platform.CarLocation
import com.hyperscopic.motix.platform.CarNavigator
import com.hyperscopic.motix.platform.DrivingMonitor

/** The car-facing services live as long as the process: one connection each to the Car API, location and navigation. */
class MoTixApp : Application() {
    lateinit var driving: DrivingMonitor
        private set
    lateinit var location: CarLocation
        private set
    lateinit var navigator: CarNavigator
        private set

    override fun onCreate() {
        super.onCreate()
        driving = DrivingMonitor(this)
        location = CarLocation(this)
        navigator = CarNavigator(this)
    }
}
