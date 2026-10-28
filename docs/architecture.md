# Architektur

**Module und Ordner:** Handy-Apps unter `android/`:
- die V3 `:app3` mit `:feature:*` und `:core:*` (`:core:data`, `:core:sync`, `:core:scan`, `:core:designsystem`,
  nur für Tests `:core:testing`),
- die eingefrorene v1 `:app` mit `:baselineprofile`,
- `:katalog`, eine kleine App zum Ansehen des Designsystems.

Gemeinsamer Code liegt unter `shared/` (`:domain`, `:sync-protocol`, `:api-protocol`). Der Server liegt unter
`server/`: `:server-sync` (API, Konten, Datenbank, Sync, Fotos, KI-Gerüst) und `:server` (nur `main`).
Abhängigkeiten laufen nur in eine Richtung: `:app3` → `:feature:*` → `:core:*` → `:domain`/`:api-protocol`
(Features kennen sich nicht), `:server` → `:server-sync` → `:api-protocol`, `:app` → `:domain`, `:sync-protocol`.
`:sync-protocol` (Protokoll 2) ist für die v1-App eingefroren und entfällt in Phase 8; der Server nutzt nur
`:api-protocol` (Protokoll 3). Gradle-Namen sind unabhängig von den Ordnern (`projectDir` in `settings.gradle.kts`). Spec:
`docs/superpowers/specs/2026-10-11-projektstruktur-design.md`.

Die Abschnitte bis „Sync (optional)“ beschreiben die v1. Die V3 beginnt mit „Server (V3)“ und „App V3: Daten und Sync“.

- **UI-Schicht** (`android/app/.../ui`): Compose-Screens + `@HiltViewModel`. Jeder Screen rendert einen unveränderlichen
  `UiState` aus `StateFlow`; Nutzeraktionen sind Methodenaufrufe am ViewModel (Unidirectional Data Flow).
  Einmalige Meldungen (Snackbar) sind Zustand, der nach Anzeige quittiert wird (`messageShown()`).
  Bildschirmzustand, der einen Prozessneustart überleben muss (Suche, Filter, Zeitraum, Editor-Entwurf), liegt im
  `SavedStateHandle`.
- **Domain** (`:domain`): reine Kotlin-Logik, vollständig per JVM-Test abgedeckt.
  - Rechnen: `RecipeScaler`, `UnitConverter`, `NutritionCalculator`
  - Einkauf: `GenerateShoppingListUseCase`, `ShoppingDiff`, `ShoppingListExporter`
  - Import: `MarkdownRecipeImporter`, `GermanAmounts` (Einheitenwörter, deutsche Zahlen)
  - Zutatennamen: `IngredientCatalog` (Synonyme, „nie einkaufen“, Abteilungs-Schätzung)
  - Kochmodus: `StepIngredientMatcher` (Zutaten + Teilmengen im Schritt), `StepTimerParser` (Zeitangaben);
    laufende Timer hält `CookTimerRepository` (App-weit), `CookTimerService` meldet das Ende auch im Hintergrund
  - Startseite: `DailyPicks` (Rezepte des Tages)
- **Data** (`android/app/.../data`): Room als Single Source of Truth, Repositories mappen Entities ↔ Domain.
  Schreibvorgänge über mehrere Tabellen laufen in Transaktionen (Rezept speichern, Listen-Snapshot,
  Diff anwenden, „gekocht“, Rezept auf Einkaufsliste, Zutaten zusammenführen/vereinheitlichen).

## Navigation

Typisierte Routen (Navigation Compose + kotlinx.serialization). `NavigationSuiteScaffold` wählt automatisch
Bottom Bar (Telefon) oder Navigation Rail (Tablet). Jeder Hauptbereich behält seinen eigenen Verlauf
(`saveState`/`restoreState`): Wer von einer Rezeptdetailseite zum Einkauf wechselt und zurück, landet wieder dort.

## Datenbank

Version **6**, Schema-Export nach `android/app/schemas` (eingecheckt). Jede Schemaänderung benötigt eine `Migration`
in `ALL_MIGRATIONS` (`FoodyDatabase.kt`) und einen Migrationstest (`MigrationTestHelper`, `MigrationTest`);
`fallbackToDestructiveMigration()` ist verboten.

| Version | Änderung |
|---|---|
| 1 | Ausgangsschema |
| 2 | `recipe.favorite`, `recipe.sourceUrl` (+ Index); Quell-URL importierter Rezepte aus den Notizen übernommen |
| 3 | `shopping_item.note`, `recipe.rating` |
| 4 | Sync-Tabellen (`sync_outbox`, `sync_record_rev`, `sync_state`, `sync_problem`), `shopping_item.updatedAt`/`checkedChangedAt`, Outbox-Trigger |
| 5 | Sync-Trigger neu angelegt: erneutes Vormerken setzt `sync_outbox.queuedAt` streng steigend (`MAX(jetzt, alt + 1)`); Tabellen unverändert |
| 6 | Foto-Sync: `sync_photo_local` (Hash-Cache je Fotodatei), `sync_photo_wanted` (Fotos, die ein Server-Rezept braucht und die noch fehlen) |
| 7 | Einordnung: `recipe.mahlzeiten`/`recipe.gaenge` (Enum-Namen kommagetrennt, `null` = vermuten); `meal_slot.slotType` auf Enum-Namen (`FRUEHSTUECK` …) umgestellt, unbekannter Freitext bleibt |
| 8 | Tagebuch: `tagebuch_eintrag` (Nährwerte festgehalten, lose Referenzen auf Rezept/Zutat/Plan-Eintrag); Sync-Trigger neu angelegt. `SyncTriggers.create` legt nur Trigger für vorhandene Tabellen an |
| 9 | `ingredient.barcode` (Strichcode, Index, nicht eindeutig); optional in Sync-Payload und Sicherung |

## Sync (optional)

Entscheidung: [ADR 0006](adr/0006-optional-self-hosted-sync.md); Server und Betrieb: [`server/README.md`](../server/README.md).
Dieser Abschnitt beschreibt die v1-App (Protokoll 2, Haushalte); sie synchronisiert bis zum Umstieg (Phase 8) mit
dem eingefrorenen Server-Stand `v1-server`. Der Server in `main` ist unter „Server (V3)“ beschrieben.

SQLite-Trigger (`SyncTriggers`, Namen `sync_*`) schreiben jede lokale Änderung der Tabellen `ingredient`, `recipe`,
`meal_slot`, `pantry_item`, `shopping_list` und `shopping_item` (bei Kindtabellen: des Elterndatensatzes) in
`sync_outbox` – in derselben Transaktion, sodass keine Schreibstelle sie vergessen kann. Die Trigger wirken nur, wenn
`sync_state.active = 1` und nicht gerade Server-Daten angewendet werden (`applyingRemote = 0`); bis `activate` bleibt
der Sync vollständig inaktiv. Die Zeile `sync_state(id = 1)` und die Trigger legen `SYNC_CALLBACK` (frische
Installation) bzw. `MIGRATION_3_4` an. Ein erneut vorgemerkter Datensatz bekommt immer ein streng größeres
`queuedAt` (auch in derselben Millisekunde), damit der Push Änderungen während eines Laufs sicher erkennt.

### Ablauf eines Laufs

`SyncEngine.run()` (ein Mutex je Server-URL; `SyncEngineFactory` liefert `null` bei inaktivem Sync, fehlendem Token oder
„unauthorized“): Fotos der Batch-Rezepte hochladen → **Push** der Outbox in Abhängigkeitsreihenfolge (Zutat → Rezept →
Planposition → Vorrat → Liste → Eintrag) → **Pull** ab dem gespeicherten Cursor (`SyncApplier` wendet in einer
Transaktion mit `applyingRemote = 1` an, kein Outbox-Echo) → fehlende Fotos laden. `410` (Cursor abgelaufen) löst einen
Voll-Abgleich aus. Ergebnis und feste Fehlerkennung landen in `sync_state` (`lastSyncAt`, `lastError`), abgelehnte
Datensätze als `sync_problem` („Sync-Probleme (n)“). Konflikte: Last-Writer-Wins am Server; Einkaufseinträge feldweise
(`checked` nach `checkedChangedAt`, bei Gleichstand gewinnt „abgehakt“); Löschen gewinnt gegen Bearbeiten ohne Kenntnis
der Löschung; Zutaten mit gleichem Namen werden zur älteren ID zusammengeführt.

