package org.enchant.core.jobmanager.constraints

import org.enchant.core.jobmanager.Constraint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConstraintTest {
    @Test
    fun `RegisteredConstraint is always met`() {
        val constraint = RegisteredConstraint()
        assertEquals("RegisteredConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `RegisteredConstraint Factory creates instance`() {
        val constraint = RegisteredConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("RegisteredConstraint", constraint.factoryKey)
    }

    @Test
    fun `VeilSenderConstraint is always met`() {
        val constraint = VeilSenderConstraint()
        assertEquals("VeilSenderConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `VeilSenderConstraint Factory creates instance`() {
        val constraint = VeilSenderConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("VeilSenderConstraint", constraint.factoryKey)
    }

    @Test
    fun `ChangeNumberConstraint is always met`() {
        val constraint = ChangeNumberConstraint()
        assertEquals("ChangeNumberConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `ChangeNumberConstraint Factory creates instance`() {
        val constraint = ChangeNumberConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("ChangeNumberConstraint", constraint.factoryKey)
    }

    @Test
    fun `DataRestoreConstraint is always met`() {
        val constraint = DataRestoreConstraint()
        assertEquals("DataRestoreConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `DataRestoreConstraint Factory creates instance`() {
        val constraint = DataRestoreConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("DataRestoreConstraint", constraint.factoryKey)
    }

    @Test
    fun `RestoreAttachmentConstraint is always met`() {
        val constraint = RestoreAttachmentConstraint()
        assertEquals("RestoreAttachmentConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `RestoreAttachmentConstraint Factory creates instance`() {
        val constraint = RestoreAttachmentConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("RestoreAttachmentConstraint", constraint.factoryKey)
    }

    @Test
    fun `BackupMessagesConstraint is always met`() {
        val constraint = BackupMessagesConstraint()
        assertEquals("BackupMessagesConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `BackupMessagesConstraint Factory creates instance`() {
        val constraint = BackupMessagesConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("BackupMessagesConstraint", constraint.factoryKey)
    }

    @Test
    fun `DecryptionsDrainedConstraint is always met`() {
        val constraint = DecryptionsDrainedConstraint()
        assertEquals("DecryptionsDrainedConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `DecryptionsDrainedConstraint Factory creates instance`() {
        val constraint = DecryptionsDrainedConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("DecryptionsDrainedConstraint", constraint.factoryKey)
    }

    @Test
    fun `StickersNotDownloadingConstraint is always met`() {
        val constraint = StickersNotDownloadingConstraint()
        assertEquals("StickersNotDownloadingConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `StickersNotDownloadingConstraint Factory creates instance`() {
        val constraint = StickersNotDownloadingConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("StickersNotDownloadingConstraint", constraint.factoryKey)
    }

    @Test
    fun `AutoDownloadEmojiConstraint is always met`() {
        val constraint = AutoDownloadEmojiConstraint()
        assertEquals("AutoDownloadEmojiConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `AutoDownloadEmojiConstraint Factory creates instance`() {
        val constraint = AutoDownloadEmojiConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("AutoDownloadEmojiConstraint", constraint.factoryKey)
    }

    @Test
    fun `DeletionNotAwaitingMediaDownloadConstraint is always met`() {
        val constraint = DeletionNotAwaitingMediaDownloadConstraint()
        assertEquals("DeletionNotAwaitingMediaDownloadConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `DeletionNotAwaitingMediaDownloadConstraint Factory creates instance`() {
        val constraint = DeletionNotAwaitingMediaDownloadConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("DeletionNotAwaitingMediaDownloadConstraint", constraint.factoryKey)
    }

    @Test
    fun `NoRemoteArchiveGarbageCollectionPendingConstraint is always met`() {
        val constraint = NoRemoteArchiveGarbageCollectionPendingConstraint()
        assertEquals("NoRemoteArchiveGarbageCollectionPendingConstraint", constraint.factoryKey)
        assertTrue(constraint.isMet())
    }

    @Test
    fun `NoRemoteArchiveGarbageCollectionPendingConstraint Factory creates instance`() {
        val constraint = NoRemoteArchiveGarbageCollectionPendingConstraint.Factory.create()
        assertNotNull(constraint)
        assertEquals("NoRemoteArchiveGarbageCollectionPendingConstraint", constraint.factoryKey)
    }

    @Test
    fun `all domain constraint factory keys are unique`() {
        val keys = listOf(
            RegisteredConstraint().factoryKey,
            VeilSenderConstraint().factoryKey,
            ChangeNumberConstraint().factoryKey,
            DataRestoreConstraint().factoryKey,
            RestoreAttachmentConstraint().factoryKey,
            BackupMessagesConstraint().factoryKey,
            DecryptionsDrainedConstraint().factoryKey,
            StickersNotDownloadingConstraint().factoryKey,
            AutoDownloadEmojiConstraint().factoryKey,
            DeletionNotAwaitingMediaDownloadConstraint().factoryKey,
            NoRemoteArchiveGarbageCollectionPendingConstraint().factoryKey
        )
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `all domain constraints implement Constraint interface`() {
        val constraints: List<Constraint> = listOf(
            RegisteredConstraint(),
            VeilSenderConstraint(),
            ChangeNumberConstraint(),
            DataRestoreConstraint(),
            RestoreAttachmentConstraint(),
            BackupMessagesConstraint(),
            DecryptionsDrainedConstraint(),
            StickersNotDownloadingConstraint(),
            AutoDownloadEmojiConstraint(),
            DeletionNotAwaitingMediaDownloadConstraint(),
            NoRemoteArchiveGarbageCollectionPendingConstraint()
        )
        assertEquals(11, constraints.size)
        constraints.forEach { assertTrue(it.isMet()) }
    }
}
