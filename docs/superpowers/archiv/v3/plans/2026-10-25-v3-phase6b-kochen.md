# V3 Phase 6b Kochen – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Kochmodus der V3: Bereitlegen, ein Schritt pro Seite mit „Du brauchst“ und Timern, die im Hintergrund weiterlaufen, Abschluss mit „gekocht“, Foto und „Rezept anpassen“.

**Architecture:** „Gekocht“ in `:core:data` (`KochenRepository`), alles andere im neuen `:feature:kochen`: reine Funktionen für Schritte und Timer-Zeitplan, Sitzung und Timer in SharedPreferences, ein Vordergrunddienst für die Timer, Bildschirm mit ViewModel. `:app3` hängt das Ziel ein und blendet die Navigation aus; der Editor aus 6a bekommt den Portionen-Hinweis.

**Tech Stack:** Kotlin 2.4.20, Compose (BOM 2026.09.00), Navigation Compose 2.10.2 (typisierte Routen), Hilt 2.60.1, Room 2.8.5, Coil 3.6.3, Robolectric 4.16.1, Roborazzi 1.76.0.

**Spec:** `docs/superpowers/specs/2026-10-25-v3-phase6b-kochen-design.md`

## Global Constraints

- v1 unverändert: `android/app/**`, `:sync-protocol`; Server-Code unverändert; v1 (`CookModeDialog`, `CookTimerService`, `CookTimerRepository`) nur als Vorlage lesen.
- Kein Schema-Wechsel: Datenbank bleibt Version 10, `android/core/data/schemas` unverändert.
- `:feature:kochen` = `android/feature/kochen`, Paket `de.foody.feature.kochen`, Plugins wie `:feature:rezepte` (`foody.android.feature`, `ksp`, `hilt`, `roborazzi`); hängt nur von `:core:*` und `:domain` ab, **nicht** von `:feature:rezepte`.
- UI nur aus `:core:designsystem`; jede Datei in `android/feature/kochen/src/main` ≤ 300 Zeilen; je Bildschirm ein ViewModel (`hiltViewModel()`).
- Mengen skalieren nur mit `RecipeScaler.scale`, anzeigen mit `QuantityFormatter.format(Quantity.of(m, einheit), einheit)`; Menge 0 = „ohne Menge“ (nur Hinweis).
- Portionen 1–99; Timer nur 1 s bis 24 h; Sitzung und abgelaufene Timer gelten 12 Stunden (`ZWOELF_STUNDEN_MS = 43_200_000`).
- Zeit nur über eine injizierte `java.time.Clock` (Hilt-Provider `Clock.systemDefaultZone()` in `KochenModul`), nie `System.currentTimeMillis()` direkt.
- Robolectric-UI-Tests: `@GraphicsMode(NATIVE)`, `junit4.v2.createComposeRule`, `application = android.app.Application::class`; Blätter/Dialoge ohne Breiten-Qualifier; Room im Test mit `setQueryExecutor { it.run() }`; ViewModels außerhalb der Komposition anlegen.
- Deutsch in Texten, Bezeichnern, Kommentaren; `allWarningsAsErrors` bleibt an. Texte: „Bereitlegen“, „Schritt 3 von 7“, „4 von 9 bereit“, „Alle zurücksetzen“, „Du brauchst“, „von 400 g“, „Weiter“, „Zurück“, „Fertig“, „Guten Appetit!“, „Foto aufnehmen“, „War etwas anders? Rezept anpassen“, „Schließen“, „Rezept nicht gefunden“, „abgelaufen“, „Ohne Benachrichtigungen meldet sich der Timer nur bei offener App“, „Gekocht mit 4 statt 2 Portionen – Mengen beziehen sich auf 2“.
- PR gegen `main` mit Auto-Merge; „Server aktualisieren? Nein“.

## Review Focus