### Hintergrund-Sync

`SyncScheduler` (WorkManager, nur mit Netzwerk): periodisch stündlich (`schedulePeriodic`, `UPDATE` bringt ein
geändertes Intervall auf bestehende Installationen), beim Öffnen der App (`requestOnAppOpen` aus `MainActivity.onStart`) und nach lokalen
Änderungen entprellt nach 5 s (`requestSoon`, ausgelöst durch das Beobachten der Outbox-Größe); Backoff exponentiell ab
30 s. `FoodyApp` plant beim Start nur, wenn der Sync aktiv ist. Wurde während eines Laufs etwas vorgemerkt, hängt der
Worker einen weiteren Lauf an (`requestSoonIfQueuedSince`).

### Bildschirme

- **Einstellungen, Karte „Synchronisierung“** (`SyncSettingsCard`): „Nicht verbunden“, „Verbunden mit Haushalt …“ oder
  „Abgemeldet“; „Jetzt synchronisieren“, „Einladen“ (Code `FOODY-XXXX-XXXX`, kopieren/teilen), „Geräte“ (anzeigen/abmelden),
  „Sync-Probleme“, „Trennen“ (immer mit Rückfrage; die Daten bleiben).
- **Server verbinden** (`SyncSetupScreen`/`SyncSetupViewModel`): Adresse (`ServerUrl`: https, im Debug-Build zusätzlich
  `http` für lokale Hosts) → Anmelden oder mit Einladungscode registrieren → Haushalt wählen oder anlegen → bei
  vorhandenen lokalen Daten und bestehendem Haushalt „Zusammenführen“ oder „Dieses Gerät ersetzen“ (erst Sicherung).
  Passwort und Token liegen nie im `SavedStateHandle`; das Token speichert `TokenStore` (Keystore).

### Fotos

Rezeptfotos reisen als JPEG-Blobs, adressiert über ihren SHA-256 (`RecipePayload.photo`, Kleinbuchstaben-Hex); nur eigene
Fotos (`file:` im Fotoordner von `RecipePhotoStore`) werden übertragen, `content:`-Links nie. `PhotoIndex` hasht je Datei
(Cache `sync_photo_local` mit Größe und Änderungszeit, damit `shrink` erkannt wird) und findet zu einem Hash die lokale Datei.

- **Hochladen vor dem Push:** `SyncEngine` sammelt je Batch die Foto-Hashes der Rezepte, fragt `POST /photos/missing` und
  lädt jedes fehlende per `PUT /photos/{sha256}` hoch. Hashes ohne lokale Datei (Wunsch, noch nicht geladen) werden
  übersprungen; Lese-/Upload-Fehler sind `Transient` (Lauf bricht ab, Outbox bleibt).
- **Herunterladen nach dem Pull** (auch nach dem Voll-Abgleich): Kennt die App das Foto eines Server-Rezepts nicht, merkt
  `SyncApplier` es in `sync_photo_wanted` vor. Je Wunsch: Rezept weg → Wunsch verwerfen; lokale Datei mit dem Hash
  vorhanden → nur verknüpfen; sonst `GET /photos/{sha256}`. 404 → der Wunsch bleibt für den nächsten Lauf. Falscher Hash
  → verwerfen, Problem `photo_mismatch`, Wunsch bleibt (kein Überschreiben des Server-Fotos), kein erneuter Abruf, bis
  ein neuer Server-Stand das Problem löscht (das Problem hält ein serverseitig gelöschtes Rezept im Voll-Abgleich lokal). Sonst wird über
  `RecipePhotoStore.newPhotoFile()` (temporäre Datei, dann Umbenennen) gespeichert, in `sync_photo_local` eingetragen und
  in einer Transaktion mit `applyingRemote = 1` `recipe.imageUri` gesetzt und der Wunsch gelöscht – nur, wenn der Wunsch
  noch mit demselben Hash besteht (ein lokal neu gewähltes Foto löscht ihn per Trigger). Kein Outbox-Echo.
- **Nicht übertragbare Fotos** (über 10 MB, leer oder kein JPEG; zuerst wird `shrink` versucht) gehen als `photo = null`
  mit Problem `photo_unsyncable` raus – die bewusste Ausnahme, weil es dauerhaft ist. Lehnt der Server ein Foto beim Upload
  ab (4xx/413), bekommt nur das betroffene Rezept dieses Problem und bleibt in der Outbox; der Lauf geht weiter.
- Der Server räumt Fotos auf, die kein lebender Rezept-Datensatz mehr nennt und die älter als 30 Tage sind.

### Einordnung (seit DB v7)

`RecipePayload` und die Sicherung tragen `mahlzeiten`/`gaenge` optional (`null` wird wegen `explicitNulls = false` nicht
gesendet). Eingehende `slotType`-Werte (Sync, Sicherung) laufen durch `Mahlzeit.ausText`, damit ältere Daten keinen
Freitext zurückbringen. **Bekannte Grenze:** Bearbeitet ein Gerät mit App-Version vor DB v7 ein Rezept, fehlen die Felder
in seinem Payload; die manuelle Einordnung dieses Rezepts geht verloren und es gilt wieder die Vermutung.

### Tagebuch (seit DB v8)

Sync-Typ `tagebuch_eintrag` (wie die übrigen Typen = Tabellenname, den die Trigger in die Outbox schreiben). Der Payload
trägt die festgehaltenen Nährwerte; Referenzen werden nicht geprüft (`PayloadValidator.references` leer), ein Eintrag
zu einem gelöschten Rezept wird also trotzdem angewendet. **Ausrollen: erst den Server, dann die Apps.** Seit Protokoll 2
(`Protocol.VERSION`/`MIN_VERSION`) meldet der Server einer alten App `protocol_too_old` („Bitte App aktualisieren“),
einer neuen App an einem alten Server antwortet er `server_too_old` („Server aktualisieren“) – statt Dekodierfehlern. Die Sicherung enthält `tagebuch`
(fehlt in älteren Dateien → leer).

## Server (V3)

Spec: `docs/superpowers/specs/2026-10-17-v3-phase2-server-schlank-design.md`. Ktor + SQLite, Routen unter
**`/api/v3`**, Header `X-Foody-Protocol` (`Protocol.VERSION = MIN_VERSION = 3` in `:api-protocol`, Paket
`de.foody.api.protocol`). `/api/v1/*` und Header < 3 → `409 protocol_too_old`, Header > 3 → `409 server_too_old`.

- **Konto statt Haushalt:** Konto = Zeile in `user`, Konto-ID = `user.id`; das Konto trägt `last_rev` und
  `compacted_before_rev`. `sync_record` ist nach `(konto_id, type, id)` geschlüsselt, `device` hat keinen Haushalt,
  `DevicePrincipal` ist `(deviceId, kontoId)`. Konten legt nur `foody-admin konto-anlegen` an (bzw. beim ersten Start
  `FOODY_ADMIN_USER`); keine Registrierung, keine Admin-Rechte in der API, keine Haushalte, Einladungen oder Geräteliste.
- **Routen:** `POST /auth/login` (`{token, kontoId}`), `POST /auth/abmelden` (widerruft das eigene Token),
  `POST /auth/password`, `POST /sync/push`, `GET /sync/pull`, `POST /photos/missing`, `PUT`/`GET /photos/{sha256}`,
  `GET /ki/status`. Push/Pull, Konfliktregeln, Validator, Anmelde-Drosselung, Body-Grenzen und Kompaktierung wie
  bisher; den Typ `pantry_item` kennt Protokoll 3 nicht mehr.
- **Migrationen:** `Migrations.apply` beim Start, jede in einer Transaktion, Stand in `PRAGMA user_version`. Steht eine
  Migration aus, schreibt `Database` vorher `vor-migration-<ziel>-<JJJJ-MM-TT-HHMM>.db` per `VACUUM INTO` neben die
  Datei; liegt für dieses Ziel schon eine Sicherung (gescheiterter Versuch), keine weitere. Migration 1 → 2
  (Haushalt → Konto) prüft vorab (je Haushalt genau ein Mitglied, kein Konto in mehreren Haushalten, keine verwaisten
  Datensätze), löscht Vorrats-Datensätze und zählt in der Transaktion gegen; scheitert etwas, startet der Server
  nicht (`MigrationAbgebrochen`, Grund im Log). Gerätetokens bleiben gültig.
