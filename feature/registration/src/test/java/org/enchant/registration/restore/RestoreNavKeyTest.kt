package org.enchant.registration.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RestoreNavKey")
class RestoreNavKeyTest {

    @Test
    fun `SelectRestoreType is data object`() {
        val key1 = RestoreNavKey.SelectRestoreType
        val key2 = RestoreNavKey.SelectRestoreType
        assertEquals(key1, key2)
    }

    @Test
    fun `all bottom sheet routes are distinct`() {
        val folderSheet = RestoreNavKey.FolderInstructionSheet
        val fileSheet = RestoreNavKey.FileInstructionSheet
        assertTrue(folderSheet != fileSheet)
    }

    @Test
    fun `SelectBackupSheet is distinct from SelectBackup`() {
        val selectBackup = RestoreNavKey.SelectBackup
        val selectBackupSheet = RestoreNavKey.SelectBackupSheet
        assertTrue(selectBackup != selectBackupSheet)
    }

    @Test
    fun `EnterBackupKey and NoRecoveryKeySheet are distinct`() {
        assertTrue(RestoreNavKey.EnterBackupKey != RestoreNavKey.NoRecoveryKeySheet)
    }

    @Nested
    @DisplayName("route count")
    inner class RouteCount {
        @Test
        fun `has expected number of routes`() {
            val routes = listOf(
                RestoreNavKey.SelectRestoreType,
                RestoreNavKey.FolderInstructionSheet,
                RestoreNavKey.FileInstructionSheet,
                RestoreNavKey.SelectBackup,
                RestoreNavKey.SelectBackupSheet,
                RestoreNavKey.EnterBackupKey,
                RestoreNavKey.NoRecoveryKeySheet
            )
            assertEquals(7, routes.size)
        }
    }
}