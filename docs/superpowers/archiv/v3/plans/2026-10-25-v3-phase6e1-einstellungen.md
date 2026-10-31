# V3 Phase 6e1 Einstellungen – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Einstellungsseite der V3: Konto, Darstellung, Tagesziel, Sicherung erstellen/wiederherstellen, alle Daten löschen, Hinweise; „Server verbinden“ als eigene Seite.

**Architecture:** Speicher und Löschen in `:core:data` (`DarstellungSpeicher`, `TageszielSpeicher.setzen`, `Sicherung.allesLoeschen` mit `StartdatenLader`), Oberfläche in `:feature:einstellungen` mit einem `EinstellungenViewModel`; `:app3` bekommt das Ziel `ServerVerbinden` und wendet die Darstellung in `MainActivity` an.

**Tech Stack:** Kotlin 2.4.20, Compose (BOM 2026.09.00), Navigation Compose 2.10.2, Hilt 2.60.1, Room 2.8.5, Robolectric 4.16.1, Roborazzi 1.76.0.

**Spec:** `docs/superpowers/specs/2026-10-25-v3-phase6e1-einstellungen-design.md`

## Global Constraints

- v1 unverändert: `android/app/**`, `:sync-protocol`; Server-Code unverändert; v1-Einstellungen (`ui/settings`) nur als Vorlage lesen.
- Kein Schema-Wechsel: Datenbank bleibt Version 10.
- Prefs `foody` mit v1-Schlüsseln: `Einstellungsschluessel.DARSTELLUNG` = `themeMode` mit `SYSTEM`/`LIGHT`/`DARK`, `Einstellungsschluessel.TAGESZIEL` = `dailyKcalGoal`.
- `:feature:einstellungen` hängt nur von `:core:*` ab (neu: `:core:data`); jede Datei in `src/main` ≤ 300 Zeilen; Roborazzi-Plugin wie `:feature:woche`.
- Tagesziel 500–10.000 kcal; leer = kein Ziel.
- Wiederherstellen und Löschen bei verbundenem Sync gesperrt; „Sicherung erstellen“ immer erlaubt.
- Robolectric-UI-Tests: `@GraphicsMode(NATIVE)`, `junit4.v2.createComposeRule`, `application = android.app.Application::class`; Dialoge mit Textfeld ohne Breiten-Qualifier; ViewModels außerhalb der Komposition. Die Seite selbst enthält ein Textfeld (Tagesziel) – Tablet-Tests prüfen per Bounds ohne Eingabe.
- Texte (wörtlich): „Einstellungen“, „Konto“, „Verbunden mit <Adresse>“, „Zuletzt synchronisiert: …“, „Nicht verbunden“, „Server verbinden“, „Darstellung“, „System“, „Hell“, „Dunkel“, „Tagesziel“, „kcal“, „Leer lassen = kein Ziel“, „500 bis 10.000 kcal“, „Sicherung“, „Rezepte, Zutaten, Plan, Einkauf, Tagebuch und Fotos als ZIP-Datei.“, „Sicherung erstellen“, „Sicherung gespeichert“, „Sicherung fehlgeschlagen: <Grund>“, „Wiederherstellen“, „Alle Daten ersetzen?“, „<n> Rezepte, <n> Zutaten, <n> Fotos wiederhergestellt“ (+ „ · Vorrat übersprungen: <n>“), „Daten“, „Alle Daten löschen“, „Alle Rezepte, Pläne, Einkaufslisten, Tagebuch und Fotos auf diesem Gerät löschen? Der Startkatalog wird neu eingespielt.“, „Alle Daten gelöscht“, „Zuerst unter Konto die Verbindung trennen.“, „Läuft …“, „Hinweise“, „Nährwerte sind rechnerische Näherungswerte und ersetzen keine ernährungsmedizinische Beratung.“
- Deutsch in Texten, Bezeichnern, Kommentaren; `allWarningsAsErrors` bleibt an. PR gegen `main` mit Auto-Merge; „Server aktualisieren? Nein“.

## Review Focus