1. **Timer läuft ab, während die App beendet ist** – beim nächsten Öffnen steht er als „abgelaufen“ da, der Dienst meldet ihn einmal (nicht bei jedem Start erneut); nach 12 h verschwindet er (Test in Task 3).
2. **Rezept per Sync geändert, während gekocht wird** – weniger Schritte: Seite auf die letzte begrenzt; weniger Zutaten: Haken mit Index ≥ Zeilenzahl fallen weg; kein Absturz (Test in Task 4).
3. **„Fertig“ doppelt getippt** (oder zweimal gekocht) – höchstens ein Plan-Eintrag je Tipp, nie zwei auf einmal (Test in Task 1 und Task 5).
4. **Deep Link mit Portionen 0 oder 500** – auf 1–99 begrenzt, keine Division durch 0 (Test in Task 4).
5. **Benachrichtigung zu einem inzwischen gelöschten Rezept** – „Rezept nicht gefunden“, laufende Timer bleiben sichtbar und lassen sich wegtippen, Sitzung gelöscht (Test in Task 4).

---

### Task 1: `KochenRepository` in `:core:data`

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/kochen/KochenRepository.kt`
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/DatenModul.kt` (Provider wie `rezeptRepository`), `Daos.kt` (`PlanEintragDao.offeneHeute(rezeptId, datum)`, `gekochtSetzen(id, zeit)`)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/kochen/KochenRepositoryTest.kt`

**Interfaces:**
- Produces: `class KochenRepository(db: FoodyDatenbank, uhr: () -> Long) { suspend fun gekochtMarkieren(rezeptId: String, heute: LocalDate): Boolean }` – in einer Transaktion der erste Eintrag mit `date == heute`, `recipeId == rezeptId`, `cookedAt IS NULL`, sortiert nach `slotType`, dann `id`; setzt `cookedAt = updatedAt = uhr()`. Provider: `KochenRepository(db, System::currentTimeMillis)`.

- [ ] **Step 1: Tests** `KochenRepositoryTest` (Room im Speicher): `markiertHeutigenEintrag` (→ `true`, `cookedAt == 5_000`), `nurHeuteUndDiesesRezept` (Eintrag gestern / anderes Rezept bleibt `null`, Ergebnis `false`), `zweiAufrufeZweiEintraege` (zwei offene heute → jeder Aufruf markiert genau einen; dritter Aufruf `false`), `ohneEintragFalse`, `landetInOutbox` (`sync_outbox` enthält `meal_slot`/ID nach dem Markieren).
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest --tests "*KochenRepositoryTest*"` → FAIL (Klasse fehlt).
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** `./gradlew :core:data:testDebugUnitTest` → grün.
- [ ] **Step 5:** Commit `"V3 Phase 6b: Gekocht markieren"`.

### Task 2: `:feature:kochen`, Schritte und Sitzung

**Files:**
- Create: `android/feature/kochen/build.gradle.kts` (wie `:feature:rezepte`, ohne `coil-test`), `settings.gradle.kts`-Eintrag, `src/main/kotlin/de/foody/feature/kochen/{KochSchritte,KochSitzung,KochenModul}.kt`
- Test: `src/test/kotlin/de/foody/feature/kochen/{KochSchritteTest,KochSitzungTest}.kt`

**Interfaces:**
- Consumes: `RezeptVoll`, `RezeptZeile` (6a), `StepIngredientMatcher.mentions`, `StepTimerParser.find`, `RecipeScaler.scale`, `QuantityFormatter`.
- Produces:
  - `data class BereitZeile(val zeilenIndex: Int, val menge: String?, val name: String, val hinweis: String?, val optional: Boolean)`; `fun bereitlegen(voll: RezeptVoll, portionen: Int): List<BereitZeile>`.
  - `data class SchrittZutat(val zeilenIndex: Int, val name: String, val menge: String?, val gesamt: String?)` – `menge` = Teilmenge aus dem Schritt, sonst Gesamtmenge; `gesamt` nur gesetzt, wenn eine Teilmenge angezeigt wird.
  - `data class KochSchritt(val text: String, val zutaten: List<SchrittZutat>, val timer: List<StepTimer>)`; `fun kochSchritte(voll: RezeptVoll, portionen: Int): List<KochSchritt>` – Matcher-Schlüssel ist der Zeilenindex als Text (gleiche Zutat zweimal = zwei Zeilen); Timer nur `1 s ≤ dauer ≤ 24 h`.
  - `data class KochSitzung(val rezeptId: String, val portionen: Int, val seite: Int, val abgehakt: Set<Int>, val geaendertAm: Long)`; `class KochSitzungSpeicher(prefs: SharedPreferences, clock: Clock) { fun lesen(rezeptId: String): KochSitzung?; fun schreiben(s: KochSitzung); fun loeschen(rezeptId: String) }` – JSON über `org.json`, Schlüssel `sitzung_<rezeptId>`; `lesen` liefert `null` und löscht, wenn `clock.millis() - geaendertAm > ZWOELF_STUNDEN_MS`.
  - `KochenModul` (Hilt, `SingletonComponent`): `Clock`, `KochSitzungSpeicher` (Prefs `kochen_sitzungen`), `@Named("kochen_timer") SharedPreferences`.

