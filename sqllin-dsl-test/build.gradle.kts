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
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            managedDevices {
                localDevices {
                    create("pixelCI") {
                        device = "Pixel 6"
                        apiLevel = 36
                        systemImageSource = "aosp-atd"
                        testedAbi = "arm64-v8a"
                    }
                }
            }
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
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.rules)
        }
    }
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
                HostManager.hostIsMingw -> listOf("-Lc:\\msys64\\mingw64\\lib", "-L$rootDir\\libs\\windows", "-lsqlite3")
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
        withType<KotlinCompilationTask<*>> {
            if (name != "kspCommonMainKotlinMetadata")
                dependsOn("kspCommonMainKotlinMetadata")
        }
    }
}