- **Fotos:** `<FOODY_PHOTO_DIR>/<Konto-ID>/<sha256>.jpg`. `FotoUmzug` benennt nach Migration 2 je Zeile in
  `haushalt_umzug` den alten Haushaltsordner in den Kontoordner um (beide vorhanden → zusammenführen, fehlt → Warnung)
  und löscht die Zeile; läuft bei jedem Start, bis die Tabelle leer ist.
- **KI-Gerüst:** `FOODY_KI_KEY` (fehlt → `GET /ki/status` meldet `eingerichtet: false`) und `KiKontingent` (Anfragen je
  Konto und Stunde, `FOODY_KI_PRO_STUNDE`, Standard 30). Noch kein Modellaufruf und keine Modellwahl (KI-Projekt nach dem Umstieg, `ToDoS/ki-plan.md`).
- Die Web-Oberfläche (`:server-web`) ist entfallen (ADR 0011); ihre Specs vom 2026-10-11 beschreiben den alten Stand.

## App V3: Daten und Sync

Spec: `docs/superpowers/specs/2026-10-18-v3-phase3-app-daten-sync-design.md`. Neu geschrieben, die v1 (`:app`,
`:sync-protocol`) war Vorlage für das Verhalten und bleibt unverändert. Noch ohne Oberfläche (Phase 4–6); die App liefert
später `SyncKonfiguration` (Debug-Build → `lokalesHttpErlaubt`), ruft `SyncAusloeser.beimStart()` auf und stellt die
`HiltWorkerFactory` bereit (Phase 5).

- **Module:** `:core:data` (`android/core/data`, Paket `de.foody.core.data`) – Room, Sync-Speicher, Fotos, Sicherung,
  Portionen-Nachtrag (`DatenModul`); `:core:sync` (`android/core/sync`, Paket `de.foody.core.sync`) – Server-Zugang,
  Sync-Lauf, Verbinden/Trennen, Auslöser, Token (`SyncModul`). `:core:sync` → `:core:data`, `:api-protocol`;
  `:core:data` → `:domain`, `:api-protocol` (per `api`, weil Entities und `FoodyDatenbank` diese Typen zeigen).
  Seit Phase 6e3 dazu `:core:scan` (`android/core/scan`, Paket `de.foody.core.scan`) – Packung scannen (`ScanModul`,
  ADR 0013). `:core:scan` → `:core:data`, `:domain` (per `api`), `:core:designsystem`; `:feature:heute`,
  `:feature:zutaten`, `:feature:einstellungen` und `:app3` → `:core:scan`.
  Nur für Tests: `:core:testing` (`android/core/testing`) mit `ViewModelHalter`; das Feature-Plugin bindet es als
  `testImplementation` ein. Tests geben ihre ViewModels hinein und rufen in `@After` erst `schliessen()`, dann
  `db.close()` – sonst fragt ein noch laufender Flow die geschlossene In-Memory-Datenbank ab. Dazu
  `SavedStateHandle.nachProzessende()` (`Prozessende.kt`): ein neues Handle, dessen Werte durch die
  Java-Serialisierung gingen wie im Bundle – Tests für Entwürfe, die ein Prozessende überstehen müssen.
- **Datenbank `foody.db`, Version 10** (`FoodyDatenbank`, Schemata 1–9 aus der v1 unter `android/core/data/schemas`):
  Schema 9 ohne `pantry_item`, `sync_state.kontoId` (nullable) statt `householdId`; ein Schema-Vergleichstest erzwingt,
  dass sonst alles gleich ist. `baueDatenbank` registriert nur `MIGRATION_9_10` und den Callback für neue Datenbanken,
  **nie** einen destruktiven Fallback. Die Migration (eine Transaktion) entfernt Vorrat samt Triggern und
  `pantry_item`-Zeilen in Outbox/Revisionen/Problemen, baut `sync_state` mit `kontoId = NULL` neu (Cursor, `active`,
  `serverUrl`, `lastSyncAt` bleiben; `lastError` der v1 wird abgebildet: `unauthorized` → `anmeldung_abgelaufen`,
  `protocol_too_old` → `app_zu_alt`, `protocol_server_too_old` → `server_zu_alt`, alles andere `NULL`) und ersetzt alle
  Trigger durch `SyncTrigger.anweisungen`. Ungesendete
  Outbox-Einträge (auch Löschungen) bleiben und gehen danach über Protokoll 3 hinaus.
- **Sync-Trigger:** wie v1 für `ingredient`, `recipe`, `meal_slot`, `shopping_list`, `shopping_item`,
  `tagebuch_eintrag` und die Kindtabellen (`recipe_ingredient`, `instruction_step` → Rezept; `shopping_item_source` →
  Einkaufsartikel); nur bei `active = 1` und `applyingRemote = 0`. `@Upsert` statt `REPLACE`.
- **Sync-Lauf** (`SyncLauf`, ein Lauf zur Zeit unter `SyncSpeicher.laufSperre`, die auch Trennen und „Ersetzen“
  nehmen): Outbox in Paketen (≤ 500 Datensätze, ≤ 5 MB, höchstens 20 Pakete je Lauf, `413` → Paket halbieren, einzelner
  zu großer Datensatz → Problem `too_large`), fehlende eigene Fotos vorher hochladen; Ergebnisse verbuchen (angenommen,
  zusammengeführt – auch in eine andere ID –, abgelehnt → `sync_problem`, fehlende Verweisziele nachreichen); alle
  Pull-Seiten holen und erst dann anwenden (`applyingRemote = 1`, je Datensatz eine Transaktion, Cursor zuletzt; lokale
  Änderungen mit offenem Outbox-Eintrag gewinnen); `410` (Cursor vor der Kompaktierungsgrenze oder hinter dem
  Server-Stand, etwa nach dem Einspielen einer Sicherung) → Voll-Abgleich, der Unsendetes, Abgelehntes, Verwiesenes und
  noch Gebrauchtes behält; zuletzt gewünschte Fotos laden. Header `X-Foody-Protocol: 3`, Routen unter `/api/v3`
  (`KtorServerZugang` über OkHttp, Antworten begrenzt gelesen, keine Weiterleitungen).
- **Auslöser** (`SyncAusloeser`, `SyncAuftrag`): einmaliger WorkManager-Auftrag mit Netz-Bedingung; sofort bei
  Vordergrund und „Jetzt synchronisieren“, nach Outbox-Änderungen entprellt um 5 s. Kein periodischer Auftrag; der
  v1-Aufträge `foody-sync-periodic` und `foody-sync-now` werden beim Start abgemeldet. Ein laufender Auftrag wird nie ersetzt, sondern plant
  einen Nachlauf. Nur vorübergehende Fehler werden mit Backoff wiederholt.
- **Fehlerkennungen** in `sync_state.lastError` (nur diese festen Werte, nie Token oder Bodies): `offline`,
  `anmeldung_abgelaufen` (`401`, kein lesbares Token – das Token wird dabei nicht gelöscht), `app_zu_alt`
  (`409 protocol_too_old`), `server_zu_alt` (`409 server_too_old` oder `404`, z. B. Server ohne `/api/v3`), `cursor_haengt`,
  `server_fehler` (5xx, unlesbare Antworten, lokale Fehler; Unerwartetes wird wie ein vorübergehender Fehler
  wiederholt und nur mit Klasse und Stelle geloggt, nie mit der Meldung). Anmelden und Aktivieren melden der Oberfläche zusätzlich
  `ungueltige_adresse`, `falsche_zugangsdaten`, `zu_viele_versuche` bzw. `token_nicht_gespeichert` (nicht in `lastError`;
  Sync-Zustand und Daten bleiben dann unverändert, das alte Token aber nicht sicher: Der `TokenSpeicher` verwirft beim
  Fehlschlag den Keystore-Schlüssel, `SyncKonto` legt das alte Token bestmöglich neu ab, sonst folgt „Anmeldung
  abgelaufen“).
