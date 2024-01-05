plugins {
    kotlin("jvm")
    id("maven-publish")
    signing
}

val GROUP: String by project
val VERSION: String by project

group = GROUP
version = VERSION

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    val kspVersion: String by project
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
}

val NEXUS_USERNAME: String by project
val NEXUS_PASSWORD: String by project

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

val sourceJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(project.the<SourceSetContainer>()["main"].allSource)
}

publishing {
    publications.create<MavenPublication>("Processor") {
        artifactId = "sqllin-processor"
        setArtifacts(
            listOf(
                "${layout.buildDirectory}/libs/sqllin-processor-$version.jar",
                javadocJar, sourceJar,
            )
        )
        with(pom) {
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
    repositories {
        maven {
            credentials {
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