package org.enchant.core.store

/**
 * Abstract base class for all EnchantStore namespace classes.
 *
 * Each namespace (Account, Settings, Backup, etc.) extends this class and:
 * - Implements [onFirstEverAppLaunch] to set default values
 * - Implements [getKeysToIncludeInBackup] to declare which keys survive backup/restore
 *
 * This mirrors Signal's SignalStoreValues.java pattern.
 */
abstract class EnchantStoreValues(
    protected val store: KeyValueStorage,
    protected val delegates: StoreValueDelegates
) {
    abstract fun onFirstEverAppLaunch()
    abstract fun getKeysToIncludeInBackup(): List<String>
}
