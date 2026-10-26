# V3 Phase 6e2 Zutatenkatalog – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den Zutatenkatalog in der V3 ansehen und pflegen: Liste mit Suche und Filtern, Editor, Anlegen, Löschen, Zusammenführen, „Namen vereinheitlichen“; Einstieg aus den Einstellungen.

**Architecture:** `ZutatenRepository` und reine Funktionen (Filter, Entwurf/Prüfung, Angleichungen) in `:core:data/zutaten`; eine gemeinsame Zusammenführung, die auch `SyncSpeicher` nutzt; `StartdatenLader` schützt Bearbeitungen. Oberfläche im neuen Modul `:feature:zutaten` (Liste, Editor, Zielwahl-Blatt), Ziele `Zutaten`/`Zutat(id)` in `:app3`, Zeile in den Einstellungen.

**Tech Stack:** Kotlin 2.4.20, Compose (BOM 2026.09.00), Navigation Compose 2.10.2, Hilt 2.60.1, Room 2.8.5, Robolectric 4.16.1, Roborazzi 1.76.0.

**Spec:** `docs/superpowers/specs/2026-10-25-v3-phase6e2-zutaten-design.md`

## Global Constraints

- v1 unverändert: `android/app/**`, `:sync-protocol`; Server-Code und `:api-protocol` unverändert. `:domain` nur additiv (`IngredientCatalog.categories`).
- Kein Schema-Wechsel: Datenbank bleibt Version 10; neue DAO-Abfragen ja, neue Tabellen/Spalten nein.
- `:feature:zutaten` hängt nur von `:core:*` und `:domain` ab; jede Datei in `src/main` ≤ 300 Zeilen; Aufbau (Roborazzi-Plugin, `outputDir`, Testabhängigkeiten, `isIncludeAndroidResources`) wie `:feature:einstellungen`.
- Startzutaten: ID beginnt mit `seed-`. Quelle bei geänderten Nährwerten ohne eigene Quellangabe: „Eigene Angabe“. Energie: Eingabe kcal, gespeichert kJ über `NutritionResult.kcalToKj`, Anzeige `NutritionResult.kcal(kJ)`.
- Prüfregeln (nur geänderte Felder): Name 1–200 Zeichen („Name fehlt“ / „Höchstens 200 Zeichen“); kcal 0–1.000 („0 bis 1.000“); Eiweiß, Kohlenhydrate, Zucker, Fett, Ballaststoffe, Salz 0–100 („0 bis 100“); Dichte > 0 und ≤ 5 („Mehr als 0, höchstens 5“); Stückgewicht > 0 und ≤ 10.000 („Mehr als 0, höchstens 10.000“); Strichcode leer oder 8–14 Ziffern („8 bis 14 Ziffern“). Keine Zahl = Fehlertext der Regel. Komma und Punkt als Dezimalzeichen.
- Robolectric-UI-Tests: `@GraphicsMode(NATIVE)`, `junit4.v2.createComposeRule`/`createAndroidComposeRule`, `application = android.app.Application::class`; Dialoge und Blätter mit Textfeld ohne Breiten-Qualifier; ViewModels außerhalb der Komposition; `runBlocking<Unit>` statt `runBlocking` als Testkörper; `Augenbraue` rendert Großbuchstaben.
- Texte (wörtlich): „Zutaten“, „Zutatenkatalog“, „Ohne Nährwerte“, „Eigene“, „Unbenutzt“, „Mit Strichcode“, „<n> Zutaten“ / „1 Zutat“ + „ · <m> ohne Nährwerte“ (nur m > 0), „<n> kcal“, „– kcal“, „in 1 Rezept“, „in <n> Rezepten“, „unbenutzt“, „Keine Zutat gefunden“, „Suchen“, „Neue Zutat“, „Name“, „Anlegen“, „Abbrechen“, „„<Name>“ gibt es schon.“, „Öffnen“, „Namen vereinheitlichen“, „<alt> → <neu>“, „<alt> → <neu> (zusammenführen)“, „und <n> weitere“, „1 Kategorie ergänzt“ / „<n> Kategorien ergänzt“, „Anwenden“, „1 Zutat angepasst“ / „<n> Zutaten angepasst“, „Alle Namen sind schon einheitlich.“, „OK“, Augenbraue „Zutat“, „Zusammenführen mit …“, „Löschen“, „Speichern“, „Auch: <a>, <b>“, Abschnitte „Kategorie“, „Nährwerte“, „Umrechnung“, „Strichcode“, Chip „Keine“, „je 100 g“, „je 100 ml“, Felder „kcal“, „Eiweiß“, „Kohlenhydrate“, „davon Zucker“, „Fett“, „Ballaststoffe“, „Salz“, „Quelle“, „Dichte (g/ml)“, „Stückgewicht (g)“, „Strichcode“, „Verwendet in: <a>, <b> und 1 weiteres“ / „… und <n> weitere“, „Noch in keinem Rezept“, „Änderungen verwerfen?“, „„<Name>“ gibt es schon. Diese Zutat damit zusammenführen?“, „„<von>“ in „<nach>“ zusammenführen? 1 Rezept wird umgestellt, „<von>“ wird gelöscht.“ / „… <n> Rezepte werden umgestellt, …“ / ohne Rezepte „„<von>“ in „<nach>“ zusammenführen? „<von>“ wird gelöscht.“, „Zusammenführen“, „Zusammengeführt in „<nach>““, „„<Name>“ löschen?“, „Gelöscht“, „In 1 Rezept verwendet. Zum Entfernen mit einer anderen Zutat zusammenführen.“ / „In <n> Rezepten verwendet. …“, „Zusammenführen …“, „Diese Zutat gibt es nicht mehr“, „Die Zutat gibt es nicht mehr“, „Gehört schon zu „<Name>““, „Eigene Angabe“.
- Deutsch in Texten, Bezeichnern, Kommentaren; `allWarningsAsErrors` bleibt an. Ein Branch `claude/v3-phase6e2-zutaten`, PR gegen `main` mit Auto-Merge; „Server aktualisieren? Nein“.

