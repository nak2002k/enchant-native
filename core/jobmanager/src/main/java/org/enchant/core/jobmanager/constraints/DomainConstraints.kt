package org.enchant.core.jobmanager.constraints

import org.enchant.core.jobmanager.Constraint

class DataRestoreConstraint : Constraint {
    override val factoryKey = "DataRestoreConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<DataRestoreConstraint> {
        override fun create(): DataRestoreConstraint = DataRestoreConstraint()
    }
}

class RestoreAttachmentConstraint : Constraint {
    override val factoryKey = "RestoreAttachmentConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<RestoreAttachmentConstraint> {
        override fun create(): RestoreAttachmentConstraint = RestoreAttachmentConstraint()
    }
}

class BackupMessagesConstraint : Constraint {
    override val factoryKey = "BackupMessagesConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<BackupMessagesConstraint> {
        override fun create(): BackupMessagesConstraint = BackupMessagesConstraint()
    }
}

class DecryptionsDrainedConstraint : Constraint {
    override val factoryKey = "DecryptionsDrainedConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<DecryptionsDrainedConstraint> {
        override fun create(): DecryptionsDrainedConstraint = DecryptionsDrainedConstraint()
    }
}

class StickersNotDownloadingConstraint : Constraint {
    override val factoryKey = "StickersNotDownloadingConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<StickersNotDownloadingConstraint> {
        override fun create(): StickersNotDownloadingConstraint = StickersNotDownloadingConstraint()
    }
}

class AutoDownloadEmojiConstraint : Constraint {
    override val factoryKey = "AutoDownloadEmojiConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<AutoDownloadEmojiConstraint> {
        override fun create(): AutoDownloadEmojiConstraint = AutoDownloadEmojiConstraint()
    }
}

class DeletionNotAwaitingMediaDownloadConstraint : Constraint {
    override val factoryKey = "DeletionNotAwaitingMediaDownloadConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<DeletionNotAwaitingMediaDownloadConstraint> {
        override fun create(): DeletionNotAwaitingMediaDownloadConstraint = DeletionNotAwaitingMediaDownloadConstraint()
    }
}

class NoRemoteArchiveGarbageCollectionPendingConstraint : Constraint {
    override val factoryKey = "NoRemoteArchiveGarbageCollectionPendingConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<NoRemoteArchiveGarbageCollectionPendingConstraint> {
        override fun create(): NoRemoteArchiveGarbageCollectionPendingConstraint = NoRemoteArchiveGarbageCollectionPendingConstraint()
    }
}
