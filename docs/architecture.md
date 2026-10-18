# Architektur

**Module und Ordner:** Handy-App unter `android/` (`:app`, `:baselineprofile`, `:feature:*`), gemeinsamer Code unter
`shared/` (`:domain`, `:sync-protocol`, `:api-protocol`), Server unter `server/` – `:server-sync` (API, Konten,
Datenbank, Sync, Fotos, KI-Gerüst) und `:server` (nur `main`). Abhängigkeiten laufen nur in eine Richtung:
`:server` → `:server-sync` → `:api-protocol`, `:app` → `:domain`, `:sync-protocol`. `:sync-protocol` (Protokoll 2)
ist für die v1-App eingefroren und entfällt in Phase 8; der Server nutzt nur `:api-protocol` (Protokoll 3). Gradle-Namen
sind unabhängig von den Ordnern (`projectDir` in `settings.gradle.kts`). Spec:
`docs/superpowers/specs/2026-10-11-projektstruktur-design.md`.

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
  Konto und Stunde, `FOODY_KI_PRO_STUNDE`, Standard 30). Noch kein Modellaufruf und keine Modellwahl (Phase 7).
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
- **Datenbank `foody.db`, Version 10** (`FoodyDatenbank`, Schemata 1–9 aus der v1 unter `android/core/data/schemas`):
  Schema 9 ohne `pantry_item`, `sync_state.kontoId` (nullable) statt `householdId`; ein Schema-Vergleichstest erzwingt,
  dass sonst alles gleich ist. `baueDatenbank` registriert nur `MIGRATION_9_10` und den Callback für neue Datenbanken,
  **nie** einen destruktiven Fallback. Die Migration (eine Transaktion) entfernt Vorrat samt Triggern und
  `pantry_item`-Zeilen in Outbox/Revisionen/Problemen, baut `sync_state` mit `kontoId = NULL` neu (Cursor, `active`,
  `serverUrl`, `lastSyncAt`, `lastError` bleiben) und ersetzt alle Trigger durch `SyncTrigger.anweisungen`. Ungesendete
  Outbox-Einträge (auch Löschungen) bleiben und gehen danach über Protokoll 3 hinaus.
- **Sync-Trigger:** wie v1 für `ingredient`, `recipe`, `meal_slot`, `shopping_list`, `shopping_item`,
  `tagebuch_eintrag` und die Kindtabellen (`recipe_ingredient`, `instruction_step` → Rezept; `shopping_item_source` →
  Einkaufsartikel); nur bei `active = 1` und `applyingRemote = 0`. `@Upsert` statt `REPLACE`.
- **Sync-Lauf** (`SyncLauf`, ein Lauf zur Zeit unter `SyncSpeicher.laufSperre`, die auch Trennen und „Ersetzen“
  nehmen): Outbox in Paketen (≤ 500 Datensätze, ≤ 5 MB, höchstens 20 Pakete je Lauf, `413` → Paket halbieren, einzelner
  zu großer Datensatz → Problem `too_large`), fehlende eigene Fotos vorher hochladen; Ergebnisse verbuchen (angenommen,
  zusammengeführt – auch in eine andere ID –, abgelehnt → `sync_problem`, fehlende Verweisziele nachreichen); alle
  Pull-Seiten holen und erst dann anwenden (`applyingRemote = 1`, je Datensatz eine Transaktion, Cursor zuletzt; lokale
  Änderungen mit offenem Outbox-Eintrag gewinnen); `410` → Voll-Abgleich, der Unsendetes, Abgelehntes, Verwiesenes und
  noch Gebrauchtes behält; zuletzt gewünschte Fotos laden. Header `X-Foody-Protocol: 3`, Routen unter `/api/v3`
  (`KtorServerZugang` über OkHttp, Antworten begrenzt gelesen, keine Weiterleitungen).
- **Auslöser** (`SyncAusloeser`, `SyncAuftrag`): einmaliger WorkManager-Auftrag mit Netz-Bedingung; sofort bei
  Vordergrund und „Jetzt synchronisieren“, nach Outbox-Änderungen entprellt um 5 s. Kein periodischer Auftrag; der
  v1-Auftrag `foody-sync-periodic` wird beim Start abgemeldet. Ein laufender Auftrag wird nie ersetzt, sondern plant
  einen Nachlauf. Nur vorübergehende Fehler werden mit Backoff wiederholt.
- **Fehlerkennungen** in `sync_state.lastError` (nur diese festen Werte, nie Token oder Bodies): `offline`,
  `anmeldung_abgelaufen` (`401`, kein lesbares Token – das Token wird dabei nicht gelöscht), `app_zu_alt`
  (`409 protocol_too_old`), `server_zu_alt` (`409 server_too_old` oder `404`, z. B. Server ohne `/api/v3`), `cursor_haengt`,
  `server_fehler` (5xx, unlesbare Antworten, lokale Fehler). Die Anmeldung meldet der Oberfläche zusätzlich
  `ungueltige_adresse`, `falsche_zugangsdaten`, `zu_viele_versuche`, `token_nicht_gespeichert` (nicht in `lastError`).
- **Verbinden/Trennen** (`SyncKonto`): Adresse prüfen (`https`; Debug zusätzlich `http` für `10.0.2.2`/`localhost`),
  anmelden (nur Token speichern), erst `aktivieren` schaltet den Sync ein (Cursor 0). „Zusammenführen“ merkt alle lokalen
  Datensätze vor. „Ersetzen“ verwirft Rezepte, Plan, Einkaufslisten, Tagebuch, eigene Fotos und Sync-Vermerke, **behält
  aber die Zutaten**: Sie sind großteils Startzutaten, der Server führt gleichnamige Zutaten zusammen, und ein fehlendes
  Verweisziel reicht der Sync bei Bedarf nach. Folge: Eine nur lokal angelegte, nie gesendete Zutat bleibt lokal, bis
  sie geändert oder von einem gesendeten Datensatz gebraucht wird (Hinweis in der Oberfläche: Phase 6). Trennen wartet auf einen laufenden Lauf, meldet beim Server ab (falls erreichbar), löscht Token und
  Sync-Tabellen; lokale Daten bleiben.
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
  Token des Handys liegt im Hardware-Keystore und ist nicht kopierbar. Die Token-Kontinuität belegen der Formattest
  `TokenSpeicherTest.gleicheAblageWieVEins` und `SyncLaufTest.alterTokenUndCursorReichen`; „ohne neue Anmeldung“ auf dem Gerät prüft
  erst Phase 8. Echte Datenbanken (`*.foody-echt.db*`) kommen nie ins Repo.

## Packung scannen (seit DB v9)

`PackungScan` (`android/app/.../scan`) steuert den Ablauf hinter Schnittstellen, die Hilt in `ScanModule` bindet und Tests durch
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
Servers lässt sie mit `FOODY_SERVER_ONLY` weg): `foody.android.application` (`:app`), `foody.android.library`
(`:core:*`) und `foody.android.feature` (`:feature:*`, Bibliothek + Compose + Test-Abhängigkeiten). Sie setzen die
SDK-Stände aus `FoodySdk` (`compileSdk` 37, `minSdk` 36, `targetSdk` 37), Java 17 und `allWarningsAsErrors`. Die
Plugins von AGP und Kotlin stehen im Root-Projekt (`apply false`); `build-logic` braucht sie nur zum Kompilieren
(`compileOnly`). Ein `:feature:x` liegt unter `android/feature/x`; das Elternprojekt `:feature` zeigt auf
`android/feature`.
