# Eintragen neu (Teil B) – Umsetzungsplan

> **Für ausführende Agenten:** Pflicht-Skill: superpowers:subagent-driven-development (empfohlen) oder
> superpowers:executing-plans, Aufgabe für Aufgabe. Schritte mit Kästchen (`- [ ]`) zum Abhaken.

**Ziel:** Eintragen wird ein eigenes Navigationsziel mit den Schritten Erfassen und Bestätigen, in allen Stilen gleich aufgebaut. Foto
und Scannen starten von überall sofort, auch von den Widgets.

**Architektur:** Ein Ziel `Eintragen` in `:app` (`Ziele.kt`) zeigt `EintragenRoute` aus `:feature:heute`. Der vorhandene
`EintragenViewModel` bekommt die Schritte und hält weiter alles, was ein Prozessende überstehen muss (Eingaben, Kamera-Pfad,
laufende Erkennung). Kamera und Scanner starten immer im Ziel Eintragen. Heute, Tagebuch und die Widgets öffnen das Ziel mit
`sofort = FOTO|SCANNEN`. Bis das Ergebnis da ist, zeichnet die Seite nur den Hintergrund. Bricht der Nutzer ab, schließt sie sich
wieder. Das alte `EintragenBlatt` mit Reitern, Chips und Blatt- bzw. Seitenform je Stil entfällt.

**Technik:** Kotlin, Jetpack Compose (Material3 1.4), Navigation Compose mit `@Serializable`-Routen, Hilt, Robolectric,
Roborazzi, ktlint.

**Spec:** `docs/superpowers/specs/2026-11-21-eintragen-neu-design.md` (vom Nutzer freigegeben)

## Globale Vorgaben

- Bezeichner, KDoc und Texte auf Deutsch. Der Code liest sich wie der umgebende Code. `allWarningsAsErrors` gilt.
- Keine Änderung an Daten, Schema, Sync oder Server. Gespeichert wird über die vorhandenen Funktionen von
  `TagebuchRepository` (`ausRezept`, `ausZutat`, `ausPlan`, `frei`, `ausPackung`). Server-Rebuild: nein.
- Fotoerkennung (Modell, `:core:erkennung`) und die Packungssuche bleiben, wie sie sind. Es ändert sich nur, wann und von wo
  sie starten.
- `EintragBearbeiten` (Ändern-Dialog) und „Gegessen?“ in Heute bleiben unverändert.
- In allen 7 Stilen × 2 Varianten derselbe Aufbau. Der Stil zeigt sich nur in Farben, Schrift, Formen und im Titel
  (`eintragenTitel()`: „Eintragen“, Sage „Mahl eintragen“, Anime „Eintragen!“, Bayrisch „Eitragn“, Grauen „Notieren“).
- Die Knöpfe heißen überall „Foto“, „Scannen“, „Eintragen“, „Aus der Galerie“, „Nährwerttabelle fotografieren“,
  „Nicht dabei? Frei eintragen“. Stilwörter wie „Siegel“, „Deuten“, „Ins Bordbuch“, „Eintragen! ✓“, „In die Chronik“ und
  „Ins Logbuch“ gibt es auf der Seite nicht mehr.
- Kontrast nach WCAG 2.2 AA, Zielflächen ≥ 48 dp. Bei Schrift 90–130 % auf 360 dp wird nichts abgeschnitten. 200 % wird
  nicht geprüft.
- Nichts geht bei Drehen oder Prozessende verloren: Eingaben, ein aufgenommenes Foto, ein Scan-Ergebnis, auch nicht bei
  offener Kamera. Einzige Ausnahme: Stirbt der Prozess, während der Google-Code-Scanner offen ist, kommt sein Ergebnis
  nicht an.
- Tests mit ViewModel und Room im Speicher: ViewModels in einen `ViewModelHalter`. In `@After` erst `schliessen()`, dann
  `db.close()`.
- Jeder PR geht gegen `main`, mit Auto-Merge und dem Satz „Server-Rebuild nötig: nein“.

## PR-Schnitt

| PR | Aufgaben | Inhalt |
|---|---|---|
| 1 | 1–3 | Ziel und zwei Schritte für alle Stile. Ein Branch, je Aufgabe ein Commit-Block mit eigenem Review; erst Aufgabe 3 macht ihn mergefähig. |
| 2 | 4 | Sofort-Wege aus den Heute-Szenen |
| 3 | 5 | Widgets direkt |
| 4 | 6 | Doku und ADR |

## Review-Schwerpunkte

1. **Tabellenfoto nach getipptem Namen.** Der Nutzer tippt in der leeren Packung einen Namen und fotografiert dann die
   Tabelle. Das Foto liefert keinen Namen. Erwartet: Der getippte Name bleibt stehen
   (Test `tabellenfotoBehaeltDenGetipptenNamen`, Aufgabe 1).
2. **Prozessende im Sofort-Scan.** Der Scanner ist offen und der Prozess stirbt. Erwartet: Nach dem Neustart steht
   Erfassen da, keine leere Seite ohne Ausweg (Test `prozessendeImScannerZeigtErfassen`, Aufgabe 1).
3. **Kamera-Launcher in jedem Schritt registriert.** Kommt das Kameraergebnis nach einem Prozessende zurück, während die
   Seite leer ist (Sofort) oder Bestätigen zeigt, muss der Launcher an derselben Stelle registriert sein. Sonst geht das
   Foto verloren (Aufgabe 2: `rememberAufnahme` steht vor jeder Verzweigung; Test `fotoKommtAuchBeiLeererSeiteAn`).
4. **Zurück von einem geplanten Gericht.** Der Nutzer wählt Geplantes (Mahlzeit aus dem Plan) und geht zurück.
   Erwartet: Die Mahlzeit ist wieder die vom Öffnen (Test `geplantNimmtDieMahlzeitDesPlans`, Aufgabe 1).
5. **Zweiter Widget-Tipp bei offener Eintragen-Seite.** Erwartet: Genau eine Eintragen-Seite liegt auf Heute, und
   zwar die neue (Test `widgetLinksOeffnenEintragenUeberHeute`, Aufgabe 5).

## Dateien

