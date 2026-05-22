package org.enchant.registration.olddevice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("TransferNavKey")
class TransferNavKeyTest {

    @Nested
    @DisplayName("Transfer")
    inner class Transfer {
        @Test
        fun `is data object`() {
            val key1 = TransferNavKey.Transfer
            val key2 = TransferNavKey.Transfer
            assertEquals(key1, key2)
        }
    }

    @Nested
    @DisplayName("PrepareDevice")
    inner class PrepareDevice {
        @Test
        fun `is data object`() {
            val key1 = TransferNavKey.PrepareDevice
            val key2 = TransferNavKey.PrepareDevice
            assertEquals(key1, key2)
        }
    }

    @Nested
    @DisplayName("Done")
    inner class Done {
        @Test
        fun `is data object`() {
            val key1 = TransferNavKey.Done
            val key2 = TransferNavKey.Done
            assertEquals(key1, key2)
        }
    }
}

@DisplayName("TransferViewModel")
class TransferViewModelTest {

    @Test
    fun `initial state has Transfer as first screen`() {
        val vm = TransferViewModel()
        assertEquals(listOf(TransferNavKey.Transfer), vm.backStack.value)
    }

    @Test
    fun `TransferClicked adds PrepareDevice to backstack`() {
        val vm = TransferViewModel()
        vm.onEvent(TransferEvent.TransferClicked)
        assertEquals(
            listOf(TransferNavKey.Transfer, TransferNavKey.PrepareDevice),
            vm.backStack.value
        )
    }

    @Test
    fun `SkipAndContinue adds Done to backstack`() {
        val vm = TransferViewModel()
        vm.onEvent(TransferEvent.SkipAndContinue)
        assertEquals(
            listOf(TransferNavKey.Transfer, TransferNavKey.Done),
            vm.backStack.value
        )
    }

    @Test
    fun `goBack removes last entry when backstack size is greater than 1`() {
        val vm = TransferViewModel()
        vm.onEvent(TransferEvent.TransferClicked)
        assertEquals(2, vm.backStack.value.size)
        vm.goBack()
        assertEquals(1, vm.backStack.value.size)
        assertEquals(TransferNavKey.Transfer, vm.backStack.value.first())
    }

    @Test
    fun `goBack does nothing when only one entry`() {
        val vm = TransferViewModel()
        vm.goBack()
        assertEquals(1, vm.backStack.value.size)
        assertEquals(TransferNavKey.Transfer, vm.backStack.value.first())
    }

    @Test
    fun `Retry clears error state`() {
        val vm = TransferViewModel()
        vm.onEvent(TransferEvent.TransferClicked)
        assertFalse(vm.state.value.error != null || vm.state.value.isTransferring)
    }
}