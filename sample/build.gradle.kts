import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

version = "1.0"

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xcontext-parameters", "-Xnested-type-aliases")
    }
    
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":sqllin-dsl"))
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

android {
    namespace = "com.ctrip.sqllin.sample"
    compileSdk = libs.versions.android.sdk.compile.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.sdk.min.get().toInt()
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":sqllin-processor"))
}

afterEvaluate { // WORKAROUND: both register() and named() fail – https://github.com/gradle/gradle/issues/9331
    tasks {
        withType<KotlinCompilationTask<*>> {
            if (name != "kspCommonMainKotlinMetadata")
                dependsOn("kspCommonMainKotlinMetadata")
        }
    }
}