import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    // 所有 Android Application 模块：Java source/target = 17
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    // 所有 Android Library 模块：Java source/target = 17
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    // 所有 Kotlin 编译任务：jvmTarget = 17
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget("17"))
    }
}