## Review Focus

1. **Nur Groß/klein am eigenen Namen ändern** („reis“ → „Reis“) – kein `NameVergeben`, die Zutat kollidiert nicht mit sich selbst (Test in Task 4).
2. **Suche mit Leerzeichen am Rand** („ reis “) findet „Reis“ (Test in Task 2).
3. **Zielwahl enthält die Quelle nicht** – man kann eine Zutat nicht in sich selbst zusammenführen (Tests in Task 8 und 9).
4. **Doppeltipp auf „Anwenden“ oder „Speichern“** – wird nur einmal ausgeführt (Tests in Task 7 und 8).
5. **Startzutat nur in Kategorie/Dichte/Strichcode bearbeitet** – überlebt neue Startdaten, obwohl ihre Nährwertquelle noch die Startquelle ist (Test in Task 1).

---

### Task 1: Gemeinsames Zusammenführen, Startdaten schützen

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/zutaten/ZutatZusammenfuehren.kt`
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/sync/SyncSpeicher.kt` (private `ergaenztUm` entfernen; in `zutatSchreiben` die Schritte Umhängen ×3, Löschen, Ziel schreiben durch den Aufruf ersetzen, `applyingRemoteSetzen(false/true)` bleibt drumherum; `if (!geaendert) dao.loeschenOutbox(INGREDIENT, r.id)` statt `ergaenzt == server`), `android/core/data/src/main/kotlin/de/foody/core/data/startdaten/StartdatenLader.kt` (`ergaenztAus` entfernen, `ergaenztUm` nutzen; im Überschreib-Zweig `category`, `densityGPerMl`, `pieceWeightG`, `barcode` von `vorhanden` behalten, wenn gesetzt)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/zutaten/ZutatErgaenzenTest.kt`, `android/core/data/src/test/kotlin/de/foody/core/data/startdaten/StartdatenLaderTest.kt`

**Interfaces:**
- Produces: `internal fun ZutatEntity.ergaenztUm(andere: ZutatEntity): ZutatEntity` (Regel wie heute: Nährwerte samt Quelle als Block nur bei `nutrientBasis == null`; sonst Kategorie, Dichte, Stückgewicht, Strichcode einzeln, wenn leer); `internal suspend fun zusammenfuehrenSchritte(db: FoodyDatenbank, quelle: ZutatEntity, ziel: ZutatEntity, jetzt: Long): Boolean` – ohne eigene Transaktion: `rezeptZeilenUmhaengen`, `einkaufsartikelUmhaengen`, `tagebuchUmhaengen` (von `quelle.id` auf `ziel.id`), `zutatDao().loeschen(quelle.id)`, dann `ziel.ergaenztUm(quelle)` mit `updatedAt = jetzt` schreiben, nur wenn es sich (ohne `updatedAt`) von `ziel` unterscheidet; Rückgabe: Ziel geändert.

- [ ] **Step 1: Tests** `ZutatErgaenzenTest` (reines JUnit): `naehrwerteAlsBlockNurOhneBasis` (Ziel ohne Basis bekommt Basis, alle sieben Werte und Quelle der anderen; Ziel mit Basis `PER_100_G` und nur `energyKj` behält seine Werte, `protein` bleibt `null`), `einzelfelderNurWennLeer` (Kategorie/Dichte/Stückgewicht/Strichcode: leer → übernommen, gesetzt → bleibt). `StartdatenLaderTest`: `bearbeiteteUmrechnungBleibt` (Review Focus 5: `seed-reis` aus `Startdaten.zutaten(1)` mit `category = "Eigene Abteilung"`, `densityGPerMl = 0.85`, `barcode = "4001234567890"`, Quelle unverändert; `seedVersion` 7; nach `ladenWennNoetig()` alle drei noch so), `leereFelderWerdenGefuellt` (`seed-reis` mit `category = null`, Vorbedingung `assertNotNull(start.category)`; danach `category == start.category`).
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest --tests "*ZutatErgaenzenTest*" --tests "*StartdatenLaderTest*"` → rot (`ergaenztUm` fehlt; `bearbeiteteUmrechnungBleibt` überschrieben).
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün – inkl. `SyncSpeicherTest.gleichnamigeZutatWirdZusammengefuehrt`, `loeschungEinerBenutztenZutatBelebtSieWieder`, `vollabgleichBehaeltBenutzteZutat`.
- [ ] **Step 5:** Commit `"V3 Phase 6e2: Gemeinsames Zusammenführen, Startdaten schützen"`.

### Task 2: Katalog lesen und filtern

**Files:**
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/db/Daos.kt` (`ZutatDao`), `android/core/data/src/main/kotlin/de/foody/core/data/DatenModul.kt`
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/zutaten/{ZutatenFilter,ZutatenRepository}.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/zutaten/{ZutatenFilterTest,ZutatenRepositoryLesenTest}.kt`