| Datei | Aufgabe | Zuständig für |
|---|---|---|
| `android/core/scan/.../PackungScan.kt`, `ScanLauf.kt`, `ScanFotos.kt` | 1, 2 | gelesener Code und Scan ohne Treffer an den Aufrufer melden; Foto-Helfer öffentlich |
| `android/feature/heute/.../tagebuch/EintragenStart.kt` (neu) | 1 | `EintragenStart`, `SofortStart` |
| `android/feature/heute/.../tagebuch/EintragenViewModel.kt` | 1, 3 | Schritte, Quellen, kcal dazu, Abbruch, Prozessende |
| `android/feature/heute/.../tagebuch/EintragenSeite.kt` (neu) | 2 | `EintragenRoute`: Kopf, Schritte, Knopf, Zurück |
| `android/feature/heute/.../tagebuch/Erfassen.kt` (neu) | 2 | Schritt Erfassen |
| `android/feature/heute/.../tagebuch/Bestaetigen.kt` (neu) | 2 | Schritt Bestätigen je Quelle, Mahlzeit-Zeile |
| `android/feature/heute/.../tagebuch/EintragenTeile.kt` (neu) | 2 | aus `EintragenBlatt.kt` übernommene Felder (Portionen, Menge, Frei, Foto-Felder, Treffer, BLS-Zeile) |
| `android/feature/heute/.../tagebuch/Aufnahme.kt` (neu) | 2 | Launcher für Kamera, Galerie und Tabellenfoto; Sofort-Start; Aufräumen |
| `android/feature/heute/.../tagebuch/PackungEingabe.kt` | 2 | „bitte prüfen“ nur mit Werten, ohne „Zurück zu Frei“ |
| `EintragenBlatt.kt`, `FotoErkennungKnopf.kt` | 3 | löschen |
| `android/feature/heute/.../HeuteScreen.kt`, `tagebuch/TagebuchTagScreen.kt` | 3, 4, 5 | öffnen das Ziel statt eines eingebauten Blatts |
| `android/feature/heute/.../HeuteViewModel.kt`, `stil/HeuteEdo.kt`, `HeuteSage.kt`, `HeuteBayrisch.kt` | 4 | Szenen-Knöpfe „Scannen“ und „Foto“ getrennt |
| `android/app/.../navigation/Ziele.kt`, `FoodyNavHost.kt`, `Hauptgeruest.kt` | 3, 5 | Ziel, Verdrahtung, Vollbild, Übergänge, Widgets |
| `android/app/.../navigation/WidgetStart.kt` (neu), `MainActivity.kt`, `widget/KnopfWidget.kt` | 5 | Widget-Links direkt zu Eintragen |
| `docs/adr/0021-eintragen-als-eigenes-ziel.md` (neu), `docs/architecture.md`, `ToDoS/stile-plan.md` | 6 | Doku |

---

### Aufgabe 1: ViewModel mit zwei Schritten und allen Quellen

Das alte Blatt muss nach dieser Aufgabe weiter kompilieren: neue Felder und Funktionen kommen dazu, die Reiter-API
(`reiter`, `weg`, `produktWaehlen`, `gescannt`, `produkte`) bleibt bis Aufgabe 3. Alte Tests, die nur an einer neuen
Regel scheitern (BLS nur beim Erfassen, Zutaten erst mit Suchtext, Scan ohne Werte öffnet die leere Packung), werden
angepasst oder durch die neuen ersetzt. Betroffen sind `reiterUndWahlSetzenDenWeg`, `blsNichtInFrei`,
`blsAuchImWegRezept`, `rezeptsucheZeigtNurGescannteProdukte` und `gescanntesProduktBeimNaechstenMalInDerSuche`. Alte
`EintragenBlatt*`-UI-Tests, die nur deshalb scheitern, fallen weg (Aufgabe 3 löscht sie ohnehin).

**Dateien:**
- Ändern: `android/core/scan/src/main/kotlin/de/foody/core/scan/PackungScan.kt`, `ScanLauf.kt`
- Neu: `android/feature/heute/src/main/kotlin/de/foody/feature/heute/tagebuch/EintragenStart.kt`
- Ändern: `android/feature/heute/src/main/kotlin/de/foody/feature/heute/tagebuch/EintragenViewModel.kt`, `EintragenBlatt.kt` (nur der Aufruf von `oeffnen`)
- Test: `android/core/scan/src/test/kotlin/de/foody/core/scan/ScanLaufTest.kt`, neu
  `android/feature/heute/src/test/kotlin/de/foody/feature/heute/tagebuch/EintragenSchritteTest.kt`

**Schnittstellen:**
- Erzeugt:
  ```kotlin
  // EintragenStart.kt
  enum class SofortStart { FOTO, SCANNEN }
  /** Wie Eintragen beginnt: [sofort] `null` = Erfassen, sonst gleich Kamera bzw. Scanner. */
  data class EintragenStart(val datum: LocalDate, val mahlzeit: Mahlzeit? = null, val sofort: SofortStart? = null)

  // EintragenViewModel.kt
  enum class EintragenSchritt { ERFASSEN, BESTAETIGEN }
  // Eingaben bekommt: schritt = ERFASSEN, sofort: SofortStart? = null, mahlzeitStart: Mahlzeit = ABENDESSEN,
  //   fotoBild: String? = null (Uri fürs Vorschaubild), fotoDatei: String? = null (Kamerafoto, am Ende löschen)
  // EintragenZustand bekommt: auswahlName: String? = null, kcalDazu: Int? = null
  fun oeffnen(datum: LocalDate, mahlzeit: Mahlzeit?, uhrzeit: LocalTime = LocalTime.now(), auftrag: String? = null, sofort: SofortStart? = null)
  fun freiEintragen()
  fun fotoGemacht(bildUri: String, datei: String?)
  fun aufnahmeAbgebrochen()
  fun zurueck(): Boolean          // true: war in Bestätigen und ist jetzt in Erfassen; false: Seite schließen
  val schliessen: Flow<Unit>      // Sofort-Start abgebrochen: Seite schließen
  // geändert: zutatWaehlen(id) wählt die Einheit nach der Basis der Zutat (PER_100_ML → ml, sonst g)
  // geändert: rezeptWaehlen, zutatWaehlen, blsWaehlen, planWaehlen, imKatalog, packungUebernehmen → schritt = BESTAETIGEN, sofort = null

  // PackungScan / ScanLauf (:core:scan)
  suspend fun strichcode(onGelesen: (String) -> Unit = {}): ScanAusgang   // onGelesen nach dem Lesen, vor Katalog/Online
  const val QUELLE_HAND = "Eigene Angabe"    // PackungScan.Companion: Quelle der leeren Packung (Zeile „bitte prüfen“ entfällt ohne Werte)
  // ScanLauf: Primärkonstruktor strichcodeLesen: suspend ((String) -> Unit) -> ScanAusgang;
  //   beide Konstruktoren am Ende: onGelesen: (String) -> Unit = {}, onOhneTreffer: (ScanAusgang) -> Unit = {}
  ```

Regeln, die die Tests festlegen (der Rest folgt dem heutigen Code):

- `oeffnen(..., sofort)` setzt `mahlzeitStart = m` und `sofort`. Mit `FOTO` wird `fotoLauf.kameraAnfragen()` aufgerufen.
  Mit `SCANNEN` und `scanLauf.verfuegbar` startet `scanLauf.strichcode()`; ohne Play-Dienste bleibt `sofort = null`.
  `weg = FREI` bei gesetztem `sofort` bleibt (das alte Blatt braucht es).