- **Verbinden/Trennen** (`SyncKonto`): Adresse prüfen (`https`; Debug zusätzlich `http` für `10.0.2.2`/`localhost`),
  anmelden (Adresse, Konto-ID und Token nur im Speicher), erst `aktivieren` speichert das Token und schaltet den Sync
  ein (Cursor 0) – beides unter der `laufSperre`, sodass kein Lauf das neue Token mit der alten Adresse sieht; ein
  abgebrochener Dialog lässt das gespeicherte Token unverändert. Eine Wiederanmeldung bei aktivem Sync an derselben Adresse (beide
  Seiten über `ServerAdresse.pruefen` vereinheitlicht) und mit derselben oder – wie nach der Migration – noch keiner
  Konto-ID übernimmt nur das neue Token und trägt die Konto-ID ein; Outbox und Daten bleiben, der Cursor auch – außer
  ohne Konto-ID (nach dem Update): dann holt der nächste Lauf einmal alles neu (Cursor 0), falls doch ein anderes Konto
  gewählt wurde. Die Wahl
  „Zusammenführen“/„Ersetzen“ wirkt nicht (sonst verwürfe „Ersetzen“ direkt nach dem Update die ungesendete
  v1-Outbox). Die Entscheidung fällt unter der `laufSperre`. „Zusammenführen“ merkt alle lokalen
  Datensätze vor, außer unveränderten Startzutaten (feste IDs, Zeitpunkt der Installation). Der Server nimmt einen
  Datensatz ohne `baseRev`, den er schon hat, nur an, wenn er jünger ist (`updatedAt`); sonst antwortet er „zusammengeführt“
  mit seinem Stand. So überschreibt ein frisch installiertes Gerät keine im Katalog korrigierten Zutaten. „Ersetzen“ verwirft Rezepte, Plan, Einkaufslisten, Tagebuch, eigene Fotos und Sync-Vermerke, **behält
  aber die Zutaten**: Sie sind großteils Startzutaten, der Server führt gleichnamige Zutaten zusammen, und ein fehlendes
  Verweisziel reicht der Sync bei Bedarf nach. Folge: Eine nur lokal angelegte, nie gesendete Zutat bleibt lokal, bis
  sie geändert oder von einem gesendeten Datensatz gebraucht wird (Hinweis in der Oberfläche: Phase 6). Trennen meldet zuerst mit dem gespeicherten Token beim Server ab (falls erreichbar, höchstens 8 s), löscht
  das Token, bricht die Aufträge ab und wartet erst dann auf die `laufSperre`, um die Sync-Tabellen zu leeren; lokale
  Daten bleiben.
- **Token-Ablage** (`TokenSpeicher`) wie v1: SharedPreferences `foody_sync`, Eintrag `token`, AES/GCM-Schlüssel im
  Android-Keystore unter `foody_sync_token`. Nicht entschlüsselbar (z. B. Schlüssel nach Gerätewiederherstellung weg) →
  Token verworfen, Zustand „Anmeldung abgelaufen“; vorübergehende Keystore-Störung → Token bleibt.
- **Fotos** (`FotoAblage`, `FotoIndex`): `filesDir/recipe_images`, `file:`-Links wie in der v1; höchstens 1.600 px an
  der längeren Seite und 600 KB, JPEG 85, Bestand einmal je Verkleinerungs-Version (`fotosVerkleinert`). Übertragen
  werden nur eigene Dateien; Galerie-Links (`content:`) gehen nie hinaus und bleiben stehen. Nicht übertragbare Fotos
  (> 10 MB oder kein JPEG nach Verkleinern) → Rezept ohne Foto mit Problem `photo_unsyncable`.
- **Sicherung** (`Sicherung`): ZIP mit `backup.json` (Format 3, ohne Vorrat) und `photos/`; liest Format 3 und alle
  v1-Formate (Vorrat übersprungen). Import ersetzt alles, ist atomar und bei aktivem Sync gesperrt; Zip-Slip und
  Größengrenzen wie v1.
- **Portionen-Nachtrag** (`PortionenNachtrag`): ordnet Markdown-Dateien über `sourceUrl` den Rezepten zu und wendet
  `RezeptImport.portionenNachtragen` an (nur `defaultServings` und Notiz); Ergebnis aktualisiert/übersprungen/ohne
  Rezept. Knopf in Phase 6.
- **Tests:** Robolectric-Unit-Tests für DAOs, Trigger, Abbildung, Sync-Speicher, Fotos, Sicherung; der Sync-Client
  läuft im Test gegen den echten `:server-sync` (`TestSyncServer`). Auf dem Emulator (`ciDevice`): Migrationstest 9 → 10
  und zwei Echtdaten-Tests, die nur mit Instrumentation-Argumenten prüfen (sonst bestanden ohne Prüfung):
  `EchteDatenMigrationTest` (`:core:data`, Argumente `echteDb`, `echteDbZahlen`) und `EchteDatenSyncTest`
  (`:core:sync`, Argumente `echteDb`, `testServer`, `testBenutzer`, `testPasswort`, optional `echteFotos`). Der Sync-Test
  migriert die echte Datenbank, behält ihren Sync-Zustand und Cursor, meldet sich aber **neu** am Test-Server an: Das
  Token des Handys liegt im Hardware-Keystore und ist nicht kopierbar. Die Token-Kontinuität belegt nur der Formattest
  `TokenSpeicherTest.gleicheAblageWieVEins` (gleiche Ablage wie die v1), die Cursor-Kontinuität
  `SyncLaufTest.alterTokenUndCursorReichen`; „ohne neue Anmeldung“ auf dem Gerät prüft
  erst Phase 8. Echte Datenbanken (`*.foody-echt.db*`) kommen nie ins Repo.

## Designsystem V3 (Phase 4)

ADR 0012, Spec `docs/superpowers/specs/2026-10-19-v3-phase4-designsystem-design.md`. Einzige Quelle für das Aussehen
der V3; die v1 (`android/app`) behält ihr eigenes Theme bis Phase 8.

- **`:core:designsystem`** (`de.foody.core.designsystem`, ohne Abhängigkeit zu `:core:data`/`:core:sync`):
  - `token/`: `FoodyFarben` (Hell/Dunkel, Richtung „Ofenwarm“), `GlasStufe` + `glasWerte` (LEICHT/DICHT mit Unschärfe 24 dp,
    BILD fast deckend ohne Unschärfe; Tönung, Lichtkante), `VerlaufHell`/`VerlaufDunkel`, `FoodyTypografie` (Fraunces bis `titleLarge`, sonst
    Figtree; Deutsch mit Silbentrennung), `FoodyFormen` (12/20/28 dp), `FoodyAbstaende` (4-dp-Raster), `Kontrast.kt`
    (`kontrast`, `mischen`, `kontrastPaare`).
  - `FoodyTheme { … }` füllt Material 3 und stellt `FoodyTheme.farben/dunkel/unschaerfe` bereit; keine Dynamic Color.
    `animationenErlaubt()` liest die Animator-Dauer des Systems – ist sie 0, entfallen Unschärfe und Drehungen.
  - `glas/`: `FoodyHintergrund` (Grund + zwei Radialverläufe, Quelle der Unschärfe) und `GlasFlaeche(stufe)` (Haze
    `hazeBlur` mit Tönung; ohne Hintergrund, ohne Unschärfe, bei Stufe BILD oder `unschaerfeErlaubt = false` – Kacheln –
    nur die Tönung).
  - `komponenten/`: `RezeptkarteGross`/`RezeptkarteRaster`, `Kachel`, `Naehrwertbalken`, `SyncStatus` (`SyncAnzeige`),
    `LeererZustandZeile`/`LeererZustandFlaeche`, `FoodyKnopf`, `FoodyChip`, `Abschnittskopf`, `Augenbraue`,
    `FoodyNavigationsleiste`/`FoodyNavigationsRail`. Zustandslos, nur einfache Werte und Slots; Berührflächen ≥ 48 dp;
    ab Schriftskalierung 1,5 mehr Zeilen und Knöpfe unter dem Text statt Abschneiden.
- **Kontrast:** WCAG 2.2 AA (Text 4,5:1, Grafik 3:1) gilt ohne Unschärfe gegen die ungünstigste Stelle des Verlaufs;
  `KontrastTest` rechnet jedes Paar aus `kontrastPaare` nach, hell und dunkel.
- **`:katalog`** (`de.foody.katalog`, nie ausgeliefert): alle Komponenten in allen Zuständen, Umschalter
  hell/dunkel/System, Schrift 200 %, ohne Unschärfe; ab 840 dp zweispaltig.