**Interfaces:**
- Produces in `ZutatDao`: `data class ZutatMitVerwendung(@Embedded val zutat: ZutatEntity, val rezepte: Int)`; `fun mitVerwendungFlow(): Flow<List<ZutatMitVerwendung>>` (`rezepte` = `COUNT(DISTINCT recipeId)` aus `recipe_ingredient` je Zutat, Unterabfrage); `fun anzahlFlow(): Flow<Int>`; `fun nachIdFlow(id: String): Flow<ZutatEntity?>`; `fun rezeptNamenFlow(id: String): Flow<List<String>>` (`DISTINCT recipe.name` über `recipe_ingredient`, `ORDER BY name COLLATE NOCASE`); `suspend fun nachStrichcode(code: String, ausser: String): ZutatEntity?`; `suspend fun rezeptAnzahl(id: String): Int`.
- Produces in `ZutatenFilter.kt`: `data class ZutatKurz(val id: String, val name: String, val kategorie: String?, val kcal: Int?, val rezepte: Int, val strichcode: String?, val eigen: Boolean)`; `enum class ZutatFilter { OHNE_NAEHRWERTE, EIGENE, UNBENUTZT, MIT_STRICHCODE }` (`kcal == null`, `eigen`, `rezepte == 0`, `strichcode != null`); `fun filtern(liste: List<ZutatKurz>, suche: String, filter: Set<ZutatFilter>): List<ZutatKurz>` (Suche getrimmt; Teilstring ohne Groß/klein oder `name.equals(IngredientCatalog.canonicalName(suche), true)`; Reihenfolge der Eingabe bleibt); `internal fun ZutatEntity.kurz(rezepte: Int): ZutatKurz`.
- Produces: `class ZutatenRepository(db: FoodyDatenbank, uhr: () -> Long)` mit `fun liste(): Flow<List<ZutatKurz>>` (sortiert mit `Collator.getInstance(Locale.GERMAN)`, Stärke `SECONDARY`, Gleichstand nach ID), `fun anzahl(): Flow<Int>`, `fun zutat(id: String): Flow<ZutatEntity?>`, `fun verwendetIn(id: String): Flow<List<String>>`, `fun synonyme(name: String): List<String>` (`IngredientCatalog.synonymsOf(name)` ohne Einträge gleich `name` ohne Groß/klein); Provider `@Provides @Singleton fun zutatenRepository(db): ZutatenRepository = ZutatenRepository(db, System::currentTimeMillis)`.

- [ ] **Step 1: Tests** `ZutatenFilterTest`: `ohneFilterAlle`, je ein Test pro Filter, `filterZusammen` (`EIGENE` + `UNBENUTZT` → nur die eine, die beides ist), `sucheOhneGrossKlein` („zwieb“ → „Zwiebel“), `sucheMitLeerzeichen` (Review Focus 2: „ reis “ → „Reis“), `sucheUeberKatalog` (`val c = IngredientCatalog.canonicalName("Zwiebeln")`, Vorbedingung `c != "Zwiebeln"`; Liste mit Name `c`, Suche „Zwiebeln“ → gefunden). `ZutatenRepositoryLesenTest` (Room im Speicher): `listeMitKcalUndRezepten` (`energyKj = 1502` → `kcal = 359`; zwei Zeilen in r1 und eine in r2 → `rezepte = 2`; ohne kJ → `kcal = null`), `eigenNachId` (`seed-reis` → `false`, `eigen-1` → `true`), `sortiertDeutsch` („Zucker“, „äpfel“, „Birne“ → „äpfel“, „Birne“, „Zucker“), `anzahlZaehlt`, `verwendetInSortiert` („Reispfanne“, „curry“ → „curry“, „Reispfanne“), `synonymeOhneEigenenNamen`.
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest --tests "*zutaten*"` → rot (fehlt).
- [ ] **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6e2: Katalog lesen und filtern"`.

### Task 3: Editor-Entwurf und Prüfung

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/zutaten/ZutatEntwurf.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/zutaten/ZutatEntwurfTest.kt`

**Interfaces:**
- Produces: `enum class ZutatFeld { NAME, KCAL, EIWEISS, KOHLENHYDRATE, ZUCKER, FETT, BALLASTSTOFFE, SALZ, DICHTE, STUECKGEWICHT, STRICHCODE }`; `data class ZutatEntwurf(val name: String, val kategorie: String?, val basis: NutrientBasis?, val kcal: String, val eiweiss: String, val kohlenhydrate: String, val zucker: String, val fett: String, val ballaststoffe: String, val salz: String, val dichte: String, val stueckgewicht: String, val strichcode: String, val quelle: String)` mit `companion object { fun aus(z: ZutatEntity): ZutatEntwurf }` (Zuordnung `protein`/`carbs`/`sugar`/`fat`/`fiber`/`salt`; kcal = `NutritionResult.kcal(energyKj).toString()`; Zahlen über `zahlText`; `null` → „“); `fun pruefen(vorher: ZutatEntwurf, nachher: ZutatEntwurf): Map<ZutatFeld, String>` (nur Felder, deren Text sich unterscheidet; Regeln und Texte aus Global Constraints); `fun nameBereinigt(text: String): String` (trimmen, Leerraum zu einem Leerzeichen); `internal fun zahlAus(text: String): BigDecimal?` (trimmen, `,` → `.`, leer oder mit `e`/`E` → `null`, sonst `ZahlGrenzen.lesen` aus `de.foody.api.protocol`); `internal fun zahlText(wert: BigDecimal?): String` (ohne Nullen am Ende, mit Komma).

- [ ] **Step 1: Tests** `ZutatEntwurfTest`: `ausStartwerten` (`energyKj` 1502 → „359“; `protein` 7.00 → „7“; Dichte 0.85 → „0,85“; `barcode` `null` → „“), `nurGeaenderteGeprueft` (vorher kcal „5000“, nachher gleich → keine Fehler; nachher „1200“ → `KCAL` = „0 bis 1.000“), `nameRegeln` („  “ → „Name fehlt“; 201 Zeichen → „Höchstens 200 Zeichen“; 200 → ok), `kcalRegeln` („-1“, „1e3“, „abc“, „1000,1“ → Fehler; „0“, „1000“, „359,4“ → ok), `naehrstoffRegeln` (Eiweiß „100,5“ → „0 bis 100“; „1,5“ und „1.5“ → ok), `dichteRegeln` („0“ und „5,1“ → Fehler; „5“ → ok), `stueckgewichtRegeln` („10000“ ok; „10001“, „0“ → Fehler), `strichcodeRegeln` („1234567“, „123456789012345“, „4001234A“ → „8 bis 14 Ziffern“; „12345678“ und „“ → ok), `nameBereinigtFasstZusammen` („  Rote   Zwiebel “ → „Rote Zwiebel“).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:data:testDebugUnitTest --tests "*ZutatEntwurfTest*"` grün. **Step 5:** Commit `"V3 Phase 6e2: Editor-Entwurf und Prüfung"`.