- Der Scan-Lauf im ViewModel wird mit `onGelesen = ::scanGelesen` und `onOhneTreffer = ::ohneTreffer` gebaut.
  - `scanGelesen(code)` öffnet Bestätigen mit `weg = FREI` und der leeren Packung
    `Packungswerte(null, null, emptyMap(), code, QUELLE_HAND)`. Gesucht wird dann noch.
  - `ohneTreffer` bei `NichtGefunden`, `KeineTabelle`, `NichtVerfuegbar` und `Fehler`: Bestätigen mit der leeren
    Packung, aber nur, wenn noch keine Packung da ist.
  - `ohneTreffer` bei `WirdGeladen`: nur `sofort = null`.
  - `ohneTreffer` bei `Abgebrochen`: wie `aufnahmeAbgebrochen()`.
- `aufnahmeAbgebrochen()`: Ist `sofort` gesetzt, wird es auf `null` gesetzt und `schliessen` sendet. Sonst passiert nichts.
- `packungUebernehmen(p)`: Ohne Namen in `p` bleibt `pName` stehen.
- `fotoGemacht(bildUri, datei)`: Bestätigen mit `weg = FREI`, ohne Packung, leeren Feldern und `foto = null`.
  `fotoBild = bildUri`, `fotoDatei = datei`. Dann `fotoLauf.erkennen(bildUri)` **ohne** `loeschen`, damit das
  Vorschaubild bleibt.
- `zurueck()` aus Bestätigen:
  - bricht die Erkennung ab und löscht `fotoDatei`;
  - setzt Auswahl, Felder, Packung und Foto zurück;
  - behält Suche und Datum;
  - bei `weg == PLAN` wird die Mahlzeit wieder `mahlzeitStart`.
- `planWaehlen(id)` setzt die Mahlzeit aus dem Plan-Slot (`Mahlzeit.ausText(slot)`).
- `kcalDazu`, immer aus der ganzen Liste, nicht der gefilterten:
  - REZEPT: `kcalJePortion × Portionen`, kaufmännisch gerundet.
  - ZUTAT: `Tagebuch.naehrwerteZutat(zutat.alsIngredient(), menge, einheit)` in kcal.
  - PLAN: `kcalJePortion`.
  - Sonst `null`.
- `auswahlName`: der Titel des Rezepts bzw. des Geplanten oder der Name der Zutat, ebenfalls aus der ganzen Liste.
- Suche im Schritt Erfassen:
  - leer: Rezepte wie heute (höchstens 30), keine Zutaten, kein BLS;
  - mit Text: Rezepte, Zutaten (gescannte Produkte eingeschlossen) und BLS, je höchstens 30.
  - Der BLS sucht nur im Schritt Erfassen, egal welcher `weg`.
- `init`: Steht nach einem Prozessende `sofort == SCANNEN` in den Eingaben, wird es `null`. Das Scanner-Ergebnis kommt
  nicht mehr an.
- `onCleared()` löscht `fotoDatei`.

- [ ] **Schritt 1: Failing Tests in `ScanLaufTest` schreiben**
  - `gelesenerCodeKommtVorDemNachschlagen`: Der Fake-Leser liefert `"4001"`, das Nachschlagen `Gefunden`. Die Aufrufe in
    Reihenfolge ergeben `["gelesen:4001", "packung"]`.
  - `ohneTrefferMeldetDenAusgang`: `NichtGefunden("4001", UNBEKANNT)` kommt bei `onOhneTreffer` an, `onPackung` nicht.
    Mit `Abgebrochen` kommt `Abgebrochen` an.
- [ ] **Schritt 2: Laufen lassen, Fehlschlag sehen**: `.\gradlew :core:scan:test --tests "*ScanLaufTest*"` → Kompilierfehler (Parameter fehlen)
- [ ] **Schritt 3: `PackungScan.strichcode(onGelesen)` und `ScanLauf` erweitern** (Zutaten-ViewModels unverändert dank Standardwerten)
- [ ] **Schritt 4: Laufen lassen**: `.\gradlew :core:scan:test` → PASS
- [ ] **Schritt 5: Failing Tests in `EintragenSchritteTest` schreiben.** Aufbau wie `EintragenViewModelTest`: Room im
  Speicher, `testScan`, `testErkennung`, `testGedaechtnis`, `testBls`. Zahlen wie hier angegeben:
  - `startetBeimErfassen`: `oeffnen(tag, null, LocalTime.of(12, 30))` → `ERFASSEN`, Mahlzeit `MITTAGESSEN`, `sofort == null`.
  - `mahlzeitAusDerZeile`: `oeffnen(tag, SNACK, LocalTime.of(8, 0))` → `SNACK`.
  - `rezeptFuehrtZuBestaetigenMitKcal`: Rezept „Linsensuppe“ mit 400 kcal je Portion.
    - `rezeptWaehlen` → `BESTAETIGEN`, `auswahlName == "Linsensuppe"`, `kcalDazu == 400`.
    - `portionenSchritt(true)` → `kcalDazu == 600`.
    - `speichern()` legt den Eintrag mit 1,5 Portionen an.
  - `zutatMitMengeZeigtKcal`: Haferflocken mit 370 kcal je 100 g, `zutatWaehlen`, `menge("50")` → `kcalDazu == 185`.
  - `gescannteMilchInMilliliter`: Eine Zutat mit Basis `PER_100_ML` und Strichcode ergibt nach `zutatWaehlen` die
    Einheit `MILLILITER`.
  - `geplantNimmtDieMahlzeitDesPlans`: Plan-Eintrag am Mittag, geöffnet mit `ABENDESSEN`.
    - `planWaehlen` → Mahlzeit `MITTAGESSEN`, `kcalDazu` gleich `kcalJePortion`.
    - `zurueck()` → `true`, Mahlzeit wieder `ABENDESSEN`.
  - `freiEintragenLeer`: `freiEintragen()` → `BESTAETIGEN`, `weg == FREI`, leere Felder.
    - `speichernMoeglich` ist `false`.
    - Nach Name und kcal ist es `true`.
  - `fotoGemachtBehaeltDasBildBisZurueck`: echte Datei unter `cacheDir/mahlzeit/`.
    - `fotoGemacht(uri, pfad)` → `BESTAETIGEN`, `fotoBild == uri`.
    - Der Vorschlag füllt die Felder; die Datei besteht nach der Erkennung weiter.
    - `zurueck()` → Datei gelöscht.
  - `scanGelesenZeigtBestaetigenMitLeererPackung`: Der Leser liefert `"4001"`, die Suche hängt (`CompletableDeferred`).
    - Während sie hängt: `BESTAETIGEN`, `packung?.strichcode == "4001"`, `packung?.werte` leer.
    - Danach füllt `Gefunden` die p-Felder.
  - `scanOhneWerteBehaeltDenStrichcode`: `NichtGefunden("4001")`.
    - → `BESTAETIGEN` mit leerer Packung und `scanLauf.zustand.fotoAnbieten`.
    - Mit `pName("Müsli")`, `pMenge("50")`, `pKcal("380")` und Häkchen speichern → die Zutat „Müsli“ trägt den
      Strichcode `"4001"`.
  - `tabellenfotoBehaeltDenGetipptenNamen`: leere Packung, `pName("Müsli")`.
    - `packungUebernehmen(Packungswerte(null, PER_100_G, mapOf(ENERGY_KJ to 1590), "4001", QUELLE_FOTO))`.
    - Danach: `pName == "Müsli"`, `pKcal` gefüllt.
  - `imKatalogFuehrtZurZutat`: Ein Scan mit `ImKatalog` ergibt `BESTAETIGEN`, `weg == ZUTAT`, die `zutatId` des Treffers.
  - `abbruchBeimSofortstartSchliesst`:
    - `oeffnen(sofort = FOTO)`, `aufnahmeAbgebrochen()` → `schliessen` sendet einmal.
    - Ohne `sofort` sendet nichts, der Schritt bleibt `ERFASSEN`.
  - `scannerAbbruchBeimSofortstartSchliesst`: Der Leser liefert `Abgebrochen`, dann `oeffnen(sofort = SCANNEN)` →
    `schliessen` sendet.
  - `zurueckVonBestaetigenNachErfassen`: `suche("Lin")`, dann `rezeptWaehlen`.
    - `zurueck() == true` → `ERFASSEN`, `rezeptId == null`, `suche == "Lin"`.
    - Ein zweites `zurueck()` liefert `false`.
  - `prozessendeInBeidenSchritten`: Mit `SavedStateHandle.nachProzessende()` überstehen alle drei Fälle:
    - `ERFASSEN` mit Suche;
    - `BESTAETIGEN` mit Zutat und Menge;
    - `BESTAETIGEN` mit `fotoBild`.
  - `prozessendeMitOffenerKamera`: `oeffnen(sofort = FOTO)`, `fotoLauf.kameraGestartet("/p.jpg")`, Prozessende → neues VM:
    `fotoLauf.zustand.value.fotoPfad == "/p.jpg"`, `sofort == FOTO`, `ERFASSEN`.
  - `prozessendeImScannerZeigtErfassen`: Der Leser hängt, `oeffnen(sofort = SCANNEN)`, Prozessende → neues VM:
    `sofort == null`, `ERFASSEN`.
  - `sucheErstMitText`: Mit Suche „“ sind `zutaten` und `bls` leer, `rezepte` nicht. Mit „Hafer“ enthält `zutaten`
    die Haferflocken und das gescannte Produkt.