- **Tests:** Robolectric-Komponententests mit nativer Grafik (echte Textmessung) und 200-%-Prüfung
  (`TestHilfen.abgeschnitteneTexte`); Roborazzi-Screenshots aller Katalogseiten (hell/dunkel, Handy 411 dp,
  Tablet 1280 dp, Schrift 200 %) in `android/katalog/src/test/screenshots/`, Vergleich in der CI
  (`:katalog:verifyRoborazziDebug`, Toleranz 0,1 %). Neu aufnehmen: `./gradlew :katalog:recordRoborazziDebug`.

## App V3: Hülle, Heute, Tagebuch (Phase 5)

Spec `docs/superpowers/specs/2026-10-24-v3-phase5-heute-design.md`. Die V3 läuft als eigenes App-Modul neben der v1,
bis Phase 8 umschaltet.

- **`:app3`** (`de.foody.app3`, `applicationId` `de.foody.app`, Debug `.debug` „Foody Debug“, Release noch aus):
  `FoodyApp` (Hilt, `HiltWorkerFactory`; beim Start `SyncAusloeser.beimStart()` und `StartdatenLader`, bei jedem Wechsel
  in den Vordergrund `appImVordergrund()`), `MainActivity` (`singleTask`; Deep Links aus `onNewIntent` gehen über
  `deepLinkOeffnen`, siehe Navigation), `AppModul` (`SyncKonfiguration`, `KontoZugriff` → `KontoAnbindung` auf `SyncKonto`/`sync_state`/
  `SyncAusloeser`, Gerätename „Hersteller Modell“ wie v1).
- **Navigation** (`navigation/`): typisierte Routen (`Heute`, `Rezepte`, `Woche`, `Einkauf`, `Einstellungen`,
  `RezeptDetail(id)`, `Kochen(rezeptId, portionen?)`, `EinkaufListe(listeId)`, `TagebuchTag(datum)`). `Hauptgeruest`:
  vier Tabs, unter 840 dp `FoodyNavigationsleiste` schwebend unten, ab 840 dp `FoodyNavigationsRail`; Detailziele
  markieren ihren Tab. **Ein `FoodyNavHost` an fester Stelle** (seit R-W6): Kochen-Vollbild, Drehen und
  Breitenwechsel blenden nur Leiste bzw. Rail daneben ein oder aus, der Zustand der Bildschirme (`rememberSaveable`)
  bleibt. Deep Links `foody://rezept/{id}`, `foody://einkauf/{listeId}`, `foody://kochen/{rezeptId}?portionen={n}`,
  `foody://tagebuch/{datum}` (ungültiges Datum → heute). Bei laufender App navigiert `deepLinkOeffnen` mit
  `launchSingleTop` ins Gerüst – **nicht** `handleDeepLink`: das baut bei `FLAG_ACTIVITY_NEW_TASK` (Tipp auf die
  Timer-Meldung) den Task neu auf und verwirft ungespeicherte Eingaben (R-W5).
  `Bildschirme` bündelt die Hilt-Bildschirme; Navigationstests setzen `Bildschirme.platzhalter` ein und laufen ohne Hilt.
- **`:core:data`**: `HeuteRepository` (Plan heute/morgen nur mit vorhandenem, nicht archiviertem Rezept; kcal je
  Portion über `RecipeProfiles`; Rezeptvorschläge; offene Artikel der zuletzt geänderten Liste), `TagebuchRepository`
  (Werte beim Eintragen festhalten, ADR 0007; `ausPlan` eine Portion, höchstens einmal in einer Transaktion; Ändern
  skaliert mit `Tagebuch.skaliert`; Löschen/Wiederherstellen mit derselben ID), `TageszielSpeicher` (Prefs
  `dailyKcalGoal` als Flow), `DomainAbbildung` (Zeilen → `:domain`-Modelle wie die v1-Mapper).
- **`:core:sync`**: `SyncLageQuelle` (`sync_state` als Flow + WorkManager-Status des Auftrags `foody-sync-v3` = „läuft“).
- **`:feature:heute`**: „Heute“ (`HeuteViewModel` über einen minütlichen `Zeitgeber` – Datum und nächste Mahlzeit
  wechseln um Mitternacht; `naechsteMahlzeit` mit Zeitfenstern Frühstück 10:00, Mittagessen 14:30, Snack 17:00, sonst
  23:59; Inspiration per `DailyPicks` nur ohne Plan für heute; Tagebuch des Tages mit Vorschlägen „Gegessen?“; offener
  Einkauf; `syncAnzeige` für den Kopf; leere Datenbank → „Server verbinden“). Tagebuch in `tagebuch/`: Eintragen-Blatt
  (Rezept, Zutat, Aus dem Plan, Frei; Mahlzeit aus der Zeile oder nach Uhrzeit; Suche Teilstring, höchstens 30 Treffer;
  `speichernMoeglich` nur bei gültigen Eingaben), Ändern-Dialog, „Alle Einträge“ (`TagebuchTagRoute`, Blättern,
  Datumsauswahl, Löschen mit „Rückgängig“). Fotos per Coil (`file:`-Links), sonst Platzhalter. Inhalte höchstens
  720 dp breit, mittig.
- **`:feature:einstellungen`**: „Server verbinden“ über `KontoZugriff` (Anmelden; mit lokalen Daten Wahl
  „Zusammenführen/Ersetzen“, bei bestehender Verbindung sofort aktivieren; verbunden: Adresse, „zuletzt
  synchronisiert“, letzter Fehler als Klartext, „Jetzt synchronisieren“, „Neu anmelden“ nach abgelaufener Anmeldung,
  Trennen mit Rückfrage). Das Passwort liegt nur im Eingabefeld.
- **Tests:** Repository-Tests (Room im Speicher), Navigations- und Deep-Link-Tests, Bildschirmtests mit echtem
  ViewModel (Uhr und Sync-Lage als Flows vorgegeben, Room-Abfragen synchron), Schrift 200 %, Hilt-Graph. Roborazzi-
  Bilder von „Heute“ und „Alle Einträge“ (hell/dunkel × Handy 411 dp/Tablet 1280 dp, erfundene Daten) in
  `android/feature/heute/src/test/screenshots/`, Vergleich in der CI (`:feature:heute:verifyRoborazziDebug`).
  Testumgebung: Robolectric dekodiert unter Windows keine Bilder (Foto-Test mit `coil-test`), und ein Textfeld in einem
  Dialog läuft mit Breiten-Qualifier endlos (der Ändern-Test nutzt nur die Höhe).

## Rezepte (Phase 6a)

Spec `docs/superpowers/specs/2026-10-24-v3-phase6a-rezepte-design.md`. Ersetzt die Platzhalter für den Tab Rezepte
und `RezeptDetail(id)`; neu sind die Routen `RezeptNeu` und `RezeptBearbeiten(id)`. Keine Schemaänderung (DB v10).

- **`:domain`**: `ZutatZeile` liest eine Freitextzeile („200 g Reis, gekocht“, „1 Prise Salz“, „Pfeffer nach
  Bedarf“, „1/2 Bund Petersilie (optional)“) über denselben Baukasten wie der Markdown-Import und schreibt sie für
  den Editor zurück. Mengenwörter wie Bund, Dose oder Zehe landen als Hinweis, „optional“ als Kennzeichen.
- **`:core:data`** (`rezepte/`): `RezeptRepository` (Karten für die Liste mit kcal je Portion, Favorit, „gekocht“
  aus dem Plan; volles Rezept als Flow; `speichern` in einer Transaktion, gibt `Gespeichert.Geloescht` zurück, wenn
  das Rezept inzwischen weg ist; `duplizieren` als „(Kopie)“ ohne Quelle und Favorit; Archiv, Favorit, Foto; ein
  ersetztes Foto wird nur gelöscht, wenn kein anderes Rezept es nutzt). `RezeptImportDienst` (`RezeptImporter`):
  mehrere Dateien oder ein ganzer Ordner über SAF, höchstens 1 MiB je Datei, in Blöcken zu 25 Rezepten; Rezepte mit
  schon vorhandener Quelle werden übersprungen, Ergebnis `importiert/übersprungen/fehler`.
