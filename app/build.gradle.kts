@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(gradleLibs.plugins.android.application)
    alias(gradleLibs.plugins.compose.compiler)
    alias(gradleLibs.plugins.google.ksp)
    alias(gradleLibs.plugins.google.services) apply false
    alias(gradleLibs.plugins.kotlin.android)
    alias(gradleLibs.plugins.kotlin.serialization)
}

if (AppConfiguration.googleServicesAvailable) {
    apply(plugin = gradleLibs.plugins.google.services.get().pluginId)
}


val signingProp = file(project.rootProject.file("signing.properties"))

val taskLine = gradle.startParameter.taskNames.joinToString(" ").lowercase()
val isReleasePackaging =
    taskLine.contains("release") && (taskLine.contains("bundle") || taskLine.contains("assemble"))

// Release 出包：reserve（落盘 +1）；其他：peek（不落盘）
val vc = if (isReleasePackaging) {
    AppConfiguration.reserveVersionCode()
} else {
    AppConfiguration.peekVersionCode()
}
val versionName = AppConfiguration.buildVersionName(vc)

android {
    signingConfigs {
        if (signingProp.exists()) {
            val properties = Properties().apply {
                load(FileInputStream(signingProp))
            }
            getByName("debug") {
                storeFile = rootProject.file(properties.getProperty("debugStoreFile"))
                keyAlias = properties.getProperty("debugKeyAlias")
                storePassword = properties.getProperty("debugStorePassword")
                keyPassword = properties.getProperty("debugKeyPassword")
            }
            create("release") {
                storeFile = rootProject.file(properties.getProperty("releaseStoreFile"))
                keyAlias = properties.getProperty("releaseKeyAlias")
                storePassword = properties.getProperty("releaseStorePassword")
                keyPassword = properties.getProperty("releaseKeyPassword")
            }
        }
    }

    namespace = AppConfiguration.APP_ID
    compileSdk = AppConfiguration.COMPILE_SDK

    defaultConfig {
        applicationId = AppConfiguration.APPLICATION_ID
        minSdk = AppConfiguration.MIN_SDK
        targetSdk = AppConfiguration.TARGET_SDK
        versionCode = vc
        versionName = AppConfiguration.buildVersionName(vc)
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    flavorDimensions += "channel"

    productFlavors {
        create("public") {
            dimension = "channel"
            buildConfigField("boolean", "IS_PRIVATE", "false")
        }
        create("private") {
            dimension = "channel"
            buildConfigField("boolean", "IS_PRIVATE", "true")
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // signingConfig = signingConfigs.getByName("release")
            if (signingProp.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".debug"
            // signingConfig = signingConfigs.getByName("debug")
            if (signingProp.exists()) signingConfig = signingConfigs.getByName("debug")
        }
        create("r8Test") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".r8test"
            if (signingProp.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    // https://issuetracker.google.com/issues/260059413
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.configureEach {
        val variant = this
        outputs.configureEach {
            (this as ApkVariantOutputImpl).apply {
                outputFileName =
                    "DiliBlay-$versionName-${variant.buildType.name}.${variant.flavorName}.APK"
                versionNameOverride = "${variant.versionName}.${variant.buildType.name}"
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-XXLanguage:+BreakContinueInInlineLambdas")
    }
}

tasks.register("assembleAllChannelsDebug") {
    group = "build"
    dependsOn("assemblePublicDebug", "assemblePrivateDebug")
}

tasks.register("assembleAllChannelsRelease") {
    group = "build"
    dependsOn("assemblePublicRelease", "assemblePrivateRelease")
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_build_reports")
    stabilityConfigurationFiles.addAll(
        layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.ui.text)
    implementation(libs.runtime)
    annotationProcessor(androidx.room.compiler)
    ksp(androidx.room.compiler)
    ksp(libs.koin.ksp.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(androidx.activity.compose)
    implementation(androidx.core.ktx)
    implementation(androidx.core.splashscreen)
    implementation(androidx.compose.constraintlayout)
    implementation(platform(androidx.compose.bom))
    androidTestImplementation(platform(androidx.compose.bom))
    implementation(androidx.compose.ui)
    implementation(androidx.compose.ui.util)
    implementation(androidx.compose.ui.tooling.preview)
    implementation(androidx.compose.material.icons)
    implementation(androidx.compose.material3)
    implementation(androidx.compose.tv.foundation)
    implementation(androidx.compose.tv.material)
    implementation(androidx.datastore.typed)
    implementation(androidx.datastore.preferences)
    implementation(androidx.lifecycle.runtime.ktx)
    implementation(androidx.media3.common)
    implementation(androidx.media3.decoder)
    implementation(androidx.media3.exoplayer)
    implementation(androidx.media3.ui)
    implementation(androidx.room.ktx)
    implementation(androidx.room.runtime)
    implementation(androidx.webkit)
    implementation(libs.akdanmaku)
    implementation(libs.androidSvg)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.geetest.sensebot)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.annotations)
    implementation(libs.kotlinx.serialization)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.serialization.kotlinx)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.logging)
    implementation(libs.lottie) { exclude(group = "androidx.compose", module = "compose-bom") }
    implementation(libs.material)
    implementation(libs.qrcode)
    implementation(libs.rememberPreference)
    implementation(libs.slf4j.android.mvysny)
    implementation(project(mapOf("path" to ":bili-api")))
    implementation(project(mapOf("path" to ":bili-subtitle")))
    implementation(project(mapOf("path" to ":bv-player")))
    testImplementation(androidx.room.testing)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(androidx.compose.ui.test.junit4)
    debugImplementation(androidx.compose.ui.test.manifest)
    debugImplementation(androidx.compose.ui.tooling)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
