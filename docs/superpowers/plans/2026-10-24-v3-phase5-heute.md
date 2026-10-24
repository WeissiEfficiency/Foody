# V3 Phase 5: Navigation, „Heute“ und Tagebuch – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App-Hülle `:app3` („Foody Debug“) mit vier Tabs, Startseite „Heute“, vollständigem Tagebuch (ohne Scan) und schlichtem „Server verbinden“, lauffähig gegen den Test-Server.

**Architecture:** `:app3` setzt Hilt, Navigation (typisierte Routen, Leiste/Rail), Sync-Verdrahtung und Startdaten zusammen. `:feature:heute` und `:feature:einstellungen` bauen ihre Bildschirme nur aus `:core:designsystem` und lesen/schreiben über Repositories in `:core:data`; Rechenarbeit bleibt in `:domain`. Rezepte, Woche, Einkauf, Rezept-Detail und Kochmodus sind Platzhalter-Ziele mit Deep Links.

**Tech Stack:** Kotlin 2.4.20, Compose-BOM 2026.09.00, Navigation Compose 2.10.2 (typisierte Routen, kotlinx.serialization), Hilt 2.60.1 + androidx.hilt, WorkManager, Room 2.8.5, Coil 3.6.3, Robolectric 4.16.1, Roborazzi 1.76.0.

**Spec:** [`docs/superpowers/specs/2026-10-24-v3-phase5-heute-design.md`](../specs/2026-10-24-v3-phase5-heute-design.md)

## Global Constraints

- v1 unverändert: `android/app/**`, `:sync-protocol`; Server-Code unverändert. v1-Code nur lesen (Vorlage).
- `:app3` = `android/app3`, Paket `de.foody.app3`, `applicationId = "de.foody.app"`, Debug `applicationIdSuffix = ".debug"`, App-Name „Foody Debug“; **Release-Variante von `:app3` abgeschaltet** (`androidComponents { beforeVariants(selector().withBuildType("release")) { it.enable = false } }`).
- `:feature:einstellungen` = `android/feature/einstellungen`, Paket `de.foody.feature.einstellungen`; `:feature:heute` vorhanden (Paket `de.foody.feature.heute`). Features hängen nicht voneinander ab, nur von `:core:*` und `:domain`.
- UI nur aus `:core:designsystem` (`FoodyTheme`, `FoodyHintergrund`, `GlasFlaeche`, Komponenten); keine eigenen Farben/Schriften.
- Kein Schema-Wechsel in `:core:data` (Datenbank bleibt Version 10, Schema-Datei unverändert).
- Bildschirme höchstens 300 Zeilen je Datei; je Bildschirm ein ViewModel (Hilt, `hiltViewModel()` aus `androidx.hilt:hilt-lifecycle-viewmodel-compose`).
- Zeitfenster „nächste Mahlzeit“: Frühstück bis 10:00, Mittagessen bis 14:30, Snack bis 17:00, Abendessen und unbekannte Slots bis 23:59 (Spec Abschnitt 2).
- Deep Links: `foody://rezept/{id}`, `foody://einkauf/{listeId}`, `foody://kochen/{rezeptId}?portionen={n}`, `foody://tagebuch/{datum}` (ISO-Datum).
- Sync-Kennungen → Klartext wie in Spec Abschnitt 2 (Status) und 4 (Anmeldung); nie Token/Passwort in Logs oder Meldungen.
- Deutsch in Texten, Bezeichnern, Kommentaren; `allWarningsAsErrors` bleibt an; Robolectric-UI-Tests mit `@GraphicsMode(NATIVE)` und `junit4.v2.createComposeRule`.
- PR gegen `main` mit Auto-Merge; „Server aktualisieren? Nein“.

## Review Focus

1. **Mitternacht und Zeitzone** – „nächste Mahlzeit“ um 23:59/00:00 und bei Planeinträgen von gestern: kein Absturz, richtige Karte („· MORGEN“ nur für morgen) (Test in Task 5).
2. **Rezept gelöscht oder archiviert, Planeintrag/Tagebucheintrag verweist noch darauf** – „Heute“ zeigt den Planeintrag nicht als Karte ohne Titel, das Tagebuch behält seinen festgehaltenen Namen und Werte (Tests in Task 3 und 6).
3. **Leere und ungültige Eingaben im Eintragen-Blatt** – Menge 0/leer, Portionen 0, kcal fehlt bei „Frei“: Speichern gesperrt, kein Eintrag mit 0-Werten (Test in Task 6).
4. **Deep Link mit unbekannter ID oder kaputtem Datum** (`foody://tagebuch/2026-13-40`) – kein Absturz, Platzhalter bzw. heutiger Tag (Test in Task 4).
5. **Verbinden ohne Netz / Server antwortet nicht** – Klartext „offline“, Felder bleiben ausgefüllt, kein hängender Ladezustand (Test in Task 7).

