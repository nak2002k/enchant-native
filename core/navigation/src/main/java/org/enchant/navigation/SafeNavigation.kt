@file:JvmName("SafeNavigation")

package org.enchant.navigation

import android.content.res.Resources
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import android.util.Log

private const val TAG = "SafeNavigation"

fun NavController.safeNavigate(@IdRes resId: Int) {
    if (currentDestination?.getAction(resId) != null) {
        navigate(resId)
    } else {
        Log.w(TAG, "No action $resId for destination")
    }
}

fun NavController.safeNavigate(@IdRes resId: Int, arguments: Bundle?) {
    if (currentDestination?.getAction(resId) != null) {
        navigate(resId, arguments)
    } else {
        Log.w(TAG, "No action $resId for destination")
    }
}

fun NavController.safeNavigate(directions: NavDirections) {
    if (currentDestination?.getAction(directions.actionId) != null) {
        navigate(directions)
    } else {
        Log.w(TAG, "No ${getDisplayName(directions.actionId)} for destination")
    }
}

fun NavController.safeNavigate(directions: NavDirections, navOptions: NavOptions?) {
    if (currentDestination?.getAction(directions.actionId) != null) {
        navigate(directions, navOptions)
    } else {
        Log.w(TAG, "No ${getDisplayName(directions.actionId)} for destination")
    }
}

private fun getDisplayName(id: Int): String? {
    return if (id <= 0x00FFFFFF) {
        id.toString()
    } else {
        try {
            Resources.getSystem().getResourceName(id)
        } catch (e: Resources.NotFoundException) {
            id.toString()
        }
    }
}