- **`:feature:rezepte`**: Liste (`liste/`: ein Bildraster mit Suche über Name, Tags und Zutaten, Chips Favoriten ·
  Oft gekocht · Ernährung · häufige Tags, Sortierung Name/Neueste/kcal/Eiweiß/Oft gekocht), Detail (`detail/`: Kopfbild mit
  Glasfläche, Portionen skalieren, Zutaten, Schritte, Einordnung, Nährwerttabelle je Portion und gesamt mit
  Vollständigkeit, Archivieren mit „Rückgängig“, Duplizieren, Kochen), Editor (`editor/`: alle Felder auf einer
  Seite, Zutaten als Freitext je Zeile mit Vorschau „im Katalog / neu / ungültig“ – unveränderte Zeilen behalten ihre
  Zutat und ihren Hinweis (`RezeptEntwurf.zutatIds`), ein nicht angefasstes Foto bleibt, auch wenn der Sync es
  inzwischen gebracht hat (`bildBehalten`); Foto per Photo-Picker oder Kamera
  über `KameraDatei` (`:core:data`, FileProvider `${applicationId}.kamera`), Speichern gesperrt bis gültig und während des Speicherns,
  „Änderungen verwerfen?“ beim Zurück), Import-Blatt (`importieren/`). `RezepteBereich` zeigt ab 840 dp Liste und
  Detail nebeneinander; ein Tipp links fragt erst, wenn rechts ein Editor ungespeicherte Änderungen hat. Inhalte höchstens 720 dp breit.
- **Tests:** Repository- und Importtests (Room im Speicher), Filter- und Zeilentests, Bildschirmtests mit echtem
  ViewModel (Schrift 200 %, Doppeltipp auf Speichern, gelöschtes Rezept im Editor, Zustand nach Drehen),
  Navigationstests in `:app3`. Roborazzi-Bilder von Liste, Detail und Editor (hell/dunkel × Handy/Tablet) in
  `android/feature/rezepte/src/test/screenshots/`, Vergleich in der CI.

## Kochen (Phase 6b)

Spec `docs/superpowers/specs/2026-10-25-v3-phase6b-kochen-design.md`. Ersetzt den Platzhalter der Route
`Kochen(rezeptId, portionen?)`; der Kochmodus ist ein Vollbild ohne Navigationsleiste/Rail. Keine Schemaänderung.

- **`:core:data`**: `KochenRepository.gekochtMarkieren(rezeptId, heute)` setzt `cookedAt`/`updatedAt` beim ersten
  heutigen, noch nicht gekochten Plan-Eintrag des Rezepts (zählt für „Oft gekocht“; der `meal_slot`-Trigger trägt es
  in die Outbox); ohne Eintrag nichts.
- **`:feature:kochen`**: `kochSchritte`/`bereitlegen` (reine Funktionen: je Schritt die erwähnten Zutaten über
  `StepIngredientMatcher` mit Teilmenge „150 g von 400 g“, skaliert mit `RecipeScaler`; Timer 1 s–24 h aus
  `StepTimerParser`). `KochenViewModel` über `RezeptRepository.voll` – Seiten „Bereitlegen“ (wenn es Zutaten gibt oder
  keine Schritte) und je Schritt eine; Seite und Haken (Zeilenindex) bleiben gültig, wenn sich das Rezept per Sync
  ändert. `KochSitzungSpeicher` (Prefs `kochen_sitzungen`): Seite, Haken, Portionen je Rezept, 12 Stunden fortsetzbar;
  „Fertig“ und „Schließen“ im Fertig-Blatt löschen sie, Zurück und das Kreuz behalten sie. Fertig-Blatt „Guten
  Appetit!“: Foto aufnehmen (nur ohne Rezeptfoto; über `KameraDatei` aus `:core:data`), „War etwas
  anders? Rezept anpassen“ → `RezeptBearbeiten(id, gekochtMit)` ersetzt den Kochmodus (der Editor zeigt „Gekocht mit 4
  statt 2 Portionen – Mengen beziehen sich auf 2“). Ab 840 dp links die Seitenliste. Display bleibt an.
- **Timer** (`timer/`): `KochTimerSpeicher` (Prefs `kochen_timer`, JSON; überstehen Neustart, abgelaufene höchstens
  12 h, `gemeldet` verhindert doppelte Meldungen), reine Zeitlogik `zeitplan(timer, jetzt)`, `KochTimerDienst`
  (Vordergrunddienst `specialUse` mit Teil-Wakelock bis kurz nach dem nächsten Ende wie v1; Kanäle `kochen_laufend`
  leise, `kochen_ende` mit Alarmton; Tipp öffnet `foody://kochen/{id}?portionen={n}`). Gestartet beim ersten Timer
  und in `MainActivity.onStart` (`wennNoetigStarten`), weil ein Vordergrunddienst nicht aus dem Hintergrund starten
  darf. Benachrichtigungs-Freigabe beim ersten Timer; abgelehnt meldet der offene Kochmodus selbst (Ton, Vibration)
  und sagt das einmal.
- **Tests:** Repository, Schritte, Sitzung, Timer-Speicher und Zeitplan, ViewModel (Doppeltipp „Fertig“, Sync-
  Änderung, gelöschtes Rezept, Portionen 0/500), Bildschirm (Schrift 200 %, Drehen, Tablet), Navigation in `:app3`.
  Roborazzi-Bilder in `android/feature/kochen/src/test/screenshots/`, Vergleich in der CI.

## Woche (Phase 6c)

Spec `docs/superpowers/specs/2026-10-25-v3-phase6c-woche-design.md`. Ersetzt den Platzhalter des Tabs „Woche“. Keine
Schemaänderung.

- **`:core:data`** (`woche/`): `WocheRepository(db, rezepte, uhr)` – `woche(montag)` als Flow mit sieben `WochenTag`
  (Einträge nach Frühstück/Mittag/Snack/Abend, dann eigene Namen; `DayNutrition` je Person) und „Ø kcal/Tag“
  (`PlanDurchschnitt`); Einträge verschwinden mit ihrem Rezept (FK `CASCADE`). Ändern (`hinzufuegen` mit Portionen
  oder denen des Rezepts, `portionen` 1–99, `verschieben`, `umbenennen` – bekannte Namen als `Mahlzeit.name`, sonst
  1–30 Zeichen –, `entfernen`/`wiederherstellen` mit derselben ID, `gekocht` an/aus) setzt `updatedAt`; Outbox über
  den `meal_slot`-Trigger. „Leere Tage füllen“: `leereTage` (ab heute, ohne Eintrag dieser Mahlzeit),
  `vorschlagen` (Eingaben für `LeereTageFuellen` aus Einordnung, Favorit, „gekocht“, letztem Plan, Merkmalen über
  `Abwechslung`; belegte Nachbartage zählen), `uebernehmen` in einer Transaktion (gelöschte Rezepte übersprungen).
  Die `Clock` für Woche und Kochen kommt aus `DatenModul`.
- **`:feature:woche`**: `WocheViewModel` (Montag im `SavedStateHandle`, Tagesziel aus `TageszielSpeicher`, Füllen-
  Zustand mit Seed, „↻“ je Tag ohne doppelte Rezepte, Sperre gegen Doppeltipp beim Übernehmen). `WocheRoute`:
  „← KW 44 · 26.10.–1.11. →“ mit „Heute“, Tage als Glasflächen (kcal und Makros, mit Tagesziel `Naehrwertbalken`,
  „mind.“ bei fehlenden Werten), „+ Mahlzeit“ (Mahlzeit wählen → Rezeptauswahl mit Suche, passende zuerst),
  Mahlzeit-Blatt (Portionen, Gekocht, Kochen, Rezept ansehen, Verschieben auf Mo–So oder ±7 Tage, Umbenennen,
  Entfernen mit „Rückgängig“), Füllen-Blatt; „Leere Tage füllen“ fest über der Navigationsleiste; Blätter ganz
  geöffnet und scrollbar; ab 840 dp zwei Spalten (Mo–Do | Fr–So).
- **Tests:** Repository (Lesen, Ändern, Vorschlag), ViewModel (Blättern, Jahreswechsel KW 53/KW 1, Füllen mit
  verstellbarer Uhr), Bildschirm (Hinzufügen mit eigenem Namen, Blätter, Rückgängig, Tablet), Navigation. Roborazzi-
  Bilder in `android/feature/woche/src/test/screenshots/`, Vergleich in der CI.