---

### Task 1: App-Modul `:app3` und Hilt-Zusammenbau

**Files:**
- Modify: `settings.gradle.kts` (`:app3` → `android/app3`, `:feature:einstellungen` → `android/feature/einstellungen`), `.github/workflows/ci.yml` (`:app3:assembleDebug` im Build-Schritt)
- Create: `android/app3/build.gradle.kts` (Plugins `foody.android.application`, `kotlin-compose`, `kotlin-serialization`, `ksp`, `hilt`; Abhängigkeiten `:core:designsystem`, `:core:data`, `:core:sync`, `:feature:heute`, `:feature:einstellungen`, `:domain`, Compose, activity-compose, navigation-compose, hilt, androidx-hilt-work + compiler, work-runtime, lifecycle-process; Test: robolectric, hilt-android-testing, compose-ui-test), `android/app3/src/main/AndroidManifest.xml` (Application `.FoodyApp`, Activity `.MainActivity`, Label „Foody Debug“ über `res/values/strings.xml`, kein Standard-WorkManager-Initializer), `android/app3/src/main/kotlin/de/foody/app3/{FoodyApp,MainActivity,AppModul}.kt`, `android/feature/einstellungen/build.gradle.kts` (Plugin `foody.android.feature` + `ksp` + `hilt`)
- Modify: `android/feature/heute/build.gradle.kts` (+ `ksp`, `hilt`, Abhängigkeiten `:core:designsystem`, `:core:data`, `:core:sync`, `:domain`, androidx-hilt-lifecycle-viewmodel-compose)
- Test: `android/app3/src/test/kotlin/de/foody/app3/HiltGraphTest.kt`

**Interfaces:**
- Produces: `@HiltAndroidApp class FoodyApp : Application(), Configuration.Provider` (WorkManager mit `HiltWorkerFactory`; `onCreate`: `SyncAusloeser.beimStart()`, Startdaten über `StartdatenLader` aus Task 2 – bis Task 2 fertig ist, ohne Startdaten; `ProcessLifecycleOwner` → `SyncAusloeser.appImVordergrund()` bei `ON_START`); `AppModul` stellt `SyncKonfiguration(lokalesHttpErlaubt = BuildConfig.DEBUG)` bereit; `@AndroidEntryPoint class MainActivity` zeigt `FoodyTheme { FoodyHintergrund { Text("Foody") } }` (Navigation folgt in Task 4).

- [ ] **Step 1: Test** `HiltGraphTest` (Robolectric, `@HiltAndroidTest`, `HiltTestApplication`, `@Config(application = HiltTestApplication::class)`): `graphLiefertSyncKontoUndAusloeser` (`@Inject lateinit var konto: SyncKonto`, `ausloeser: SyncAusloeser`, `konfiguration: SyncKonfiguration` → nach `hiltRule.inject()` initialisiert, `konfiguration.lokalesHttpErlaubt == true` im Debug).
- [ ] **Step 2:** Rot: `./gradlew :app3:testDebugUnitTest` → Fehler (Modul/Klassen fehlen).
- [ ] **Step 3:** Umsetzen wie oben.
- [ ] **Step 4:** `./gradlew :app3:testDebugUnitTest :app3:assembleDebug check` → grün; `:app3:assembleRelease` existiert nicht (Task fehlt).
- [ ] **Step 5:** Commit `"V3 Phase 5: App-Modul app3 und Hilt"`.

### Task 2: Startdaten in `:core:data`

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/startdaten/{Startdaten,StartdatenLader}.kt`
- Modify: `android/app3/.../FoodyApp.kt` (Startdaten beim Start)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/startdaten/StartdatenLaderTest.kt`

