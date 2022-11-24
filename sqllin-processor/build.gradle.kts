plugins {
    kotlin("jvm")
    id("maven-publish")
}

val GROUP: String by project
val VERSION: String by project

group = GROUP
version = VERSION

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    val kspVersion: String by project
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
}

publishing {
    publications.create<MavenPublication>("Processor") {
        artifactId = "sqllin-processor"
        artifact("$buildDir/libs/sqllin-processor-$version.jar")
    }
    repositories {
        maven {

        }
    }
}