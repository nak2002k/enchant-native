package org.enchant.core.jobmanager.constraints

import android.content.Context
import org.enchant.core.jobmanager.Constraint
import org.enchant.core.jobmanager.NetworkConstraint

object ConstraintRegistry {
    private val factories = mutableMapOf<String, Constraint.Factory<out Constraint>>()
    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        NetworkConstraint.Factory.initialize(context)
        WifiConstraint.Factory.initialize(context)
        ChargingConstraint.Factory.initialize(context)
        NetworkOrCellServiceConstraint.Factory.initialize(context)

        factories.clear()
        factories["NetworkConstraint"] = NetworkConstraint.Factory
        factories["WifiConstraint"] = WifiConstraint.Factory
        factories["ChargingConstraint"] = ChargingConstraint.Factory
        factories["BatteryNotLowConstraint"] = BatteryNotLowConstraint.Factory
        factories["NotInCallConstraint"] = NotInCallConstraint.Factory
        factories["RegisteredConstraint"] = RegisteredConstraint.Factory
        factories["DiskSpaceNotLowConstraint"] = DiskSpaceNotLowConstraint.Factory
        factories["SealedSenderConstraint"] = SealedSenderConstraint.Factory
        factories["NetworkOrCellServiceConstraint"] = NetworkOrCellServiceConstraint.Factory
        factories["ChangeNumberConstraint"] = ChangeNumberConstraint.Factory
        factories["DataRestoreConstraint"] = DataRestoreConstraint.Factory
        factories["RestoreAttachmentConstraint"] = RestoreAttachmentConstraint.Factory
        factories["BackupMessagesConstraint"] = BackupMessagesConstraint.Factory
        factories["DecryptionsDrainedConstraint"] = DecryptionsDrainedConstraint.Factory
        factories["StickersNotDownloadingConstraint"] = StickersNotDownloadingConstraint.Factory
        factories["AutoDownloadEmojiConstraint"] = AutoDownloadEmojiConstraint.Factory
        factories["DeletionNotAwaitingMediaDownloadConstraint"] = DeletionNotAwaitingMediaDownloadConstraint.Factory
        factories["NoRemoteArchiveGarbageCollectionPendingConstraint"] = NoRemoteArchiveGarbageCollectionPendingConstraint.Factory
        initialized = true
    }

    fun getAllFactories(): Map<String, Constraint.Factory<out Constraint>> {
        ensureInitialized()
        return factories.toMap()
    }

    fun getFactory(key: String): Constraint.Factory<out Constraint>? {
        ensureInitialized()
        return factories[key]
    }

    fun getAllKeys(): List<String> {
        ensureInitialized()
        return factories.keys.toList()
    }

    private fun ensureInitialized() {
        if (!initialized) {
            throw IllegalStateException("ConstraintRegistry not initialized. Call initialize() first.")
        }
    }
}
