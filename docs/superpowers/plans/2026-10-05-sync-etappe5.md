# Sync Etappe 5 (Einstellungen, Verbinden, Dokumentation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nutzer können in den Einstellungen ihren Server verbinden (anmelden oder mit Einladungscode registrieren, Haushalt anlegen/wählen, ersten Abgleich wählen), den Sync-Status sehen, sofort synchronisieren, Probleme und Geräte einsehen, andere einladen und die Verbindung trennen.

**Architecture:** `SyncAccountRepository` bündelt alle Konto-Aufrufe (über `KtorSyncApi`), speichert das Token (`TokenStore`), aktiviert/deaktiviert den lokalen Sync (`SyncLocalStore`) und steuert `SyncScheduler`. Zwei ViewModels: `SyncSettingsViewModel` (Karte „Synchronisierung“ in den Einstellungen) und `SyncSetupViewModel` (eigener Bildschirm „Server verbinden“ mit Schritten). Fehler werden in deutsche Meldungen übersetzt. Dokumentation: ADR 0006, `security.md`, `architecture.md`, README.

**Tech Stack:** Compose Material 3, Hilt, Navigation Compose (typisierte Routen wie bestehend), Robolectric/JVM-Tests (Repository gegen den echten Server über `TestSyncServer`, ViewModels mit Fakes).

**Spec:** `docs/superpowers/specs/2026-10-04-sync-server-design.md` §2.3, §3.2, §3.3, §6. Folge-Issues #59, #63 (URL-/TLS-Fehlermeldungen).

## Entscheidungen

- **URL-Regeln:** Release: nur `https://`. Debug-Build zusätzlich `http://10.0.2.2[:port]` und `http://localhost[:port]` (für Tests mit lokalem Server auf dem Entwicklungsrechner) – per `src/debug/res/xml/network_security_config.xml`, das Klartext nur für diese Hosts erlaubt. Abschließender `/` wird entfernt; Pfad erlaubt (Reverse-Proxy-Unterpfad).
- **Kein Passwortwechsel und keine Mitgliederverwaltung in der UI** (YAGNI; Server kann es, UI folgt bei Bedarf).
- **Erster Abgleich (Spec 3.3):** Haushalt neu angelegt → alle lokalen Daten hochladen. Bestehendem Haushalt beitreten **mit** lokalen Rezepten/Plan/Vorrat/Listen → Dialog „Zusammenführen“ (alles hochladen) oder „Dieses Gerät ersetzen“ (erst Sicherung anbieten, dann lokale Daten löschen, dann nur herunterladen). Ohne lokale Daten (nur Startzutaten) → direkt herunterladen, Startzutaten werden über ihre festen IDs zugeordnet.
- **„Jetzt synchronisieren“** ruft `SyncScheduler.requestSoon()` (gleicher Weg wie Hintergrund, Mutex schützt).
- **Trennen** = Gerät beim Server abmelden (best effort, Fehler ignorieren), Token löschen, `SyncLocalStore.deactivate()`, Planung beenden. Lokale Daten bleiben.

## Global Constraints

- Alle UI-Texte deutsch, in `strings.xml`; Stil der bestehenden Einstellungen (`SettingsCard`, `ScreenHeader`, runde Buttons).
- Fehlermeldungen (Strings): `invalid_credentials` → „Benutzername oder Passwort falsch.“; `throttled` → „Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.“; `invalid_invite` → „Einladungscode ungültig oder abgelaufen.“; `username_taken` → „Dieser Benutzername ist schon vergeben.“; `invalid_input` beim Registrieren → „Passwort mindestens 10 Zeichen, Benutzername 3–32 Zeichen (Buchstaben, Ziffern, . _ -).“; Protokoll zu alt/neu → „App und Server passen nicht zusammen – bitte {App|Server} aktualisieren.“; `Transient` mit TLS-Ursache (`SSLHandshakeException`/`CertPathValidatorException`) → „Zertifikat des Servers wird nicht vertraut.“; übrige Netzwerkfehler → „Server nicht erreichbar – Adresse sowie WLAN/VPN prüfen.“; ungültige URL → „Adresse muss mit https:// beginnen.“
- Statuszeile der Karte: aus `sync_state` – aus („Nicht verbunden“), verbunden („Verbunden mit {Haushalt} · zuletzt {relative Zeit}“), `lastError = "unauthorized"` → „Abgemeldet – bitte erneut verbinden“ mit Button „Erneut verbinden“ (öffnet den Setup-Bildschirm mit vorbelegter URL und vorhandenem Haushalt).
- Haushaltsname wird lokal mitgespeichert: neue Spalte? **Nein** – `sync_state` behält nur die ID; der Name wird beim Öffnen der Karte über `api.households()` geholt (Fehler → nur „Verbunden“ ohne Namen).
- Kein Token, kein Passwort in Logs, SavedStateHandle oder UI-State nach dem Absenden (Passwortfelder werden nach erfolgreichem Login geleert und nicht in SavedStateHandle gespeichert).
- Bildschirmzustand, der Prozessneustart überleben muss (eingegebene URL, Benutzername, gewählter Modus, Schritt), liegt im `SavedStateHandle` (wie `docs/architecture.md` verlangt) – **ohne** Passwörter.