### Task 4: Anlegen, Speichern, Löschen, Zusammenführen

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/zutaten/{ZutatErgebnis,ZutatAenderungen}.kt`
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/zutaten/ZutatenRepository.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/zutaten/ZutatenRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 `zusammenfuehrenSchritte`; Task 2 DAO; Task 3 `ZutatEntwurf`, `pruefen`, `zahlAus`, `nameBereinigt`.
- Produces: `sealed interface ZutatErgebnis { data class Gespeichert(val id: String); data class Ungueltig(val fehler: Map<ZutatFeld, String>); data class NameVergeben(val id: String, val name: String); data class StrichcodeVergeben(val id: String, val name: String); data class InVerwendung(val rezepte: Int); data object Geloescht; data class Zusammengefuehrt(val id: String, val name: String); data object NichtGefunden }`.
- Produces: `internal fun ZutatEntity.mitAenderungen(vorher: ZutatEntwurf, nachher: ZutatEntwurf, mitName: Boolean = true): ZutatEntity` – nur Felder mit geändertem Text (bzw. geänderter `kategorie`/`basis`); kcal → `energyKj = NutritionResult.kcalToKj(kcal)`, leer → `null`; danach: hat ein Nährwert einen Wert → `nutrientBasis = nutrientBasis ?: PER_100_G`, sonst Basis und Quelle `null`; hat sich ein Nährwertfeld oder die Basis geändert und das Quellfeld nicht → Quelle „Eigene Angabe“ (sofern Nährwerte da).
- Produces in `ZutatenRepository` (jede Methode in `db.withTransaction`): `suspend fun anlegen(name: String): ZutatErgebnis` (UUID, `canonicalName = nameBereinigt(name)`, `category = IngredientCatalog.guessCategory(name)`, `createdAt = updatedAt = uhr()`; ungültig → `Ungueltig`; Name schon da → `NameVergeben`); `suspend fun speichern(id: String, vorher: ZutatEntwurf, nachher: ZutatEntwurf): ZutatErgebnis` (Reihenfolge: `pruefen` → Zeile laden (weg → `NichtGefunden`) → Namenskollision mit *anderer* Zutat per `equals(ignoreCase = true)` über `alle()` → Strichcode per `nachStrichcode(code, ausser = id)` → `mitAenderungen` mit `updatedAt = uhr()` schreiben → `Gespeichert(id)`; `SQLiteConstraintException` → `NameVergeben` der Zutat mit dem Namen); `suspend fun loeschen(id: String): ZutatErgebnis` (weg → `NichtGefunden`; `rezeptAnzahl > 0` → `InVerwendung`; sonst löschen → `Geloescht`); `suspend fun zusammenfuehren(vonId: String, nachId: String, entwurf: Pair<ZutatEntwurf, ZutatEntwurf>? = null): ZutatErgebnis` (gleiche ID oder eine fehlt → `NichtGefunden`; Entwurf ungültig → `Ungueltig`; Quelle im Speicher `mitAenderungen(…, mitName = false)`, nicht geschrieben; `zusammenfuehrenSchritte(db, quelle, ziel, uhr())` → `Zusammengefuehrt(ziel.id, ziel.canonicalName)`).

- [ ] **Step 1: Tests** `ZutatenRepositoryTest` (Room im Speicher mit `CALLBACK`; Outbox-Tests mit `SyncZustandEntity(active = true, …)`): `anlegenRaetKategorie` („Paprika“ → `category = IngredientCatalog.guessCategory("Paprika")`, nicht `seed-`), `anlegenNameVergeben` („reis“ bei vorhandenem „Reis“ → `NameVergeben(reisId, "Reis")`), `speichernNurGeaenderteFelder` (kJ 1502, nur Dichte „0,85“ geändert → Dichte 0.85, `energyKj` exakt 1502), `syncStandBleibt` (Entwurf von vorher; Zeile danach direkt mit `pieceWeightG = 50` geschrieben; Speichern mit geänderter Dichte → Stückgewicht 50), `kcalGeaendertWirdKj` (Zutat ohne Nährwerte, kcal „100“ → `energyKj.compareTo(BigDecimal("418.4")) == 0`, Basis `PER_100_G`, Quelle „Eigene Angabe“), `quelleVomNutzerBleibt` (kcal und Quelle „Packung“ geändert → Quelle „Packung“), `alleNaehrwerteLeer` (alle Felder geleert → Basis und Quelle `null`), `eigenenNamenUmschreiben` (Review Focus 1: „reis“ → „Reis“ → `Gespeichert`), `nameVergebenAuchUmlaute` („Äpfel“ vorhanden, andere Zutat → „äpfel“ → `NameVergeben`), `strichcodeVergeben`, `ungueltigSpeichertNichts`, `zeileWegNichtGefunden`, `loeschenUnbenutzt`, `loeschenBenutzt` (zwei Rezepte → `InVerwendung(2)`, Zutat noch da), `zusammenfuehrenHaengtAllesUm` (Quelle in Rezeptzeile, Einkaufsartikel, Tagebucheintrag; Ziel ohne Dichte → alle drei zeigen aufs Ziel, Ziel hat Dichte der Quelle, Quelle weg, Outbox enthält `(RecordType.RECIPE.wire, r1)` und `(RecordType.INGREDIENT.wire, quelleId)` mit `deleted = true`), `zusammenfuehrenMitEntwurf` (Ziel ohne Nährwerte, Entwurf der Quelle kcal „200“ → Ziel `energyKj` = `kcalToKj(200)`), `zusammenfuehrenGleicheOderFehlendeIds` (`NichtGefunden`, Bestand unverändert).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6e2: Zutaten anlegen, speichern, löschen, zusammenführen"`.

