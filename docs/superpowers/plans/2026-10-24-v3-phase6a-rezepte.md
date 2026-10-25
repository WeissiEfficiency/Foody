# V3 Phase 6a Rezepte – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Bereich „Rezepte“ der V3: Liste, Detail, Editor mit Freitext-Zutaten, Fotos und Markdown-Import, auf dem Tablet zweispaltig.

**Architecture:** Zeilen-Zerlegung in `:domain` (`ZutatZeile`, gemeinsam mit dem Markdown-Import), Lesen/Schreiben in `:core:data` (`RezeptRepository`, `RezeptImportDienst`), Oberfläche im neuen `:feature:rezepte`; `:app3` hängt die Ziele ein. Kein Schema-Wechsel.

**Tech Stack:** Kotlin 2.4.20, Compose (BOM 2026.09.00), Navigation Compose 2.10.2 (typisierte Routen), Hilt 2.60.1, Room 2.8.5, Coil 3.6.3, Robolectric 4.16.1, Roborazzi 1.76.0.

**Spec:** `docs/superpowers/specs/2026-10-24-v3-phase6a-rezepte-design.md`

## Global Constraints

- v1 unverändert: `android/app/**`, `:sync-protocol`; Server-Code unverändert; v1-Code nur als Vorlage lesen.
- Kein Schema-Wechsel: Datenbank bleibt Version 10, `android/core/data/schemas` unverändert.
- `:feature:rezepte` = `android/feature/rezepte`, Paket `de.foody.feature.rezepte`, Plugin `foody.android.feature` + `ksp` + `hilt` (wie `:feature:heute`); hängt nur von `:core:*` und `:domain` ab.
- UI nur aus `:core:designsystem`; keine eigenen Farben/Schriften. Jede Datei in `android/feature/rezepte/src/main` ≤ 300 Zeilen; je Bildschirm ein ViewModel (`hiltViewModel()`).
- kcal und Nährwerte nur über `RecipeProfiles`/`NutritionCalculator` mit `alsRecipe`/`alsIngredient` aus `DomainAbbildung` – Karte, Detail und „Heute“ gleich.
- Zahlen aus Eingaben nur über `ZahlGrenzen` (kein Exponent); Namen ≤ 200 Zeichen; Portionen 1–99; Zeiten 0–1440 Minuten.
- Robolectric-UI-Tests: `@GraphicsMode(NATIVE)`, `junit4.v2.createComposeRule`, `application = android.app.Application::class`; Textfelder in Dialog/Blatt **ohne** Breiten-Qualifier (sonst hängt Robolectric); Room im Test mit `setQueryExecutor { it.run() }`.
- Deutsch in Texten, Bezeichnern, Kommentaren; `allWarningsAsErrors` bleibt an.
- PR gegen `main` mit Auto-Merge; „Server aktualisieren? Nein“.

## Review Focus

1. **Derselbe Ordner zweimal importiert** – zweiter Lauf: alles „schon vorhanden“, keine Dubletten, Zahlen in der Zusammenfassung stimmen (Test in Task 4).
2. **Duplikat eines importierten Rezepts** – Kopie ohne Quelle und ohne Favorit (wie v1), erneuter Import überspringt das Original weiter (Test in Task 3).
3. **Schwierige Zutatzeilen** – „1 Dose Tomaten“, „2-3 EL Olivenöl“, „Salz und Pfeffer“, „1 Prise Salz“, „etwas Petersilie“, „½ Bund Dill, gehackt“, „200 g Mehl (optional)“: dieselbe Zerlegung wie der Markdown-Import (Test in Task 1).
4. **Doppeltipp auf „Speichern“** im Editor – genau ein Rezept (Test in Task 7).
5. **Rezept per Sync gelöscht, während der Editor offen ist** – Speichern legt kein Rezept unter neuer ID an, sondern meldet „Rezept wurde inzwischen gelöscht“ (Test in Task 3 und 7).

---

### Task 1: `ZutatZeile` in `:domain`

