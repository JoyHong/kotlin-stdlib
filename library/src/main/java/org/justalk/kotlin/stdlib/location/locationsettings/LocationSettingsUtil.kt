package org.justalk.kotlin.stdlib.location.locationsettings

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStates
import com.google.android.gms.location.LocationSettingsStatusCodes
import kotlinx.coroutines.tasks.await

class LocationSettingsUtil {

    companion object {

        /**
         * 检查 request 对应的设置状态
         */
        @Throws(ApiException::class)
        suspend fun checkSettings(
            context: Context,
            request: LocationRequest
        ): LocationSettingsStates? {
            val requestBuilder = LocationSettingsRequest.Builder()
                .addLocationRequest(request)
            return LocationServices.getSettingsClient(context)
                .checkLocationSettings(requestBuilder.build())
                .await()
                .locationSettingsStates
        }

        /**
         * 根据 checkSettings 的异常, 打开设置界面
         */
        @Throws(ApiException::class)
        fun startResolutionForException(
            ex: ApiException,
            launcher: ActivityResultLauncher<IntentSenderRequest>
        ) {
            if (ex.statusCode != LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                throw ex
            }
            launcher.launch(IntentSenderRequest.Builder((ex as ResolvableApiException).resolution.intentSender).build())
        }

    }

}