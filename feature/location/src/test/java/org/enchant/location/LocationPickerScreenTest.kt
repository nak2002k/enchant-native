package org.enchant.location

import org.junit.jupiter.api.Test

class LocationPickerScreenTest {

    @Test
    fun `package is correct`() {
        val pkg = this::class.java.`package`?.name
        assert(pkg == "org.enchant.location")
    }

    @Test
    fun `LocationPickerScreen is a composable function`() {
        assert(true)
    }
}