**Files:**
- Create: `shared/domain/src/main/kotlin/de/foody/domain/ZutatZeile.kt`
- Modify: `shared/domain/src/main/kotlin/de/foody/domain/MarkdownRecipeImporter.kt` (`buildIngredient` → `internal`, sonst unverändert)
- Test: `shared/domain/src/test/kotlin/de/foody/domain/ZutatZeileTest.kt`

**Interfaces:**
- Produces: `object ZutatZeile { fun lesen(text: String): ImportedIngredient?; fun schreiben(menge: BigDecimal?, einheit: MeasureUnit?, name: String, hinweis: String?, optional: Boolean): String; fun zeilen(text: String): List<ImportedIngredient?> }` – `lesen` liefert `null` für leere Zeilen und Zeilen ohne Namen; `zeilen` zerlegt einen mehrzeiligen Text (leere Zeilen entfallen, die Liste hat je nicht-leerer Zeile einen Eintrag).
- `lesen` teilt die Zeile in Mengen- und Namensteil und ruft `MarkdownRecipeImporter.buildIngredient(mengenteil, namensteil, group = null)`: Mengenteil = führende Zahl (`GermanAmounts.NUMBER`, auch Spanne „2-3“) plus folgende Wörter, solange sie klein geschrieben sind, eine bekannte Einheit (`GermanAmounts.unitOf`) oder ein Mengenwort sind (`MENGENWOERTER`, ohne Groß/klein: Prise, Bund, Dose, Glas, Zehe, Packung, Pck., Päckchen, Becher, Stück, Stk., Scheibe, Tasse, Handvoll, Msp., Zweig, Würfel, Blatt, Flasche, Tüte, Beutel, Stange, Kopf, Knolle, jeweils auch mit Endung -n/-e/-en); ohne Zahl ein führender vager Ausdruck aus `vagueAmounts` („etwas“, „evtl.“, „nach Belieben“ …). Das Wort „optional“ (auch „(optional)“) setzt `optional = true` und erscheint nicht im Hinweis.
- `schreiben`: Menge mit Komma ohne Nullen am Ende, Einheit als `symbol` außer `PIECE` (ohne Einheit), dann Name, dann „, Hinweis“, dann „ (optional)“.

- [ ] **Step 1: Tests** `ZutatZeileTest`:
  - `gleichWieMarkdownZweizeilig` (Review Focus 3): für jede Zeile aus `listOf("200 g Mehl, gesiebt", "1 Dose Tomaten", "2-3 EL Olivenöl", "1 Prise Salz", "½ Bund Dill, gehackt", "2 große Zwiebeln", "1,5 l Milch", "3 Eier")` gilt `lesen(z)` == die eine Zutat aus `MarkdownRecipeImporter.parse("# T\n## Zutaten\n<mengenteil>\n<namensteil>\n")` (Mengen-/Namensteil im Test von Hand getrennt).
  - `ohneMenge`: `lesen("Salz und Pfeffer")` → Name „Salz und Pfeffer“, `amount == null`, `unit == null`; `lesen("etwas Petersilie")` → Name „Petersilie“, Hinweis „etwas“.
  - `optional`: `lesen("200 g Mehl (optional)")` → `optional`, Hinweis `null`; `lesen("Zucker, optional")` ebenso.
  - `leer`: `lesen("   ")` und `lesen("200 g")` → `null`.
  - `rundweg`: für jede Zeile `z` der ersten Liste: `val a = lesen(z)!!; lesen(schreiben(a.amount, a.unit, a.name, a.note, a.optional)) == a`.
  - `zeilenUeberspringtLeere`: `zeilen("200 g Mehl\n\n3 Eier\n")` hat 2 Einträge.
- [ ] **Step 2:** `./gradlew :domain:test --tests "*ZutatZeileTest*"` → FAIL (`ZutatZeile` fehlt).
- [ ] **Step 3:** Umsetzen wie oben.
- [ ] **Step 4:** `./gradlew :domain:test` → alle grün (auch `MarkdownRecipeImporterTest`).
- [ ] **Step 5:** Commit `"V3 Phase 6a: Zutatzeilen lesen und schreiben"`.

