# V3 Phase 6c Woche – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Wochenplan der V3: eine Woche zum Blättern, Mahlzeiten mit freien Namen, Portionen, Verschieben, „Gekocht“, Nährwerte je Tag mit Tagesziel und „Leere Tage füllen“ mit Vorschau.

**Architecture:** Lesen, Ändern und Vorschlag im neuen `WocheRepository` (`:core:data`, Room + `:domain`-Regeln `LeereTageFuellen`, `Abwechslung`, `DayNutrition`, `PlanDurchschnitt`); Oberfläche im neuen `:feature:woche` mit einem `WocheViewModel`; `:app3` macht den Tab „Woche“ echt.

**Tech Stack:** Kotlin 2.4.20, Compose (BOM 2026.09.00), Navigation Compose 2.10.2, Hilt 2.60.1, Room 2.8.5, Coil 3.6.3, Robolectric 4.16.1, Roborazzi 1.76.0.

**Spec:** `docs/superpowers/specs/2026-10-25-v3-phase6c-woche-design.md`

## Global Constraints

- v1 unverändert: `android/app/**`, `:sync-protocol`; Server-Code unverändert; v1-Planer (`ui/planner`) nur als Vorlage lesen.
- Kein Schema-Wechsel: Datenbank bleibt Version 10, `android/core/data/schemas` unverändert.
- `:feature:woche` = `android/feature/woche`, Paket `de.foody.feature.woche`, Plugins wie `:feature:rezepte`; hängt nur von `:core:*` und `:domain` ab (nicht von anderen Features).
- UI nur aus `:core:designsystem`; jede Datei in `android/feature/woche/src/main` ≤ 300 Zeilen; ein ViewModel für den Bereich (`hiltViewModel()`).
- kcal/Nährwerte nur über `RecipeProfiles`/`DayNutrition.of` mit `alsRecipe`/`alsIngredient` (wie Karte, Detail, „Heute“); Tageswerte je Person.
- Jede Änderung an `meal_slot` setzt `updatedAt = uhr()`; Outbox über den vorhandenen Trigger.
- Portionen 1–99; eigener Mahlzeitname 1–30 Zeichen (getrimmt); Wochen Mo–So, KW nach ISO 8601 (`WeekFields.ISO`).
- Zeit nur über `uhr: () -> Long` im Repository bzw. injizierte `Clock` im ViewModel (Provider in `WocheModul`).
- Robolectric-UI-Tests: `@GraphicsMode(NATIVE)`, `junit4.v2.createComposeRule`, `application = android.app.Application::class`; Blätter/Dialoge mit Textfeld **ohne** Breiten-Qualifier; Room mit `setQueryExecutor { it.run() }`; ViewModels außerhalb der Komposition.
- Texte: „Woche“, „KW 44 · 26.10.–1.11.“, „Heute“, „Ø 1.850 kcal/Tag“, „+ Mahlzeit“, „2 P.“, „Rezept gelöscht“, „mind.“, „Leere Tage füllen“, „Neu mischen“, „Übernehmen (n)“, „kein Vorschlag“, „Keine passenden Rezepte – alles schon in den letzten 14 Tagen geplant“, „Alle Tage ab heute haben schon ein <Mahlzeit>“, „Vorige Woche“, „Nächste Woche“, „Gekocht“, „Kochen“, „Rezept ansehen“, „Verschieben“, „Umbenennen“, „Entfernen“, „Rückgängig“.
- Deutsch in Texten, Bezeichnern, Kommentaren; `allWarningsAsErrors` bleibt an. PR gegen `main` mit Auto-Merge; „Server aktualisieren? Nein“.

## Review Focus

1. **Jahreswechsel** – Woche vom 28.12.2026 (Mo) bis 3.1.2027 heißt „KW 53 · 28.12.–3.1.“, Blättern davor/danach landet auf KW 52 und KW 1 (Test in Task 4).
2. **Mahlzeitnamen in anderer Schreibweise** – ein eigener Name „abendessen “ oder „Abendessen“ gilt als Abendessen (Sortierung, „Leere Tage füllen“ zählt den Tag als belegt) (Test in Task 2 und 3).
3. **Rezept zwischen Vorschau und Übernehmen gelöscht** – `uebernehmen` überspringt es, legt die übrigen an, kein Absturz (Test in Task 3).
4. **Verschieben in eine andere Woche** – „Nächste Woche“ am Sonntag legt den Eintrag auf den Sonntag +7; die gezeigte Woche bleibt, der Eintrag verschwindet aus ihr (Test in Task 6).
5. **„Heute“ ändert sich, während die Woche offen ist** – „Leere Tage füllen“ nimmt den Tag beim Öffnen des Blatts, nicht beim Öffnen des Bildschirms (Test in Task 4).