- [ ] **Schritt 6: Laufen lassen**: `.\gradlew :feature:heute:test --tests "*EintragenSchritteTest*"` → FAIL (Felder/Funktionen fehlen)
- [ ] **Schritt 7: ViewModel nach den Regeln oben umbauen.** `EintragenBlatt` ruft
  `oeffnen(..., sofort = auftrag.start.alsSofort())` mit `FOTO → FOTO`, `SCANNEN → SCANNEN`, sonst `null`.
- [ ] **Schritt 8: Laufen lassen**: `.\gradlew :feature:heute:test :core:scan:test :feature:zutaten:test` → PASS (angepasste Alt-Tests eingeschlossen)
- [ ] **Schritt 9: ktlint und Commit**: `.\gradlew ktlintPruefen`, dann
  `git commit -m "Eintragen: ViewModel mit Schritten Erfassen und Bestätigen"`

### Aufgabe 2: Eintragen-Seite (noch nicht verdrahtet)

**Dateien:**
- Neu:
  - `android/feature/heute/src/main/kotlin/de/foody/feature/heute/tagebuch/EintragenSeite.kt`
  - `Erfassen.kt`, `Bestaetigen.kt`, `EintragenTeile.kt`, `Aufnahme.kt` (alle in `tagebuch/`)
- Ändern:
  - `PackungEingabe.kt`;
  - `EintragenBlatt.kt`: die privaten Teile ziehen nach `EintragenTeile.kt`, das Blatt nutzt sie von dort;
  - `android/core/scan/src/main/kotlin/de/foody/core/scan/ScanFotos.kt`: `scanOrdner`, `neuesScanFoto`, `scanFotoUri`
    und `aufraeumen` werden `public`.
- Test: neu `android/feature/heute/src/test/kotlin/de/foody/feature/heute/tagebuch/EintragenSeiteTest.kt`, dazu ein
  Test-Helfer `FesteErgebnisse` (siehe Schritt 1)

**Schnittstellen:**
- Nutzt: alles aus Aufgabe 1.
- Erzeugt:
  ```kotlin
  @Composable fun EintragenRoute(start: EintragenStart, auftrag: String, onFertig: () -> Unit, vm: EintragenViewModel = hiltViewModel())
  const val EINTRAGEN_SPEICHERN = "eintragen-speichern"   // bleibt
  const val ERFASSEN_FOTO = "erfassen-foto"
  const val ERFASSEN_SCANNEN = "erfassen-scannen"
  internal class Aufnahme(val foto: () -> Unit, val galerie: () -> Unit, val scannen: () -> Unit, val tabelle: () -> Unit, val tabelleGalerie: () -> Unit)
  @Composable internal fun rememberAufnahme(vm: EintragenViewModel): Aufnahme
  ```

Aufbau, den die Tests prüfen:

- **`EintragenRoute`**
  - Ruft zuerst und ohne Bedingung `rememberAufnahme(vm)` auf. Dann
    `LaunchedEffect(auftrag) { vm.oeffnen(start.datum, start.mahlzeit, auftrag = auftrag, sofort = start.sofort) }`.
  - Sammelt `vm.schliessen` → `onFertig`.
  - `BackHandler`: `if (!vm.zurueck()) onFertig()`. Der Zurück-Knopf im `Seitenkopf` macht dasselbe.
  - Bei `sofort != null && schritt == ERFASSEN` zeichnet sie nur eine leere `Box(fillMaxSize)`.
  - Sonst eine Seite in der Inhaltsbreite mit `safeDrawingPadding`, keine Leiste darunter (Vollbild ab Aufgabe 3).
    Oben der `Seitenkopf`, darunter der Schritt scrollbar, in Bestätigen unten fest
    `FoodyKnopf("Eintragen", testTag EINTRAGEN_SPEICHERN, aktiv = speichernMoeglich)`.
  - Speichern mit Ergebnis ≠ `null` → `onFertig`.
- **Titel**
  - Erfassen: `eintragenTitel()`.
  - Bestätigen: `auswahlName`, sonst `eintragenTitel()`.
  - `beschreibung = "Eintragen"`.
