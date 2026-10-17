pluginManagement {
    // Convention-Plugins der Android-Module; der Docker-Build des Servers braucht sie nicht
    if (System.getenv("FOODY_SERVER_ONLY") == null) includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Foody"
include(":domain")
include(":sync-protocol")
include(":api-protocol")
include(":server")
include(":server-sync")
// Server: sync (API, Konten, Datenbank), start (main, Docker-Startpunkt)
project(":server").projectDir = file("server/start")
project(":server-sync").projectDir = file("server/sync")
// Gemeinsamer Code von App und Server liegt unter shared/ (Gradle-Namen bleiben gleich)
project(":domain").projectDir = file("shared/domain")
project(":sync-protocol").projectDir = file("shared/sync-protocol")
project(":api-protocol").projectDir = file("shared/api-protocol")
// Docker-Build des Servers läuft ohne Android-SDK: FOODY_SERVER_ONLY lässt die Android-Module weg.
if (System.getenv("FOODY_SERVER_ONLY") == null) {
    include(":app")
    include(":baselineprofile")
    // Handy-App unter android/ (Gradle-Namen bleiben gleich)
    project(":app").projectDir = file("android/app")
    project(":baselineprofile").projectDir = file("android/baselineprofile")
    // v2: ein Modul je Bereich unter android/feature/ (ADR 0009)
    include(":feature:heute")
    project(":feature").projectDir = file("android/feature")
    project(":feature:heute").projectDir = file("android/feature/heute")
}
