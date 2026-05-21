package org.enchant.core.store

/**
 * Versioned application migration system for the EnchantStore.
 *
 * Each migration is identified by a version number and is executed exactly once.
 * Migrations run during [EnchantStore.init] after the store is initialized.
 *
 * Usage:
 * ```
 * ApplicationMigrations.register(1, "migrate_theme_to_v2") { store ->
 *     val oldTheme = store.getString("settings.theme")
 *     if (oldTheme == "light") store.putString("settings.theme", "light_v2")
 * }
 * ```
 *
 * Migrations are tracked by key: `migration.applied.<version>`
 */
object ApplicationMigrations {

    private val migrations = mutableMapOf<Int, Migration>()

    data class Migration(
        val version: Int,
        val name: String,
        val block: (KeyValueStorage) -> Unit
    )

    fun register(version: Int, name: String, block: (KeyValueStorage) -> Unit) {
        migrations[version] = Migration(version, name, block)
    }

    fun execute(store: KeyValueStorage) {
        val sorted = migrations.keys.sorted()
        for (version in sorted) {
            val migrationKey = "migration.applied.$version"
            if (store.getBoolean(migrationKey, false)) continue

            val migration = migrations[version] ?: continue
            try {
                migration.block(store)
                store.putBoolean(migrationKey, true)
            } catch (e: Exception) {
                android.util.Log.e("AppMigrations", "Migration ${migration.name} (v${migration.version}) failed", e)
            }
        }
    }

    fun getLastAppliedVersion(store: KeyValueStorage): Int {
        val sorted = migrations.keys.sortedDescending()
        for (version in sorted) {
            if (store.getBoolean("migration.applied.$version", false)) {
                return version
            }
        }
        return 0
    }

    fun getPendingMigrations(store: KeyValueStorage): List<Migration> {
        return migrations.values.filter { migration ->
            !store.getBoolean("migration.applied.${migration.version}", false)
        }
    }
}