### Task 5: Namen vereinheitlichen

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/zutaten/Angleichungen.kt`
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/zutaten/ZutatenRepository.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/zutaten/AngleichungenTest.kt`, `ZutatenRepositoryTest.kt`

**Interfaces:**
- Produces: `sealed interface Angleichung { data class Umbenennen(val id: String, val alt: String, val neu: String); data class Zusammenfuehren(val vonId: String, val vonName: String, val nachId: String, val nachName: String); data class KategorieErgaenzen(val id: String, val name: String, val kategorie: String) }`; `fun angleichungen(zutaten: List<ZutatEntity>): List<Angleichung>` – Algorithmus laut Spec 1. (Reihenfolge Name ohne Groß/klein, dann ID; Stand nach den bisherigen Schritten simulieren; Ziel `canonicalName`; andere Zutat mit diesem Namen ohne Groß/klein → `Zusammenfuehren` in sie; sonst `Umbenennen`, wenn der Name exakt abweicht; danach `KategorieErgaenzen`, wenn Kategorie leer und `guessCategory(neuer Name) != null`); `suspend fun vereinheitlichenVorschau(): List<Angleichung>`; `suspend fun vereinheitlichen(): Int` (in der Transaktion neu berechnen und ausführen; `Zusammenfuehren` über `zusammenfuehrenSchritte` mit den aktuellen Zeilen; Rückgabe = Zahl der Schritte).

- [ ] **Step 1: Tests** `AngleichungenTest` (mit `val c = IngredientCatalog.canonicalName("Zwiebeln")`, Vorbedingung `c != "Zwiebeln"`): `pluralWirdUmbenannt` (nur „Zwiebeln“ → `[Umbenennen(id, "Zwiebeln", c)]` + ggf. `KategorieErgaenzen`), `kollisionWirdZusammengefuehrt` („Zwiebeln“ und `c` → `Zusammenfuehren(zwiebelnId, "Zwiebeln", cId, c)`), `zweiSchreibweisenEineBleibt` („Zwiebeln“ id a, „zwiebeln“ id b → `[Umbenennen(a, "Zwiebeln", c), Zusammenfuehren(b, "zwiebeln", a, c)]`), `kategorieErgaenzt`, `unbekannterNameBleibt`, `einheitlichLeer`. `ZutatenRepositoryTest`: `vereinheitlichenWieVorschau` (a „Zwiebeln“, b „zwiebeln“ mit Rezeptzeile → Vorschau hat 2 Umbenennen/Zusammenführen-Schritte; `vereinheitlichen()` = Größe der Vorschau; danach nur a mit Name `c`, Rezeptzeile zeigt auf a).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6e2: Namen vereinheitlichen"`.

### Task 6: Zusammenführen über den Sync

**Files:**
- Test: `android/core/sync/src/test/kotlin/de/foody/core/sync/SyncLaufTest.kt`

**Interfaces:**
- Consumes: `ZutatenRepository(db, uhr).zusammenfuehren(vonId, nachId)` aus Task 4; Helfer `geraet`, `rezeptMitZutat`, `zutat` im Test.

- [ ] **Step 1: Tests** `zusammenfuehrenKommtAufZweitemGeraetAn` (A: Zutaten `z-alt` „Zwiebeln“ und `z-neu` „Zwiebel“, Rezept r1 mit `z-alt`; A und B synchronisieren; A führt `z-alt` in `z-neu` zusammen und synchronisiert; B synchronisiert → B: Zeile von r1 zeigt auf `z-neu`, `z-alt` weg; Outbox beider Geräte leer, `probleme()` leer). `zusammenfuehrenMitOffenerAenderungAufB` (wie oben, aber B ändert r1 lokal (Name „Rezept r1 B“) vor A’s Sync; danach Sync A, B, A → B hat `z-alt` noch; auf beiden Geräten existiert r1, und jede Zeile von r1 zeigt auf eine vorhandene Zutat).
- [ ] **Step 2:** `./gradlew :core:sync:testDebugUnitTest --tests "*SyncLaufTest*"` → erwartet **grün** (Absicherung bestehender Abläufe). Gegenprobe: in `zusammenfuehrenSchritte` `rezeptZeilenUmhaengen` auskommentieren → erster Test rot; zurücknehmen.
- [ ] **Step 3:** Commit `"V3 Phase 6e2: Zusammenführen über den Sync abgesichert"`.

### Task 7: Modul `:feature:zutaten` – Liste

**Files:**
- Modify: `settings.gradle.kts` (`include(":feature:zutaten")`, `projectDir = file("android/feature/zutaten")`), `shared/domain/src/main/kotlin/de/foody/domain/IngredientCatalog.kt` (`val categories: List<String>` = Namen aus `categoryRules` in deren Reihenfolge)
- Create: `android/feature/zutaten/build.gradle.kts` (wie `:feature:einstellungen`, ohne `:core:sync`; mit `:core:data`, `:core:designsystem`, `activity.compose` für `BackHandler`), `android/feature/zutaten/src/main/kotlin/de/foody/feature/zutaten/{ZutatenMeldungen,ZutatenTexte,ZutatenListeViewModel,ZutatenListeScreen,ListeDialoge}.kt`
- Test: `android/feature/zutaten/src/test/kotlin/de/foody/feature/zutaten/{ZutatenTestHilfen,ZutatenTexteTest,ZutatenListeViewModelTest,ZutatenListeScreenTest}.kt`