## Einkauf (Phase 6d)

Spec `docs/superpowers/specs/2026-10-25-v3-phase6d-einkauf-design.md`. Ersetzt die Platzhalter des Tabs „Einkauf“ und
der Route `EinkaufListe(listeId)`. Keine Schemaänderung; gespeichert wird wie in v1 (Plan-Artikel `manual = false` mit
Menge in der Basiseinheit und Herkunft je `NeedSource`, manuelle Artikel ohne Herkunft).

- **`:core:data`** (`einkauf/`): `EinkaufRepository(db, uhr, ignoriert)`. Aktuell ist die zuletzt **angelegte** Liste
  (`createdAt DESC, id DESC`; Heute nutzt dieselbe Reihenfolge). Lesen als Flows: `liste(id)` (offene Artikel je
  Abteilung in Laufreihenfolge von `ShoppingCatalog`, Menge und Herkunft als Text), `zuletzt()` (gekaufte aller Listen,
  einer je Artikel, höchstens 18), `fruehere()`, `abgleich(id)`. Schreiben: Kacheln und Eingabe nach `EinkaufEingabe`
  (ohne Liste entsteht „Einkauf“), Details (Name 1–60, Notiz, Abteilung), Löschen/Wiederherstellen samt Herkunft,
  `ausPlanAnlegen` (über `EinkaufsBedarf` ohne Vorrat; offene manuelle Artikel der bisherigen Liste wandern mit),
  `rezeptAufListe` (über `RezeptAufListe`). **Abgleich:** die Plan-Artikel der Liste gegen den aktuellen Bedarf ihres
  Zeitraums (`ShoppingDiff`), gefiltert nach `EinkaufsAbgleich` und nach `IgnorierteSchluessel` (lokal je Liste in den
  Prefs `einkauf`: in Vorschau/Abgleich abgewählte und gelöschte Plan-Artikel). Die Liste selbst ist der alte Stand –
  der Hinweis „Plan geändert“ erscheint genau dann, wenn „Übernehmen“ etwas ändern würde.
- **`:feature:einkauf`**: `EinkaufViewModel` (angezeigte Liste, Blatt, Zeitraum und Suchtext im `SavedStateHandle`;
  ein Rechen-Auftrag für die Vorschau; Sperre gegen Doppeltipp bei „Liste anlegen“/„Übernehmen“). `EinkaufRoute`:
  Kopf mit Menü, Hinweis, „Was willst du einkaufen?“, offene Kacheln je Abteilung (Antippen = gekauft, Langdruck =
  Details), „Zuletzt verwendet“, Katalog nach Abteilungen; ab 840 dp der Katalog rechts. Blätter: Vorschau (Zeitraum
  ‹ ›, höchstens 28 Tage), Abgleich, Details mit Herkunft, Frühere Listen; Knopf fest unten.
- **`:core:designsystem`**: `Kachel` mit `aufDerListe` (rot), `onLangdruck` („Details“) und `zustandsText`;
  `KachelEmoji` für die Katalog-Emojis.
- **`:feature:rezepte`**: „Auf die Einkaufsliste“ im Rezeptdetail (Portionen-Dialog, Snackbar mit „Ansehen“ →
  `zumEinkauf()` in `:app3`).
- **Tests:** Repository (Lesen, Schreiben, Plan, Abgleich, Rezept → Liste), ViewModel, Nebenläufigkeit (Abfragen und
  Transaktionen hinter einer Schranke), Bildschirm und Blätter, Navigation. Roborazzi-Bilder in
  `android/feature/einkauf/src/test/screenshots/`, Vergleich in der CI.

## Einstellungen (Phase 6e1)

Spec `docs/superpowers/specs/2026-10-25-v3-phase6e1-einstellungen-design.md`. Das Profilsymbol in Heute öffnet die
Einstellungsseite; „Server verbinden“ ist das eigene Ziel `ServerVerbinden`. Keine Schemaänderung.

- **`:core:data`:** `DarstellungSpeicher` (`themeMode` mit den v1-Werten `SYSTEM`/`LIGHT`/`DARK`, Flow und `aktuell()`
  als Startwert), `TageszielSpeicher.setzen` (500–10.000 kcal, `null` = kein Ziel), `Sicherung.allesLoeschen()`:
  bei aktivem Sync `SyncAktiv` (auch in der Transaktion geprüft), sonst alle Fachtabellen in einer Transaktion leeren
  (derselbe Weg wie beim Wiederherstellen), eigene Fotos samt Sync-Fotoindex entfernen, Startdaten-Version
  zurücksetzen und den Startkatalog neu einspielen (`StartdatenLader`, jetzt im Konstruktor von `Sicherung`).
- **`:feature:einstellungen`:** `EinstellungenViewModel` (Darstellung und Ziel sofort speichern; Erstellen,
  Wiederherstellen, Löschen im `viewModelScope`, eine Aktion zur Zeit, Ergebnis als Meldung) und `EinstellungenRoute`
  (Konto, Darstellung, Tagesziel, Sicherung, Daten, Hinweise; Dateidialoge über SAF in der Route, für Tests als
  `Dateiwahl` austauschbar). Wiederherstellen und Löschen fragen nach und sind bei verbundenem Sync gesperrt.
- **`:app3`:** `MainActivity` zeichnet nach `DarstellungSpeicher` (`istDunkel`), Änderungen wirken sofort;
  `SystemleistenPassend` richtet die Symbole von Status- und Navigationsleiste nach der gewählten Darstellung (wie v1).
- **Tests:** Speicher, Löschen (Fotoindex, Sync-Sperre, Startkatalog), ViewModel (Doppeltipp per Mutation geprüft),
  Bildschirm, Navigation, Darstellung. Roborazzi-Bilder in `android/feature/einstellungen/src/test/screenshots/`,
  Vergleich in der CI.

## Zutatenkatalog (Phase 6e2)

Spec `docs/superpowers/specs/2026-10-25-v3-phase6e2-zutaten-design.md`. Einstellungen → „Zutatenkatalog“ (Ziele
`Zutaten` und `Zutat(id)`). Keine Schema- und keine Protokolländerung.

- **`:core:data/zutaten`:** `ZutatenRepository` (Liste mit Rezeptzahl, anlegen, speichern, löschen, zusammenführen,
  „Namen vereinheitlichen“ mit Vorschau). Reine Funktionen: `filtern` (Suche auch über `IngredientCatalog.canonicalName`,
  Filter „Ohne Nährwerte“, „Eigene“, „Unbenutzt“, „Mit Strichcode“), `ZutatEntwurf`/`pruefen` (Editor als Texte,
  Prüfregeln nur für geänderte Felder), `angleichungen` (wie v1 `harmonizeNames`). Speichern schreibt nur geänderte
  Felder auf den aktuellen Stand der Zeile: unberührtes kcal-Feld lässt kJ exakt, ein Sync-Stand dazwischen bleibt.
  Geänderte Nährwerte ohne eigene Quelle bekommen „Eigene Angabe“.
- **Gemeinsames Zusammenführen:** `ergaenztUm` (Lücken füllen wie v1 `fillFrom`) und `zusammenfuehrenSchritte`
  (Rezeptzeilen, Einkaufsartikel, Tagebuch umhängen, Quelle löschen, Ziel ergänzen) nutzen Katalog, `SyncSpeicher`
  (Namenskollision beim Anwenden) und `StartdatenLader` gleichermaßen.
- **Startdaten:** Der Lader überschreibt Kategorie, Dichte, Stückgewicht und Strichcode einer Startzutat nicht mehr;
  bearbeitete Nährwerte gelten über die Quelle als verändert. Bekannte Grenze: Eine gelöschte oder weg-zusammengeführte
  Startzutat kehrt beim nächsten Anstieg von `Startdaten.VERSION` zurück (unbenutzt).
- **Sync auf dem zweiten Gerät:** `anwenden` schreibt erst Datensätze, dann Löschungen; mit offener Rezeptänderung wird
  die Quelle wiederbelebt (`nochGebraucht`). Belegt durch zwei Tests in `SyncLaufTest`.