- **Erfassen**, in dieser Reihenfolge:
  1. Eine Zeile aus zwei großen Knöpfen: „Foto“ (`ERFASSEN_FOTO`, nur mit `fotoKnopf`) und „Scannen“
     (`ERFASSEN_SCANNEN`, gesperrt ohne `scanLauf.verfuegbar`, Fortschritt bei `scanLauf.zustand.laeuft`).
     Ohne `fotoKnopf` steht „Frei eintragen“ an der Stelle von „Foto“.
  2. Darunter Textknöpfe: „Aus der Galerie“ (nur mit `fotoKnopf`) und „Nährwerttabelle fotografieren“.
  3. Der Hinweis: Scan-Meldung, sonst `ScanTexte.NICHT_VERFUEGBAR` ohne Play-Dienste, sonst die Meldung der Erkennung.
  4. Das Suchfeld „Rezept oder Zutat suchen“, ohne Tastatur beim Öffnen.
  5. Der Abschnitt „Geplant“: Zeilen „Titel · Mahlzeit“.
  6. „Rezepte“, „Zutaten“ und „Aus dem Bundeslebensmittelschlüssel“, wie `EintragenZustand` sie liefert.
  7. Zuletzt der Textknopf „Nicht dabei? Frei eintragen“. Er entfällt, wenn „Frei eintragen“ schon oben steht.
- **Bestätigen** je Quelle, wie in der Tabelle der Spec. In jeder Quelle steht die Mahlzeit-Zeile.
  - **REZEPT**: Portionen (− / Feld / +) und „= 600 kcal“ aus `kcalDazu`.
  - **ZUTAT**: Menge und Einheiten-Chips, kcal.
  - **PLAN**: Text „1 Portion · 420 kcal“. Die Mahlzeit nur als Text, ohne Menü.
  - **FREI mit Packung**:
    - `PackungEingabe` ohne „Zurück zu Frei“; die Zeile „bitte prüfen“ nur mit `werte.isNotEmpty()`.
    - Bei `scanLauf.zustand.laeuft` zuerst eine Fortschrittszeile „Sucht das Produkt …“.
    - Die Scan-Meldung.
    - Bei `fotoAnbieten` die Textknöpfe „Foto der Nährwerttabelle“ → `aufnahme.tabelle` und
      „Aus der Galerie“ → `aufnahme.tabelleGalerie`.
  - **FREI mit `fotoBild`**: Vorschaubild 96 dp über `rezeptBild(fotoBild)`, `FotoErkennungStand` (Fortschritt mit
    „Abbrechen“ bzw. Meldung), die Foto-Felder mit Markierung und Portions-Chips wie bisher.
  - **FREI**: die freien Felder.
- **Mahlzeit-Zeile**: „Mahlzeit“ links, der Wert rechts mit ⌄. `heightIn(min = 48.dp)`, Rolle Button,
  contentDescription „Mahlzeit: Mittagessen, ändern“. Sie öffnet `FoodyMenue` mit den vier Mahlzeiten
  (`FoodyMenuePunkt(..., gewaehlt = m == e.mahlzeit)`).
- **`rememberAufnahme`**
  - Vier Launcher:
    - `TakePicture` fürs Mahlzeitfoto: Pfad über `fotoLauf.kameraGestartet`/`kameraZurueck`.
      - Bei `ok`: `vm.fotoGemacht(Uri.fromFile(datei).toString(), datei.path)`.
      - Sonst: Datei löschen, dann `vm.aufnahmeAbgebrochen()`.
    - `PickVisualMedia` fürs Mahlzeitfoto: mit Uri `vm.fotoGemacht(uri, null)`, ohne `vm.aufnahmeAbgebrochen()`.
    - `TakePicture` für die Tabelle: Pfad über `scanLauf.zustand.kameraGestartet`, dann `scanLauf.foto(uri) { datei.delete() }`.
    - `PickVisualMedia` für die Tabelle: `scanLauf.foto(uri)`.
  - Vor einem neuen Versuch aus Erfassen: `scanLauf.zustand.menueVersuch()`.
  - `scannen = scanLauf::strichcode`.
  - Sofort-Foto und Aufräumen wie in `FotoErkennungKnopf`. Erst aufräumen, dann bei `kameraAngefragt`:
    `kameraAnfrageErledigt()`, Kamera starten. Aufgeräumt werden `mahlzeit/` (außer offenem Kamerapfad und
    `fotoDatei`) und `scan/` (außer offenem Tabellenfoto); dazu `scanLauf.vorladen()`.
  - `ActivityNotFoundException`: `kameraZurueck()`, dann `aufnahmeAbgebrochen()`.

- [ ] **Schritt 1: Failing Tests in `EintragenSeiteTest` schreiben.**
  - Rahmen: `createAndroidComposeRule<ComponentActivity>()`, Qualifier `w360dp-h1600dp`.
  - Kamera und Galerie liefern über
    `CompositionLocalProvider(LocalActivityResultRegistryOwner provides …)` mit einem Test-Helfer
    `FesteErgebnisse : ActivityResultRegistry` feste Ergebnisse (`foto: Boolean`, `galerie: Uri?`) und merken sich die
    gestarteten Verträge.
  - `fotoKnopfStartetDieKameraSofort`: Ein Tipp auf `ERFASSEN_FOTO` startet genau einen `TakePicture`-Vertrag. Es gibt
    keinen Knoten „Foto aufnehmen“ (das alte Menü).
  - `kameraAbbruchBleibtBeimErfassen`: `foto = false` → `ERFASSEN_SCANNEN` ist noch da, `onFertig` wurde nicht gerufen.
  - `sofortFotoAbbruchSchliesst`: `EintragenStart(sofort = FOTO)`, `foto = false` → `onFertig` genau einmal. Der Knoten
    „Scannen“ erscheint nie (nach `waitForIdle` geprüft).
  - `sofortFotoZeigtBestaetigenMitFortschritt`: `foto = true`, der Erkenner hängt → `ErkennungTexte.LAEUFT` und
    „Abbrechen“ sind zu sehen.
  - `fotoKommtAuchBeiLeererSeiteAn`: `sofort = FOTO`, `fotoLauf.kameraGestartet(pfad)` wie vor einem Prozessende, dann
    den Launcher-Rückruf mit `true` auslösen (`FesteErgebnisse` liefert ihn verzögert) → Bestätigen mit Vorschaubild.
  - `speichernKehrtZurueck`: Rezept antippen → „Eintragen“ → `onFertig` einmal, ein Eintrag in der Datenbank.
  - `zurueckFuehrtZuErfassenDannHinaus`: Rezept → Bestätigen.
    - `onBackPressedDispatcher.onBackPressed()` → Erfassen.
    - Noch einmal → `onFertig`.
  - `mahlzeitUeberMenue`: Rezept → Mahlzeit-Zeile → „Snack“ → Zeile zeigt „Snack“; gespeichert mit `SNACK`.
  - `gleicherAufbauInAllenStilen`:
    - Je Kombination aus `FoodyStil.entries × StilVariante.entries` (14) über einen `mutableStateOf` neu gesetzt.
    - Die Texte der Knöpfe in Erfassen in Bildschirmreihenfolge sind gleich: „Foto“, „Scannen“, „Aus der Galerie“,
      „Nährwerttabelle fotografieren“, „Nicht dabei? Frei eintragen“.
    - In Bestätigen (Rezept) sind „Mahlzeit“ und „Eintragen“ da.
    - Keiner der Texte „Siegel“, „Deuten“, „Ins Bordbuch“, „Eintragen! ✓“, „In die Chronik“, „Ins Logbuch“ ist da.
  - `ohneModellFreiStattFoto`: `testErkennung(modell = false)` → kein `ERFASSEN_FOTO`, „Frei eintragen“ steht
    oben, „Nicht dabei? Frei eintragen“ nicht.
  - `scanOhneWerteZeigtTabellenKnoepfe`: Die Suche liefert `NichtGefunden`, „Scannen“ → Bestätigen mit leerem
    Namensfeld, `ScanTexte.NICHT_GEFUNDEN`, „Foto der Nährwerttabelle“ und „Aus der Galerie“.
  - `schrift130OhneAbschneiden`: `MitSchrift(1.3f)`, Erfassen und Bestätigen (Foto mit Vorschlag) →
    `pruefeNichtsAbgeschnitten()`.
