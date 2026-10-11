pluginManagement {
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
include(":server")
include(":server-sync")
include(":server-web")
// Server: sync (API, Konten, Datenbank), web (Oberfläche), start (main, Docker-Startpunkt)
project(":server").projectDir = file("server/start")
project(":server-sync").projectDir = file("server/sync")
project(":server-web").projectDir = file("server/web")
// Gemeinsamer Code von App und Server liegt unter shared/ (Gradle-Namen bleiben gleich)
project(":domain").projectDir = file("shared/domain")
project(":sync-protocol").projectDir = file("shared/sync-protocol")
// Docker-Build des Servers läuft ohne Android-SDK: FOODY_SERVER_ONLY lässt die Android-Module weg.
if (System.getenv("FOODY_SERVER_ONLY") == null) {
    include(":app")
    include(":baselineprofile")
    // Handy-App unter android/ (Gradle-Namen bleiben gleich)
    project(":app").projectDir = file("android/app")
    project(":baselineprofile").projectDir = file("android/baselineprofile")
}
