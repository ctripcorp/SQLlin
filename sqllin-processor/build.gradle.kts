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

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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