## Review Focus

1. **Gerät ersetzen bei vorhandenen Daten**: ohne angebotene Sicherung keine Löschung; Abbruch des Sicherungsdialogs bricht das Ersetzen ab → Test in Task 3.
2. **Falsche Adresse / selbstsigniertes Zertifikat / http im Release**: klare Meldung, keine endlose Wiederholung im Hintergrund (Sync wird gar nicht erst aktiviert) → Test in Task 1.
3. **Token widerrufen** (auf anderem Gerät abgemeldet): Karte zeigt „Abgemeldet“, „Erneut verbinden“ führt mit vorhandenen Daten zurück, ohne Daten zu verlieren → Test in Task 2/3.
4. **Prozessneustart mitten im Verbinden**: URL und Benutzername bleiben, Passwort nicht → Test in Task 3.
5. **Trennen während Offline**: lokal trotzdem getrennt (Server-Abmeldung best effort) → Test in Task 1.

---

## Dateistruktur

```
app/src/main/java/de/foody/app/sync/SyncAccountRepository.kt, SyncErrors.kt (Fehler → String-Ressource), ServerUrl.kt (Prüfung)
app/src/main/java/de/foody/app/ui/settings/SyncSettingsViewModel.kt, SyncSettingsCard.kt (Composable), SettingsScreen.kt (Karte einbinden)
app/src/main/java/de/foody/app/ui/sync/SyncSetupScreen.kt, SyncSetupViewModel.kt; ui/FoodyRoot.kt (Route)
app/src/debug/res/xml/network_security_config.xml; app/src/main/res/values/strings.xml
docs/adr/0006-optional-self-hosted-sync.md, docs/adr/0001-offline-first.md (Verweis), docs/security.md, docs/architecture.md, README.md, server/README.md
Tests: app/src/test/.../sync/SyncAccountRepositoryTest.kt (Robolectric + echter Server), ServerUrlTest.kt, SyncErrorsTest.kt, ui/settings/SyncSettingsViewModelTest.kt, ui/sync/SyncSetupViewModelTest.kt
```

---

### Task 1: `SyncAccountRepository`, URL-Prüfung, Fehlermeldungen, Debug-Netzwerkregel

**Interfaces (Produces):**
- `object ServerUrl { fun normalize(input: String, allowLocalHttp: Boolean): String? }` (`allowLocalHttp = BuildConfig.DEBUG` beim Aufrufer).
- `object SyncErrors { @StringRes fun messageFor(e: Throwable, context: ErrorContext): Int }`, `enum class ErrorContext { LOGIN, REGISTER, GENERAL }`.
- `@Singleton class SyncAccountRepository @Inject constructor(db, tokenStore, localStore: SyncLocalStore, scheduler: SyncScheduler, httpClient: HttpClient /* singleton aus SyncEngineFactory oder eigenes Provides */)` mit
  `suspend fun login(url, username, password, deviceName): LoginResult` (Token speichern; `LoginResult(householdId: String?)`),
  `suspend fun register(url, code, username, password, deviceName): LoginResult`,
  `suspend fun households(url): List<HouseholdDto>`, `suspend fun createHousehold(url, name): HouseholdDto`, `suspend fun selectHousehold(url, id)`,
  `suspend fun hasLocalData(): Boolean` (Rezepte, Planpositionen, Vorrat oder Einkaufslisten vorhanden; Startzutaten zählen nicht),
  `suspend fun activate(url, householdId, mode: FirstSync)` mit `enum class FirstSync { UPLOAD_ALL, DOWNLOAD_ONLY }` → `localStore.activate(url, householdId, uploadExisting = mode == UPLOAD_ALL)`, dann `scheduler.schedulePeriodic()`, `startObservingOutbox(appScope)`, `requestSoon()`,
  `suspend fun createInvite(): InviteDto`, `suspend fun devices(): List<DeviceDto>`, `suspend fun revokeDevice(id)`, `suspend fun disconnect()` (Server-Abmeldung des eigenen Geräts best effort, `tokenStore.clear()`, `localStore.deactivate()`, `scheduler.cancelAll()`).
  Gerätename: `"${Build.MANUFACTURER} ${Build.MODEL}"` gekürzt auf 64.
