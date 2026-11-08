plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
}

val GROUP_ID = project.property("GROUP_ID") as String
val VERSION = project.property("VERSION") as String

group = GROUP_ID
version = VERSION

repositories {
    mavenCentral()
}

// The JDK of the other modules, which compiles both the Java and the Kotlin classes for it, rather than the Kotlin ones
// for the JDK that runs the build
kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(libs.ksp)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    val artifactId = "sqllin-processor"
    coordinates(
        groupId = GROUP_ID,
        artifactId = artifactId,
        version = VERSION,
    )

    pom {
        name.set(artifactId)
        description.set("KSP code be used to generate the database column properties")
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