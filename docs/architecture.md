# Architektur

- **UI-Schicht** (`app/.../ui`): Compose-Screens + `@HiltViewModel`. Jeder Screen rendert einen unveränderlichen
  `UiState` aus `StateFlow`; Nutzeraktionen sind Methodenaufrufe am ViewModel (Unidirectional Data Flow).
  Einmalige Meldungen (Snackbar) sind Zustand, der nach Anzeige quittiert wird (`messageShown()`).
  Bildschirmzustand, der einen Prozessneustart überleben muss (Suche, Filter, Zeitraum, Editor-Entwurf), liegt im
  `SavedStateHandle`.
- **Domain** (`:domain`): reine Kotlin-Logik, vollständig per JVM-Test abgedeckt.
  - Rechnen: `RecipeScaler`, `UnitConverter`, `NutritionCalculator`
  - Einkauf: `GenerateShoppingListUseCase`, `ShoppingDiff`, `ShoppingListExporter`
  - Import: `MarkdownRecipeImporter`, `GermanAmounts` (Einheitenwörter, deutsche Zahlen)
  - Zutatennamen: `IngredientCatalog` (Synonyme, „nie einkaufen“, Abteilungs-Schätzung)
  - Kochmodus: `StepIngredientMatcher` (Zutaten + Teilmengen im Schritt), `StepTimerParser` (Zeitangaben);
    laufende Timer hält `CookTimerRepository` (App-weit), `CookTimerService` meldet das Ende auch im Hintergrund
  - Startseite: `DailyPicks` (Rezepte des Tages)
- **Data** (`app/.../data`): Room als Single Source of Truth, Repositories mappen Entities ↔ Domain.
  Schreibvorgänge über mehrere Tabellen laufen in Transaktionen (Rezept speichern, Listen-Snapshot,
  Diff anwenden, „gekocht“, Rezept auf Einkaufsliste, Zutaten zusammenführen/vereinheitlichen).

## Navigation

Typisierte Routen (Navigation Compose + kotlinx.serialization). `NavigationSuiteScaffold` wählt automatisch
Bottom Bar (Telefon) oder Navigation Rail (Tablet). Jeder Hauptbereich behält seinen eigenen Verlauf
(`saveState`/`restoreState`): Wer von einer Rezeptdetailseite zum Einkauf wechselt und zurück, landet wieder dort.

## Datenbank

Version **2**, Schema-Export nach `app/schemas` (eingecheckt). Jede Schemaänderung benötigt eine `Migration`
in `ALL_MIGRATIONS` (`FoodyDatabase.kt`) und einen Migrationstest (`MigrationTestHelper`, `MigrationTest`);
`fallbackToDestructiveMigration()` ist verboten.

| Version | Änderung |
|---|---|
| 1 | Ausgangsschema |
| 2 | `recipe.favorite`, `recipe.sourceUrl` (+ Index); Quell-URL importierter Rezepte aus den Notizen übernommen |

## Startdaten

`SeedData` enthält generische Zutaten mit gerundeten Nährwerten. Der Zähler `SeedData.VERSION` (in
SharedPreferences `seedVersion`) sorgt dafür, dass Erweiterungen auch bestehende Installationen erreichen.
Einträge, die noch unverändert aus den Startdaten stammen (`nutrientSource` = „Näherungswert (Startdaten)“),
übernehmen Korrekturen; vom Nutzer bearbeitete Werte bleiben unangetastet.

## Build

AGP 9 mit eingebautem Kotlin (kein `org.jetbrains.kotlin.android`-Plugin) und neuer DSL, ohne
Kompatibilitäts-Flags in `gradle.properties`. Gradle braucht JDK 17+ (z. B. das JBR von Android Studio).