**Interfaces:**
- Produces: `object Startdaten { const val VERSION = 8; const val QUELLE = "Näherungswert (Startdaten)"; fun zutaten(jetzt: Long): List<ZutatEntity> }` – Zeilen 1:1 aus v1 `android/app/src/main/java/de/foody/app/data/db/SeedData.kt` (IDs `seed-*`, Werte, Dichte, Stückgewicht, Quelle); `class StartdatenLader @Inject constructor(db: FoodyDatenbank, @ApplicationContext context: Context) { suspend fun ladenWennNoetig(): Int }` – liest Prefs `foody`, `seedVersion` (alt `seeded` = 1); bei `< VERSION` in einer Transaktion: fehlende anlegen, unveränderte Starteinträge (gleiche ID, Quelle = `QUELLE` oder Zeilenquelle) aktualisieren, gleichnamige ohne Nährwerte ergänzen (wie v1 `fillFrom`); danach `seedVersion = VERSION`; liefert Anzahl geänderter Zeilen. Kein `harmonizeNames` (Ruling in Spec-Sinn: frische V3 braucht es nicht, v1-Umsteiger haben Version 8).

- [ ] **Step 1: Tests** (Room im Speicher, Robolectric): `leereDatenbankBekommtStartzutaten` (Anzahl = `Startdaten.zutaten(0).size`, Prefs danach 8), `zweiterAufrufTutNichts` (liefert 0), `vEinsMitVersionAchtTutNichts` (Prefs `seedVersion = 8` vorab → 0), `altesSeededGiltAlsEins` (`seeded = true` → läuft, legt fehlende an), `eigeneGleichnamigeZutatWirdNurErgaenzt` (vorhandene „Butter“ ohne Nährwerte, andere ID → behält ID, bekommt Nährwerte), `geaenderteStartzutatBleibt` (gleiche ID, Quelle „eigene“ → unverändert), `beiAktivemSyncLandenStartdatenInDerOutbox` (Sync aktiv → Outbox enthält die neuen IDs).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen; `FoodyApp` ruft `ladenWennNoetig()` in einem Application-Scope (`Dispatchers.IO`) auf. **Step 4:** `./gradlew :core:data:testDebugUnitTest :app3:testDebugUnitTest` grün; `SchemaVergleichTest` grün.
- [ ] **Step 5:** Commit `"V3 Phase 5: Startdaten"`.

### Task 3: Lesezugriffe und Repositories in `:core:data`

**Files:**
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/db/Daos.kt` (neue Flow-Abfragen), `DatenModul.kt` (Bindungen)
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/heute/{HeuteRepository,DomainAbbildung}.kt`, `.../tagebuch/TagebuchRepository.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/heute/HeuteRepositoryTest.kt`, `.../tagebuch/TagebuchRepositoryTest.kt`

**Interfaces:**
- Produces (DAO): `PlanEintragDao.zwischen(von: LocalDate, bis: LocalDate): Flow<List<PlanEintragEntity>>`; `TagebuchDao.amTag(datum: LocalDate): Flow<List<TagebuchEintragEntity>>`; `RezeptDao.aktive(): Flow<List<RezeptEntity>>` (`archivedAt IS NULL`); `EinkaufsartikelDao.offeneDerJuengstenListe(): Flow<List<EinkaufsartikelEntity>>` (Liste mit größtem `updatedAt`, `checked = 0`, Reihenfolge wie v1 Einkauf: `sortOrder`, `id`); `ZutatDao.alleFlow(): Flow<List<ZutatEntity>>`.
- Produces (Abbildung): `fun RezeptEntity.alsRecipe(zeilen: List<RezeptZutatEntity>, schritte: List<ArbeitsschrittEntity>): Recipe`, `fun ZutatEntity.alsIngredient(): Ingredient` – feldgenau wie v1 (`android/app/.../data/repo` Mapping) für `:domain`-Berechnungen.
- Produces: `data class GeplanteMahlzeit(val planEintragId: String, val datum: LocalDate, val slot: String, val rezeptId: String, val titel: String, val portionen: Int, val kcalJePortion: Int?, val zeitMinuten: Int?, val bild: String?, val gekocht: Boolean)`; `class HeuteRepository @Inject constructor(db)` mit `fun planHeuteUndMorgen(heute: LocalDate): Flow<List<GeplanteMahlzeit>>` (nur Einträge mit existierendem, nicht archiviertem Rezept), `fun rezeptVorschlaege(): Flow<List<RezeptKurz>>` (`data class RezeptKurz(val id: String, val titel: String, val kcalJePortion: Int?, val zeitMinuten: Int?, val bild: String?)`), `fun offenerEinkauf(): Flow<OffenerEinkauf?>` (`data class OffenerEinkauf(val listeId: String, val anzahl: Int, val artikel: List<ArtikelKurz>)`, `ArtikelKurz(name, menge)`; `null` ohne offene Artikel). kcal je Portion über die Phase-1-Funktion in `:domain` (dieselbe wie die v1-Rezeptanzeige).
- Produces: `class TagebuchRepository @Inject constructor(db, uhr: () -> Long)` mit `fun amTag(datum): Flow<List<TagebuchEintragEntity>>`, `suspend fun ausRezept(datum, mahlzeit: Mahlzeit, rezeptId, portionen: BigDecimal): String`, `suspend fun ausZutat(datum, mahlzeit, zutatId, menge: BigDecimal, einheit: MeasureUnit): String`, `suspend fun ausPlan(planEintragId): String`, `suspend fun frei(datum, mahlzeit, name, kcal: Int, eiweiss: BigDecimal?, kohlenhydrate: BigDecimal?, fett: BigDecimal?): String`, `suspend fun aendern(id, menge: BigDecimal?, mahlzeit: Mahlzeit, datum: LocalDate, freiWerte: Naehrwerte?)`, `suspend fun loeschen(id): TagebuchEintragEntity?`, `suspend fun wiederherstellen(e: TagebuchEintragEntity)`. Rückgabe = neue ID (UUID). Werte über `Tagebuch.*` aus `:domain`.

