package org.justalk.kotlin.stdlib.location.locationupdates

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY

class LocationUpdates(
    context: Context,
    locationRequest: LocationRequest =
        LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 10_000).build(),
    private val lifecycleOwner: LifecycleOwner = ProcessLifecycleOwner.get(),
    onUpdate: (result: LocationResult) -> Unit
) {

    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            onUpdate(result)
        }
    }

    @SuppressLint("MissingPermission")
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) {
            locationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper(),
            )
        } else if (event == Lifecycle.Event.ON_STOP) {
            locationClient.removeLocationUpdates(locationCallback)
        }
    }

    fun request() {
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    fun dispose() {
        locationClient.removeLocationUpdates(locationCallback)
        lifecycleOwner.lifecycle.removeObserver(observer)
    }

}