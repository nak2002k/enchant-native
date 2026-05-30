package org.enchant.navigation

import androidx.navigation.NavHostController

fun NavRoute.navigate(controller: NavHostController) {
    controller.navigate(resolvedRoute)
}

fun NavRoute.navigateAndClearStack(controller: NavHostController) {
    controller.navigate(resolvedRoute) {
        popUpTo(0) { inclusive = true }
    }
}


