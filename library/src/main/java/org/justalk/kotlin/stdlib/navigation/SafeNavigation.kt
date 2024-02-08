package org.justalk.kotlin.stdlib.navigation

import android.os.Bundle
import android.util.Log
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions

private const val TAG = "SafeNavigation"

/**
 * Check [currentDestination] has an action with [resId] before attempting to navigate.
 */
fun NavController.safeNavigate(@IdRes resId: Int, arguments: Bundle? = null, navOptions: NavOptions? = null) {
    if (currentDestination?.getAction(resId) != null) {
        navigate(resId, arguments, navOptions)
    } else {
        Log.e(TAG, "Unable to find action ${getDisplayName(resId)} for $currentDestination")
    }
}

/**
 * Check [currentDestination] has an action for [directions] before attempting to navigate.
 */
fun NavController.safeNavigate(directions: NavDirections, arguments: Bundle? = null, navOptions: NavOptions? = null) {
    safeNavigate(directions.actionId, arguments, navOptions)
}

private fun getDisplayName(id: Int): String? {
    return if (id <= 0x00FFFFFF) {
        id.toString()
    } else {
//        try {
//            context.resources.getResourceName(id)
//        } catch (e: Resources.NotFoundException) {
            id.toString()
//        }
    }
}