import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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

kotlin {
    explicitApi()
    jvmToolchain(21)
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
    }
}

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
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
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