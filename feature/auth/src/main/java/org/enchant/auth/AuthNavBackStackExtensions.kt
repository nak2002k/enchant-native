package org.enchant.auth

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

internal fun NavBackStack<NavKey>.goToPhoneEntry() {
    add(AuthNavKey.PhoneEntry)
}

internal fun NavBackStack<NavKey>.goToOtpVerify(identifier: String) {
    add(AuthNavKey.OtpVerify(identifier = identifier))
}

internal fun NavBackStack<NavKey>.goToKeyGeneration() {
    add(AuthNavKey.KeyGeneration)
}

internal fun NavBackStack<NavKey>.goToProfileSetup() {
    add(AuthNavKey.ProfileSetup)
}

internal fun NavBackStack<NavKey>.popToPhoneEntry() {
    while (size > 1 && get(size - 1) !is AuthNavKey.PhoneEntry) {
        removeAt(size - 1)
    }
}