- [ ] **Step 1: Tests** `KochSchritteTest` (Rezept 2 Portionen: 400 g Reis, 1 Prise Salz ohne Menge, 2 Zwiebeln; Schritte „150 g Reis waschen.“, „Reis und Zwiebeln 20 Minuten garen.“, „Abschmecken.“): `teilmengeAusSchritt` (Schritt 1: `menge == "150 g"`, `gesamt == "400 g"`), `gesamtOhneTeilmenge` (Schritt 2: Reis `menge == "400 g"`, `gesamt == null`), `skaliert` (4 Portionen → „300 g“ / „800 g“), `ohneMenge` (Salz in `bereitlegen`: `menge == null`, Hinweis „1 Prise“), `salzUndPfeffer` (Zeile „Salz und Pfeffer“, Schritt „mit Pfeffer würzen“ → getroffen), `schrittOhneZutat` (Schritt 3: `zutaten` leer), `timerGrenzen` („0 Minuten“ und „25 Stunden“ → kein Timer; „20 Minuten“ → einer). `KochSitzungTest` (Robolectric-Prefs, feste `Clock`): `schreibenLesen`, `abgelaufenNach12Stunden` (12 h + 1 ms → `null`, danach auch kein Eintrag mehr), `loeschen`.
- [ ] **Step 2:** `./gradlew :feature:kochen:testDebugUnitTest` → FAIL.
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** grün.
- [ ] **Step 5:** Commit `"V3 Phase 6b: Kochschritte und Sitzung"`.

### Task 3: Timer – Speicher, Zeitplan, Dienst

**Files:**
- Create: `android/feature/kochen/src/main/kotlin/de/foody/feature/kochen/timer/{LaufenderTimer,KochTimerSpeicher,TimerZeitplan,KochTimerDienst,TimerBenachrichtigung}.kt`, `android/feature/kochen/src/main/AndroidManifest.xml`
- Test: `src/test/kotlin/de/foody/feature/kochen/timer/{KochTimerSpeicherTest,TimerZeitplanTest}.kt`

