rootProject.name = "SQLlin"
include(":sqllin-driver")
include(":sqllin-dsl")
include(":sqllin-processor")
include(":sqllin-dsl-test")
include(":sqllin-driver-test")
include(":sample")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}