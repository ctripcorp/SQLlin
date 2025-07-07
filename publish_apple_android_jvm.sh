# Publish artifacts on macOS env
./gradlew :sqllin-driver:publishAllPublicationsToMavenCentralRepository -PonCICD
./gradlew :sqllin-dsl:publishAllPublicationsToMavenCentralRepository -PonCICD