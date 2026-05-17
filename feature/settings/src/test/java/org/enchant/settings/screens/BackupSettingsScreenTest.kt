package org.enchant.settings.screens

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("BackupSettingsScreen")
class BackupSettingsScreenTest {

    @Test
    @DisplayName("BackupInfo data class holds values")
    fun `backup info data`() {
        val info = BackupInfo(
            backupId = "backup-1",
            version = 2,
            totalSize = 1024 * 1024,
            completedTs = "2026-05-17T10:00:00Z"
        )
        assertEquals("backup-1", info.backupId)
        assertEquals(2, info.version)
        assertEquals(1024 * 1024, info.totalSize)
        assertEquals("2026-05-17T10:00:00Z", info.completedTs)
    }

    @Test
    @DisplayName("BackupInfo with null completedTs")
    fun `backup info null completed ts`() {
        val info = BackupInfo(
            backupId = "backup-2",
            version = 1,
            totalSize = 0L,
            completedTs = null
        )
        assertEquals("backup-2", info.backupId)
        assertEquals(1, info.version)
        assertEquals(0L, info.totalSize)
        assertNull(info.completedTs)
    }

    @Test
    @DisplayName("BackupInfo with zero size")
    fun `backup info zero size`() {
        val info = BackupInfo(
            backupId = "backup-3",
            version = 1,
            totalSize = 0L,
            completedTs = "2026-05-17"
        )
        assertEquals(0L, info.totalSize)
    }

    @Test
    @DisplayName("BackupInfo equality works")
    fun `backup info equality`() {
        val info1 = BackupInfo("backup-1", 2, 1024L, "2026-05-17")
        val info2 = BackupInfo("backup-1", 2, 1024L, "2026-05-17")
        assertEquals(info1, info2)
    }

    @Test
    @DisplayName("BackupInfo with different backupIds are not equal")
    fun `backup info inequality`() {
        val info1 = BackupInfo("backup-1", 2, 1024L, "2026-05-17")
        val info2 = BackupInfo("backup-2", 2, 1024L, "2026-05-17")
        assertNotEquals(info1, info2)
    }
}