- [ ] **Step 1: Tests** `HeuteRepositoryTest`: `planHeuteUndMorgenNurMitRezept` (Eintrag mit gelöschtem Rezept fehlt, archiviertes fehlt), `kcalJePortionWieDomain`, `offenerEinkaufJuengsteListe` (zwei Listen, nur offene der jüngeren, Anzahl stimmt), `keinOffenerEinkaufGibtNull`. `TagebuchRepositoryTest`: `ausRezeptHaeltWerteFest` (danach Rezeptzutat ändern → Eintrag unverändert), `ausZutatOhneNaehrwerteUnvollstaendig`, `ausPlanUebernimmtPortionenUndVerweis` (`planEintragId` gesetzt), `freiMitKcal`, `aendernSkaliert` (Portionen 1 → 2 verdoppelt kcal), `loeschenUndWiederherstellen` (gleiche ID, gleiche Werte), `beiAktivemSyncOutbox` (Typ `tagebuch_eintrag`), `geloeschtesRezeptBehaeltEintrag` (Review Focus 2: Rezept löschen → Eintrag mit Name und Werten bleibt).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün, Schema-Datei unverändert. **Step 5:** Commit `"V3 Phase 5: Repositories für Heute und Tagebuch"`.

### Task 4: Navigation, Leiste/Rail, Platzhalter, Deep Links

**Files:**
- Create: `android/app3/src/main/kotlin/de/foody/app3/navigation/{Ziele,FoodyNavHost,Hauptgeruest,Platzhalter}.kt`
- Modify: `MainActivity.kt` (zeigt `Hauptgeruest()`), `AndroidManifest.xml` (Intent-Filter `foody://` für `MainActivity`, `launchMode="singleTask"`)
- Test: `android/app3/src/test/kotlin/de/foody/app3/navigation/{NavigationTest,DeepLinkTest}.kt`

**Interfaces:**
- Produces: `@Serializable` Routen `Heute`, `Rezepte`, `Woche`, `Einkauf`, `Einstellungen`, `RezeptDetail(val id: String)`, `Kochen(val rezeptId: String, val portionen: Int? = null)`, `EinkaufListe(val listeId: String)`, `TagebuchTag(val datum: String)` (ISO); `@Composable fun Hauptgeruest(navController: NavHostController = rememberNavController())` – vier Tabs (`NavZiel` „Heute“, „Rezepte“, „Woche“, „Einkauf“), ab 840 dp Breite `FoodyNavigationsRail` links, sonst `FoodyNavigationsleiste` unten; `fun tagebuchDatum(text: String, heute: LocalDate): LocalDate` (ungültig → heute). Platzhalter zeigen `LeererZustandFlaeche` „Kommt in Phase 6“ und darunter die Argumente als Text (z. B. „Rezept r1 · 3 Portionen“). Heute-Ziel und Einstellungen-Ziel hängen in Task 5/7 die echten Bildschirme ein; bis dahin Platzhalter.