**Interfaces:**
- Produces:
  - `data class LaufenderTimer(val id: String, val rezeptId: String, val portionen: Int, val rezeptName: String, val label: String, val endeUm: Long, val gemeldet: Boolean = false)`.
  - `@Singleton class KochTimerSpeicher @Inject constructor(@Named("kochen_timer") prefs: SharedPreferences, clock: Clock) { val timer: StateFlow<List<LaufenderTimer>>; fun starten(rezeptId: String, portionen: Int, rezeptName: String, label: String, dauer: Duration): String; fun entfernen(id: String); fun gemeldet(id: String) }` – JSON in den Prefs, beim Anlegen gelesen; Timer mit `endeUm` älter als 12 h fallen beim Lesen weg.
  - `data class Zeitplan(val neuAbgelaufen: List<LaufenderTimer>, val naechstesEnde: Long?)`; `fun zeitplan(timer: List<LaufenderTimer>, jetzt: Long): Zeitplan` – `neuAbgelaufen` = `endeUm <= jetzt && !gemeldet`; `naechstesEnde` = kleinstes `endeUm > jetzt`.
  - `class KochTimerDienst : Service` (`@AndroidEntryPoint`, `foregroundServiceType="specialUse"`) mit `companion object { fun wennNoetigStarten(context: Context, speicher: KochTimerSpeicher) }` (startet nur, wenn ein Timer noch läuft oder ungemeldet abgelaufen ist). Ablauf wie v1 `CookTimerService`: `collectLatest` auf `timer`, Schleife über `zeitplan`, je `neuAbgelaufen` Benachrichtigung „<Rezept>: <Label> sind um“ + `speicher.gemeldet(id)`; Teil-Wakelock bis `naechstesEnde + 2 s`; ohne laufende Timer `stopSelf()`. Tipp auf eine Benachrichtigung: `Intent(ACTION_VIEW, "foody://kochen/<rezeptId>?portionen=<n>")` mit `setPackage(packageName)`.
  - Manifest: Dienst, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK`; Kanäle `kochen_laufend` („Laufende Timer“, leise) und `kochen_ende` („Timer abgelaufen“, Ton + Vibration).

- [ ] **Step 1: Tests** `KochTimerSpeicherTest`: `startenUndEntfernen`, `wiederherstellenNachNeustart` (zweiter Speicher mit denselben Prefs sieht den Timer), `gemeldetBleibtGemeldet` (Review Focus 1: nach `gemeldet(id)` und Neustart → `gemeldet == true`), `alteAbgelaufeneVerschwinden` (Ende vor 12 h + 1 ms → weg), `zweiGleicheTimerUnabhaengig`. `TimerZeitplanTest`: `naechstesEnde`, `nurUngemeldeteAbgelaufen`, `leer` (`Zeitplan(emptyList(), null)`).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen (Dienst ohne eigenen Test – Sichtprüfung in Task 7). **Step 4:** `./gradlew :feature:kochen:testDebugUnitTest :feature:kochen:lintDebug` grün. **Step 5:** Commit `"V3 Phase 6b: Kochtimer"`.

### Task 4: Kochbildschirm

**Files:**
- Create: `android/feature/kochen/src/main/kotlin/de/foody/feature/kochen/{KochenViewModel,KochenScreen,BereitlegenSeite,SchrittSeite,TimerLeiste}.kt`
- Test: `src/test/kotlin/de/foody/feature/kochen/{KochenViewModelTest,KochenScreenTest,TestHilfen}.kt` (`TestHilfen` aus `:feature:rezepte` kopiert)

**Interfaces:**
- Consumes: Task 2 und 3, `RezeptRepository.voll`, `KochenRepository`.
- Produces:
  - `sealed interface KochZustand { Laden; NichtGefunden; data class Da(val name: String, val bild: String?, val rezeptPortionen: Int, val portionen: Int, val bereit: List<BereitZeile>, val schritte: List<KochSchritt>, val seite: Int, val abgehakt: Set<Int>) }` – Seiten = (`bereit` nicht leer **oder** `schritte` leer ? [Bereitlegen] : []) + Schritte; `seite` immer im gültigen Bereich.
  - `@HiltViewModel class KochenViewModel @Inject constructor(repo: RezeptRepository, kochen: KochenRepository, sitzungen: KochSitzungSpeicher, timerSpeicher: KochTimerSpeicher, clock: Clock)` mit `val zustand: StateFlow<KochZustand>`, `val timer: StateFlow<List<LaufenderTimer>>`, `fun starten(rezeptId: String, portionen: Int?)` (Portionen: Argument, sonst Sitzung, sonst Rezept; begrenzt 1–99; Seite/Haken aus der Sitzung), `fun seiteWaehlen(i: Int)`, `fun abhaken(zeilenIndex: Int)`, `fun alleZuruecksetzen()`, `fun portionenSchritt(mehr: Boolean)`, `fun timerStarten(t: StepTimer): String`, `fun timerEntfernen(id: String)`. Jede Änderung schreibt die Sitzung; `NichtGefunden` löscht sie.
  - `@Composable fun KochenRoute(rezeptId: String, portionen: Int?, onRezeptAnpassen: (String, Int) -> Unit, onSchliessen: () -> Unit, vm: KochenViewModel = hiltViewModel())` – Kopf, `HorizontalPager`, Timer-Leiste, Zurück/Weiter; `view.keepScreenOn` per `DisposableEffect`; Benachrichtigungs-Freigabe beim ersten Timer, abgelehnt → einmal der Hinweis (Flag in den Timer-Prefs), Ton + Vibration bei offener App für abgelaufene Timer. Der Fertig-Teil kommt in Task 5 (hier: „Fertig“ ruft vorerst `onSchliessen`).

- [ ] **Step 1: Tests** `KochenViewModelTest` (Room + echte Speicher, feste `Clock`): `seitenMitBereitlegen`, `ohneZutatenKeinBereitlegen`, `ohneSchritteNurBereitlegen`, `portionenBegrenzt` (Review Focus 4: `starten("r1", 0)` → 1, `starten("r1", 500)` → 99), `sitzungFortsetzen` (Seite 2 + Haken {0}, neues VM → gleiche Seite und Haken), `anderePortionenBehaltenSeite`, `portionenSchrittSkaliert` (2 → 3 Portionen: Reis „600 g“ in `bereit`), `rezeptGeaendertBegrenztSeite` (Review Focus 2: auf letzter Seite Schritt löschen → `seite` = neue letzte; Zeile löschen → Haken für Index ≥ Zeilenzahl weg), `geloeschtNichtGefunden` (Review Focus 5: Timer bleibt in `timer`, Sitzung gelöscht). `KochenScreenTest` (`w411dp-h891dp`): `bereitlegenAbhaken` („0 von 2 bereit“ → Tipp → „1 von 2 bereit“), `weiterZuSchritt` („Schritt 1 von 3“, „Du brauchst“, „von 400 g“), `timerStartenZeigtLeiste` (Chip „20 Minuten“ → Leiste mit Restzeit), `nichtGefundenZurueck`, `schrift200OhneAbschneiden`, `ueberstehtDrehen` (`StateRestorationTester`).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:kochen:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6b: Kochbildschirm"`.