1. **Tagesziel mit Tausenderpunkt oder Leerzeichen** – „2.000“ und „ 2000 “ gelten als 2000 kcal, nicht als Fehler (Test in Task 3).
2. **Sync wird zwischen Rückfrage und Bestätigen verbunden** – `allesLoeschen` prüft in der Transaktion erneut und ändert nichts (Test in Task 2).
3. **Gelöschte eigene Fotos im Sync-Fotoindex** – nach dem Löschen sind sie auch dort entfernt (`loeschenFotoLokal`), sonst lädt der nächste Sync sie nicht neu bzw. meldet Fehler (Test in Task 2).
4. **v1-Sicherung wiederherstellen** – Meldung nennt „Vorrat übersprungen: n“ (Test in Task 3).
5. **Dateiname am Datum der App-Uhr** – `foody-sicherung-2026-10-28.zip` aus der injizierten `Clock`, nicht aus `System` (Test in Task 3).

---

### Task 1: Darstellung und Tagesziel speichern

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/DarstellungSpeicher.kt`
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/TageszielSpeicher.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/{DarstellungSpeicherTest,TageszielSpeicherTest}.kt`

**Interfaces:**
- Produces: `enum class Darstellung { SYSTEM, HELL, DUNKEL }`; `class DarstellungSpeicher @Inject constructor(@ApplicationContext kontext: Context) { fun darstellung(): Flow<Darstellung>; fun setzen(d: Darstellung) }` (gespeichert `SYSTEM`/`LIGHT`/`DARK`; Flow per `callbackFlow` wie `TageszielSpeicher`); `TageszielSpeicher.setzen(kcal: Int?)` (`null` entfernt, 500–10.000 speichert, sonst `IllegalArgumentException`); `const val TAGESZIEL_MIN = 500`, `const val TAGESZIEL_MAX = 10_000` (in `TageszielSpeicher.kt`).

- [ ] **Step 1: Tests** `DarstellungSpeicherTest`: `ohneWertSystem`, `v1WerteGelesen` (`LIGHT` → `HELL`, `DARK` → `DUNKEL`), `unbekanntSystem` („PINK“ → `SYSTEM`), `setzenSchreibtV1Wert` (`setzen(DUNKEL)` → Prefs-String `DARK`), `flussMeldetAenderung`. `TageszielSpeicherTest`: `setzenUndLesen` (2000), `nullEntfernt`, `grenzen` (500 und 10.000 gespeichert; 499 und 10.001 → `IllegalArgumentException`, Wert unverändert).
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest --tests "*DarstellungSpeicherTest*" --tests "*TageszielSpeicherTest*"` → FAIL (fehlt).
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün.
- [ ] **Step 5:** Commit `"V3 Phase 6e1: Darstellung und Tagesziel speichern"`.

### Task 2: Alle Daten löschen

**Files:**
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/sicherung/Sicherung.kt` (Konstruktor + `allesLoeschen`; Leeren aus `ersetzen` in `private suspend fun leeren()` herausziehen), `android/core/data/src/main/kotlin/de/foody/core/data/startdaten/StartdatenLader.kt` (`fun versionZuruecksetzen()`), `android/core/data/src/main/kotlin/de/foody/core/data/DatenModul.kt` (Provider)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/sicherung/{SicherungLoeschenTest,SicherungTest}.kt` (`SicherungTest` nur Konstruktor)

**Interfaces:**
- Produces: `class Sicherung(context, db, ablage, sync, startdaten: StartdatenLader)`; `sealed interface LoeschErgebnis { data object Fertig; data object SyncAktiv }`; `suspend fun allesLoeschen(): LoeschErgebnis` – Sync aktiv → `SyncAktiv` (auch erneut in der Transaktion geprüft); sonst `leeren()` in einer Transaktion (`NonCancellable` wie `lesen`), dann `startdaten.versionZuruecksetzen()`, eigene Fotos per `ablage.unbenutzteEntfernen(emptyList())` entfernen und je Link `db.syncDao().loeschenFotoLokal(link)`, dann `startdaten.ladenWennNoetig()`; Fehler nach der Transaktion nur protokollieren. `StartdatenLader.versionZuruecksetzen()` setzt `seedVersion` = 0 und entfernt `seeded`.

- [ ] **Step 1: Tests** `SicherungLoeschenTest` (Bestand: Rezept mit eigenem Foto, Zeilen, Plan, Liste mit Artikel und Herkunft, Tagebuch, eigene Zutat; Prefs `dailyKcalGoal` = 2000): `allesLeerAusserStartkatalog` (alle Fachtabellen leer bis auf Zutaten; Zutaten = Startkatalog, z. B. „Reis“ aus `Startdaten` vorhanden, eigene Zutat weg), `eigeneFotosEntferntFremdeLinksNicht` (Datei im Fotoordner weg; `content:`-Link-Rezept ist ohnehin gelöscht – kein Fehler), `fotoindexBereinigt` (Review Focus 3: vorher ein `sync_photo_local`-Eintrag für das Foto, danach `db.syncDao().fotoLokal(link) == null`), `prefsBleiben` (`dailyKcalGoal` noch 2000), `syncAktivNichtsGeaendert` (Review Focus 2: `sync_state.active = true` → `SyncAktiv`, Rezept noch da), `startdatenVersionWiederGesetzt` (`seedVersion` == `Startdaten.VERSION` nach dem Löschen).
- [ ] **Step 2:** Rot (`allesLoeschen` fehlt). **Step 3:** Umsetzen; `SicherungTest` und `DatenModul` auf den neuen Konstruktor. **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6e1: Alle Daten löschen"`.