- [ ] **Step 1: Tests** `NavigationTest`: `handyZeigtLeiste` (`@Config(qualifiers = "w411dp-h891dp")`, Tab „Woche“ antippen → Platzhalter „Woche“), `tabletZeigtRail` (`w1280dp-h800dp`), `zurueckVomTabFuehrtZuHeute`. `DeepLinkTest` (Intent mit `Uri` in die Activity bzw. `navController.handleDeepLink`): `rezeptLink` („Rezept r1“), `kochenLinkMitPortionen` („Rezept r1 · 3 Portionen“), `kochenLinkOhnePortionen`, `einkaufLink`, `tagebuchLinkKaputtesDatumZeigtHeute` (Review Focus 4), `unbekannteRezeptIdKeinAbsturz`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :app3:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 5: Navigation und Deep Links"`.

### Task 5: Startseite „Heute“

**Files:**
- Create: `android/feature/heute/src/main/kotlin/de/foody/feature/heute/{HeuteLogik,SyncAnzeigeAbbildung,HeuteViewModel,HeuteScreen,HeuteBereiche}.kt` (ersetzt das Gerüst `HeuteScreen.kt`)
- Modify: `android/app3/.../navigation/FoodyNavHost.kt` (Ziel `Heute` → `HeuteRoute`), `android/feature/heute/src/test/.../HeuteScreenTest.kt` (ersetzen)
- Test: `android/feature/heute/src/test/kotlin/de/foody/feature/heute/{HeuteLogikTest,SyncAnzeigeAbbildungTest,HeuteScreenTest}.kt`

**Interfaces:**
- Consumes: `HeuteRepository`, `TagebuchRepository` (Task 3), `SyncSpeicher.zustand()`, Komponenten aus `:core:designsystem`.
- Produces: `fun naechsteMahlzeit(plan: List<GeplanteMahlzeit>, jetzt: LocalDateTime): NaechsteMahlzeit?` (`data class NaechsteMahlzeit(val mahlzeit: GeplanteMahlzeit, val morgen: Boolean)`); `fun fensterEnde(slot: String): LocalTime` (Spec-Zeitfenster, Slot über `Mahlzeit.ausText`); `fun mahlzeitNachUhrzeit(zeit: LocalTime): Mahlzeit`; `fun syncAnzeige(aktiv: Boolean, laeuft: Boolean, letzterFehler: String?): SyncAnzeige?` (`null` wenn nicht aktiv; Texte wie Spec); `@Composable fun HeuteRoute(onKochen: (String, Int) -> Unit, onRezept: (String) -> Unit, onEinkauf: (String) -> Unit, onAlleEintraege: () -> Unit, onEinstellungen: () -> Unit, onEintragen: (Mahlzeit?) -> Unit)`; `HeuteViewModel` mit `val zustand: StateFlow<HeuteZustand>` (Datum, nächste Mahlzeit, Inspiration, Tagebuch des Tages inkl. Bilanz und Vorschläge, offener Einkauf, Sync-Anzeige, `datenbankLeer`).

- [ ] **Step 1: Tests** `HeuteLogikTest`: `vorZehnFruehstueck`, `genauZehnIstFruehstueckVorbei` (10:00 → nächstes Fenster), `gekochtWirdUebersprungen`, `nachAbendessenMorgen` (23:59 → erster von morgen, `morgen = true`), `mitternachtZeigtHeute` (00:00 → heutiges Frühstück), `gestrigerEintragZaehltNicht`, `unbekannterSlotBisMitternacht`, `nichtsGeplantNull`, `mahlzeitNachUhrzeitGrenzen`. `SyncAnzeigeAbbildungTest`: je Kennung der Text aus der Spec, `nichtAktivNull`. `HeuteScreenTest` (Room im Speicher + echtes ViewModel, Uhr fest): `geplantZeigtKarteUndKochen` (Klick „Kochen“ → `onKochen(r1, 2)`), `nichtsGeplantZeigtInspiration`, `geplantKeineInspiration`, `leererEinkaufOhneBereich`, `offenerEinkaufZeigtAnzahlUndKacheln` („3 offen“), `leereDatenbankZeigtVerbinden`, `vorschlagGegessenTraegtEin` (Planeintrag → Tipp → Tagebuch enthält Eintrag), `schrift200OhneAbschneiden`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen (Bereiche als eigene Composables in `HeuteBereiche.kt`; `HeuteScreen.kt` ≤ 300 Zeilen). **Step 4:** `./gradlew :feature:heute:testDebugUnitTest :app3:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 5: Startseite Heute"`.