- [ ] **Schritt 2: Laufen lassen**: `.\gradlew :feature:heute:test --tests "*EintragenSeiteTest*"` → FAIL
- [ ] **Schritt 3: Seite, Schritte, Teile und Aufnahme bauen** wie oben. Das alte Blatt bleibt unverändert, nur seine
  Teile liegen jetzt in `EintragenTeile.kt`.
- [ ] **Schritt 4: Laufen lassen**: `.\gradlew :feature:heute:test :core:scan:test` → PASS
- [ ] **Schritt 5: ktlint und Commit**: `git commit -m "Eintragen: Seite mit Erfassen und Bestätigen"`

### Aufgabe 3: Ziel in der Navigation, altes Blatt weg

**Dateien:**
- Ändern:
  - `android/app/src/main/kotlin/de/foody/app/navigation/Ziele.kt`, `FoodyNavHost.kt`, `Hauptgeruest.kt`
  - `android/feature/heute/src/main/kotlin/de/foody/feature/heute/HeuteScreen.kt`
  - `android/feature/heute/src/main/kotlin/de/foody/feature/heute/tagebuch/TagebuchTagScreen.kt`, `EintragenViewModel.kt`
- Löschen:
  - `tagebuch/EintragenBlatt.kt`, `tagebuch/FotoErkennungKnopf.kt` (`FotoErkennungStand` zieht nach `Bestaetigen.kt`)
  - `BlattAuftrag` und `EintragenReiter`
  - im ViewModel `reiter`, `weg`, `produktWaehlen`, `packungVerwerfen`, `ProduktKurz`, `produkte`, `gescannt`
- Tests:
  - Ändern: `android/app/src/test/kotlin/de/foody/app/navigation/NavigationTest.kt`, `Platzhalter.kt`
  - Neu: `android/feature/heute/src/test/kotlin/de/foody/feature/heute/tagebuch/EintragenScreenshotTest.kt`
  - Löschen: `EintragenBlattTest`, `EintragenBlattFotoTest`, `EintragenBlattPackungTest`, `stil/EintragenStileTest`,
    `FotoScreenshotTest`, `PackungScreenshotTest` samt ihrer Bilder unter `src/test/screenshots/`
  - Erhalten bleibt aus `EintragenBlattTest` der Test `aendernUeberstehtNeuaufbau` (Ändern-Dialog). Er zieht nach
    `TagebuchTagScreenTest`.
  - Diese Prüfungen der alten Blatt-Tests ziehen auf die Seite um, nach `EintragenSeiteTest`: `vorschlagInDenFeldern`,
    `portionsChipsRechnenUm`, `wieBeimLetztenMal`, `meldungOhneVorschlag`, `chipsOhneBasis`,
    `imKatalogFokusAufMenge`.
  - `TagebuchTagScreenTest.chronikEintragenBlattImStil` entfällt. `chronikBlaetternUndEintragen` prüft statt des Blatts
    den Rückruf `onEintragen`.

**Schnittstellen:**
- Nutzt: `EintragenRoute`, `EintragenStart`, `SofortStart` (Aufgaben 1–2).
- Erzeugt:
  ```kotlin
  // Ziele.kt
  @Serializable data class Eintragen(val datum: String, val mahlzeit: String? = null, val sofort: String? = null, val auftrag: String)
  fun EintragenStart.alsZiel(auftrag: String = UUID.randomUUID().toString()): Eintragen
  fun Eintragen.alsStart(heute: LocalDate): EintragenStart   // Datum über tagebuchDatum; unbekannte Mahlzeit/sofort → null
  // FoodyNavHost.kt: Bildschirme bekommt  val eintragen: @Composable (Eintragen, NavHostController) -> Unit
  // HeuteRoute(..., onEintragen: (EintragenStart) -> Unit, vm, schnellstart, onSchnellstartAngenommen)   // eintragenVm entfällt
  // TagebuchTagRoute(datum, onDatum, onZurueck, onEintragen: (EintragenStart) -> Unit, vm)               // eintragenVm entfällt
  ```

Verdrahtung:

- `echt.eintragen`: `EintragenRoute(ziel.alsStart(LocalDate.now()), ziel.auftrag, onFertig = { nav.popBackStack() })`.
- Heute und Tagebuch navigieren mit `nav.navigate(it.alsZiel())`.
- Heute nimmt eine Widget-Anfrage weiter an (bis Aufgabe 5). Statt des Blatts ruft sie
  `onEintragen(EintragenStart(LocalDate.now(), null, sofort))`, dabei wird `SCANNEN → SCANNEN`, `FOTO → FOTO`,
  `EINTRAGEN → null`; danach `onSchnellstartAngenommen()`.
- `Hauptgeruest`: `vollbild` gilt für `Kochen` **oder** `Eintragen`.

- [ ] **Schritt 1: Failing Tests schreiben (NavigationTest, Platzhalter)**
  - `eintragenAusHeuteUndZurueck`: Der Heute-Platzhalter ruft `onEintragen(EintragenStart(tag, SNACK))`. Oben liegt dann
    `Eintragen` mit `mahlzeit == "SNACK"` und `sofort == null`; `popBackStack()` → Heute.
  - `eintragenOhneLeiste`: Auf `Eintragen` ist die Navigationsleiste nicht im Baum (wie bei `Kochen`).
  - `eintragenZielRundreise`: `EintragenStart(tag, MITTAGESSEN, FOTO).alsZiel("a").alsStart(heute)` ist gleich dem
    Start. `Eintragen("kaputt", "QUARK", "BLITZ", "a").alsStart(heute)` ergibt
    `EintragenStart(heute, null, null)`.
