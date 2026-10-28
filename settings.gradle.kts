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
    /** Ein Modul: Gradle-Name und Ordner untereinander (Gradle-Namen sind unabhängig von den Ordnern). */
    fun modul(name: String, ordner: String) {
        include(name)
        project(name).projectDir = file(ordner)
    }

    // v1-App, eingefroren; entfällt in Phase 8
    modul(":app", "android/app")
    modul(":baselineprofile", "android/baselineprofile")

    // V3: App-Hülle (wird in Phase 8 zu :app) und Katalog-App zum Ansehen des Designsystems
    modul(":app3", "android/app3")
    modul(":katalog", "android/katalog")

    // V3: gemeinsame Bausteine unter android/core/ (Abhängigkeiten nur nach unten)
    modul(":core:data", "android/core/data")
    modul(":core:sync", "android/core/sync")
    modul(":core:designsystem", "android/core/designsystem")
    modul(":core:scan", "android/core/scan")
    modul(":core:testing", "android/core/testing") // nur für Tests
    project(":core").projectDir = file("android/core")

    // V3: ein Modul je Bereich unter android/feature/ (ADR 0009); Features kennen sich nicht
    modul(":feature:heute", "android/feature/heute")
    modul(":feature:rezepte", "android/feature/rezepte")
    modul(":feature:kochen", "android/feature/kochen")
    modul(":feature:woche", "android/feature/woche")
    modul(":feature:einkauf", "android/feature/einkauf")
    modul(":feature:einstellungen", "android/feature/einstellungen")
    modul(":feature:zutaten", "android/feature/zutaten")
    project(":feature").projectDir = file("android/feature")
}
