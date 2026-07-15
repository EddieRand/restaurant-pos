pluginManagement {
    repositories {
        google()
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

rootProject.name = "RestaurantPOS"

// Core modules
include(":core:model")
include(":core:config")
include(":core:database")
include(":core:domain")
include(":core:hardware")
include(":core:sync")
include(":core:designsystem")
include(":core:network")

// Feature modules
include(":feature:order")
include(":feature:tables")
include(":feature:checkout")
include(":feature:kds")
include(":feature:report")
include(":feature:settings")
include(":feature:auth")
include(":feature:menu")
include(":feature:crm")

// Server module
include(":server")

// App modules
include(":app:cashier")
include(":app:handheld")
include(":app:kds")
include(":app:kiosk")
include(":app:pad")
include(":app:pickup-display")
