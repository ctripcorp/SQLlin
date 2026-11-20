import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.konan.target.HostManager

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
        namespace = "com.ctrip.sqllin.dsl.test"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
        minSdk = libs.versions.android.sdk.min.get().toInt()
        withHostTest {
            isIncludeAndroidResources = true
        }
        // On an emulator or a device, which the workflow emulator.yml runs
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),

        macosArm64(),

        watchosArm32(),
        watchosArm64(),
        watchosSimulatorArm64(),
        watchosDeviceArm64(),

        tvosArm64(),
        tvosSimulatorArm64(),

        linuxX64(),
        linuxArm64(),

        mingwX64(),
    ).forEach {
        it.setupNativeConfig()
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
    
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":sqllin-dsl"))
                implementation(project(":sqllin-dsl-paging"))
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.coroutines.core)
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.paging.testing)
            }
        }
        // The class of the Android tests is in androidSharedTest, which both the tests on Robolectric and those on a
        // device compile: its runner, AndroidJUnit4, runs it on Robolectric on the JVM
        getByName("androidHostTest") {
            kotlin.srcDir("src/androidSharedTest/kotlin")
            dependencies {
                implementation(libs.junit)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.robolectric)
            }
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/androidSharedTest/kotlin")
            dependencies {
                implementation(libs.junit)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.robolectric.annotations)
            }
        }
    }
}

// Robolectric reflects into JDK internals when setting up newer Android SDKs,
// which the module system blocks by default since JDK 17.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

gradle.taskGraph.whenReady {
    if (!project.hasProperty("onCICD"))
        return@whenReady
    tasks.forEach {
        when {
            it.name.contains("linux", true) -> it.enabled = HostManager.hostIsLinux
            it.name.contains("mingw", true) -> it.enabled = HostManager.hostIsMingw
            it.name.contains("ios", true)
                    || it.name.contains("macos", true)
                    || it.name.contains("watchos", true)
                    || it.name.contains("tvos", true) -> it.enabled = HostManager.hostIsMac
        }
    }
}


fun KotlinNativeTarget.setupNativeConfig() {
    binaries {
        all {
            linkerOpts += when {
                HostManager.hostIsLinux -> listOf("-lsqlite3", "-L$rootDir/libs/linux", "-L/usr/lib/x86_64-linux-gnu", "-L/usr/lib", "-L/usr/lib64")
                HostManager.hostIsMingw -> listOf("-L$rootDir\\libs\\windows", "-Lc:\\msys64\\mingw64\\lib", "-lsqlite3")
                else -> listOf("-lsqlite3")
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