### Task 2: `RezeptRepository` – Lesen

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/rezepte/{RezeptModelle,RezeptRepository}.kt`
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/db/Daos.kt` (neue Abfragen)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/rezepte/RezeptLesenTest.kt`

**Interfaces:**
- Consumes: `alsRecipe`, `alsIngredient` (`DomainAbbildung`), `RecipeProfiles`, `NutritionCalculator`, `RezeptEinordnung.einordnen`, `Mahlzeit.mengeAus`, `Gang.mengeAus`.
- DAO neu: `RezeptDao.alleFlow(): Flow<List<RezeptEntity>>`, `RezeptDao.nachIdFlow(id): Flow<RezeptEntity?>`, `ArbeitsschrittDao.nachRezept(recipeId): List<ArbeitsschrittEntity>` (nach `position`), `ArbeitsschrittDao.nachRezeptFlow(recipeId)`, `RezeptZutatDao.nachRezeptFlow(recipeId)`, `PlanEintragDao.gekochtJeRezept(): Flow<List<GekochtZahl>>` (`SELECT recipeId, COUNT(*) AS anzahl FROM meal_slot WHERE cookedAt IS NOT NULL GROUP BY recipeId`; `data class GekochtZahl(val recipeId: String, val anzahl: Int)`).
- Produces:
  - `data class RezeptKarte(val id: String, val name: String, val bild: String?, val kcalJePortion: Int?, val eiweissJePortion: Int?, val zeitMinuten: Int?, val favorit: Boolean, val gekocht: Int, val diaeten: Set<Diet>, val einordnung: Einordnung, val tags: List<String>, val zutatNamen: List<String>, val archiviert: Boolean, val angelegt: Long)`
  - `data class RezeptVoll(val rezept: RezeptEntity, val zeilen: List<RezeptZeile>, val schritte: List<String>, val naehrwerte: NutritionResult, val einordnung: Einordnung)`; `data class RezeptZeile(val zutatId: String, val name: String, val menge: BigDecimal, val einheit: MeasureUnit, val hinweis: String?, val optional: Boolean, val hatNaehrwerte: Boolean)`
  - `class RezeptRepository @Inject constructor(db: FoodyDatenbank, ablage: FotoAblage, uhr: Uhr)` (`fun interface Uhr { fun jetzt(): Long }`, in `DatenModul` als `System::currentTimeMillis`) mit `fun karten(): Flow<List<RezeptKarte>>` (alle, auch archivierte) und `fun voll(id: String): Flow<RezeptVoll?>`. Tags: Komma-getrennt, getrimmt, leere entfallen. Zeit = Summe aus Vorbereitung und Kochzeit (null, wenn beide fehlen).

- [ ] **Step 1: Tests** `RezeptLesenTest` (Room im Speicher mit `FoodyDatenbank.CALLBACK`): `karteMitKcalUndGekocht` (Rezept mit Reis 400 g/2 Portionen → `kcalJePortion == 717`; zwei gekochte Planeinträge → `gekocht == 2`), `gleicheKcalWieHeute` (`karten().first().single().kcalJePortion == HeuteRepository(db).rezeptVorschlaege().first().single().kcalJePortion`), `vollMitZeilenSchrittenUndNaehrwerten` (Zeilen in `sortOrder`, Schritte in `position`, `naehrwerte.perServing(ENERGY_KJ) == 3000`), `vollUnbekanntGibtNull`, `tagsUndZutatNamen` (Tags „ schnell, ,vegan “ → `["schnell","vegan"]`; `zutatNamen == ["Reis"]`), `archiviertBleibtInKarten` (`archiviert == true`).
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest --tests "*RezeptLesenTest*"` → FAIL.
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** Test grün.
- [ ] **Step 5:** Commit `"V3 Phase 6a: Rezepte lesen"`.

