# Nährwerte per Strichcode und Foto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Packungs-Nährwerte per Strichcode (Katalog → Open Food Facts) oder Foto der Nährwerttabelle (ML Kit) vorausfüllen – im Zutaten-Dialog und im Tagebuch.

**Architecture:** Parser (`NaehrwertScan`) in `domain`; Open-Food-Facts-Auswertung als reine Funktion in `app`; Ablauf in `PackungScan` hinter Schnittstellen (`StrichcodeLeser`, `TabellenScanner`, `ProduktSuche`, `KatalogSuche`) mit ML-Kit-/Ktor-Umsetzungen, per Hilt injiziert und in Tests durch Fakes ersetzt. DB v9: `ingredient.barcode`.

**Tech Stack:** Kotlin, Compose M3, Room, Hilt, Ktor-Client (OkHttp), kotlinx.serialization, Google Play-Dienste (`play-services-mlkit-text-recognition`, `play-services-code-scanner`, `play-services-base`).

**Spec:** `docs/superpowers/specs/2026-10-10-naehrwerte-scan-design.md`

## Global Constraints

- Werte nie ungeprüft übernehmen: vorausfüllen + markieren; Speichern ist die Bestätigung.
- Plausibilität: Energie ≤ 4000 kJ, Gramm-Nährwerte ≤ 100, Zucker ≤ Kohlenhydrate.
- `nutrientSource`: „Open Food Facts“ bzw. „Packung (Foto)“.
- Kameradatei unter `cacheDir/scan/` nach der Erkennung löschen.
- Neue Bezeichner deutsch; Build/Test wie in den Plänen zu A/B (JAVA_HOME, Emulator `Foody_Test`, eine Testklasse je Aufruf, `ViewModel.aufraeumen()` vor `db.close()`).

## Review Focus

1. **Zweispaltige Tabelle, Werte als eigene ML-Kit-Elemente** → richtige Zuordnung über die Höhe. Test in Task 1.
2. **„davon gesättigte Fettsäuren 2,1 g“ vor/nach „Fett 3,5 g“** → Fett = 3,5. Test in Task 1.
3. **Open Food Facts liefert nur `energy-kcal_100g`** → in kJ umgerechnet. Test in Task 2.
4. **Strichcode schon im Katalog** → keine Online-Abfrage, auch wenn die Online-Suche an ist. Test in Task 4.
5. **Tagebuch-Packung mit vorhandenem Zutatennamen** → Zutat nicht überschrieben (Häkchen standardmäßig aus). Test in Task 6.

---

### Task 1: Parser `NaehrwertScan` (domain)
**Files:** Create `domain/src/main/kotlin/de/foody/domain/NaehrwertScan.kt`; Test `domain/src/test/kotlin/de/foody/domain/NaehrwertScanTest.kt`
**Produces:** `data class OcrElement(text: String, links: Int, oben: Int, unten: Int)`, `data class ScanErgebnis(basis: NutrientBasis, werte: Map<Nutrient, BigDecimal>)`, `object NaehrwertScan { fun auswerten(elemente: List<OcrElement>): ScanErgebnis; fun plausibel(werte: Map<Nutrient, BigDecimal>): Map<Nutrient, BigDecimal> }`.
- [ ] Tests (Werte als Strings verglichen über `compareTo`): einspaltig („Brennwert 1.234 kJ / 295 kcal“, „Fett 3,5 g“, „davon gesättigte Fettsäuren 2,1 g“, „Kohlenhydrate 45 g“, „davon Zucker 12 g“, „Eiweiß 8,0 g“, „Salz 0,02 g“) → kJ 1234, Fett 3,5, KH 45, Zucker 12, Eiweiß 8, Salz 0,02; zweispaltig (Bezeichnungen links, Werte rechts als eigene Elemente gleicher Höhe ±4 px); nur kcal 250 → kJ 1046; „<0,5 g“ → 0,5; „Spuren“ → 0; englisch („Energy 1500 kJ“, „Protein 10 g“, „Fat 2 g“); „je 100 ml“ → `PER_100_ML`; Fett 350 g → verworfen; Zucker 50 > KH 40 → Zucker verworfen; leere Liste → leere Werte, Basis g.
- [ ] RED → implementieren (Spec 3.1) → GREEN → Commit „Scan: Nährwerttabelle aus erkanntem Text lesen“.

### Task 2: Open-Food-Facts-Auswertung (app, JVM)
**Files:** Create `app/src/main/java/de/foody/app/scan/OpenFoodFacts.kt`; Test `app/src/test/java/de/foody/app/scan/OpenFoodFactsTest.kt`
**Produces:** `data class Packung(name: String?, basis: NutrientBasis, werte: Map<Nutrient, BigDecimal>, strichcode: String?, quelle: String)`; `object OpenFoodFacts { const val QUELLE = "Open Food Facts"; fun auswerten(json: JsonObject, strichcode: String): Packung? }`.
- [ ] Tests mit eingebetteten JSON-Antworten: vollständig (Name de bevorzugt), nur `energy-kcal_100g`, `nutrition_data_per = "100ml"`, ohne Energie → `null`, `status 0` → `null`, unplausible Werte verworfen (nutzt `NaehrwertScan.plausibel`).
- [ ] RED → GREEN → Commit „Scan: Antworten von Open Food Facts auswerten“.