### Task 3: `EinstellungenViewModel`

**Files:**
- Modify: `android/feature/einstellungen/build.gradle.kts` (`implementation(project(":core:data"))`, Roborazzi-Plugin und `outputDir` wie `:feature:woche`, Testabhängigkeiten `roborazzi`, `roborazzi.compose`, `roborazzi.junit.rule`, `testOptions.unitTests.isIncludeAndroidResources = true`)
- Create: `android/feature/einstellungen/src/main/kotlin/de/foody/feature/einstellungen/{EinstellungenViewModel,EinstellungenTexte}.kt`
- Test: `android/feature/einstellungen/src/test/kotlin/de/foody/feature/einstellungen/{EinstellungenTestHilfen,EinstellungenViewModelTest}.kt` (`ErsatzKonto` aus `VerbindenScreenTest` in die Testhilfen verschieben)

**Interfaces:**
- Consumes: Task 1/2; `KontoZugriff.zustand`, `ImportErgebnis`, `zuletztText`.
- Produces: `enum class Aktion { ERSTELLEN, WIEDERHERSTELLEN, LOESCHEN }`; `data class EinstellungenZustand(val darstellung: Darstellung = Darstellung.SYSTEM, val tagesziel: Int? = null, val konto: VerbindungsZustand? = null, val laeuft: Aktion? = null)` mit `val gesperrt: Boolean get() = konto?.verbunden == true`; `@HiltViewModel class EinstellungenViewModel @Inject constructor(@ApplicationContext kontext: Context, darstellung: DarstellungSpeicher, ziel: TageszielSpeicher, sicherung: Sicherung, konto: KontoZugriff, clock: Clock)` mit `val zustand: StateFlow<EinstellungenZustand>`, `val meldungen: Flow<String>` (Channel), `fun darstellungSetzen(d)`, `fun tageszielEingabe(text: String): Boolean` (leer → `null` speichern, `true`; Ziffern nach Entfernen von „.“ und Leerzeichen im Bereich → speichern, `true`; sonst `false`, nichts speichern), `fun dateiname(): String` (`foody-sicherung-<ISO-Datum aus clock>.zip`), `fun sicherungErstellen(uri: Uri)`, `fun wiederherstellen(uri: Uri)`, `fun allesLoeschen()` – alle drei im `viewModelScope`, ignoriert solange `laeuft != null`, setzen/leeren `laeuft`, Ergebnis als Meldung. In `EinstellungenTexte.kt`: `fun importMeldung(e: ImportErgebnis): String` („12 Rezepte, 230 Zutaten, 5 Fotos wiederhergestellt“ + „ · Vorrat übersprungen: 2“ wenn > 0; `SyncAktiv` → „Zuerst unter Konto die Verbindung trennen.“; `Fehler` → Grund).