### Task 3: `RezeptRepository` – Schreiben

**Files:**
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/rezepte/{RezeptModelle,RezeptRepository}.kt`, `Daos.kt` (`RezeptZutatDao.loeschenFuerRezept`, `ArbeitsschrittDao.loeschenFuerRezept`)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/rezepte/RezeptSchreibenTest.kt`

**Interfaces:**
- Produces:
  - `data class RezeptEntwurf(val id: String?, val name: String, val portionen: Int, val vorbereitung: Int?, val kochzeit: Int?, val tags: String, val zeilen: List<ImportedIngredient>, val schritte: List<String>, val notizen: String?, val quelle: String?, val mahlzeiten: Set<Mahlzeit>?, val gaenge: Set<Gang>?, val bild: String?)` (`mahlzeiten`/`gaenge` `null` = vermuten)
  - `sealed interface Gespeichert { data class Ok(val id: String) : Gespeichert; data object Geloescht : Gespeichert }`
  - `suspend fun speichern(e: RezeptEntwurf): Gespeichert` – eine Transaktion; neue ID bei `id == null`; existiert die ID nicht (mehr) → `Geloescht`, nichts geschrieben; alte Zeilen/Schritte ersetzen; Zutaten auflösen per `nachNameOhneGross(name)`, sonst `nachNameOhneGross(IngredientCatalog.canonicalName(name))`, sonst anlegen mit `canonicalName` und `IngredientCatalog.guessCategory`; Zeilen ohne Menge → Menge 0, Einheit `PIECE`, Hinweis bleibt (wie Import); `favorite`, `rating`, `archivedAt`, `createdAt` bleiben; `updatedAt = uhr.jetzt()`, `version + 1`; Einordnung per `alsText(menge, bisher, entries)`.
  - `suspend fun entwurf(id: String): RezeptEntwurf?` (Zeilen als `ImportedIngredient` mit Zutatnamen; `mahlzeiten = Mahlzeit.mengeAus(...)`)
  - `suspend fun duplizieren(id: String): String?` – Kopie „Name (Kopie)“ ohne Quelle, ohne Favorit, Einordnung übernommen.
  - `suspend fun archivieren(id: String, archiviert: Boolean)`, `suspend fun favorit(id: String, an: Boolean)`.
  - `suspend fun fotoSetzen(id: String, link: String?)` – setzt `imageUri`; danach `ablage.unbenutzteEntfernen(alle imageUri)`.

- [ ] **Step 1: Tests** `RezeptSchreibenTest`: `anlegenMitNeuerZutat` (Zeile „200 g Quinoa“ → Zutat „Quinoa“ neu, Kategorie gesetzt), `vorhandeneZutatOhneGrossKlein` („200 g reis“ nutzt `z-reis`), `aendernErsetztZeilenBehaeltFavorit`, `duplizierenOhneQuelleUndFavorit` (Original mit `sourceUrl` und Favorit; Kopie: neue IDs für Rezept, Zeilen, Schritte; `sourceUrl == null`; `favorite == false`; `nachQuelle(url).size == 1`), `archivierenUndWiederherstellen`, `geloeschtWaehrendBearbeitung` (Entwurf von r1 holen, r1 löschen, `speichern` → `Geloescht`, `rezeptDao().alle()` leer), `beiAktivemSyncOutbox` (Typ `recipe`), `fotoErsetzenRaeumtAuf` (Datei des alten Links ist nach `fotoSetzen(id, neu)` weg), `entwurfRundweg` (`entwurf(speichern(e))` hat dieselben Zeilen).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6a: Rezepte schreiben"`.

