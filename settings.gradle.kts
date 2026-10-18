pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ID4MediaHub"
include(":app")

// Separate compact car-first APK: excludes every film/TV/VLC/MapLibre class.
include(":evlite")
