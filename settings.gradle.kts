pluginManagement {
    val kspVersion: String by settings
    val kotlinVersion: String by settings
    plugins {
        id("com.google.devtools.ksp") version kspVersion apply false
        kotlin("plugin.serialization") version kotlinVersion
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