### Task 4: `RezeptImportDienst`

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/rezepte/RezeptImportDienst.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/rezepte/RezeptImportDienstTest.kt`

**Interfaces:**
- Consumes: `RezeptRepository.speichern`, `MarkdownRecipeImporter.parse`, `RezeptImport.vorbereiten`, `RezeptImport.hinweis`, `RezeptDao.nachQuelle`.
- Produces: `data class ImportErgebnis(val importiert: List<String>, val uebersprungen: Int, val fehler: List<String>)` (Fehler = Dateinamen); `interface RezeptImporter { suspend fun dateien(uris: List<Uri>, onFortschritt: (Int, Int) -> Unit = { _, _ -> }): ImportErgebnis; suspend fun ordner(baum: Uri, onFortschritt: (Int, Int) -> Unit = { _, _ -> }): ImportErgebnis }` (in `DatenModul` an `RezeptImportDienst` gebunden); `class RezeptImportDienst @Inject constructor(@ApplicationContext kontext: Context, db: FoodyDatenbank, rezepte: RezeptRepository) : RezeptImporter` mit `suspend fun dateien(uris: List<Uri>, onFortschritt: (Int, Int) -> Unit = { _, _ -> }): ImportErgebnis` (Tag „Import“) und `suspend fun ordner(baum: Uri, onFortschritt: ...): ImportErgebnis` (Tag „Ideen“; Dateien `.md`/`.markdown`/`.txt`, nach Namen sortiert, ohne Unterordner). Verhalten wie v1 `RecipeImportRepository`: Vorgabe 4 Portionen, Notiz `RezeptImport.hinweis(quelle, portionen, geschaetzt, ausDatei)`, höchstens 1 MiB je Datei, Blöcke zu 25 Rezepten je Transaktion, Zutaten-Zwischenspeicher je Lauf, gleiche Quelle (auch archiviert) → übersprungen, kein Titel oder keine Zutaten → Fehler.

- [ ] **Step 1: Tests** `RezeptImportDienstTest` (Dateien im Cache-Ordner, `Uri.fromFile`): `importiertMitPortionenAusDatei` („Portionen: 2“ → `defaultServings == 2`, Notiz nur Quelle), `geschaetztMitHinweis`, `gleicheQuelleUebersprungen` (Review Focus 1: zweimal dieselben 3 Dateien → zweiter Lauf `importiert` leer, `uebersprungen == 3`, Rezepte insgesamt 3), `kaputteDateiZaehltAlsFehler` (Datei ohne `## Zutaten` und eine > 1 MiB → `fehler` enthält beide Namen, der Rest ist importiert), `tagImport`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen (Ordner-Abfrage über `DocumentsContract` wie v1; im Test nur `dateien`). **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6a: Markdown-Import"`.

### Task 5: `:feature:rezepte` und Rezeptliste

**Files:**
- Create: `android/feature/rezepte/build.gradle.kts` (wie `:feature:heute`, plus `coil-compose`, Roborazzi), `settings.gradle.kts`-Eintrag, `src/main/kotlin/de/foody/feature/rezepte/liste/{ListeFilter,RezeptListeViewModel,RezeptListeScreen,ListeBereiche}.kt`, `src/main/kotlin/de/foody/feature/rezepte/RezeptBild.kt` (wie in `:feature:heute`)
- Test: `src/test/kotlin/de/foody/feature/rezepte/liste/{ListeFilterTest,RezeptListeScreenTest}.kt`

**Interfaces:**
- Produces: `data class ListeFilter(val suche: String = "", val favoriten: Boolean = false, val oftGekocht: Boolean = false, val diaeten: Set<Diet> = emptySet(), val mahlzeit: Mahlzeit? = null, val tags: Set<String> = emptySet(), val archiv: Boolean = false, val sortierung: Sortierung = Sortierung.NAME)`; `enum class Sortierung { NAME, NEUESTE, KCAL, EIWEISS, OFT_GEKOCHT }`; `fun filtern(karten: List<RezeptKarte>, f: ListeFilter): List<RezeptKarte>` (Suche: Teilstring ohne Groß/klein in Name, Tags, Zutatnamen; Archiv-Schalter wie Spec; „Oft gekocht“ = `gekocht > 0`; Mahlzeit über `einordnung.passtZu`; `KCAL` aufsteigend, ohne kcal zuletzt; `EIWEISS`/`OFT_GEKOCHT`/`NEUESTE` absteigend; Gleichstand nach Name); `@Composable fun RezeptListeRoute(onOeffnen: (String) -> Unit, onNeu: () -> Unit, onImport: () -> Unit, ausgewaehlt: String? = null, vm: RezeptListeViewModel = hiltViewModel())`.