**Interfaces:**
- Consumes: Tasks 2, 4, 5.
- Produces: `@Singleton class ZutatenMeldungen @Inject constructor()` mit `fun senden(text: String)` und `val fluss: Flow<String>` (`Channel(BUFFERED)`, ein Empfänger: die Liste). `ZutatenTexte.kt`: `fun verwendungText(rezepte: Int)`, `fun zaehlzeile(treffer: List<ZutatKurz>)`, `fun kcalText(kcal: Int?)`, `fun vorschauZeilen(a: List<Angleichung>, max: Int = 8): List<String>` (nur Umbenennen/Zusammenführen, danach „und <n> weitere“), `fun kategorienText(n: Int)`, `fun angepasstText(n: Int)`, `fun verwendetInText(namen: List<String>)` (bis 2 Namen, dann „und 1 weiteres“ / „und <n> weitere“), `fun zusammenfuehrenFrage(von: String, nach: String, rezepte: Int)`, `fun inVerwendungText(rezepte: Int)`.
- Produces: `@HiltViewModel class ZutatenListeViewModel @Inject constructor(repo: ZutatenRepository, meldungen: ZutatenMeldungen)`: `data class ListeZustand(val suche: String = "", val filter: Set<ZutatFilter> = emptySet(), val treffer: List<ZutatKurz> = emptyList(), val geladen: Boolean = false, val laeuft: Boolean = false)`; `val zustand: StateFlow<ListeZustand>`; `val vorschau: StateFlow<List<Angleichung>?>` (`null` = kein Dialog); `fun suchen(text: String)`, `fun filterUmschalten(f: ZutatFilter)`, `fun vorschauLaden()`, `fun vorschauSchliessen()`, `fun anwenden()` (ignoriert, solange `laeuft`), `fun anlegen(name: String)`; `sealed interface ListeEreignis { data class Oeffnen(val id: String); data class GibtEsSchon(val id: String, val name: String); data class Meldung(val text: String) }`, `val ereignisse: Flow<ListeEreignis>` (Meldungen aus `ZutatenMeldungen` werden zu `Meldung`).
- Produces: `@Composable fun ZutatenListeRoute(onZutat: (String) -> Unit, onZurueck: () -> Unit, vm: ZutatenListeViewModel = hiltViewModel())` – Kopf: Zurück, Augenbraue „Einstellungen“, Titel „Zutatenkatalog“, Menü mit „Namen vereinheitlichen“; Suchfeld „Suchen“; Chips (`FoodyChip`) in `FlowRow`; Zählzeile; `LazyColumn` (Kategorie klein, Name, rechts `kcalText`, darunter `verwendungText`); „Keine Zutat gefunden“; FAB „+“ mit Beschreibung „Neue Zutat“; Snackbar; Inhalt max. 720 dp. `ListeDialoge.kt`: `NeueZutatDialog`, `VereinheitlichenDialog` (Texte aus Global Constraints).

- [ ] **Step 1: Tests** `ZutatenTexteTest` (alle Texte inkl. Einzahl/Mehrzahl und Grenzen: 8 Vorschauzeilen + „und 2 weitere“ bei 10; `verwendetInText` mit 0, 1, 2, 3, 5 Namen; `zusammenfuehrenFrage` mit 0, 1, 3). `ZutatenListeViewModelTest` (Room + echtes Repository): `filterUndSuche`, `anlegenOeffnet`, `anlegenGibtEsSchon`, `vorschauUndAnwenden` („Zwiebeln“/„zwiebeln“ → Vorschau 2 Schritte → `anwenden()` → `Meldung("2 Zutaten angepasst")`, Vorschau `null`), `anwendenDoppeltippEinmal` (Review Focus 4: zwei Aufrufe direkt hintereinander → genau eine Meldung), `meldungAusEditorKommtAn` (`meldungen.senden("Gelöscht")` → `Meldung("Gelöscht")`). `ZutatenListeScreenTest`: `zeilen` („359 kcal“, „in 2 Rezepten“, „– kcal“, „unbenutzt“), `chipFiltert` („Unbenutzt“ → benutzte Zeile verschwindet), `leereSuche` („Keine Zutat gefunden“), `neueZutat` (FAB → „Neue Zutat“ → Name → „Anlegen“ → `onZutat` mit neuer ID), `vereinheitlichen` (Menü → Dialog mit „Zwiebeln → <c>“ → „Anwenden“ → Snackbar), `schonEinheitlich` (leere Vorschau → „Alle Namen sind schon einheitlich.“ mit „OK“), `schrift200`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:zutaten:testDebugUnitTest :domain:test` grün. **Step 5:** Commit `"V3 Phase 6e2: Zutatenliste"`.

### Task 8: Editor-ViewModel

**Files:**
- Create: `android/feature/zutaten/src/main/kotlin/de/foody/feature/zutaten/ZutatEditorViewModel.kt`
- Test: `android/feature/zutaten/src/test/kotlin/de/foody/feature/zutaten/ZutatEditorViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 2–4, 7 (`ZutatenMeldungen`, Texte).
- Produces: `@HiltViewModel class ZutatEditorViewModel @Inject constructor(repo: ZutatenRepository, meldungen: ZutatenMeldungen)`: `data class EditorZustand(val geladen: Boolean = false, val vorher: ZutatEntwurf? = null, val entwurf: ZutatEntwurf? = null, val fehler: Map<ZutatFeld, String> = emptyMap(), val synonyme: List<String> = emptyList(), val verwendetIn: List<String> = emptyList(), val ziele: List<ZutatKurz> = emptyList(), val laeuft: Boolean = false)` mit `val geaendert: Boolean get() = entwurf != vorher`; `fun laden(id: String)` (einmal; erster Stand → `vorher` = `entwurf` = `ZutatEntwurf.aus`; `ziele` = Liste ohne `id`); `fun aendern(neu: ZutatEntwurf)`; `fun feldFertig(f: ZutatFeld)` (Fehler sichtbar = `pruefen(vorher, entwurf)` gefiltert auf die fertigen Felder); `fun speichern()` (alle Fehler sichtbar; gültig → Repository); `fun loeschen()`; `fun zusammenfuehren(nachId: String)` (mit `vorher to entwurf`, wenn `geaendert`). `sealed interface EditorEreignis { data object Fertig; data class NameVergeben(val id: String, val name: String); data class InVerwendung(val rezepte: Int); data class Meldung(val text: String) }`, `val ereignisse: Flow<EditorEreignis>`. Ergebnisse: `Gespeichert` → `Fertig`; `Geloescht` → `meldungen.senden("Gelöscht")` + `Fertig`; `Zusammengefuehrt` → „Zusammengeführt in „<nach>““ über `meldungen` + `Fertig`; `StrichcodeVergeben` → Feldfehler `STRICHCODE` = „Gehört schon zu „<Name>““; `NichtGefunden` beim Zusammenführen → `Meldung("Die Zutat gibt es nicht mehr")`; Zutat wird nach dem Laden `null` → `meldungen.senden("Diese Zutat gibt es nicht mehr")` + `Fertig`. Zweiter Tipp während `laeuft` wird ignoriert.