### Task 6: Tagebuch – Eintragen, Ändern, „Alle Einträge“

**Files:**
- Create: `android/feature/heute/src/main/kotlin/de/foody/feature/heute/tagebuch/{EintragenViewModel,EintragenBlatt,EintragBearbeiten,TagebuchTagViewModel,TagebuchTagScreen,EintragZeile}.kt`
- Modify: `HeuteRoute`/`FoodyNavHost` (Blatt aus „Heute“ und aus `TagebuchTag`; Ziel `TagebuchTag` → `TagebuchTagScreen`)
- Test: `android/feature/heute/src/test/kotlin/de/foody/feature/heute/tagebuch/{EintragenViewModelTest,TagebuchTagScreenTest}.kt`

**Interfaces:**
- Consumes: `TagebuchRepository`, `HeuteRepository.rezeptVorschlaege()`, `ZutatDao.alleFlow()`, `PlanEintragDao.zwischen`.
- Produces: `class EintragenViewModel` mit Reitern `enum class EintragenWeg { REZEPT, ZUTAT, PLAN, FREI }`, `val speichernMoeglich: StateFlow<Boolean>`, `suspend fun speichern(): String?`; `@Composable fun TagebuchTagRoute(datum: LocalDate, onDatum: (LocalDate) -> Unit)`.
- Regeln: Portionen > 0 (Schritt 0,5), Menge > 0, „Frei“ braucht Name und kcal ≥ 0; sonst `speichernMoeglich = false` (Review Focus 3). Suche Rezept/Zutat: Teilstring, Groß/klein egal, höchstens 30 Treffer.

- [ ] **Step 1: Tests** `EintragenViewModelTest`: `rezeptMitPortionen` (gespeicherter Eintrag hat Werte × Portionen), `zutatMitMengeUndEinheit`, `ausPlan`, `freiOhneKcalGesperrt`, `mengeNullGesperrt`, `portionenNullGesperrt`, `mahlzeitVorbelegt` (Blatt mit `Mahlzeit.SNACK` geöffnet → Snack). `TagebuchTagScreenTest`: `blaetternVorUndZurueck`, `bilanzUndBalken` (Kopf „… / 2.000 kcal“ mit Ziel 2000 in Prefs), `eintragAendernSkaliert`, `loeschenMitRueckgaengig` (Snackbar „Eintrag gelöscht“, „Rückgängig“ → Eintrag wieder da), `geloeschtesRezeptEintragBleibtSichtbar` (Review Focus 2), `schrift200OhneAbschneiden`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen (Dateien je ≤ 300 Zeilen). **Step 4:** `./gradlew :feature:heute:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 5: Tagebuch"`.

### Task 7: „Server verbinden“

**Files:**
- Create: `android/feature/einstellungen/src/main/kotlin/de/foody/feature/einstellungen/{KontoZugriff,VerbindenViewModel,VerbindenScreen,FehlerTexte}.kt`
- Modify: `FoodyNavHost` (Ziel `Einstellungen` → `VerbindenRoute`), `AppModul` (`KontoZugriff` an `SyncKonto`/`SyncSpeicher`/`SyncAusloeser` binden)
- Test: `android/feature/einstellungen/src/test/kotlin/de/foody/feature/einstellungen/{FehlerTexteTest,VerbindenScreenTest}.kt`

