package org.justalk.kotlin.stdlib.location.useractivityrecog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import com.google.android.gms.location.ActivityTransitionResult
import org.justalk.kotlin.stdlib.location.useractivityrecog.UserActivityTransitionManager.Companion.getActivityType
import org.justalk.kotlin.stdlib.location.useractivityrecog.UserActivityTransitionManager.Companion.getTransitionType

@RestrictTo(RestrictTo.Scope.LIBRARY)
class UserActivityBroadcastReceiver(
    private val systemEvent: (userActivity: String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val result = intent?.let { ActivityTransitionResult.extractResult(it) } ?: return
        var resultStr = ""
        for (event in result.transitionEvents) {
            resultStr += getActivityType(event.activityType) +
                "-${getTransitionType(event.transitionType)}"
        }
        systemEvent(resultStr)
    }

}