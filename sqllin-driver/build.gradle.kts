import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

val GROUP_ID: String by project
val VERSION: String by project

group = GROUP_ID
version = VERSION

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    android {
        namespace = "com.ctrip.sqllin.driver"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
        minSdk = libs.versions.android.sdk.min.get().toInt()
        withDeviceTest {
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
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xcontext-parameters")
    }
    
    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.annotation)
        }
        getByName("androidDeviceTest") {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.rules)
            }
        }
        jvmMain.dependencies {
            implementation(libs.sqlite.jdbc)
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
    val main by compilations.getting
    val sqlite3 by main.cinterops.creating {
        includeDirs("$projectDir/src/include")
    }
    binaries.all {
        linkerOpts += when {
            HostManager.hostIsLinux -> listOf("-lsqlite3", "-L$rootDir/libs/linux", "-L/usr/lib/x86_64-linux-gnu", "-L/usr/lib", "-L/usr/lib64")
            HostManager.hostIsMingw -> listOf("-Lc:\\msys64\\mingw64\\lib", "-L$rootDir\\libs\\windows", "-lsqlite3")
            else -> listOf("-lsqlite3")
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    val artifactId = "sqllin-driver"
    coordinates(
        groupId = GROUP_ID,
        artifactId = artifactId,
        version = VERSION,
    )

    pom {
        name.set(artifactId)
        description.set("Low-level API for SQLite on Kotlin Multiplatform")
        val githubURL: String by project
        url.set(githubURL)
        licenses {
            license {
                val licenseName: String by project
                name.set(licenseName)
                val licenseURL: String by project
                url.set(licenseURL)
            }
        }
        developers {
            developer {
                val developerID: String by project
                id.set(developerID)
                val developerName: String by project
                name.set(developerName)
                val developerEmail: String by project
                email.set(developerEmail)
            }
        }
        scm {
            url.set(githubURL)
            val scmURL: String by project
            connection.set(scmURL)
            developerConnection.set(scmURL)
        }
    }
}