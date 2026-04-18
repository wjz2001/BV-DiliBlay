@file:Suppress("UnstableApiUsage")
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.name) {
                "crashlytics" -> useModule("com.google.firebase:firebase-crashlytics-gradle:2.9.9")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
    }
    versionCatalogs {
        create("androidx") { from(files("gradle/androidx.versions.toml")) }
        create("gradleLibs") { from(files("gradle/gradle.versions.toml")) }
    }
}
rootProject.name = "BV"
include(":app")
include(":bili-api")
include(":bili-subtitle")
include(":bv-player")
include(":libs:av1Decoder")
include(":libs:ffmpegDecoder")
include(":libs:libVLC")
include(":bili-api-grpc")