- [ ] **Step 1: Tests** `ListeFilterTest`: `sucheInZutaten`, `archivNurMitChip`, `favoritenUndOftGekocht`, `diaetVegan`, `sortierungKcalOhneWerteZuletzt`, `gleichstandNachName`. `RezeptListeScreenTest` (Room + echtes ViewModel): `rasterMitKcal` („717 kcal“), `leerZeigtAnlegenUndImportieren`, `keinTrefferZeigtZuruecksetzen` (Suche „xyz“ → „Nichts gefunden“, Tipp „Filter zurücksetzen“ → Rezept wieder da), `menuNeuUndImport`, `schrift200OhneAbschneiden`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:rezepte:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6a: Rezeptliste"`.

### Task 6: Rezeptdetail

**Files:**
- Create: `android/feature/rezepte/src/main/kotlin/de/foody/feature/rezepte/detail/{RezeptDetailViewModel,RezeptDetailScreen,DetailBereiche,Naehrwerttabelle}.kt`
- Test: `src/test/kotlin/de/foody/feature/rezepte/detail/RezeptDetailScreenTest.kt`

**Interfaces:**
- Consumes: `RezeptRepository.voll/favorit/archivieren/duplizieren`.
- Produces: `@Composable fun RezeptDetailRoute(id: String, onKochen: (String, Int) -> Unit, onBearbeiten: (String) -> Unit, onDupliziert: (String) -> Unit, onZurueck: () -> Unit, vm: RezeptDetailViewModel = hiltViewModel())`; `fun mengeSkaliert(menge: BigDecimal, von: Int, auf: Int): BigDecimal` (6 Nachkommastellen, ohne Nullen am Ende); Anzeige der Mengen mit `QuantityFormatter.format(Quantity.of(m, einheit), einheit)`, Menge 0 → nur Hinweis.

- [ ] **Step 1: Tests** `RezeptDetailScreenTest`: `zeigtZutatenSchritteUndKcal`, `portionenSkalieren` („+“ → „600 g“ statt „400 g“; „je Portion“ bleibt „717 kcal“), `naehrwerteMitVollstaendigkeit` (Zutat ohne Werte → „Für 1 von 2 Zutaten fehlen Werte“), `kochenMitPortionen` (`onKochen("r1", 3)` nach „+“), `archivierenMitRueckgaengig` (Snackbar „Rezept archiviert“, „Rückgängig“ → `archivedAt == null`), `archiviertZeigtWiederherstellen`, `nichtGefunden` („Rezept nicht gefunden“, „Zurück“ ruft `onZurueck`), `duplizierenOeffnetKopie`, `schrift200OhneAbschneiden`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6a: Rezeptdetail"`.

### Task 7: Editor mit Fotos

**Files:**
- Create: `android/feature/rezepte/src/main/kotlin/de/foody/feature/rezepte/editor/{EditorViewModel,EditorScreen,EditorBereiche,ZeilenVorschau,FotoWahl}.kt`, `android/feature/rezepte/src/main/AndroidManifest.xml` (FileProvider `${applicationId}.rezeptfotos`, Pfad `cache/kamera/`), `src/main/res/xml/rezeptfotos_pfade.xml`
- Test: `src/test/kotlin/de/foody/feature/rezepte/editor/{EditorViewModelTest,EditorScreenTest}.kt`