- [ ] **Schritt 2: Laufen lassen**: `.\gradlew :app:test --tests "*NavigationTest*"` → FAIL
- [ ] **Schritt 3: Ziel, Verdrahtung und Vollbild bauen. Heute und Tagebuch umstellen, Altes löschen, Alt-Tests
  umziehen oder löschen** (Listen oben).
- [ ] **Schritt 4: `EintragenScreenshotTest` schreiben.**
  - Qualifier `w360dp-h800dp`, je `FoodyStil` in `IMMERSIV`, hell.
  - Drei Zustände: `erfassen`, `bestaetigen_foto` (Fake-Erkenner „Leberkässemmel“, 480 kcal, wie der alte
    `FotoScreenshotTest`) und `bestaetigen_rezept`.
  - Bilder unter `src/test/screenshots/eintragen_<stil>_<zustand>.png`, insgesamt 21.
- [ ] **Schritt 5: Bilder aufnehmen und ansehen**: `.\gradlew :feature:heute:recordRoborazziDebug`. Jedes neue Bild
  öffnen und prüfen: gleicher Aufbau, nichts abgeschnitten.
- [ ] **Schritt 6: Alles laufen lassen**: `.\gradlew check ktlintPruefen` → PASS (dazu `KontrastTest`)
- [ ] **Schritt 7: Commit, dann PR 1 öffnen** („Eintragen: eigenes Ziel mit Erfassen und Bestätigen“, Auto-Merge,
  „Server-Rebuild nötig: nein“)

### Aufgabe 4: Sofort-Wege aus den Heute-Szenen (PR 2)

**Dateien:**
- Ändern: `android/feature/heute/src/main/kotlin/de/foody/feature/heute/HeuteViewModel.kt`, `HeuteScreen.kt`,
  `stil/HeuteEdo.kt`, `stil/HeuteSage.kt`, `stil/HeuteBayrisch.kt`
- Test: `android/feature/heute/src/test/kotlin/de/foody/feature/heute/Beispiele.kt` (`KEINE_AKTIONEN`),
  `stil/HeuteEdoTest.kt`, `HeuteSageTest.kt`, `HeuteBayrischTest.kt`

**Schnittstellen:**
- Nutzt: `SofortStart`, `EintragenStart`, `HeuteRoute(onEintragen)` (Aufgabe 3).
- Erzeugt:
  ```kotlin
  // HeuteZustand bekommt  val fotoMoeglich: Boolean = false   (HeuteViewModel liest ErkennungEinstellung.sichtbar)
  // HeuteAktionen bekommt nach onEintragen:  val onSofort: (SofortStart) -> Unit
  // HeuteRoute: onSofort = { onEintragen(EintragenStart(datum, null, it)) }
  ```

Änderungen an den Szenen:

- **Edo:** „Scannen · Foto“ wird zu den Knöpfen „Scannen“ und „Foto“.
- **Sage:** „+ SCANNEN · FOTO“ wird zu „+ SCANNEN“ und „+ FOTO“.
- **Bayrisch:** Der Kamera-Knopf „Scannen oder Foto“ wird zu zwei runden Symbolknöpfen: `DocumentScanner` mit
  „Scannen“ und `PhotoCamera` mit „Foto“.
- In allen drei Szenen gilt:
  - „Foto“ steht nur mit `fotoMoeglich`.
  - Jeder Knopf ruft direkt `onSofort`.
  - „＋ Eintragen“ und Ähnliches öffnen weiter Erfassen mit der Mahlzeit der Zeile.
  - Die Anordnung bestimmt der Umsetzer, solange bei 90–130 % auf 360 dp nichts abgeschnitten wird und jede Zielfläche
    ≥ 48 dp hat.

- [ ] **Schritt 1: Failing Tests schreiben** (je Szene einer):
  - `scannenUndFotoGetrennt`: „Scannen“ → `onSofort(SCANNEN)`, „Foto“ → `onSofort(FOTO)`. Mit
    `fotoMoeglich = false` gibt es keinen Knopf „Foto“.
  - `szenenKnoepfeBei130`: `MitSchrift(1.3f)` auf `w360dp` → `pruefeNichtsAbgeschnitten()`.
- [ ] **Schritt 2: Laufen lassen**: `.\gradlew :feature:heute:test --tests "*HeuteEdoTest*" --tests "*HeuteSageTest*" --tests "*HeuteBayrischTest*"` → FAIL
- [ ] **Schritt 3: Zustand, Aktionen und die drei Szenen umbauen**
- [ ] **Schritt 4: Szenenbilder neu aufnehmen**: `.\gradlew :feature:heute:recordRoborazziDebug`. Nur die Bilder von
  Edo, Sage und Bayrisch dürfen sich ändern, jedes ansehen.
- [ ] **Schritt 5: Laufen lassen**: `.\gradlew check ktlintPruefen` → PASS
- [ ] **Schritt 6: Commit, PR 2** („Heute: Scannen und Foto starten direkt“)

### Aufgabe 5: Widgets direkt (PR 3)

**Dateien:**
- Neu: `android/app/src/main/kotlin/de/foody/app/navigation/WidgetStart.kt`
- Ändern:
  - `android/app/src/main/kotlin/de/foody/app/navigation/FoodyNavHost.kt`
  - `android/app/src/main/kotlin/de/foody/app/MainActivity.kt`
  - `android/app/src/main/kotlin/de/foody/app/widget/KnopfWidget.kt`, `EintragenWidget.kt` (nur KDoc)
  - `android/feature/heute/src/main/kotlin/de/foody/feature/heute/HeuteScreen.kt`
- Löschen: `android/feature/heute/src/main/kotlin/de/foody/feature/heute/Schnellstart.kt`
- Test: `android/app/src/test/kotlin/de/foody/app/navigation/DeepLinkTest.kt`, `Platzhalter.kt`

**Schnittstellen:**
- Nutzt: `Eintragen`, `alsZiel` (Aufgabe 3), `SofortStart`.
- Erzeugt:
  ```kotlin
  enum class WidgetStart(val sofort: SofortStart?) { SCANNEN(SofortStart.SCANNEN), FOTO(SofortStart.FOTO), EINTRAGEN(null) }
  fun Intent.widgetStart(): WidgetStart?                      // ersetzt Intent.schnellstart(); Links unverändert
  fun NavHostController.widgetStartOeffnen(start: WidgetStart) // ersetzt schnellstartAnfragen
  // Bildschirme.heute wird  @Composable (NavHostController) -> Unit ; HeuteRoute verliert schnellstart/onSchnellstartAngenommen
  ```

