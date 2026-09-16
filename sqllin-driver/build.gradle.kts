import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
        namespace = "com.ctrip.sqllin.driver"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
        minSdk = libs.versions.android.sdk.min.get().toInt()
        withHostTest {
            isIncludeAndroidResources = true
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
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }
        jvmMain.dependencies {
            implementation(libs.sqlite.jdbc)
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