**Interfaces:**
- Produces: `interface KontoZugriff { val zustand: Flow<VerbindungsZustand>; suspend fun anmelden(adresse: String, benutzer: String, passwort: String): Ergebnis<Unit>; suspend fun hatLokaleDaten(): Boolean; suspend fun aktivieren(modus: ErsterSync): Ergebnis<Unit>; suspend fun trennen(); fun jetztSynchronisieren() }` (`data class VerbindungsZustand(val verbunden: Boolean, val adresse: String?, val letzterSync: Long?, val letzterFehler: String?)`; `Ergebnis`/`ErsterSync` aus `:core:sync`); `fun anmeldeFehlerText(kennung: String): String` und `fun syncFehlerText(kennung: String): String` (Texte aus Spec Abschnitt 4: `falsche_zugangsdaten` „Benutzername oder Passwort falsch.“, `ungueltige_adresse` „Die Adresse ist ungültig (https nötig).“, `zu_viele_versuche` „Zu viele Versuche – bitte kurz warten.“, `offline` „Keine Verbindung zum Server.“, `server_zu_alt` „Der Server ist zu alt für diese App.“, `app_zu_alt` „Diese App ist zu alt für den Server.“, `token_nicht_gespeichert` „Anmeldung konnte nicht gespeichert werden.“, sonst „Anmeldung fehlgeschlagen.“); `@Composable fun VerbindenRoute(onZurueck: () -> Unit)`.

- [ ] **Step 1: Tests** `FehlerTexteTest`: jede Kennung → Text, unbekannt → Rückfall. `VerbindenScreenTest` (Ersatz-`KontoZugriff`): `anmeldenOhneLokaleDatenAktiviertDirekt`, `mitLokalenDatenFragtZusammenfuehrenOderErsetzen` (beide Knöpfe, Erklärung „Zutaten bleiben“), `falschesPasswortZeigtText`, `offlineZeigtTextFelderBleiben` (Review Focus 5: Adresse/Benutzer weiter ausgefüllt, kein Ladeindikator), `verbundenZeigtStatusUndJetzt`, `trennenFragtNach` (Rückfrage, erst „Trennen“ ruft `trennen()`), `passwortNieImText` (Passwort taucht in keinem Knoten außer dem Feld auf).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:einstellungen:testDebugUnitTest :app3:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 5: Server verbinden"`.

### Task 8: Fotos, Screenshots, Sichtprüfung, Abschluss

**Files:**
- Modify: `android/feature/heute/...` (Bild-Slot der Karten mit Coil `AsyncImage` für `file:`-Links, sonst Platzhalter), `android/feature/heute/build.gradle.kts` (coil-compose, Roborazzi-Plugin + `outputDir` `src/test/screenshots`), `.github/workflows/ci.yml` (`:feature:heute:verifyRoborazziDebug`), `docs/architecture.md` (Abschnitt „App V3: Hülle, Heute, Tagebuch“), `ToDoS/v3-plan.md` (Phase 5 abhaken mit Ergebnis), `ToDoS/v3-abnahme.md` (Heute/Tagebuch außer Scan; Sync beim Öffnen/nach Änderungen)
- Test: `android/feature/heute/src/test/kotlin/de/foody/feature/heute/HeuteScreenshotTest.kt`

- [ ] **Step 1: Test** `HeuteScreenshotTest`: „Heute“ (geplant, mit Tagebuch und Einkauf) und `TagebuchTag` je hell/dunkel × Handy `w411dp-h1600dp` / Tablet `w1280dp-h1200dp`, Animationen aus, Toleranz 0,1 %; erfundene Daten.
- [ ] **Step 2:** `recordRoborazziDebug`; Bilder ansehen (abgeschnitten? überlappend? Silbentrennung?) – Befunde per TDD beheben; dann `verifyRoborazziDebug` grün und Gegenprobe rot.
- [ ] **Step 3 (Controller):** „Foody Debug“ auf eigenem Emulator `Foody_Test` gegen den Test-Server (SSH-Tunnel, `http://10.0.2.2:8081`, Wegwerf-Passwort aus `.Certs`, nur über die App-Oberfläche): verbinden, Daten erscheinen, Tagebuch-Eintrag anlegen, Sync; Screenshots an den Nutzer. Danach App deinstallieren, Tunnel/Emulator beenden.
- [ ] **Step 4:** Zeilencheck: keine Datei in `android/feature/**/src/main` und `android/app3/src/main` über 300 Zeilen (`wc -l`). `./gradlew check :app3:assembleDebug :feature:heute:verifyRoborazziDebug :katalog:verifyRoborazziDebug` grün.
- [ ] **Step 5:** Doku; Commit `"V3 Phase 5: Doku und Abschluss"`; Gesamtreview; PR gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 (4 und 7 hängen nur an 1 und dürfen früher laufen; 5 und 6 brauchen 3). Ein Branch (`claude/v3-phase5-heute`), ein PR am Ende.
