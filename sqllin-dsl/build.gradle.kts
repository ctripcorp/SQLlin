import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.vanniktech.maven.publish)
}

val GROUP_ID = project.property("GROUP_ID") as String
val VERSION = project.property("VERSION") as String

group = GROUP_ID
version = VERSION

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    android {
        namespace = "com.ctrip.sqllin.dsl"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
        minSdk = libs.versions.android.sdk.min.get().toInt()
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }

    iosArm64()
    iosSimulatorArm64()

    macosArm64()

    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()

    tvosArm64()
    tvosSimulatorArm64()

    linuxX64()
    linuxArm64()

    mingwX64()

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
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
        val githubURL = project.property("githubURL") as String
        url.set(githubURL)
        licenses {
            license {
                val licenseName = project.property("licenseName") as String
                name.set(licenseName)
                val licenseURL = project.property("licenseURL") as String
                url.set(licenseURL)
            }
        }
        developers {
            developer {
                val developerID = project.property("developerID") as String
                id.set(developerID)
                val developerName = project.property("developerName") as String
                name.set(developerName)
                val developerEmail = project.property("developerEmail") as String
                email.set(developerEmail)
            }
        }
        scm {
            url.set(githubURL)
            val scmURL = project.property("scmURL") as String
            connection.set(scmURL)
            developerConnection.set(scmURL)
        }
    }
}