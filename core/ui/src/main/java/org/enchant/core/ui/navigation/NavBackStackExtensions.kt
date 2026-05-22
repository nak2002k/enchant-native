package org.enchant.core.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

fun NavBackStack<NavKey>.navigateOrPopTo(key: NavKey) {
    if (contains(key)) {
        popTo(key)
    } else {
        add(key)
    }
}

fun NavBackStack<NavKey>.safePop(): NavKey? {
    return if (isNotEmpty()) {
        val last = get(size - 1)
        removeAt(size - 1)
        last
    } else null
}

private fun NavBackStack<NavKey>.popTo(key: NavKey) {
    while (size > 1 && get(size - 1) != key) {
        removeAt(size - 1)
    }
}