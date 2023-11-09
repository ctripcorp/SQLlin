# Publish artifacts on macOS env
./gradlew :sqllin-driver:publishAllPublicationsToMavenRepository -PonCICD
./gradlew :sqllin-dsl:publishAllPublicationsToMavenRepository -PonCICD