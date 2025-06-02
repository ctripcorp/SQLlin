rootProject.name = "SQLlin"
include(":sqllin-driver")
include(":sqllin-dsl")
include(":sqllin-processor")
include(":sqllin-dsl-test")
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