### Task 3: DB v9 Strichcode, Sync, Sicherung
**Files:** `Entities.kt` (`IngredientEntity.barcode: String? = null`, Index), `FoodyDatabase.kt` (v9, `MIGRATION_8_9`), `Daos.kt` (`IngredientDao.findByBarcode(code): IngredientEntity?` – älteste zuerst), `Repositories.kt` (`IngredientRepository.zutatMitStrichcode`, `merge` übernimmt Code), `Payloads.kt` (`IngredientPayload.barcode`), `PayloadValidator.kt` (≤ 14 Ziffern), `SyncMapper.kt`, `BackupRepository.kt`, Schema `9.json`; Tests `MigrationTest.migrate8To9…`, `SyncApplierTest`, `BackupZipTest`, `PayloadValidatorTest`, `IngredientHarmonizeTest` (Merge übernimmt Code).
- [ ] RED → GREEN → Commit „DB v9: Zutat merkt sich ihren Strichcode“.

### Task 4: Scan-Ablauf `PackungScan` + Umsetzungen
**Files:** Create `app/src/main/java/de/foody/app/scan/{PackungScan.kt, Schnittstellen.kt, MlKitScanner.kt, OffProduktSuche.kt, ScanPreferences.kt}`, `app/src/main/java/de/foody/app/di/ScanModule.kt`; `gradle/libs.versions.toml` + `app/build.gradle.kts` (Play-Dienste); Test `app/src/test/java/de/foody/app/scan/PackungScanTest.kt` (Fakes).
**Produces:**
- `interface StrichcodeLeser { suspend fun lesen(): String? }`, `interface TabellenScanner { suspend fun scan(bild: Uri): ScanStatus }` (ScanStatus wie Spec 3.2), `interface ProduktSuche { suspend fun suche(code: String): Antwort }` mit `Antwort { Gefunden(Packung), Unbekannt, Offline }`, `fun interface KatalogSuche { suspend fun zutatMitStrichcode(code: String): IngredientEntity? }`, `interface PlayDienste { val verfuegbar: Boolean }`.
- `sealed interface ScanAusgang { Gefunden(Packung); ImKatalog(IngredientEntity); NichtGefunden(strichcode: String?, offline: Boolean); KeineTabelle(strichcode: String?); WirdGeladen; NichtVerfuegbar; Abgebrochen }`.
- `class PackungScan @Inject constructor(...) { val verfuegbar: Boolean; suspend fun strichcode(): ScanAusgang; suspend fun foto(bild: Uri, strichcode: String?): ScanAusgang }` – Reihenfolge Spec 2; `foto` liefert `Packung(name = null, quelle = "Packung (Foto)")`.
- `ScanPreferences.onlineSuche: StateFlow<Boolean>` (Standard true), `setOnlineSuche(Boolean)`.
- [ ] Tests (JVM, Fakes): Katalog-Treffer → `ImKatalog`, ProduktSuche nie gefragt (Focus 4); online aus → nur Katalog, sonst `NichtGefunden(code, offline=false)`; Offline → `NichtGefunden(code, true)`; Abbruch → `Abgebrochen`; Foto ohne Werte → `KeineTabelle`; keine Play-Dienste → `NichtVerfuegbar`.
- [ ] RED → GREEN; `./gradlew :app:assembleDebug` (Abhängigkeiten auflösen) → Commit „Scan: Strichcode, Open Food Facts und Texterkennung verbinden“.

### Task 5: Zutaten-Dialog
**Files:** `IngredientsScreen.kt`, `IngredientsViewModel.kt` (PackungScan, Ausgang als State), Create `app/src/main/java/de/foody/app/ui/common/PackungScanKnopf.kt` (Menü, Kamera/Galerie-Launcher, Fortschritt, Meldungen); strings; Test `app/src/androidTest/.../IngredientScanTest.kt` (Compose, Fake-PackungScan via Konstruktor).
- [ ] Test: Scan liefert Packung (kJ 1234, Fett 3,5) → Felder gefüllt, Hinweis „bitte prüfen“ sichtbar, Speichern schreibt `barcode` und `nutrientSource = "Open Food Facts"`; Name leer → aus Packung, sonst unverändert.
- [ ] RED → GREEN → Sichtprüfung → Commit „Zutaten: Nährwerte von der Packung scannen“.

### Task 6: Tagebuch-Packungsmodus
**Files:** `TagebuchViewModel.kt` (`packungEintragen(m, name, menge, einheit, packung: Packung, alsZutat: Boolean)`, `scan` über PackungScan), `TagebuchDialoge.kt` (Reiter „Frei“: Scan-Knopf, Packungsmodus, Häkchen); Tests `TagebuchViewModelTest` (Packung als Zutat → neue Zutat mit Werten/Code + `ZUTAT`-Eintrag; ohne Häkchen → `FREI` mit berechneten Werten; vorhandener Name → Zutat unverändert, Eintrag mit Scan-Werten; Katalog-Treffer → Zutat-Reiter vorausgewählt).
- [ ] RED → GREEN → Commit „Tagebuch: Packung scannen und eintragen“.

### Task 7: Einstellung, Doku, Abschluss
**Files:** `SettingsScreen.kt`/`SettingsViewModel.kt` (Schalter „Online-Produktsuche“), strings, ADR 0008, `architecture.md`, `security.md`, `domain-rules.md`, ToDo.
- [ ] `check` + volle Instrumentation-Suite grün; Sichtprüfung (Scan-Knopf, Meldungen, Packungsmodus); Commit „Scan: Einstellung Online-Produktsuche, Doku“.