- [ ] **Step 1: Tests** `EinstellungenViewModelTest` (Room + echte `Sicherung`, `ErsatzKonto`, feste `Clock` 28.10.2026): `darstellungSetzen`, `tageszielEingabe` (Review Focus 1: „2.000“ → 2000, „ 2000 “ → 2000, „“ → `null`, „499“ → `false` und Ziel unverändert, „abc“ → `false`), `dateinameAusUhr` (Review Focus 5: `foody-sicherung-2026-10-28.zip`), `erstellenMeldet` (Uri auf Datei im Cache → „Sicherung gespeichert“, Datei ist ZIP), `wiederherstellenV1` (Review Focus 4: v1-JSON mit 2 Vorratseinträgen → Meldung endet auf „Vorrat übersprungen: 2“), `loeschenMeldet` („Alle Daten gelöscht“), `gesperrtBeiSync` (`ErsatzKonto` verbunden → `zustand.gesperrt`, `allesLoeschen()` → „Zuerst unter Konto die Verbindung trennen.“, Daten da), `zweiteAktionIgnoriert` (während `laeuft` = `LOESCHEN` ein `sicherungErstellen` → keine zweite Meldung). `importMeldung`-Test: alle drei Fälle.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:einstellungen:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6e1: Einstellungen-ViewModel"`.

### Task 4: Einstellungsseite

**Files:**
- Create: `android/feature/einstellungen/src/main/kotlin/de/foody/feature/einstellungen/{EinstellungenScreen,EinstellungenAbschnitte}.kt`
- Test: `android/feature/einstellungen/src/test/kotlin/de/foody/feature/einstellungen/EinstellungenScreenTest.kt`

**Interfaces:**
- Produces: `@Composable fun EinstellungenRoute(onServer: () -> Unit, onZurueck: () -> Unit, vm: EinstellungenViewModel = hiltViewModel(), dateien: Dateiwahl = rememberDateiwahl(vm))`; `class Dateiwahl(val erstellen: (String) -> Unit, val oeffnen: () -> Unit)` – `rememberDateiwahl` nutzt `rememberLauncherForActivityResult(CreateDocument("application/zip"))` → `vm.sicherungErstellen`, `OpenDocument()` mit `arrayOf("application/zip", "application/json", "application/octet-stream")` → Rückfrage → `vm.wiederherstellen`; Tests geben eine eigene `Dateiwahl` mit festen Uris. Kopf wie `VerbindenInhalt` (Zurück, Augenbraue „Einstellungen“, Titel „Einstellungen“), Inhalt max. 720 dp, Abschnitte als `GlasFlaeche` mit Augenbraue; Rückfragen als `AlertDialog`; Snackbar aus `vm.meldungen`.