---

### Task 1: `WocheRepository` – Lesen

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/woche/{WocheModelle,WocheRepository}.kt`
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/DatenModul.kt` (Provider `WocheRepository(db, System::currentTimeMillis)`, `@Singleton`)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/woche/WocheLesenTest.kt`

**Interfaces:**
- Produces: `data class PlanEintrag(val id: String, val datum: LocalDate, val mahlzeit: String, val rezeptId: String, val rezeptName: String?, val bild: String?, val kcalJePortion: Int?, val portionen: Int, val gekocht: Boolean)`; `data class WochenTag(val datum: LocalDate, val eintraege: List<PlanEintrag>, val naehrwerte: DayNutrition?)`; `data class WochenAnsicht(val montag: LocalDate, val tage: List<WochenTag>, val durchschnitt: Durchschnitt?)`; `fun mahlzeitReihenfolge(text: String): Int` (Frühstück 0, Mittagessen 1, Snack 2, Abendessen 3, sonst 4 – über `Mahlzeit.ausText`); `class WocheRepository(db, uhr: () -> Long) { fun woche(montag: LocalDate): Flow<WochenAnsicht>; fun rezeptAuswahl(): Flow<List<RezeptKarte>> }` – Einträge je Tag nach `mahlzeitReihenfolge`, dann Name (ohne Groß/klein), dann ID; gelöschtes Rezept → `rezeptName = null`, nicht in `naehrwerte`; `durchschnitt` über `PlanDurchschnitt.kcal` aus den sieben Tagen; `rezeptAuswahl` = Karten ohne `archiviert` (Kartenbau aus `RezeptRepository` wiederverwenden, nicht kopieren).

- [ ] **Step 1: Tests** `WocheLesenTest` (Room im Speicher, Rezept r1 „Reispfanne“ 2 Portionen 400 g Reis 1500 kJ/100 g → 717 kcal je Portion): `siebenTageAbMontag`, `reihenfolgeDerMahlzeiten` („Abendessen“, „Frühstück“, „Brotzeit am See“, „Snack“ → Frühstück, Snack, Abendessen, Brotzeit am See), `tagesKcalJePerson` (zwei Einträge r1 mit 2 und 4 Portionen → `kcal == 1434`), `geloeschtesRezeptZaehltNicht` (Eintrag mit unbekannter rezeptId → `rezeptName == null`, Tag `naehrwerte == null`), `durchschnittNurVolleTage` (Tag mit nur Abendessen zählt nicht), `auswahlOhneArchivierte`.
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest --tests "*WocheLesenTest*"` → FAIL (Klassen fehlen).
- [ ] **Step 3:** Umsetzen (Flow aus `planEintragDao().zwischen(montag, montag+6)`, Rezepte, Zeilen, Zutaten wie `RezeptRepository.karten`).
- [ ] **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün.
- [ ] **Step 5:** Commit `"V3 Phase 6c: Woche lesen"`.

### Task 2: `WocheRepository` – Ändern

**Files:**
- Modify: `woche/WocheRepository.kt`, `db/Daos.kt` (nur falls eine Abfrage fehlt)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/woche/WocheSchreibenTest.kt`

**Interfaces:**
- Produces: `suspend fun hinzufuegen(datum: LocalDate, mahlzeit: String, rezeptId: String, portionen: Int): String`; `suspend fun portionen(id: String, n: Int)` (begrenzt 1–99); `suspend fun verschieben(id: String, datum: LocalDate)`; `suspend fun umbenennen(id: String, mahlzeit: String)` (getrimmt; leer oder > 30 Zeichen → `IllegalArgumentException`; ein bekannter Name wird als `Mahlzeit.name` gespeichert, sonst der Text); `suspend fun entfernen(id: String): PlanEintragEntity?`; `suspend fun wiederherstellen(e: PlanEintragEntity)`; `suspend fun gekocht(id: String, an: Boolean)`. `hinzufuegen` normalisiert den Namen wie `umbenennen`.

- [ ] **Step 1: Tests** `WocheSchreibenTest`: `hinzufuegenMitPortionen`, `portionenBegrenzt` (0 → 1, 120 → 99), `verschieben`, `umbenennenEigenerName` („Brotzeit am See“), `bekannterNameWirdMahlzeit` (Review Focus 2: „abendessen “ → `slotType == "ABENDESSEN"`), `umbenennenLeerAbgelehnt`, `entfernenUndWiederherstellenMitDerselbenId`, `gekochtAnUndAus` (`cookedAt` 5_000 → `null`), `aenderungenInDerOutbox` (Sync aktiv: nach `portionen` ist `meal_slot`/ID in `sync_outbox`), `updatedAtGesetzt`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6c: Woche ändern"`.

