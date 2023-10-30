import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.android.library")
    id("maven-publish")
    signing
}

val GROUP: String by project
val VERSION: String by project

group = GROUP
version = VERSION

kotlin {
    explicitApi()
    androidTarget {
        publishLibraryVariants("release")
    }

    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
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

        mingwX64(),
    ).forEach {
        it.setupNativeConfig()
    }
    
    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }
        val commonMain by getting {
            dependencies {
                api(project(":sqllin-driver"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1")
                val coroutinesVersion: String by project
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.annotation:annotation:1.7.0")
            }
        }
        val androidInstrumentedTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation("androidx.test:core:1.5.0")
                implementation("androidx.test:runner:1.5.2")
                implementation("androidx.test:rules:1.5.0")
            }
        }

        val jvmMain by getting

        val jvmTest by getting

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val macosX64Main by getting
        val macosArm64Main by getting

        val watchosX64Main by getting
        val watchosArm32Main by getting
        val watchosArm64Main by getting
        val watchosSimulatorArm64Main by getting
        val watchosDeviceArm64Main by getting

        val tvosX64Main by getting
        val tvosArm64Main by getting
        val tvosSimulatorArm64Main by getting

        val linuxX64Main by getting

        val mingwX64Main by getting

        val nativeMain by creating {
            dependsOn(commonMain)

            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)

            macosX64Main.dependsOn(this)
            macosArm64Main.dependsOn(this)

            watchosX64Main.dependsOn(this)
            watchosArm32Main.dependsOn(this)
            watchosArm64Main.dependsOn(this)
            watchosSimulatorArm64Main.dependsOn(this)
            watchosDeviceArm64Main.dependsOn(this)

            tvosX64Main.dependsOn(this)
            tvosArm64Main.dependsOn(this)
            tvosSimulatorArm64Main.dependsOn(this)

            linuxX64Main.dependsOn(this)

            mingwX64Main.dependsOn(this)
        }

        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting

        val macosX64Test by getting
        val macosArm64Test by getting

        val watchosX64Test by getting
        val watchosArm32Test by getting
        val watchosArm64Test by getting
        val watchosSimulatorArm64Test by getting
        val watchosDeviceArm64Test by getting

        val tvosX64Test by getting
        val tvosArm64Test by getting
        val tvosSimulatorArm64Test by getting

        val linuxX64Test by getting

        val mingwX64Test by getting

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        val appleTest by creating {
            dependsOn(nativeTest)

            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)

            macosX64Test.dependsOn(this)
            macosArm64Test.dependsOn(this)

            watchosX64Test.dependsOn(this)
            watchosArm32Test.dependsOn(this)
            watchosArm64Test.dependsOn(this)
            watchosSimulatorArm64Test.dependsOn(this)
            watchosDeviceArm64Test.dependsOn(this)

            tvosX64Test.dependsOn(this)
            tvosArm64Test.dependsOn(this)
            tvosSimulatorArm64Test.dependsOn(this)
        }

        val linuxTest by creating {
            dependsOn(nativeTest)

            linuxX64Test.dependsOn(this)
        }

        val mingwTest by creating {
            dependsOn(nativeTest)

            mingwX64Test.dependsOn(this)
        }
    }

    tasks.findByName("publishLinuxX64PublicationToMavenRepository")?.enabled = HostManager.hostIsLinux
    tasks.findByName("publishMingwX64PublicationToMavenRepository")?.enabled = HostManager.hostIsMingw
}

android {
    namespace = "com.ctrip.sqllin.dsl"
    compileSdk = 33
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_17
    }
}

fun KotlinNativeTarget.setupNativeConfig() {
    val compileArgs = listOf("-Xruntime-logs=gc=info")
    compilations["main"].kotlinOptions.freeCompilerArgs += compileArgs
    compilations["test"].kotlinOptions.freeCompilerArgs += compileArgs
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
    val sourceSet = listOf(
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
        "kspWatchosSimulatorArm64Test",

        "kspTvosX64Test",
        "kspTvosArm64Test",
        "kspTvosSimulatorArm64Test",

        "kspLinuxX64Test",

        "kspMingwX64Test",
    )
    sourceSet.forEach {
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