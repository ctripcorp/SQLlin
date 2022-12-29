import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
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
    android {
        publishLibraryVariants("release")
    }

    listOf(
        iosX64(),
        iosArm32(),
        iosArm64(),
        iosSimulatorArm64(),

        macosX64(),
        macosArm64(),

        watchosArm32(),
        watchosArm64(),
        watchosX86(),
        watchosX64(),
        watchosSimulatorArm64(),

        tvosArm64(),
        tvosX64(),
        tvosSimulatorArm64(),

        linuxX64(),

        mingwX64(),
        mingwX86(),
    ).forEach {
        it.setupNativeConfig()
    }
    
    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                // languageVersion = "1.8"
            }
        }
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.annotation:annotation:1.5.0")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation("androidx.test:core:1.5.0")
                implementation("androidx.test:runner:1.5.1")
                implementation("androidx.test:rules:1.5.0")
            }
        }

        val iosX64Main by getting
        val iosArm32Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val macosX64Main by getting
        val macosArm64Main by getting

        val watchosX86Main by getting
        val watchosX64Main by getting
        val watchosArm32Main by getting
        val watchosArm64Main by getting
        val watchosSimulatorArm64Main by getting

        val tvosX64Main by getting
        val tvosArm64Main by getting
        val tvosSimulatorArm64Main by getting

        val linuxX64Main by getting

        val mingwX64Main by getting
        val mingwX86Main by getting

        val nativeMain by creating {
            dependsOn(commonMain)

            iosX64Main.dependsOn(this)
            iosArm32Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)

            macosX64Main.dependsOn(this)
            macosArm64Main.dependsOn(this)

            watchosX86Main.dependsOn(this)
            watchosX64Main.dependsOn(this)
            watchosArm32Main.dependsOn(this)
            watchosArm64Main.dependsOn(this)
            watchosSimulatorArm64Main.dependsOn(this)

            tvosX64Main.dependsOn(this)
            tvosArm64Main.dependsOn(this)
            tvosSimulatorArm64Main.dependsOn(this)

            linuxX64Main.dependsOn(this)

            mingwX64Main.dependsOn(this)
            mingwX86Main.dependsOn(this)
            dependencies {
                implementation("co.touchlab:sqliter-driver:1.2.1")
            }
        }

        val iosX64Test by getting
        val iosArm32Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting

        val macosX64Test by getting
        val macosArm64Test by getting

        val watchosX86Test by getting
        val watchosX64Test by getting
        val watchosArm32Test by getting
        val watchosArm64Test by getting
        val watchosSimulatorArm64Test by getting

        val tvosX64Test by getting
        val tvosArm64Test by getting
        val tvosSimulatorArm64Test by getting

        val linuxX64Test by getting

        val mingwX64Test by getting
        val mingwX86Test by getting

        val nativeTest by creating {
            dependsOn(commonTest)

            iosX64Test.dependsOn(this)
            iosArm32Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)

            macosX64Test.dependsOn(this)
            macosArm64Test.dependsOn(this)

            watchosX86Test.dependsOn(this)
            watchosX64Test.dependsOn(this)
            watchosArm32Test.dependsOn(this)
            watchosArm64Test.dependsOn(this)
            watchosSimulatorArm64Test.dependsOn(this)

            tvosX64Test.dependsOn(this)
            tvosArm64Test.dependsOn(this)
            tvosSimulatorArm64Test.dependsOn(this)

            linuxX64Test.dependsOn(this)

            mingwX64Test.dependsOn(this)
            mingwX86Test.dependsOn(this)
        }
    }
}

android {
    compileSdk = 33
    buildToolsVersion = "33.0.0"
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets.getByName("androidTest") {
        manifest.srcFile(File("src/androidTest/AndroidManifest.xml"))
        java.srcDir("src/androidTest/kotlin")
    }
    defaultConfig {
        minSdk = 23
        targetSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

fun KotlinNativeTarget.setupNativeConfig() {
    val compileArgs = listOf("-Xruntime-logs=gc=info")
    compilations["main"].kotlinOptions.freeCompilerArgs += compileArgs
    compilations["test"].kotlinOptions.freeCompilerArgs += compileArgs
    binaries {
        all {
            linkerOpts += when {
                HostManager.hostIsLinux -> "-lsqlite3 -L/usr/lib/x86_64-linux-gnu -L/usr/lib"
                HostManager.hostIsMingw -> "-lsqlite3 -Lc:\\msys64\\mingw64\\lib"
                else -> "-lsqlite3"
            }
        }
    }
}

val NEXUS_USERNAME: String by project
val NEXUS_PASSWORD: String by project

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)
        with(pom) {
            name.set("sqllin-driver")
            description.set("Low-level API for SQLite in Kotlin Multiplatform")
            url.set("https://github.com/ctripcorp/SQLlin")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("qiaoyuang")
                    name.set("Yuang Qiao")
                    email.set("qiaoyuang2012@gmail.com")
                }
            }
            scm {
                url.set("https://github.com/ctripcorp/SQLlin")
                connection.set("scm:git:https://github.com/ctripcorp/SQLlin.git")
                developerConnection.set("scm:git:https://github.com/ctripcorp/SQLlin.git")
            }
        }
    }
    repositories {
        maven {
            credentials {
                username = NEXUS_USERNAME
                password = NEXUS_PASSWORD
            }
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
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