### Task 3: Vorschlag und Übernehmen

**Files:**
- Modify: `woche/WocheRepository.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/woche/WocheVorschlagTest.kt`

**Interfaces:**
- Consumes: `LeereTageFuellen.vorschlagen(rezepte, leereTage, mahlzeit, seed, belegt)`, `Abwechslung.merkmale(name, pflichtZutaten)`.
- Produces: `suspend fun leereTage(montag: LocalDate, mahlzeit: Mahlzeit, heute: LocalDate): List<LocalDate>` (Tage der Woche ≥ heute ohne Eintrag, dessen `Mahlzeit.ausText(slotType) == mahlzeit`); `suspend fun vorschlagen(tage: List<LocalDate>, mahlzeit: Mahlzeit, seed: Long): Map<LocalDate, String>` (nur nicht archivierte Rezepte; `gekocht` = Zahl der Einträge mit `cookedAt`; `zuletztGeplant` = spätestes Datum; Merkmale aus Name und Pflichtzutaten (`optional == false`); `belegt` aus Einträgen von `tage.min()-14` bis `tage.max()+7`); `suspend fun uebernehmen(vorschlag: Map<LocalDate, String>, mahlzeit: Mahlzeit): Int` (eine Transaktion, Portionen = `defaultServings`, gelöschte Rezepte übersprungen, Rückgabe = angelegte Einträge).

- [ ] **Step 1: Tests** `WocheVorschlagTest` (fünf Hauptspeisen fürs Abendessen, erfunden): `leereTageNurAbHeute` (heute = Mittwoch → Mi–So), `belegterTagFehlt` (Mittwoch mit Eintrag „abendessen“ als eigener Name → fehlt; Review Focus 2), `gleicherSeedGleichesErgebnis`, `vierzehnTageSperre` (Rezept vor 10 Tagen geplant → nie vorgeschlagen), `archivierteNie`, `ohnePassendeLeer` (nur Nachspeisen → leere Map), `uebernehmenInEinerTransaktion` (3 Tage → 3 Einträge mit `defaultServings`), `uebernehmenUeberspringtGeloeschte` (Review Focus 3: ein Rezept vorher löschen → Rückgabe 2, kein Fehler).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6c: Leere Tage füllen"`.

### Task 4: `:feature:woche` und `WocheViewModel`

**Files:**
- Create: `android/feature/woche/build.gradle.kts` (wie `:feature:kochen`, plus `coil-compose` und `coil-test`), `settings.gradle.kts`-Eintrag, `src/main/kotlin/de/foody/feature/woche/{WocheViewModel,WocheModul,KalenderWoche}.kt`, `src/test/.../TestHilfen.kt` (aus `:feature:rezepte` kopiert)
- Test: `src/test/kotlin/de/foody/feature/woche/{KalenderWocheTest,WocheViewModelTest}.kt`

**Interfaces:**
- Consumes: Tasks 1–3, `TageszielSpeicher.tagesziel(): Flow<Int?>`.
- Produces: `fun montagVon(datum: LocalDate): LocalDate`; `fun wochenTitel(montag: LocalDate): String` („KW 44 · 26.10.–1.11.“, ISO-KW, Tag und Monat ohne führende Null); `data class FuellenZustand(val mahlzeit: Mahlzeit, val tage: List<LocalDate>, val vorschlag: Map<LocalDate, String>, val abgewaehlt: Set<LocalDate>, val hinweis: String?)`; `@HiltViewModel class WocheViewModel @Inject constructor(saved: SavedStateHandle, repo: WocheRepository, ziel: TageszielSpeicher, clock: Clock)` mit `val woche: StateFlow<WochenAnsicht?>`, `val tagesziel: StateFlow<Int?>`, `val fuellen: StateFlow<FuellenZustand?>`, `fun blaettern(wochen: Long)`, `fun heute()`, `val zeigtHeute: StateFlow<Boolean>`, `fun hinzufuegen(datum, mahlzeit, rezeptId, portionen)`, `fun portionen(id, n)`, `fun verschieben(id, datum)`, `fun umbenennen(id, mahlzeit)`, `fun gekocht(id, an)`, `suspend fun entfernen(id): PlanEintragEntity?`, `fun wiederherstellen(e)`, `fun fuellenOeffnen(mahlzeit: Mahlzeit = Mahlzeit.ABENDESSEN)` (Tage über `leereTage(montag, mahlzeit, LocalDate.now(clock))` zum Zeitpunkt des Aufrufs), `fun neuMischen()`, `fun tagNeu(datum)` (neuer Seed nur für den Tag, andere Vorschläge bleiben), `fun abwaehlen(datum)`, `suspend fun uebernehmen(): Int?` (Sperre gegen Doppeltipp; `null` beim zweiten Tipp), `fun fuellenSchliessen()`. Montag im `SavedStateHandle` als Epoch-Day unter `"montag"`.