- [ ] **Step 1: Tests** `EinstellungenScreenTest` (`h1600dp`): `abschnitteSichtbar` (Augenbrauen in Großschrift: „KONTO“, „DARSTELLUNG“, „TAGESZIEL“, „SICHERUNG“, „DATEN“, „HINWEISE“), `kontoNichtVerbunden` („Nicht verbunden“, Antippen → `onServer`), `kontoVerbunden` („Verbunden mit http://10.0.2.2:8081“), `chipsSetzenDarstellung` („Dunkel“ → `DarstellungSpeicher` liefert `DUNKEL`), `tageszielFehlerAmFeld` („99“ → „500 bis 10.000 kcal“ sichtbar), `wiederherstellenMitRueckfrage` („Wiederherstellen“ → „Alle Daten ersetzen?“ → bestätigen → Meldung), `loeschenMitRueckfrage` (Rückfragetext wörtlich, bestätigen → „Alle Daten gelöscht“), `gesperrtBeiSync` („Wiederherstellen“ und „Alle Daten löschen“ `assertIsNotEnabled`, Hinweis „Zuerst unter Konto die Verbindung trennen.“ sichtbar, „Sicherung erstellen“ aktiv), `schrift200OhneAbschneiden`, `ueberstehtDrehen` (Tagesziel-Eingabe bleibt).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6e1: Einstellungsseite"`.

### Task 5: Navigation und Darstellung

**Files:**
- Modify: `android/app3/src/main/kotlin/de/foody/app3/navigation/{Ziele,FoodyNavHost}.kt` (`@Serializable data object ServerVerbinden`; `Bildschirme.einstellungen` echt = `EinstellungenRoute(onServer = { nav.navigate(ServerVerbinden) }, onZurueck = { nav.popBackStack() })`; neues `Bildschirme.serverVerbinden` = `VerbindenRoute(onZurueck = { nav.popBackStack() })`, Platzhalter „Server verbinden“), `android/app3/src/main/kotlin/de/foody/app3/MainActivity.kt` (`@Inject lateinit var darstellung: DarstellungSpeicher`; `FoodyTheme(dunkel = when (d) { SYSTEM -> isSystemInDarkTheme(); HELL -> false; DUNKEL -> true })`), `android/app3/src/main/kotlin/de/foody/app3/Darstellung.kt` (neu: `@Composable fun istDunkel(d: Darstellung): Boolean`)
- Test: `android/app3/src/test/kotlin/de/foody/app3/navigation/NavigationTest.kt`, `android/app3/src/test/kotlin/de/foody/app3/DarstellungTest.kt`

- [ ] **Step 1: Tests** `NavigationTest`: `einstellungenZumServer` (`nav.navigate(Einstellungen)` → Platzhalter „Einstellungen“; `nav.navigate(ServerVerbinden)` → „Server verbinden“; Zurück → „Einstellungen“). `DarstellungTest`: `istDunkel` für `HELL` → false, `DUNKEL` → true, `SYSTEM` folgt `isSystemInDarkTheme()` (Robolectric `night`-Qualifier).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :app3:testDebugUnitTest :app3:assembleDebug` grün. **Step 5:** Commit `"V3 Phase 6e1: Navigation und Darstellung"`.

### Task 6: Screenshots, Sichtprüfung, Abschluss

**Files:**
- Create: `android/feature/einstellungen/src/test/kotlin/de/foody/feature/einstellungen/EinstellungenScreenshotTest.kt`, Referenzbilder in `src/test/screenshots/`
- Modify: `.github/workflows/ci.yml` (`:feature:einstellungen:verifyRoborazziDebug`), `docs/architecture.md` (Abschnitt „Einstellungen (Phase 6e1)“), `ToDoS/v3-plan.md` (Einstellungen: 6e1 erledigt, 6e2 offen), `ToDoS/v3-abnahme.md` (Sicherung/Löschung, Darstellung, Tagesziel)

- [ ] **Step 1:** `EinstellungenScreenshotTest`: Seite verbunden und nicht verbunden – hell/dunkel × Handy `w411dp-h1600dp` / Tablet `w1280dp-h1200dp` (Tablet ohne Eingabe); Rückfrage „Alle Daten löschen“ als ganzer Bildschirm (`captureScreenRoboImage`, eigener Test).
- [ ] **Step 2:** `recordRoborazziDebug`, Bilder ansehen, Befunde beheben; `verifyRoborazziDebug` grün, Gegenprobe rot.
- [ ] **Step 3:** Sichtprüfung auf `Foody_Test` (Port 5556, AVD-Name prüfen) gegen den Test-Server: anmelden, Darstellung Dunkel/Hell/System, Tagesziel 2000 → Heute zeigt den Balken, Sicherung erstellen (Datei im Download-Ordner), Wiederherstellen/Löschen gesperrt; trennen, alle Daten löschen (Rezepte weg, Zutatenkatalog da), aus der Sicherung wiederherstellen (Rezepte zurück), wieder verbinden mit „Zusammenführen“ und synchronisieren (keine doppelten Rezepte). „Erneut anmelden“ nach v1-Update nur, wenn ein v1-Stand auf dem Gerät ist – sonst als nicht belegt notieren. Screenshots an den Nutzer; danach Tagesziel und Darstellung zurück, App deinstallieren, Emulator und Tunnel beenden.
- [ ] **Step 4:** Zeilencheck; `./gradlew check :app3:assembleDebug` und alle `verifyRoborazziDebug` (katalog, heute, rezepte, kochen, woche, einkauf, einstellungen) grün.
- [ ] **Step 5:** Doku; Commit `"V3 Phase 6e1: Doku und Abschluss"`; Gesamtreview; PR gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 (3 braucht 1 und 2; 4 braucht 3; 5 braucht 1 und 4). Ein Branch (`claude/v3-phase6e1-einstellungen`), ein PR am Ende.
