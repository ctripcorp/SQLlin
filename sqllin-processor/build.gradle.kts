plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
}

val GROUP_ID: String by project
val VERSION: String by project

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