import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(gradleLibs.plugins.android.application) apply false
    alias(gradleLibs.plugins.android.library) apply false
    alias(gradleLibs.plugins.compose.compiler) apply false
    alias(gradleLibs.plugins.google.ksp) apply false
    alias(gradleLibs.plugins.kotlin.android) apply false
    alias(gradleLibs.plugins.kotlin.jvm) apply false
    alias(gradleLibs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.stability.analyzer) apply false
    alias(gradleLibs.plugins.versions)
}

subprojects {
    // --- 统一所有纯 JVM(Java) 模块的 Java 编译版本 ---
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    // --- 统一所有 Kotlin JVM 模块的 Kotlin toolchain ---
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }
    }

    // --- Android Kotlin 也显式用 toolchain ---
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(17)
        }
    }
}