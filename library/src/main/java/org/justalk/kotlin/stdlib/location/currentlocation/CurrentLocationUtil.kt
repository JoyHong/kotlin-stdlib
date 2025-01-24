package org.justalk.kotlin.stdlib.location.currentlocation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class CurrentLocationUtil {

    companion object {
        @SuppressLint("MissingPermission")
        suspend fun getCurrentLocation(
            context: Context,
            priority: Int = Priority.PRIORITY_HIGH_ACCURACY
        ): Location? {
            return LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(priority, CancellationTokenSource().token)
                .await()
        }
    }

}