- Debug-`network_security_config.xml`: Klartext nur für `10.0.2.2` und `localhost`.
- [ ] **Step 1: Failing tests:** `ServerUrlTest` (https ok; http nur mit allowLocalHttp für 10.0.2.2/localhost; `"https://x/"` → `"https://x"`; Müll → null); `SyncErrorsTest` (jede Zuordnung aus Global Constraints, TLS-Ursache in der Cause-Kette); `SyncAccountRepositoryTest` (Robolectric, echter Server per `TestSyncServer`): `loginStoresTokenAndReturnsHousehold`, `registerWithInviteJoinsHousehold`, `activateUploadAllQueuesLocalData`, `activateDownloadOnlyQueuesNothing`, `hasLocalDataIgnoresSeedIngredients`, `disconnectWorksOffline` (Server weg → lokal getrennt, Token weg; Review Focus 5), `wrongUrlNeverActivates` (Login scheitert → `sync_state.active` bleibt 0; Review Focus 2).
- [ ] **Step 2–4:** rot → implementieren → `:app:testDebugUnitTest :app:assembleRelease` grün.
- [ ] **Step 5: Commit** `git commit -m "Sync: Konto-Funktionen für die App"`

### Task 2: Karte „Synchronisierung“ in den Einstellungen

**Interfaces:** `@HiltViewModel class SyncSettingsViewModel(db, repo: SyncAccountRepository, scheduler)` mit `val state: StateFlow<SyncSettingsState>` (`connected`, `householdName`, `lastSyncAt`, `unauthorized`, `problemCount`, `busy`, `message: Int?`), Aktionen `syncNow()`, `loadDevices()`, `revokeDevice(id)`, `createInvite()` (Code im State für Dialog mit Kopieren/Teilen), `disconnect()`, `problems()`; `SyncSettingsCard` mit Status, Buttons „Server verbinden“/„Erneut verbinden“ (Navigation), „Jetzt synchronisieren“, „Einladen“, „Geräte“ (Dialog mit Liste + „Abmelden“), „Sync-Probleme (n)“ (Dialog mit Liste: Rezeptname/Typ + verständlicher Grund je Code `too_large`, `photo_unsyncable`, `invalid_payload`, `missing_reference`, `apply_failed`, `photo_mismatch`), „Trennen“ (Bestätigung).
- [ ] **Step 1: Failing tests** (`SyncSettingsViewModelTest`, Robolectric, In-Memory-DB, Fake-Repository): Status aus `sync_state` (aus/verbunden/abgemeldet; Review Focus 3), `syncNow` ruft `requestSoon`, Probleme werden mit Text aufgelöst, `disconnect` setzt Status auf „Nicht verbunden“, Einladung landet im State.
- [ ] **Step 2–4:** rot → implementieren (Karte zwischen „Sicherung“ und „Datenschutz“ einfügen; Datenschutztext ergänzen: „Ohne verbundenen Server bleiben alle Daten auf dem Gerät.“) → grün; App auf `emulator-5558` starten, Einstellungen öffnen, kein Absturz (Screenshot im Bericht beschreiben).
- [ ] **Step 5: Commit** `git commit -m "Sync: Karte Synchronisierung in den Einstellungen"`

### Task 3: Bildschirm „Server verbinden“