- [ ] **Step 1: Tests** `KalenderWocheTest`: `titelKw44` („KW 44 · 26.10.–1.11.“), `jahreswechsel` (Review Focus 1: `wochenTitel(2026-12-28) == "KW 53 · 28.12.–3.1."`, `montagVon(2027-01-01) == 2026-12-28`, +1 Woche → „KW 1 · 4.1.–10.1.“). `WocheViewModelTest` (Room + echtes Repository, feste `Clock` am Mittwoch 28.10.2026): `startetMitAktuellerWoche`, `blaetternUndHeute`, `montagUeberstehtNeuaufbau` (gleiche `SavedStateHandle` → gleiche Woche), `fuellenNimmtHeuteBeimOeffnen` (Review Focus 5: Clock nach dem Anlegen auf Freitag stellen, dann `fuellenOeffnen` → Tage ab Freitag), `uebernehmenEinmal` (zwei parallele `uebernehmen()` → einmal Zahl, einmal `null`, Einträge nur einmal), `tagNeuAendertNurDiesenTag`, `abwaehlenWirdNichtAngelegt`, `ohnePassendeHinweis` („Keine passenden Rezepte – alles schon in den letzten 14 Tagen geplant“), `ohneLeereTageHinweis` („Alle Tage ab heute haben schon ein Abendessen“), `entfernenUndRueckgaengig`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen (`Clock` für die Tests als `MutableClock` in den Test-Hilfen). **Step 4:** `./gradlew :feature:woche:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6c: Woche-ViewModel"`.

### Task 5: Wochenansicht und Hinzufügen

**Files:**
- Create: `src/main/kotlin/de/foody/feature/woche/{WocheScreen,TagKarte,MahlzeitWahl,RezeptAuswahlBlatt,RezeptBild}.kt` (`RezeptBild` aus `:feature:rezepte` kopiert)
- Test: `src/test/kotlin/de/foody/feature/woche/{WocheScreenTest,RezeptAuswahlTest}.kt`

**Interfaces:**
- Produces: `@Composable fun WocheRoute(onKochen: (String, Int) -> Unit, onRezept: (String) -> Unit, vm: WocheViewModel = hiltViewModel())`; `fun auswahlSortiert(karten: List<RezeptKarte>, suche: String, mahlzeit: Mahlzeit?): List<RezeptKarte>` (Teilstring ohne Groß/klein in Name, Tags, Zutaten; passende `einordnung.passtZu(mahlzeit)` zuerst, dann Name); Mahlzeit-Wahl: Chips Frühstück/Mittagessen/Snack/Abendessen + Feld „Eigener Name“ (1–30 Zeichen).

- [ ] **Step 1: Tests** `RezeptAuswahlTest`: `passendeZuerst`, `sucheInZutaten`. `WocheScreenTest` (`h1600dp`, ohne Breiten-Qualifier wegen Textfeld im Blatt): `zeigtWocheMitKcal` („KW 44 · 26.10.–1.11.“, „Reispfanne“, „2 P.“, „717 kcal“), `durchschnittImKopf` (zwei volle Tage → „Ø … kcal/Tag“; nur Abendessen → kein „Ø“), `mindBeiFehlendenWerten` („mind. 717 kcal“), `tageszielBalken` (Ziel 2000 → „717 / 2.000 kcal“), `hinzufuegenMitEigenemNamen` („+ Mahlzeit“ → „Eigener Name“ „Brotzeit“ → Rezept wählen → Eintrag sichtbar), `geloeschtesRezept` („Rezept gelöscht“), `blaetternPfeile`, `schrift200OhneAbschneiden`, `ueberstehtDrehen`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6c: Wochenansicht"`.

### Task 6: Mahlzeit-Blatt, Füllen-Blatt, Tablet

