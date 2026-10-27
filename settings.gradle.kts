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
    // V3: Datenbank und Sync-Client der App unter android/core/
    include(":core:data", ":core:sync")
    project(":core").projectDir = file("android/core")
    project(":core:data").projectDir = file("android/core/data")
    project(":core:sync").projectDir = file("android/core/sync")
    // V3: Designsystem (Tokens, Glas, Komponenten) und Katalog-App zum Ansehen (Phase 4)
    include(":core:designsystem", ":katalog")
    // V3: App-Hülle neben der v1 (Phase 5); wird in Phase 8 zu :app
    include(":app3", ":feature:einstellungen")
    project(":app3").projectDir = file("android/app3")
    project(":feature:einstellungen").projectDir = file("android/feature/einstellungen")
    // V3 Phase 6: Bereiche neu bauen (6a Rezepte, 6b Kochen, 6c Woche, 6d Einkauf)
    include(":feature:rezepte", ":feature:kochen", ":feature:woche", ":feature:einkauf")
    project(":feature:rezepte").projectDir = file("android/feature/rezepte")
    project(":feature:kochen").projectDir = file("android/feature/kochen")
    project(":feature:woche").projectDir = file("android/feature/woche")
    project(":feature:einkauf").projectDir = file("android/feature/einkauf")
    // V3 Phase 6e2: Zutatenkatalog
    include(":feature:zutaten")
    project(":feature:zutaten").projectDir = file("android/feature/zutaten")
    // V3 Phase 6e3: Packung scannen (Tagebuch und Zutatenkatalog, ADR 0013)
    include(":core:scan")
    project(":core:scan").projectDir = file("android/core/scan")
    project(":core:designsystem").projectDir = file("android/core/designsystem")
    project(":katalog").projectDir = file("android/katalog")
}
