import com.android.build.api.dsl.SettingsExtension

include(":hevtunnel")


pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.settings") version "8.12.0"
}

rootProject.name = "amneziawg-android"

include(":tunnel")
include(":ui")
include(":hevtunnel")

configure<SettingsExtension> {
    buildToolsVersion = "35.0.0"
    compileSdk = 35
    minSdk = 24
    ndkVersion = "26.1.10909125"
}