**Files:**
- Create: `src/main/kotlin/de/foody/feature/woche/{MahlzeitBlatt,FuellenBlatt}.kt`
- Modify: `WocheScreen.kt` (Blätter, Snackbar „Rückgängig“, zwei Spalten ab 840 dp)
- Test: `WocheScreenTest` (erweitert)

**Interfaces:**
- Consumes: Task 4/5. Verschieben: Chips Mo–So der gezeigten Woche, „Vorige Woche“ = Datum −7, „Nächste Woche“ = Datum +7.

- [ ] **Step 1: Tests** `WocheScreenTest`: `portionenImBlatt` („+“ → „3 P.“), `umbenennenImBlatt` („Umbenennen“ → „Mittagessen“ → Eintrag steht unter Mittagessen), `verschiebenAufMittwoch`, `verschiebenNaechsteWoche` (Review Focus 4: Eintrag am Sonntag → „Nächste Woche“ → nicht mehr in der Woche, `datum == Sonntag + 7`), `entfernenMitRueckgaengig`, `gekochtUmschalten`, `kochenUndRezept` (`onKochen("r1", 2)`, `onRezept("r1")`), `fuellenVorschauUndUebernehmen` (Haken bei einem Tag entfernen → „Übernehmen (n-1)“ → n-1 Einträge), `fuellenKeinVorschlag` („kein Vorschlag“), `tabletZweiSpalten` (`w1280dp-h1200dp`, ohne Blatt: Montag und Freitag gleichzeitig sichtbar).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6c: Mahlzeit- und Füllen-Blatt"`.

### Task 7: Navigation

**Files:**
- Modify: `android/app3/build.gradle.kts` (`:feature:woche`), `navigation/FoodyNavHost.kt` (`Bildschirme.woche: @Composable (NavHostController) -> Unit`, echt: `WocheRoute(onKochen = { r, p -> nav.navigate(Kochen(r, p)) }, onRezept = { nav.navigate(RezeptDetail(it)) })`, Platzhalter „Wochenplan“)
- Test: `android/app3/src/test/kotlin/de/foody/app3/navigation/NavigationTest.kt`

- [ ] **Step 1: Tests** `NavigationTest`: `wocheTabZeigtPlan` (Tab „Woche“ → Platzhalter-Text „Wochenplan“), bestehende Tests bleiben grün.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :app3:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6c: Navigation"`.

### Task 8: Screenshots, Sichtprüfung, Abschluss

**Files:**
- Create: `android/feature/woche/src/test/kotlin/de/foody/feature/woche/WocheScreenshotTest.kt`, Referenzbilder in `src/test/screenshots/`
- Modify: `.github/workflows/ci.yml` (`:feature:woche:verifyRoborazziDebug`), `docs/architecture.md` (Abschnitt „Woche (Phase 6c)“), `ToDoS/v3-plan.md` („Woche“ abhaken mit Ergebnis), `ToDoS/v3-abnahme.md` (Abschnitt „Woche“)

- [ ] **Step 1:** `WocheScreenshotTest`: Woche, Mahlzeit-Blatt, Füllen-Blatt – hell/dunkel × Handy `w411dp-h1600dp` / Tablet `w1280dp-h1200dp`; Blätter als ganzer Bildschirm (`captureScreenRoboImage`, je Bild ein Test), erfundene Daten, Toleranz 0,1 %.
- [ ] **Step 2:** `recordRoborazziDebug`, Bilder ansehen, Befunde beheben; `verifyRoborazziDebug` grün, Gegenprobe rot.
- [ ] **Step 3:** Sichtprüfung auf `Foody_Test` (Port 5556, AVD-Name prüfen) gegen den Test-Server: Woche mit Serverdaten, Eintrag heute anlegen, Leere Tage füllen (Vorschläge plausibel?), heutigen Eintrag kochen → „Fertig“ → Haken „gekocht“ in der Woche (Gerätebeleg aus 6b); Screenshots an den Nutzer; danach angelegte Einträge entfernen, App deinstallieren, Emulator und Tunnel beenden.
- [ ] **Step 4:** Zeilencheck; `./gradlew check :app3:assembleDebug` und alle `verifyRoborazziDebug` (katalog, heute, rezepte, kochen, woche) grün.
- [ ] **Step 5:** Doku; Commit `"V3 Phase 6c: Doku und Abschluss"`; Gesamtreview; PR gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 (2 und 3 bauen auf 1; 4 braucht 1–3; 5/6 brauchen 4; 7 braucht 5). Ein Branch (`claude/v3-phase6c-woche`), ein PR am Ende.