### Task 5: Abschluss, Foto, Tablet

**Files:**
- Create: `android/feature/kochen/src/main/kotlin/de/foody/feature/kochen/{FertigBlatt,SeitenListe}.kt`
- Modify: `KochenViewModel.kt` (`fertig`, `fotoUebernehmen`, `schliessen`), `KochenScreen.kt`
- Test: `KochenViewModelTest`, `KochenScreenTest` (erweitert)

**Interfaces:**
- Produces: `suspend fun fertig(): Boolean` (Sperre gegen Doppeltipp wie `EditorViewModel.speichern`; `kochen.gekochtMarkieren(id, LocalDate.now(clock))`, Sitzung löschen, Blatt offen), `suspend fun fotoUebernehmen(quelle: Uri)` (`FotoAblage.speichern` → `RezeptRepository.fotoSetzen`), `fun schliessen()` (Sitzung löschen). Kamera wie im 6a-Editor: `TakePicture` über den FileProvider `"${context.packageName}.rezeptfotos"` in `cache/kamera/`. Ab 840 dp: links `SeitenListe` (280 dp, „Bereitlegen“ + Schritte gekürzt auf eine Zeile, Tipp springt), rechts die Seite, höchstens 720 dp.

- [ ] **Step 1: Tests** `KochenViewModelTest`: `fertigMarkiertEinmal` (Review Focus 3: zwei parallele `fertig()` → ein `true`, ein Plan-Eintrag markiert), `fertigLoeschtSitzung`, `fertigOhnePlanEintrag` (Blatt trotzdem). `KochenScreenTest`: `fertigBlattMitFoto` (Rezept ohne Foto: „Guten Appetit!“, „Foto aufnehmen“), `fertigBlattOhneFotoKnopf` (Rezept mit Foto), `rezeptAnpassen` (Tipp → `onRezeptAnpassen("r1", 3)`), `tabletSeitenliste` (`w1280dp-h800dp`: Tipp auf „Schritt 2“ in der Liste → „Schritt 2 von 3“).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6b: Abschluss und Tablet"`.

### Task 6: Navigation, Editor-Hinweis, Timer beim Start

**Files:**
- Modify: `android/app3/build.gradle.kts` (`:feature:kochen`), `navigation/{Ziele,FoodyNavHost,Hauptgeruest}.kt`, `MainActivity.kt`; `android/feature/rezepte/src/main/kotlin/de/foody/feature/rezepte/editor/{EditorScreen,EditorViewModel}.kt`
- Test: `android/app3/src/test/kotlin/de/foody/app3/navigation/{NavigationTest,DeepLinkTest}.kt`, `android/feature/rezepte/src/test/kotlin/de/foody/feature/rezepte/editor/EditorScreenTest.kt`