- [ ] **Step 1: Tests** `ZutatEditorViewModelTest`: `laedtEntwurf`, `aendernMarkiertGeaendert`, `fehlerErstNachFeldFertig` (kcal „5000“ → keine Fehler; `feldFertig(KCAL)` → „0 bis 1.000“), `speichernZeigtAlleFehler`, `speichernFertig`, `nameVergebenEreignis`, `strichcodeVergebenAmFeld`, `loeschenUnbenutzt` (`ZutatenMeldungen` liefert „Gelöscht“, `Fertig`), `loeschenInVerwendung` (`InVerwendung(2)`), `zusammenfuehrenMitEntwurf` (kcal-Eingabe landet im Ziel; Meldung „Zusammengeführt in „Zwiebel““), `zieleOhneSichSelbst` (Review Focus 3), `zutatVerschwindet` (Zeile per DAO löschen → Meldung + `Fertig`), `doppeltippSpeichertEinmal` (Review Focus 4).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:zutaten:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6e2: Editor-ViewModel"`.

### Task 9: Editor-Oberfläche

**Files:**
- Create: `android/core/designsystem/src/main/kotlin/de/foody/core/designsystem/komponenten/VerwerfenFrage.kt` (aus `feature/rezepte/.../editor/EditorScreen.kt` verschoben, öffentlich; Rezepteditor importiert sie), `android/feature/zutaten/src/main/kotlin/de/foody/feature/zutaten/{ZutatEditorScreen,EditorFelder,EditorDialoge,ZielwahlBlatt}.kt`
- Modify: `android/feature/rezepte/src/main/kotlin/de/foody/feature/rezepte/editor/EditorScreen.kt`
- Test: `android/feature/zutaten/src/test/kotlin/de/foody/feature/zutaten/ZutatEditorScreenTest.kt`

**Interfaces:**
- Consumes: Task 8.
- Produces: `@Composable fun ZutatEditorRoute(id: String, onFertig: () -> Unit, vm: ZutatEditorViewModel = hiltViewModel())` (`LaunchedEffect(id) { vm.laden(id) }`; `BackHandler(geaendert)` → `VerwerfenFrage`); Kopf: Zurück, Augenbraue „Zutat“, Titel = Name, Menü „Zusammenführen mit …“, „Löschen“; „Speichern“ unten fest; Inhalt scrollbar, max. 720 dp. `EditorFelder.kt`: Name mit „Auch: …“; Abschnitt „Kategorie“ mit Chips `IngredientCatalog.categories` + „Keine“ + ein freier Wert als eigener Chip; „Nährwerte“ mit Chips „je 100 g“/„je 100 ml“, Dezimalfeldern und „Quelle“; „Umrechnung“; „Strichcode“ (Zifferntastatur); `verwendetInText`. Feldfehler als `supportingText`, `ImeAction.Done`/Fokusverlust → `feldFertig`. `EditorDialoge.kt`: Löschen-Frage, „In Verwendung“ (Knöpfe „Zusammenführen …“/„Abbrechen“), Name-vergeben-Frage („Zusammenführen“/„Abbrechen“ → Bestätigung), Zusammenführen-Bestätigung (`zusammenfuehrenFrage`). `ZielwahlBlatt.kt`: `ModalBottomSheet(skipPartiallyExpanded = true)`, Titel „Zusammenführen mit …“, Suchfeld „Suchen“, Liste aus `filtern(ziele, suche, emptySet())`.

