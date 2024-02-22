package org.justalk.kotlin.stdlib.location.orientationupdates

import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.LocationServices

class OrientationUpdates(
    context: Context,
    samplingPeriodMicros: Long = DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT,
    private val lifecycleOwner: LifecycleOwner = ProcessLifecycleOwner.get(),
    onUpdate: (degrees: Float) -> Unit
) {

    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private val orientationListener = DeviceOrientationListener {
        onUpdate(it.headingDegrees)
    }

    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) {
            locationClient.requestDeviceOrientationUpdates(
                DeviceOrientationRequest.Builder(samplingPeriodMicros).build(),
                orientationListener,
                Looper.getMainLooper()
            )
        } else if (event == Lifecycle.Event.ON_STOP) {
            locationClient.removeDeviceOrientationUpdates(orientationListener)
        }
    }

    fun request() {
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    fun dispose() {
        locationClient.removeDeviceOrientationUpdates(orientationListener)
        lifecycleOwner.lifecycle.removeObserver(observer)
    }

}