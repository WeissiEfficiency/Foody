# Architektur

- **UI-Schicht** (`app/.../ui`): Compose-Screens + `@HiltViewModel`. Jeder Screen rendert einen unveränderlichen
  `UiState` aus `StateFlow`; Nutzeraktionen sind Methodenaufrufe am ViewModel (Unidirectional Data Flow).
  Einmalige Meldungen (Snackbar) sind Zustand, der nach Anzeige quittiert wird (`messageShown()`).
  Bildschirmzustand, der einen Prozessneustart überleben muss (Suche, Zeitraum, Editor-Entwurf), liegt im
  `SavedStateHandle`.
- **Domain** (`:domain`): reine Kotlin-Logik, vollständig per JVM-Test abgedeckt.
  `RecipeScaler`, `UnitConverter`, `NutritionCalculator`, `GenerateShoppingListUseCase`, `ShoppingDiff`,
  `ShoppingListExporter`.
- **Data** (`app/.../data`): Room als Single Source of Truth, Repositories mappen Entities ↔ Domain.
  Schreibvorgänge über mehrere Tabellen laufen in Transaktionen (Rezept speichern, Listen-Snapshot,
  Diff anwenden, „gekocht“, Import).

## Navigation

Typisierte Routen (Navigation Compose + kotlinx.serialization). `NavigationSuiteScaffold` wählt automatisch
Bottom Bar (Telefon) oder Navigation Rail (Tablet).

## Datenbank

Version 1, Schema-Export nach `app/schemas`. Jede Schemaänderung benötigt eine `Migration` und einen
Migrationstest (`MigrationTestHelper`); `fallbackToDestructiveMigration()` ist verboten.
