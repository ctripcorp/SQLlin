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
    android {
        namespace = "com.ctrip.sqllin.sample"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
        minSdk = libs.versions.android.sdk.min.get().toInt()
    }
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xcontext-parameters")
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

dependencies {
    add("kspCommonMainMetadata", project(":sqllin-processor"))
}

afterEvaluate { // WORKAROUND: both register() and named() fail – https://github.com/gradle/gradle/issues/9331
    tasks {
        matching { (it is KotlinCompilationTask<*> || it.name.startsWith("ksp")) && it.name != "kspCommonMainKotlinMetadata" }
            .configureEach { dependsOn("kspCommonMainKotlinMetadata") }
    }
}
