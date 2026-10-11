# Architektur

**Module und Ordner:** Handy-App unter `android/` (`:app`, `:baselineprofile`), gemeinsamer Code unter `shared/`
(`:domain`, `:sync-protocol`), Server unter `server/` – `:server-sync` (API, Konten, Datenbank, Sync), `:server-web`
(Web-Oberfläche) und `:server` (nur `main`). Abhängigkeiten laufen nur in eine Richtung:
`:server` → `:server-web` → `:server-sync` → `:sync-protocol`, `:server-web` → `:domain`, `:app` → `:domain`,
`:sync-protocol`. Die Web-Oberfläche dockt über `foodyModule(deps, erweiterungen)` an; der Sync-Server kennt sie nicht.
Gradle-Namen sind unabhängig von den Ordnern (`projectDir` in `settings.gradle.kts`). Spec:
`docs/superpowers/specs/2026-10-11-projektstruktur-design.md`.

- **UI-Schicht** (`app/.../ui`): Compose-Screens + `@HiltViewModel`. Jeder Screen rendert einen unveränderlichen
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
- **Data** (`app/.../data`): Room als Single Source of Truth, Repositories mappen Entities ↔ Domain.
  Schreibvorgänge über mehrere Tabellen laufen in Transaktionen (Rezept speichern, Listen-Snapshot,
  Diff anwenden, „gekocht“, Rezept auf Einkaufsliste, Zutaten zusammenführen/vereinheitlichen).

## Navigation

Typisierte Routen (Navigation Compose + kotlinx.serialization). `NavigationSuiteScaffold` wählt automatisch
Bottom Bar (Telefon) oder Navigation Rail (Tablet). Jeder Hauptbereich behält seinen eigenen Verlauf
(`saveState`/`restoreState`): Wer von einer Rezeptdetailseite zum Einkauf wechselt und zurück, landet wieder dort.

## Datenbank

Version **6**, Schema-Export nach `app/schemas` (eingecheckt). Jede Schemaänderung benötigt eine `Migration`
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

## Packung scannen (seit DB v9)

`PackungScan` (`app/scan`) steuert den Ablauf hinter Schnittstellen, die Hilt in `ScanModule` bindet und Tests durch
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
Kompatibilitäts-Flags in `gradle.properties`. Gradle braucht JDK 17+ (z. B. das JBR von Android Studio).
