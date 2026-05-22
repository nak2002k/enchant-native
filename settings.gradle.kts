pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex(".*google.*")
                includeGroupByRegex(".*android.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Enchant"

include(":app")

include(":core:auth")
include(":core:base")
include(":core:network")
include(":core:database")
include(":core:crypto")
include(":core:model")
include(":core:jobmanager")
include(":core:protos")
include(":core:store")
include(":core:notifications")
include(":core:push")
include(":core:calls")
include(":core:navigation")
include(":core:performance")
include(":core:accessibility")
include(":core:crash")
include(":core:config")
include(":core:ui")

include(":feature:auth")
include(":feature:chat")
include(":feature:chat-list")
include(":feature:calls")
include(":feature:groups")
include(":feature:contacts")
include(":feature:status")
include(":feature:channels")
include(":feature:profile")
include(":feature:registration")
include(":feature:settings")
include(":feature:stickers")
include(":feature:polls")
include(":feature:location")
include(":feature:backup")
include(":feature:share")