**Interfaces:**
- Consumes: `ZutatZeile.zeilen/schreiben`, `ZahlGrenzen`, `RezeptRepository.entwurf/speichern/fotoSetzen`, `FotoAblage.speichern(Uri)`, `ZutatDao.alle()` (für „im Katalog“/„neu“).
- Produces: `data class EditorEingaben(name, portionen: String, vorbereitung: String, kochzeit: String, tags, zutatenText, schritteText, notizen, quelle, mahlzeiten: Set<Mahlzeit>?, gaenge: Set<Gang>?, bild: String?)`; `data class ZeilenStatus(val zeile: String, val erkannt: ImportedIngredient?, val imKatalog: Boolean, val fehler: String?)`; `class EditorViewModel` mit `val zustand: StateFlow<EditorZustand>`, `val speichernMoeglich: StateFlow<Boolean>`, `val geaendert: StateFlow<Boolean>`, `fun laden(id: String?)`, `suspend fun speichern(): Gespeichert?` (Sperre gegen Doppeltipp wie `EintragenViewModel`), `suspend fun fotoUebernehmen(quelle: Uri)`; `@Composable fun EditorRoute(id: String?, onFertig: (String) -> Unit, onZurueck: () -> Unit, vm: EditorViewModel = hiltViewModel())`. Gültig: Name 1–200 Zeichen, Portionen 1–99, Zeiten leer oder 0–1440, jede nicht-leere Zutatzeile erkannt und Menge in `ZahlGrenzen`.

- [ ] **Step 1: Tests** `EditorViewModelTest`: `zeilenVorschauMitKatalog` („200 g Reis“ → im Katalog, „1 Bund Bärlauch“ → neu), `unerkannteZeileSperrt` („200 g“ → Fehler „Keine Zutat erkannt“), `zuGrosseMengeSperrt` („1e5 g Mehl“), `portionenGrenzen` (0 und 100 gesperrt), `rundwegBearbeiten` (`laden("r1")` → `zutatenText == "400 g Reis"`; unverändert speichern → Zeilen gleich), `doppeltippEinRezept` (Review Focus 4), `geloeschtMeldet` (Review Focus 5: `speichern()` → `Geloescht`, Zustand zeigt „Rezept wurde inzwischen gelöscht“), `automatischeEinordnung` („Automatisch“ setzt `mahlzeiten = null`). `EditorScreenTest` (ohne Breiten-Qualifier): `neuesRezeptSpeichern` (Name, „200 g Reis“, „Kochen“ → `onFertig(id)`, Rezept in DB), `verwerfenFragt` (Änderung, Zurück → „Änderungen verwerfen?“), `ueberstehtNeuaufbau` (`StateRestorationTester`, VM außerhalb der Komposition, Text bleibt), `schrift200OhneAbschneiden`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. Fotos: Photo-Picker `PickVisualMedia(ImageOnly)` → `fotoUebernehmen(uri)`; Kamera `TakePicture` in eine Datei unter `cache/kamera/` über den FileProvider, danach `fotoUebernehmen(Uri.fromFile(datei))` und Datei löschen; Fehler → Hinweis „Foto konnte nicht übernommen werden“, altes Bild bleibt. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6a: Rezept-Editor"`.

### Task 8: Import-Blatt, Navigation, Tablet

**Files:**
- Create: `android/feature/rezepte/src/main/kotlin/de/foody/feature/rezepte/import/{ImportViewModel,ImportBlatt}.kt`, `android/feature/rezepte/src/main/kotlin/de/foody/feature/rezepte/RezepteBereich.kt`
- Modify: `android/app3/build.gradle.kts` (`:feature:rezepte`), `android/app3/src/main/kotlin/de/foody/app3/navigation/{Ziele,FoodyNavHost}.kt` (Routen `RezeptNeu`, `RezeptBearbeiten(id)`; `Bildschirme` um `rezepte`, `rezeptDetail`, `editor` erweitert, Platzhalter für Tests)
- Test: `android/feature/rezepte/src/test/kotlin/de/foody/feature/rezepte/{ImportBlattTest,RezepteBereichTest}.kt`, `android/app3/src/test/kotlin/de/foody/app3/navigation/{NavigationTest,DeepLinkTest}.kt` (anpassen)

