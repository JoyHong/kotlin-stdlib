package org.justalk.kotlin.stdlib.location.useractivityrecog

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

class UserActivityTransitionManager(private val context: Context) {

    private var broadcastReceiver: UserActivityBroadcastReceiver? = null

    @RequiresPermission(anyOf = ["android.permission.ACTIVITY_RECOGNITION", "com.google.android.gms.permission.ACTIVITY_RECOGNITION"])
    fun registerActivityTransitions(systemEvent: (userActivity: String) -> Unit) {
        ContextCompat.registerReceiver(
            context,
            UserActivityBroadcastReceiver(systemEvent).also { broadcastReceiver = it },
            IntentFilter(CUSTOM_INTENT_USER_ACTION),
            RECEIVER_NOT_EXPORTED
        )
        activityClient.requestActivityTransitionUpdates(
            ActivityTransitionRequest(
                activityTransitions
            ), pendingIntent
        )
    }

    @RequiresPermission(anyOf = ["android.permission.ACTIVITY_RECOGNITION", "com.google.android.gms.permission.ACTIVITY_RECOGNITION"])
    fun deregisterActivityTransitions() {
        activityClient.removeActivityUpdates(pendingIntent)
        context.unregisterReceiver(broadcastReceiver)
    }

    private val activityClient = ActivityRecognition.getClient(context)

    // list of activity transitions to be monitored
    private val activityTransitions: List<ActivityTransition> by lazy {
        listOf(
            getUserActivity(
                DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER
            ),
            getUserActivity(
                DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT
            ),
            getUserActivity(
                DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER
            ),
            getUserActivity(
                DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT
            ),
            getUserActivity(
                DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER
            ),
            getUserActivity(
                DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_EXIT
            ),
            getUserActivity(
                DetectedActivity.RUNNING, ActivityTransition.ACTIVITY_TRANSITION_ENTER
            ),
            getUserActivity(
                DetectedActivity.RUNNING, ActivityTransition.ACTIVITY_TRANSITION_EXIT
            ),
        )
    }

    private val pendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            CUSTOM_REQUEST_CODE_USER_ACTION,
            Intent(CUSTOM_INTENT_USER_ACTION).setPackage(context.packageName),
            // Note: must use FLAG_MUTABLE in order for Play Services to add the result data to the
            // intent starting in API level 31. Otherwise the BroadcastReceiver will be started but
            // the Intent will have no data.
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun getUserActivity(detectedActivity: Int, transitionType: Int): ActivityTransition {
        return ActivityTransition.Builder()
            .setActivityType(detectedActivity)
            .setActivityTransition(transitionType)
            .build()
    }

    companion object {
        private const val CUSTOM_INTENT_USER_ACTION = "USER-ACTIVITY-DETECTION-INTENT-ACTION"
        private const val CUSTOM_REQUEST_CODE_USER_ACTION = 1000

        fun getActivityType(int: Int): String {
            return when (int) {
                DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
                DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
                DetectedActivity.ON_FOOT -> "ON_FOOT"
                DetectedActivity.STILL -> "STILL"
                DetectedActivity.UNKNOWN -> "UNKNOWN"
                DetectedActivity.TILTING -> "TILTING"
                DetectedActivity.WALKING -> "WALKING"
                DetectedActivity.RUNNING -> "RUNNING"
                else -> "UNKNOWN"
            }
        }

        fun getTransitionType(int: Int): String {
            return when (int) {
                0 -> "STARTED"
                1 -> "STOPPED"
                else -> ""
            }
        }
    }

}