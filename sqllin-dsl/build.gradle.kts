import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.maven.publish)
    signing
}

val GROUP: String by project
val VERSION: String by project

group = GROUP
version = VERSION

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    explicitApi()
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
        freeCompilerArgs.add("-Xexpect-actual-classes")
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
            implementation(libs.kotlinx.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidInstrumentedTest {
            setCommonTestDir()
            dependencies {
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.rules)
            }
        }
        jvmTest {
            setCommonTestDir()
        }

        iosX64Test {
            setNativeTestDir()
        }
        iosArm64Test {
            setNativeTestDir()
        }
        iosSimulatorArm64Test {
            setNativeTestDir()
        }


        macosX64Test {
            setNativeTestDir()
        }
        macosArm64Test {
            setNativeTestDir()
        }

        watchosX64Test {
            setNativeTestDir()
        }
        watchosArm32Test {
            setNativeTestDir()
        }
        watchosArm64Test {
            setNativeTestDir()
        }
        watchosDeviceArm64Test {
            setNativeTestDir()
        }
        watchosSimulatorArm64Test {
            setNativeTestDir()
        }

        tvosX64Test {
            setNativeTestDir()
        }
        tvosArm64Test {
            setNativeTestDir()
        }
        tvosSimulatorArm64Test {
            setNativeTestDir()
        }

        linuxX64Test {
            setNativeTestDir()
        }
        linuxArm64Test {
            setNativeTestDir()
        }

        mingwX64Test {
            setNativeTestDir()
        }
    }
}

fun KotlinSourceSet.setCommonTestDir(vararg path: String) = kotlin.srcDirs("src/commonTestCode/kotlin", path)
fun KotlinSourceSet.setNativeTestDir() = setCommonTestDir("src/nativeTestCode/kotlin")

gradle.taskGraph.whenReady {
    if (!project.hasProperty("onCICD"))
        return@whenReady
    tasks.forEach {
        when {
            it.name.contains("linux", true) -> {
                it.enabled = HostManager.hostIsLinux
            }
            it.name.contains("mingw", true) -> {
                it.enabled = HostManager.hostIsMingw
            }
        }
    }
}

android {
    namespace = "com.ctrip.sqllin.dsl"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_21
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
    val sourceSets = listOf(
        "kspAndroidAndroidTest",

        "kspJvmTest",

        "kspIosX64Test",
        "kspIosArm64Test",
        "kspIosSimulatorArm64Test",

        "kspMacosX64Test",
        "kspMacosArm64Test",

        "kspWatchosX64Test",
        "kspWatchosArm32Test",
        "kspWatchosArm64Test",
        "kspWatchosDeviceArm64Test",
        "kspWatchosSimulatorArm64Test",

        "kspTvosX64Test",
        "kspTvosArm64Test",
        "kspTvosSimulatorArm64Test",

        "kspLinuxX64Test",
        "kspLinuxArm64Test",

        "kspMingwX64Test",
    )
    sourceSets.forEach {
        add(it, project(":sqllin-processor"))
    }
}

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)
        with(pom) {
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
    repositories {
        maven {
            credentials {
                val NEXUS_USERNAME: String by project
                val NEXUS_PASSWORD: String by project
                username = NEXUS_USERNAME
                password = NEXUS_PASSWORD
            }
            val mavenRepositoryURL: String by project
            url = uri(mavenRepositoryURL)
        }
    }
    signing {
        val SIGNING_KEY_ID: String by project
        val SIGNING_KEY: String by project
        val SIGNING_PASSWORD: String by project
        useInMemoryPgpKeys(SIGNING_KEY_ID, SIGNING_KEY, SIGNING_PASSWORD)
        sign(publishing.publications)
    }
}

// TODO: remove after https://youtrack.jetbrains.com/issue/KT-46466 is fixed
project.tasks.withType(AbstractPublishToMaven::class.java).configureEach {
    dependsOn(project.tasks.withType(Sign::class.java))
}