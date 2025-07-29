import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.konan.target.HostManager
import kotlin.collections.plusAssign

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

version = "1.0"

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    androidTarget {
        publishLibraryVariants("release")
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),

        macosX64(),
        macosArm64(),

        watchosArm32(),
        watchosArm64(),
        watchosX64(),
        watchosSimulatorArm64(),
        watchosDeviceArm64(),

        tvosArm64(),
        tvosX64(),
        tvosSimulatorArm64(),

        linuxX64(),
        linuxArm64(),

        mingwX64(),
    ).forEach {
        it.setupNativeConfig()
    }

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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidInstrumentedTest {
            dependencies {
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.rules)
            }
        }
    }
}

android {
    namespace = "com.ctrip.sqllin.dsl.test"
    compileSdk = libs.versions.android.sdk.compile.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.sdk.min.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

fun KotlinNativeTarget.setupNativeConfig() {
    binaries {
        all {
            linkerOpts += when {
                HostManager.hostIsLinux -> listOf("-lsqlite3", "-L$rootDir/libs/linux", "-L/usr/lib/x86_64-linux-gnu", "-L/usr/lib", "-L/usr/lib64")
                HostManager.hostIsMingw -> listOf("-Lc:\\msys64\\mingw64\\lib", "-L$rootDir\\libs\\windows", "-lsqlite3")
                else -> listOf("-lsqlite3")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
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