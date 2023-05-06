pluginManagement {
    val kspVersion: String by settings
    plugins {
        id("com.google.devtools.ksp") version kspVersion apply false
        kotlin("plugin.serialization") version "1.8.20"
    }
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "SQLlin"
include(":sqllin-driver")
include(":sqllin-dsl")
include(":sqllin-processor")
include(":sample")
