import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.vanniktech.maven.publish)
}

val GROUP_ID: String by project
val VERSION: String by project

group = GROUP_ID
version = VERSION

kotlin {
    explicitApi()
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

    macosX64()
    macosArm64()

    watchosArm32()
    watchosArm64()
    watchosX64()
    watchosSimulatorArm64()
    watchosDeviceArm64()

    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()

    linuxX64()
    linuxArm64()

    mingwX64()

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xcontext-parameters", "-Xnested-type-aliases")
    }
    
    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }
        commonMain.dependencies {
            api(project(":sqllin-driver"))
            implementation(libs.kotlinx.serialization)
            implementation(libs.kotlinx.coroutines.core)
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

android {
    namespace = "com.ctrip.sqllin.dsl"
    compileSdk = libs.versions.android.sdk.compile.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.sdk.min.get().toInt()
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    val artifactId = "sqllin-dsl"
    coordinates(
        groupId = GROUP_ID,
        artifactId = artifactId,
        version = VERSION,
    )

    pom {
        name.set(artifactId)
        description.set("SQL DSL APIs for SQLite on Kotlin Multiplatform")
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