- **`:feature:zutaten`:** Liste (`ZutatenListeViewModel`), Editor (`ZutatEditorViewModel`, `ZielwahlBlatt`), Meldungen
  vom Editor an die Liste über `ZutatenMeldungen`. „Änderungen verwerfen?“ liegt jetzt als `VerwerfenFrage` im
  Designsystem. Knöpfe und Snackbars halten `UEBER_LEISTE` (88 dp) Abstand zur schwebenden Navigationsleiste.
- **Tests:** Repository, reine Funktionen, Startdaten, Zwei-Geräte-Sync, ViewModels, Bildschirme, Navigation;
  Roborazzi-Bilder in `android/feature/zutaten/src/test/screenshots/`, Vergleich in der CI.

## Packung scannen (Phase 6e3)

ADR 0013, Spec `docs/superpowers/specs/2026-10-26-v3-phase6e3-scan-design.md`. Scannen im Tagebuch („Frei“), im
Zutateneditor und im Dialog „Neue Zutat“; Werte werden nur vorausgefüllt und markiert, Speichern ist die Bestätigung.
Keine Schema- und keine Protokolländerung (nur die Abfrage `ZutatDao.mitStrichcode`).

- **`:core:data/zutaten`:** `Packungswerte` (Name, Basis oder `null`, Werte je 100 g/ml, Strichcode, Quelle; `kcalText`,
  `feldText`). `ZutatEntwurf.mitPackung` übernimmt Zahlen, Basis, Quelle und einen fehlenden Strichcode und nennt die
  markierten Felder. `ZutatenRepository.speichern(…, scan)`: unverändertes kcal-Feld → genaue kJ der Packung, Scan-Quelle
  gilt nicht als „Eigene Angabe“; `anlegen(name, packung)` mit Werten und freiem Strichcode. `zutatMitNamen` (roher, dann
  kanonischer Name) für Tagebuch und „Neue Zutat“. `TagebuchRepository.ausPackung` in einer Transaktion: ohne Häkchen
  „Frei“ mit den Werten der Menge, mit Häkchen Zutat anlegen oder als Block aktualisieren, dann Eintrag „Zutat“.
- **`:core:scan`:** Ablauf `PackungScan` wie v1 (Strichcode → eigener Katalog → Open Food Facts, wenn erlaubt → Foto der
  Nährwerttabelle, ML Kit) hinter Schnittstellen, die `ScanModul` bindet und Tests durch Fakes ersetzen; dazu
  `online(code)` für „Online abrufen“. `OffProduktSuche` (eigener Ktor-Client, 8 s, nur Produktnummer und User-Agent mit
  der App-Version aus `ScanKonfiguration`), `GooglePlayDienste` (vorladen, Module nachladen), `ScanEinstellung`
  („Online-Produktsuche“, v1-Schlüssel `onlineProduktsuche`, Standard an). `PackungScanKnopf` mit Menü „Strichcode“,
  „Foto der Nährwerttabelle“, „Aus der Galerie“, Meldungen und Foto-Knöpfen darunter; der Ablauf liegt in `ScanLauf`,
  den das ViewModel hält: Strichcode und Erkennung laufen im `viewModelScope` (brechen beim Drehen nicht ab), der
  `ScanKnopfZustand` liegt im `SavedStateHandle` (übersteht ein Prozessende). Kamerafoto kurz in `cache/scan/` über
  `KameraDatei` (`:core:data`). Antworten von Open Food Facts höchstens 256 kB. Ohne Play-Dienste ausgegraut.
- **`:core:designsystem`:** `vorausgefuelltFarben` – Rahmen und Beschriftung markierter Felder in der Akzentfarbe.
- **`:feature:heute`:** Scan-Knopf über „Frei“; nach einem Treffer `PackungEingabe` (Name, Menge in g/ml nach der Basis,
  ohne Basis Chips, Werte je 100 markiert, Vorschau „= 359 kcal“, Häkchen „Als Zutat speichern“ bzw. „Zutat „…“
  aktualisieren“, „Zurück zu Frei“). „Im Katalog“ wechselt zu „Zutat“ mit Fokus auf der Menge.
- **`:feature:zutaten`:** Editor mit Scan-Knopf im Abschnitt „Nährwerte“, Markierung endet beim Ändern; „Im Katalog“
  mit dieser Zutat → „Online abrufen“, mit einer anderen → Zusammenführen. „Neue Zutat“ mit Scan-Knopf; Packung bzw.
  Meldung gehen über `ZutatUebergabe` an den Editor.
- **`:feature:einstellungen`:** Schalter „Online-Produktsuche“ im Abschnitt „Zutaten“, wirkt sofort für alle Einstiege.
- **Tests:** Packungswerte, Repositories, Namenssuche, Ablauf, Open Food Facts (`MockEngine`), Einstellung,
  Knopf-Zustand, ViewModels und Bildschirme mit einem Fake-Scan ohne Play-Dienste; Roborazzi-Bilder „Frei“ mit Packung
  (hell/dunkel), Editor mit markierten Werten und „Neue Zutat“ nach einem Scan, Vergleich in der CI.

## Packung scannen (seit DB v9)

v1 (`:app`); die V3 nutzt `:core:scan` (oben). `PackungScan` (`android/app/.../scan`) steuert den Ablauf hinter Schnittstellen, die Hilt in `ScanModule` bindet und Tests durch
Fakes ersetzen: `StrichcodeLeser` (Google Code Scanner) → `KatalogSuche` (`ingredient.barcode`) → `ProduktSuche`
(`OffProduktSuche`, Open Food Facts, nur wenn `ScanPreferences.onlineSuche`) → sonst `TabellenScanner` (ML Kit).
Fehlende Play-Dienste-Module werden über `ModuleInstallClient` nachgeladen. Die Auswertung ist rein: `NaehrwertScan`
(`domain`, OCR-Zeilen → Werte je 100 g/ml) und `OpenFoodFacts.auswerten` (JSON → `Packung`). Die Oberfläche
(`PackungScanKnopf`) füllt nur vor; Speichern übernimmt (Zutaten-Dialog, Tagebuch-Reiter „Frei“). ADR 0008.

Hinweis: Room setzt `recursive_triggers = 1`; jeder Trigger-Rumpf muss seine eigene WHEN-Bedingung falsch machen
(`MAX(jetzt, alt + 1)`). `OnConflictStrategy.REPLACE` auf Wurzeltabellen würde `sync_*_ad` auslösen und eine Löschung
vormerken – stattdessen `@Upsert`.

## Startdaten

`SeedData` enthält generische Zutaten mit gerundeten Nährwerten. Der Zähler `SeedData.VERSION` (in
SharedPreferences `seedVersion`) sorgt dafür, dass Erweiterungen auch bestehende Installationen erreichen.
Einträge, die noch unverändert aus den Startdaten stammen (`nutrientSource` = „Näherungswert (Startdaten)“),
übernehmen Korrekturen; vom Nutzer bearbeitete Werte bleiben unangetastet.

## Build

AGP 9 mit eingebautem Kotlin (kein `org.jetbrains.kotlin.android`-Plugin) und neuer DSL, ohne
Kompatibilitäts-Flags in `gradle.properties`. Gradle braucht JDK 21+ (z. B. das JBR von Android Studio): Robolectric startet Android-SDK 35+ nur
unter Java 21. Der Bytecode bleibt Java 17.

**Convention-Plugins** (`build-logic/`, eingebunden über `pluginManagement { includeBuild(...) }`; der Docker-Build des
Servers lässt sie mit `FOODY_SERVER_ONLY` weg): `foody.android.application` (`:app`, `:app3`, `:katalog`),
`foody.android.library` (`:core:*` ohne Compose: `:core:data`, `:core:sync`, `:core:testing`) und
`foody.android.feature` (Bibliothek + Compose + Test-Abhängigkeiten samt `:core:testing`): alle `:feature:*` sowie
`:core:designsystem` und `:core:scan`. Sie setzen die
SDK-Stände aus `FoodySdk` (`compileSdk` 37, `minSdk` 36, `targetSdk` 37), Java 17 und `allWarningsAsErrors`. Die
Plugins von AGP und Kotlin stehen im Root-Projekt (`apply false`); `build-logic` braucht sie nur zum Kompilieren
(`compileOnly`). Ein `:feature:x` liegt unter `android/feature/x`; das Elternprojekt `:feature` zeigt auf
`android/feature`.