**Interfaces:** Route `SyncSetupRoute(reconnect: Boolean = false)` in `FoodyRoot`; `@HiltViewModel class SyncSetupViewModel(savedState, repo)` mit Schritten `URL → ACCOUNT (Anmelden | Mit Einladungscode registrieren) → HOUSEHOLD (wählen | neu anlegen; übersprungen, wenn Login einen Haushalt liefert) → FIRST_SYNC (nur wenn bestehendem Haushalt beigetreten und hasLocalData) → DONE`; Gerät ersetzen: Ereignis „Sicherung anbieten“ → UI startet den vorhandenen Export (SAF `CreateDocument`) → nach Erfolg `BackupRepository.deleteAll()` → `activate(DOWNLOAD_ONLY)`; Abbruch des Exports bricht das Ersetzen ab (nichts gelöscht). `reconnect = true`: URL vorbelegt, nach Login wird der bisherige Haushalt gewählt und ohne Rückfrage `activate(UPLOAD_ALL)` (Outbox bleibt; nichts geht verloren).
- [ ] **Step 1: Failing tests** (`SyncSetupViewModelTest`, Fake-Repository, `SavedStateHandle`): Schrittfolge für Login mit/ohne Haushalt, Registrieren, neuer Haushalt → UPLOAD_ALL ohne Rückfrage, Beitritt ohne lokale Daten → DOWNLOAD_ONLY ohne Rückfrage, Beitritt mit Daten → Rückfrage; „Ersetzen“ ohne erfolgreiche Sicherung löscht nichts (Review Focus 1); Fehler werden als Meldung angezeigt und der Schritt bleibt; Prozessneustart: URL/Benutzername/Schritt aus `SavedStateHandle`, Passwort leer (Review Focus 4); `reconnect` behält Haushalt und Outbox.
- [ ] **Step 2–4:** rot → implementieren (Compose-Bildschirm mit `ScreenHeader`, Eingabefeldern, Passwortfeld mit Sichtbarkeits-Umschalter, Fortschrittsanzeige) → grün; manuelle Prüfung siehe Task 4.
- [ ] **Step 5: Commit** `git commit -m "Sync: Bildschirm Server verbinden"`

### Task 4: Dokumentation und Ende-zu-Ende-Prüfung im Emulator

**Files:** `docs/adr/0006-optional-self-hosted-sync.md` (neu: Kontext, Entscheidung, Folgen – löst ADR 0001 für den optionalen Fall ab), `docs/adr/0001-offline-first.md` (Status „teilweise abgelöst durch 0006“), `docs/security.md` (Angriffsfläche: Netzwerk optional HTTPS, Token, Fotos, Debug-Ausnahme für lokale Hosts), `docs/architecture.md` (Abschnitt Sync vervollständigen: Ablauf, Bildschirme), `README.md` (Kurzabschnitt „Synchronisierung (optional)“ mit Verweis auf `server/README.md`).
- [ ] **Step 1:** Dokumente schreiben.
- [ ] **Step 2: Ende-zu-Ende im Emulator:** Server lokal starten (`FOODY_SERVER_ONLY=1 ./gradlew :server:installDist`, dann `server/build/install/foody-server/bin/foody-server` mit `FOODY_DB_PATH`/`FOODY_PHOTO_DIR` in einem Scratch-Ordner, `FOODY_PORT=18080`, Admin-Variablen gesetzt); Debug-App auf `emulator-5558` installieren; Einstellungen → Server verbinden → `http://10.0.2.2:18080` → als Admin anmelden → Haushalt anlegen → ein Rezept anlegen → „Jetzt synchronisieren“ → Server-Pull per `curl` zeigt das Rezept; Einladen zeigt Code; Trennen → Karte „Nicht verbunden“. Screenshots (`adb exec-out screencap`) in den Scratch-Ordner, Ergebnisse im Bericht. Server danach stoppen.
- [ ] **Step 3:** Gesamtlauf `:sync-protocol:test :server:test :domain:test :app:testDebugUnitTest :app:assembleRelease` + gesamte Instrumented-Suite grün.
- [ ] **Step 4: Commit** `git commit -m "Sync: Dokumentation (ADR 0006, Sicherheit, Architektur)"`

## Abschluss
- [ ] PR gegen `main` (Etappe 5 von 5) mit Auto-Merge; Issues #59/#63 aktualisieren (erledigte Punkte abhaken).