Verhalten:

- **`widgetStartOeffnen`**: `popBackStack<Heute>(inclusive = false)`, dann
  `navigate(EintragenStart(LocalDate.now(), null, start.sofort).alsZiel())`.
  - Die Konstante `SCHNELLSTART` und das Lesen aus dem `savedStateHandle` von Heute entfallen.
- **Übergänge, nur für Sofort-Ziele**: Ein Sofort-Ziel ist `Eintragen` mit `sofort != null`.
  - `composable<Eintragen>`: `enterTransition` und `popExitTransition` liefern dann `EnterTransition.None` bzw.
    `ExitTransition.None`, sonst `null` (Standard).
  - `composable<Heute>`: `exitTransition` liefert `ExitTransition.None`, wenn das Ziel ein Sofort-Ziel ist, sonst `null`.
- **`MainActivity`, Kaltstart von „KI-Foto“ oder „Scannen“**:
  - `var verdeckt by remember { mutableStateOf(widgetBeimStart?.sofort != null) }`.
  - `Hauptgeruest` liegt in einer `Box(Modifier.graphicsLayer { alpha = if (verdeckt) 0f else 1f })`.
  - `LaunchedEffect(nav) { widgetBeimStart?.let(nav::widgetStartOeffnen); verdeckt = false }`.
  - So wird Heute nie sichtbar gezeichnet.

- [ ] **Schritt 1: Failing Tests in `DeepLinkTest` schreiben** (ersetzen `widgetLinksFuehrenZuHeuteMitAnfrage`,
  `widgetLinksErkannt` und `schnellstartAlsText`):
  - `widgetLinksOeffnenEintragenUeberHeute`: Aus `RezeptDetail("r1")` öffnet `SCAN_LINK` den Stapel
    `[Heute, Eintragen(sofort = "SCANNEN")]`.
    - Ein zweiter Tipp: weiter genau ein `Eintragen` im Stapel, mit neuem `auftrag`.
    - `FOTO_LINK` → `sofort == "FOTO"`.
    - `EINTRAGEN_LINK` → `sofort == null`.
  - `widgetLinksErkannt`: `foody://scannen`, `foody://foto` und `foody://eintragen` ergeben die drei `WidgetStart`;
    `foody://rezept/r1` und `ACTION_MAIN` ergeben `null`.
- [ ] **Schritt 2: Laufen lassen**: `.\gradlew :app:test --tests "*DeepLinkTest*"` → FAIL
- [ ] **Schritt 3: `WidgetStart`, `widgetStartOeffnen`, Übergänge und `MainActivity` umsetzen. Schnellstart aus Heute
  entfernen, KDoc der Widgets anpassen** („öffnet gleich die Kamera bzw. den Scanner, danach Bestätigen“)
- [ ] **Schritt 4: Laufen lassen**: `.\gradlew check ktlintPruefen` → PASS
- [ ] **Schritt 5: Am Handy prüfen** (Debug-APK, mit Zustimmung des Nutzers installieren).
  - Beide Widgets bei kalter und bei laufender App: nur kurz der Startbildschirm, dann Kamera bzw. Scanner.
  - Abbrechen → Heute.
  - „Schnell eintragen“ → Erfassen.
- [ ] **Schritt 6: Commit, PR 3** („Widgets: Kamera und Scanner ohne Umweg“)

### Aufgabe 6: Doku und ADR (PR 4)

**Dateien:**
- Neu: `docs/adr/0021-eintragen-als-eigenes-ziel.md`
- Ändern:
  - `docs/adr/0020-waehlbare-stile.md`: ein Satz unter „Status“, dass Blatt/Seite je Stil für Eintragen durch 0021
    ersetzt ist.
  - `docs/architecture.md`: Abschnitt „Eintragen (Teil B)“. Er nennt Ziel, Schritte, Sofort-Start, Prozessende und
    Widgets und ersetzt die Beschreibung des Blatts.
  - `ToDoS/stile-plan.md`: „Ergebnis Teil B“ mit den PR-Nummern und dem, was offen bleibt.
  - `docs/superpowers/specs/2026-11-21-eintragen-neu-design.md`: Statuszeile „umgesetzt“.

- [ ] **Schritt 1: ADR 0021 schreiben** (Kontext, Entscheidung „Ansatz 1“, verworfene Ansätze 2 und 3, Folgen: Szenen
  mit getrennten Knöpfen, ein Ausnahmefall beim GMS-Scanner)
- [ ] **Schritt 2: architecture.md, stile-plan.md, ADR 0020 und Spec-Status anpassen**
- [ ] **Schritt 3: Commit, PR 4** („Doku: Eintragen als eigenes Ziel“)

---

## Selbstprüfung

- **Abdeckung der Spec:**
  - Ziel 1: Aufgaben 1 und 2.
  - Ziel 2: Aufgabe 2, `fotoKnopfStartetDieKameraSofort`, `kameraAbbruchBleibtBeimErfassen`.
  - Ziel 3: Aufgaben 4 und 5; das Tagebuch öffnet Erfassen ab Aufgabe 3.
  - Ziel 4: `mahlzeitUeberMenue`, `geplantNimmtDieMahlzeitDesPlans`, `startetBeimErfassen`.
  - Ziel 5: `gleicherAufbauInAllenStilen` und die Screenshots.
  - Ziel 6: Aufgabe 3.
  - Tabelle der Quellen: Aufgabe 1 (Regeln) und Aufgabe 2 (Bestätigen).
  - „Meine Entscheidungen“:
    - Frei eintragen: `ohneModellFreiStattFoto`.
    - Gescannte Produkte in der Suche: `sucheErstMitText`.
    - Szenen-Knöpfe: Aufgabe 4.
    - Nährwerttabelle: Aufgabe 2.
    - Widget bei laufender App: Aufgabe 5.
    - Geplantes nur zur Ansicht und kcal dazu: Aufgabe 1.
    - ADR: Aufgabe 6.
  - Prüfung der Spec: VM-, Seiten-, Navigations- und Screenshot-Tests sowie die Prüfung am Handy in Aufgabe 5.
- **Dem Umsetzer überlassen und hier entschieden:**
  - Die Launcher liegen in der Seite; Heute und die Widgets öffnen das Ziel mit `sofort`.
  - Übergabe über die Argumente des Ziels.
  - PR-Schnitt wie in der Tabelle oben.
  - Die Tastatur öffnet sich bei „Schnell eintragen“ nicht.
- **Entscheidungen ohne Vorgabe der Spec:**
  - Eintragen ist Vollbild ohne Navigationsleiste. Kosten, falls falsch: eine Zeile in `Hauptgeruest`.
  - Beide Schritte haben den Zurück-Pfeil statt ✕.
  - Die leere Packung hat die Quelle `QUELLE_HAND`.
  - Prozessende im Sofort-Scan führt zu Erfassen.