**Interfaces:**
- Produces: `RezeptBearbeiten(val id: String, val gekochtMit: Int? = null)`; `EditorRoute(id, onFertig, onZurueck, gekochtMit: Int? = null, vm)` zeigt über den Feldern „Gekocht mit {gekochtMit} statt {Rezeptportionen} Portionen – Mengen beziehen sich auf {Rezeptportionen}“, wenn `gekochtMit` gesetzt und ungleich der geladenen Portionen; `Bildschirme.kochen: @Composable (String, Int?, NavHostController) -> Unit` – „Rezept anpassen“ navigiert zu `RezeptBearbeiten(id, portionen)` mit `popUpTo<Kochen> { inclusive = true }`, „Schließen“ = `popBackStack()`; `Hauptgeruest` blendet Leiste und Rail aus, solange das aktuelle Ziel `hasRoute<Kochen>()` ist; `MainActivity.onStart` ruft `KochTimerDienst.wennNoetigStarten(this, speicher)`.

- [ ] **Step 1: Tests** `NavigationTest`: `kochenOhneLeiste` (nach `navigate(Kochen("r1", 2))` kein Knoten mit Tag `Hauptgeruest.LEISTE`), `kochenZuRezeptAnpassen` (Platzhalter-`kochen` ruft „anpassen“ → Ziel `RezeptBearbeiten("r1", 2)`, `Kochen` nicht mehr im Stapel). `DeepLinkTest`: `kochenMitPortionen` bleibt grün. `EditorScreenTest`: `hinweisGekochtMitAnderenPortionen` („Gekocht mit 4 statt 2 Portionen – Mengen beziehen sich auf 2“), `keinHinweisBeiGleichenPortionen`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:rezepte:testDebugUnitTest :app3:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6b: Navigation und Editor-Hinweis"`.

### Task 7: Screenshots, Sichtprüfung, Abschluss

**Files:**
- Create: `android/feature/kochen/src/test/kotlin/de/foody/feature/kochen/KochenScreenshotTest.kt`, Referenzbilder in `android/feature/kochen/src/test/screenshots/`
- Modify: `.github/workflows/ci.yml` (`:feature:kochen:verifyRoborazziDebug`), `docs/architecture.md` (Abschnitt „Kochen (Phase 6b)“), `ToDoS/v3-plan.md` (Phase 6: „Kochen“ abhaken mit Ergebnis), `ToDoS/v3-abnahme.md` (Abschnitt „Kochen“)

- [ ] **Step 1:** `KochenScreenshotTest`: Bereitlegen, Schrittseite mit laufendem Timer, Fertig-Blatt – hell/dunkel × Handy `w411dp-h1600dp` / Tablet `w1280dp-h1200dp`, Animationen aus, Toleranz 0,1 %, erfundene Daten.
- [ ] **Step 2:** `recordRoborazziDebug`, Bilder ansehen; Befunde per TDD beheben; `verifyRoborazziDebug` grün, Gegenprobe rot.
- [ ] **Step 3:** Sichtprüfung auf `Foody_Test` (Port 5556, AVD-Name prüfen) gegen den Test-Server (SSH-Tunnel, `http://10.0.2.2:8081`): Rezept kochen, Timer „1 Minute“ starten, Bildschirm aus (`input keyevent KEYCODE_SLEEP`), Benachrichtigung kommt, antippen öffnet den Schritt; Plan-Eintrag heute → „Fertig“ markiert ihn; Screenshots an den Nutzer; danach App deinstallieren, Emulator und Tunnel beenden.
- [ ] **Step 4:** Zeilencheck (keine Datei in `android/feature/kochen/src/main` über 300); `./gradlew check :app3:assembleDebug :feature:kochen:verifyRoborazziDebug :feature:rezepte:verifyRoborazziDebug :feature:heute:verifyRoborazziDebug :katalog:verifyRoborazziDebug` grün.
- [ ] **Step 5:** Doku; Commit `"V3 Phase 6b: Doku und Abschluss"`; Gesamtreview; PR gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 → 7 (1–3 unabhängig voneinander; 4 braucht 1–3; 5 braucht 4; 6 braucht 4). Ein Branch (`claude/v3-phase6b-kochen`), ein PR am Ende.