**Interfaces:**
- Produces: `@Composable fun RezepteBereich(onOeffnen: (String) -> Unit, onNeu: () -> Unit, onBearbeiten: (String) -> Unit, onKochen: (String, Int) -> Unit)` – unter 840 dp nur die Liste (Navigation zu `RezeptDetail`), ab 840 dp Liste links (360 dp) und Detail/Editor rechts (Auswahl per `rememberSaveable`); `ImportViewModel` mit `val zustand: StateFlow<ImportZustand>` (`Bereit`, `Laeuft(fertig, gesamt)`, `Fertig(ImportErgebnis)`), Zusammenfassung „n importiert · m übersprungen (schon vorhanden) · k Fehler: a.md, b.md“.

- [ ] **Step 1: Tests** `ImportBlattTest` (Ersatz für `RezeptImporter` aus Task 4): `zusammenfassungMitFehlern`, `fortschritt`. `RezepteBereichTest`: `tabletZweispaltig` (`w1280dp-h800dp`: Liste und nach Tipp das Detail nebeneinander), `handyNavigiert` (`w411dp-h891dp`: Tipp ruft `onOeffnen`). `NavigationTest`: `rezepteTabZeigtListe` (Bildschirme mit Platzhaltern), `rezeptNeuUndBearbeiten`. `DeepLinkTest`: bestehende Tests bleiben grün.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:rezepte:testDebugUnitTest :app3:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6a: Import, Navigation und Tablet"`.

### Task 9: Screenshots, Sichtprüfung, Abschluss

**Files:**
- Create: `android/feature/rezepte/src/test/kotlin/de/foody/feature/rezepte/RezepteScreenshotTest.kt`, Referenzbilder in `android/feature/rezepte/src/test/screenshots/`
- Modify: `.github/workflows/ci.yml` (`:feature:rezepte:verifyRoborazziDebug`), `docs/architecture.md` (Abschnitt „Rezepte (Phase 6a)“), `ToDoS/v3-plan.md` (Phase 6: „Rezepte“ abhaken mit Ergebnis), `ToDoS/v3-abnahme.md` („Rezepte“ außer „Rezept direkt auf die Einkaufsliste“)

- [ ] **Step 1:** `RezepteScreenshotTest`: Liste, Detail, Editor – hell/dunkel × Handy `w411dp-h1600dp` / Tablet `w1280dp-h1200dp`, Animationen aus, Toleranz 0,1 %, erfundene Daten, zustandslose Inhalte (wie `HeuteScreen`/`TagebuchTagInhalt`).
- [ ] **Step 2:** `recordRoborazziDebug`, Bilder ansehen; Befunde per TDD beheben; `verifyRoborazziDebug` grün, Gegenprobe rot.
- [ ] **Step 3:** Sichtprüfung auf `Foody_Test` (Port 5556, AVD-Name prüfen) gegen den Test-Server (SSH-Tunnel, `http://10.0.2.2:8081`): Liste mit den Server-Rezepten, Filter, Detail, neues Rezept mit Foto aus der Fotoauswahl, Import einer Markdown-Datei aus `/sdcard/Download`; Screenshots an den Nutzer; danach App deinstallieren, Emulator und Tunnel beenden.
- [ ] **Step 4:** Zeilencheck (`wc -l`, keine Datei in `android/feature/rezepte/src/main` über 300); `./gradlew check :app3:assembleDebug :feature:rezepte:verifyRoborazziDebug :feature:heute:verifyRoborazziDebug :katalog:verifyRoborazziDebug` grün.
- [ ] **Step 5:** Doku; Commit `"V3 Phase 6a: Doku und Abschluss"`; Gesamtreview; PR gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 (4 braucht 3; 5–8 brauchen 2/3; 1 ist unabhängig). Ein Branch (`claude/v3-phase6a-rezepte`), ein PR am Ende.
