import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

version = "1.0"

kotlin {
    android {
        publishLibraryVariants("release")
    }
    iosX64 {
        setupIOSConfig()
    }
    iosArm64 {
        setupIOSConfig()
    }
    iosSimulatorArm64 {
        setupIOSConfig()
    }
    
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":sqllin-dsl"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
            }
        }
        val androidMain by getting
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

android {
    compileSdk = 33
    buildToolsVersion = "33.0.0"
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets.getByName("androidTest") {
        manifest.srcFile(File("src/androidTest/AndroidManifest.xml"))
    }
    defaultConfig {
        minSdk = 23
        targetSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

fun KotlinNativeTarget.setupIOSConfig() {
    val compileArgs = listOf("-Xallocator=mimalloc", "-Xruntime-logs=gc=info")
    compilations["main"].kotlinOptions.freeCompilerArgs += compileArgs
}

dependencies {
    add("kspCommonMainMetadata", project(":sqllin-processor"))
}