- [ ] **Step 1: Tests** `ZutatEditorScreenTest`: `felderZeigenWerte` („359“, „0,85“), `synonymeSichtbar`, `kategorieChipSetzt` („Kühlregal“), `freieKategorieAlsChip` (v1-Wert „Gewürzregal“ sichtbar und ausgewählt), `feldfehlerNachFertig` (kcal „5000“ + IME Done → „0 bis 1.000“), `zurueckMitAenderungFragt` („Änderungen verwerfen?“), `loeschenFragt`, `inVerwendungBietetZusammenfuehren` (Dialog → „Zusammenführen …“ öffnet das Blatt), `zielwahlOhneSichSelbst` (Review Focus 3), `zusammenfuehrenBestaetigt` („3 Rezepte werden umgestellt“ → „Zusammenführen“ → `onFertig`), `nameVergebenFragtZusammenfuehren` (Name einer anderen Zutat → „Speichern“ → „„Zwiebel“ gibt es schon. Diese Zutat damit zusammenführen?“ → „Zusammenführen“ → Bestätigung), `verwendetIn` („Verwendet in: curry, Reispfanne“), `schrift200`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:zutaten:testDebugUnitTest :feature:rezepte:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6e2: Editor-Oberfläche"`.

### Task 10: Navigation und Einstellungen

**Files:**
- Modify: `android/app3/build.gradle.kts` (`implementation(project(":feature:zutaten"))`), `android/app3/src/main/kotlin/de/foody/app3/navigation/{Ziele,FoodyNavHost}.kt`, `android/feature/einstellungen/src/main/kotlin/de/foody/feature/einstellungen/{EinstellungenViewModel,EinstellungenScreen,EinstellungenAbschnitte}.kt`
- Test: `android/app3/src/test/kotlin/de/foody/app3/navigation/NavigationTest.kt`, `android/feature/einstellungen/src/test/kotlin/de/foody/feature/einstellungen/{EinstellungenTestHilfen,EinstellungenViewModelTest,EinstellungenScreenTest}.kt`

**Interfaces:**
- Produces: `@Serializable data object Zutaten`, `@Serializable data class Zutat(val id: String)`; `Bildschirme.zutaten: @Composable (NavHostController) -> Unit` (echt `ZutatenListeRoute(onZutat = { nav.navigate(Zutat(it)) }, onZurueck = { nav.popBackStack() })`, Platzhalter „Zutatenkatalog“), `Bildschirme.zutat: @Composable (String, NavHostController) -> Unit` (echt `ZutatEditorRoute(id, onFertig = { nav.popBackStack() })`, Platzhalter „Zutat <id>“); `EinstellungenRoute(onServer, onZutaten: () -> Unit, onZurueck, …)` (echt `onZutaten = { nav.navigate(Zutaten) }`); `EinstellungenViewModel(…, zutaten: ZutatenRepository, …)` → `EinstellungenZustand.zutaten: Int? = null`; `internal fun ZutatenAbschnitt(anzahl: Int?, onZutaten: () -> Unit)` zwischen Tagesziel und Sicherung: Abschnitt „Zutaten“, ganze Zeile „Zutatenkatalog“, rechts Anzahl und „›“.

- [ ] **Step 1: Tests** `NavigationTest.einstellungenZumZutatenkatalog` (Einstellungen → `Zutaten` → „Zutatenkatalog“ → `Zutat("z1")` → „Zutat z1“ → Zurück → „Zutatenkatalog“). `EinstellungenViewModelTest.zutatenAnzahl` (3 Zutaten → `zustand.zutaten == 3`). `EinstellungenScreenTest.zutatenZeile` („ZUTATEN“, „Zutatenkatalog“, „3“; Antippen → `onZutaten`).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:einstellungen:testDebugUnitTest :app3:testDebugUnitTest :app3:assembleDebug` grün. **Step 5:** Commit `"V3 Phase 6e2: Navigation und Einstellungen"`.

### Task 11: Screenshots, Sichtprüfung, Abschluss

**Files:**
- Create: `android/feature/zutaten/src/test/kotlin/de/foody/feature/zutaten/ZutatenScreenshotTest.kt`, Referenzbilder in `android/feature/zutaten/src/test/screenshots/`
- Modify: Referenzbilder `:feature:einstellungen`, `.github/workflows/ci.yml` (`:feature:zutaten:verifyRoborazziDebug`), `docs/architecture.md` (Abschnitt „Zutatenkatalog (Phase 6e2)“), `ToDoS/v3-plan.md` (6e2 erledigt, 6e3 offen), `ToDoS/v3-abnahme.md` (Zutatenkatalog-Zeile: Vermerk „ohne Scan, folgt 6e3“)

- [ ] **Step 1:** `ZutatenScreenshotTest`: Liste hell/dunkel (Handy `w411dp-h891dp`), Liste gefiltert ohne Treffer, Editor Handy hell und Tablet `w1280dp-h1200dp` (ohne Eingabe), Vorschau-Dialog, Zielwahl-Blatt und „In Verwendung“-Dialog als ganzer Bildschirm (`captureScreenRoboImage`).
- [ ] **Step 2:** `recordRoborazziDebug` für `:feature:zutaten` und `:feature:einstellungen`, Bilder ansehen, Befunde beheben; `verifyRoborazziDebug` grün, Gegenprobe rot.
- [ ] **Step 3:** Sichtprüfung auf `Foody_Test` (Port 5556, AVD-Name prüfen) gegen den Test-Server laut Spec 4.; Screenshots an den Nutzer; danach Testdaten zurück, App deinstallieren, Emulator und Tunnel beenden.
- [ ] **Step 4:** Zeilencheck (`src/main` ≤ 300); `./gradlew check :app3:assembleDebug` und alle `verifyRoborazziDebug` (katalog, heute, rezepte, kochen, woche, einkauf, einstellungen, zutaten) grün.
- [ ] **Step 5:** Doku; Commit `"V3 Phase 6e2: Doku und Abschluss"`; Gesamtreview; PR gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 (4 braucht 1–3; 5 braucht 1 und 4; 6 braucht 4; 7 braucht 2, 4, 5; 8 braucht 3, 4, 7; 9 braucht 8; 10 braucht 7 und 9). Ein Branch, ein PR am Ende.
