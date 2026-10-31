# Sync Etappe 4 (Fotos) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eigene Rezeptfotos werden mit synchronisiert: Ein auf dem Tablet fotografiertes Rezept erscheint samt Foto auf dem Handy.

**Architecture:** Fotos werden über den SHA-256 ihres JPEG-Inhalts adressiert (`RecipePayload.photo`). Der Server speichert sie je Haushalt als Dateien, prüft Hash/Größe/JPEG-Signatur und räumt nicht mehr referenzierte Fotos nach 30 Tagen auf. Die App merkt sich je lokaler Fotodatei ihren Hash (`sync_photo_local`) und je Rezept ein noch fehlendes Foto (`sync_photo_wanted`). Vor dem Push lädt `SyncEngine` fehlende Fotos hoch, nach dem Pull lädt sie gewünschte Fotos herunter und verknüpft sie mit dem Rezept – ohne Outbox-Echo.

**Tech Stack:** wie Etappe 1–3 (Ktor Server/Client 3.5.0, Room, Robolectric für JVM-Tests), `java.security.MessageDigest`.

**Spec:** `docs/superpowers/specs/2026-10-04-sync-server-design.md` §5 (Fotos), §4.2 Punkt 1/3, §7.

## Entscheidungen

- Nur eigene Fotos (`file:`-Links im Fotoordner `files/recipe_images/` von `RecipePhotoStore`) werden übertragen. Ein Rezept mit `content:`-Link (Galerie einer anderen App) sendet `photo = null`; beim Anwenden eines Server-Datensatzes mit `photo = null` bleibt ein lokaler `content:`-Link erhalten, ein eigenes Foto wird entfernt (Verknüpfung gelöst; die Datei räumt `RecipePhotoStore.pruneUnused` wie bisher auf).
- Hash-Cache je Datei mit Größe und Änderungszeit: `RecipePhotoStore.shrink`/`shrinkAll` ersetzt Dateien – ein geänderter Inhalt wird so erkannt und neu gehasht.
- Ein Foto, das der Server (noch) nicht hat (404 beim Download), bleibt in `sync_photo_wanted` und wird beim nächsten Lauf erneut versucht; das Rezept bleibt solange ohne bzw. mit altem Foto.
- Der Server lehnt Rezepte mit unbekanntem Foto-Hash **nicht** ab (Upload und Push sind getrennte Aufrufe; der Client lädt vorher hoch).

## Global Constraints

- Foto-Hash: Kleinbuchstaben-Hex, 64 Zeichen, `^[0-9a-f]{64}$`; `RecipePayload.photo` ist `null` oder ein solcher Hash (`PayloadValidator` prüft das).
- Endpunkte (authentifiziert, Protokoll-Header): `POST /api/v1/photos/missing` Body `PhotosMissingRequest(hashes: List<String>)` (höchstens 500, jeder gültig) → `PhotosMissingResponse(missing: List<String>)`; `PUT /api/v1/photos/{sha256}` Body `image/jpeg` → 204 (bereits vorhanden → 204, idempotent); `GET /api/v1/photos/{sha256}` → 200 `image/jpeg` oder 404.
- Server-Prüfungen beim Upload: Pfadsegment `^[0-9a-f]{64}$` (sonst 400 `invalid_input`), Größe ≤ **10 MB** gezählt beim Lesen (sonst 413 `too_large`), JPEG-Signatur `FF D8 FF` (sonst 400 `invalid_input`), SHA-256 des Inhalts = Pfad (sonst 400 `invalid_input`). Ablage `<photoDir>/<householdId>/<sha>.jpg` über temporäre Datei + atomares Umbenennen. Haushalt immer aus dem Gerät.
- Server-Konfiguration `FOODY_PHOTO_DIR` (Standard `/data/photos`).
- Aufräumen: in `Compactor.run()` – Fotos, die in keinem lebenden `recipe`-Datensatz des Haushalts als `photo` stehen und deren Datei älter als **30 Tage** ist, werden gelöscht.
- App-Datenbank **v6**: `sync_photo_local(uri TEXT NOT NULL PRIMARY KEY, sha256 TEXT NOT NULL, size INTEGER NOT NULL, modifiedAt INTEGER NOT NULL)` mit Index auf `sha256`; `sync_photo_wanted(recipeId TEXT NOT NULL PRIMARY KEY, sha256 TEXT NOT NULL)`. Nur Tabellen, SQLite-3.18-kompatibel, Migrationstest.
- Downloads werden über `RecipePhotoStore.newPhotoFile()` gespeichert (temporär schreiben, Hash prüfen, dann umbenennen); das Rezept-`imageUri` wird mit `applyingRemote = 1` gesetzt (kein Outbox-Echo).
- Fotos laden höchstens 10 MB je Datei; Upload/Download-Fehler: IO/5xx → `Transient`, 404 beim Download → bleibt „gewünscht“.

## Review Focus

1. **Foto auf Gerät A ersetzt** (neues Foto statt altem): B zeigt danach das neue Foto, nicht das alte → Test in Task 4.
2. **Rezept mit Galerie-Link (`content:`) auf Gerät A**: A verliert seinen Galerie-Link nicht durch den eigenen Rückweg über den Server → Test in Task 3.
3. **Foto wird beim Start verkleinert (`shrinkAll`)**: geänderter Inhalt → neuer Hash wird hochgeladen, nicht der alte → Test in Task 2.
4. **Server hat das Foto noch nicht** (anderes Gerät hat den Upload abgebrochen): Rezept kommt ohne Foto an, das Foto wird später nachgeladen → Test in Task 4.
5. **Ungültiger Hash im Pfad** (`../`, Großbuchstaben, 63 Zeichen): 400 ohne Dateizugriff → Test in Task 1.

---

## Dateistruktur

```
sync-protocol/.../Photos.kt (neu), PayloadValidator.kt (photo-Format)
server/.../photos/PhotoStore.kt, PhotoRoutes.kt (neu); ServerConfig.kt (photoDir); ServerDeps.kt; Application.kt; sync/Compactor.kt
server/src/test/.../PhotoTest.kt
app/.../data/db/SyncEntities.kt, SyncDao.kt, FoodyDatabase.kt (v6, MIGRATION_5_6); app/schemas/.../6.json
app/.../sync/PhotoIndex.kt (neu: Hash je Datei, Lookup je Hash)
app/.../sync/SyncApi.kt, KtorSyncApi.kt (Foto-Aufrufe)
app/.../sync/SyncMapper.kt, SyncLocalStore.kt, SyncApplier.kt (photo-Feld)
app/.../sync/SyncEngine.kt (Upload vor Push, Download nach Pull)
Tests: app/src/test/.../PhotoIndexTest.kt, SyncEngineTest.kt, SyncEndToEndTest.kt, KtorSyncApiTest.kt; androidTest MigrationTest.kt, SyncApplierTest.kt
docs/architecture.md, docs/security.md, server/README.md
```

---

### Task 1: Protokoll + Server – Foto-Endpunkte, Prüfung, Aufräumen

**Files:** Create `sync-protocol/src/main/kotlin/de/foody/sync/protocol/Photos.kt`, `server/src/main/kotlin/de/foody/server/photos/PhotoStore.kt`, `PhotoRoutes.kt`, `server/src/test/kotlin/de/foody/server/PhotoTest.kt`; Modify `PayloadValidator.kt` (+ Test), `ServerConfig.kt`, `ServerDeps.kt`, `Application.kt`, `sync/Compactor.kt`, `server/README.md`, `docs/security.md`

**Interfaces:**
- Produces: `@Serializable data class PhotosMissingRequest(val hashes: List<String>)`, `@Serializable data class PhotosMissingResponse(val missing: List<String>)`, `object PhotoHash { val REGEX: Regex; fun isValid(h: String): Boolean; fun of(bytes: ByteArray): String }`, `const val Protocol.MAX_PHOTO_BYTES = 10_485_760L`; server `class PhotoStore(root: Path) { fun exists(household, sha): Boolean; fun read(household, sha): Path?; fun write(household, sha, bytes: ByteArray); fun listHashes(household): List<Pair<String, Instant>>; fun delete(household, sha) }`; `ServerConfig.photoDir` (`FOODY_PHOTO_DIR`, Standard `/data/photos`).

- [ ] **Step 1: Failing tests** (`PhotoTest`, `testApplication`, Foto-Ordner = JUnit-Temp-Verzeichnis): `uploadThenDownloadRoundTrip`; `uploadIsIdempotent`; `missingListsOnlyUnknownHashes`; `wrongHashIsRejected` (Inhalt ≠ Pfad → 400); `nonJpegIsRejected`; `tooLargeIsRejected` (10 MB + 1 → 413); `invalidPathSegmentsAre400` (`"../x"` url-kodiert, 63 Zeichen, Großbuchstaben) und nichts außerhalb des Foto-Ordners entsteht (Review Focus 5); `householdsCannotReadEachOthersPhotos` (B holt A's Hash → 404); `compactorRemovesUnreferencedOldPhotos` (Foto ohne lebendes Rezept, Datei-Zeit 31 Tage alt → gelöscht; referenziertes oder jüngeres bleibt). `PayloadValidatorTest`: `photoMustBeNullOrLowercaseHex64`.
- [ ] **Step 2: Run** `./gradlew :sync-protocol:test :server:test` → FAIL.
- [ ] **Step 3: Implementieren** (Upload-Body mit gezähltem Lesen wie Push, Limit `MAX_PHOTO_BYTES + 1`; Pfade nur aus validiertem Hash bilden; README/security.md: Foto-Volume, Prüfungen).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync-Server: Fotos hochladen, abrufen und aufräumen"`

### Task 2: App – Datenbank v6, `PhotoIndex`, Foto-Aufrufe im Client

**Files:** Modify `SyncEntities.kt`, `SyncDao.kt`, `FoodyDatabase.kt`; Create `app/src/main/java/de/foody/app/sync/PhotoIndex.kt`; Modify `SyncApi.kt`, `KtorSyncApi.kt`; Tests `MigrationTest` (Instrumented), `PhotoIndexTest` (Robolectric), `KtorSyncApiTest`

**Interfaces:**
- Produces: Entities `SyncPhotoLocalEntity(uri, sha256, size, modifiedAt)`, `SyncPhotoWantedEntity(recipeId, sha256)`, DAO-Methoden dafür; `MIGRATION_5_6`; `@Singleton class PhotoIndex @Inject constructor(db: FoodyDatabase, photoStore: RecipePhotoStore)` mit `suspend fun hashOf(imageUri: String?): String?` (nur eigene `file:`-Fotos im Fotoordner, die existieren; Cache nach Größe + Änderungszeit, sonst neu hashen und speichern), `suspend fun uriFor(sha256: String): String?` (nur wenn Datei existiert und Cache-Eintrag aktuell); `RecipePhotoStore` bekommt `fun isOwnPhoto(uri: String): Boolean` und `fun fileOf(uri: String): File?`. `SyncApi`: `suspend fun photosMissing(hashes: List<String>): List<String>`, `suspend fun uploadPhoto(sha256: String, bytes: ByteArray)`, `suspend fun downloadPhoto(sha256: String): ByteArray?` (404 → `null`).
- [ ] **Step 1: Failing tests:** `MigrationTest.migrate5To6AddsPhotoTables`; `PhotoIndexTest`: `hashMatchesSha256OfFile`, `contentUriHasNoHash`, `missingFileHasNoHash`, `changedFileIsRehashed` (Datei überschreiben, andere Größe/Zeit → neuer Hash; Review Focus 3), `uriForFindsCachedFile`; `KtorSyncApiTest`: Upload/Download/Missing gegen den echten Server, 404 → `null`.
- [ ] **Step 2–4:** Run rot → implementieren → `:app:testDebugUnitTest`, `MigrationTest` (Emulator) grün.
- [ ] **Step 5: Commit** `git commit -m "Sync: Foto-Index und Foto-Aufrufe in der App"`

### Task 3: Fotos in Abbildung, Push-Datensätzen und Anwenden

**Files:** Modify `SyncMapper.kt`, `SyncLocalStore.kt`, `SyncApplier.kt`; Tests `SyncMapperTest`, `SyncLocalStoreTest`/`SyncApplierTest` (Instrumented)

**Interfaces:**
- `SyncMapper.recipe(r, lines, steps, photo: String?)` setzt `photo`; `SyncMapper.recipe(id, p, updatedAt, existing)` liefert `RecipeParts` mit `imageUri` nach der Regel unten und zusätzlich `wantedPhoto: String?`.
- `SyncLocalStore.pendingBatch` füllt `photo = photoIndex.hashOf(recipe.imageUri)`.
- `SyncApplier` beim Anwenden eines Rezepts: `photo == null` → `content:`-Link bleibt, eigenes Foto wird entfernt (`imageUri = null`), `sync_photo_wanted` für das Rezept gelöscht; `photo != null` → `uriFor(photo)` vorhanden → `imageUri = uri`, Wunsch gelöscht; sonst `imageUri` unverändert lassen und `sync_photo_wanted(recipeId, photo)` setzen. Gelöschte Rezepte entfernen ihren Wunsch.
- [ ] **Step 1: Failing tests:** `SyncMapperTest.recipePhotoRoundTrip`; `SyncLocalStoreTest.pendingRecipeCarriesPhotoHash` (eigenes Foto) / `galleryLinkSendsNoPhoto`; `SyncApplierTest`: `knownPhotoIsLinked`, `unknownPhotoIsWanted`, `nullPhotoKeepsGalleryLink` (Review Focus 2), `nullPhotoRemovesOwnPhoto`, `deletedRecipeDropsWish`.
- [ ] **Step 2–4:** rot → implementieren → grün (JVM + Instrumented einzeln).
- [ ] **Step 5: Commit** `git commit -m "Sync: Foto-Hash in Rezepten senden und anwenden"`

### Task 4: `SyncEngine` – Fotos hoch- und herunterladen, Ende-zu-Ende

**Files:** Modify `SyncEngine.kt`, `SyncEngineFactory` (falls `PhotoIndex`/`RecipePhotoStore` gebraucht), Tests `SyncEngineTest`, `SyncEndToEndTest`; `docs/architecture.md`

**Interfaces:** `SyncEngine` bekommt `PhotoIndex` und `RecipePhotoStore` (Konstruktor). Ablauf: vor jedem Push-Batch Hashes der Rezept-Datensätze sammeln → `photosMissing` → jeden fehlenden mit `uploadPhoto` hochladen (Datei über `PhotoIndex`/`RecipePhotoStore` lesen; fehlt die Datei inzwischen → Rezept trotzdem senden). Nach dem Pull (auch Voll-Abgleich): je `sync_photo_wanted` → `downloadPhoto`; `null` → bleibt gewünscht; Bytes → Hash prüfen (falsch → verwerfen, Problem `photo_mismatch`), über `newPhotoFile()` + Umbenennen speichern, `sync_photo_local` eintragen, Rezept-`imageUri` in einer Transaktion mit `applyingRemote = 1` setzen, Wunsch löschen. Download-/Upload-Fehler → wie andere `Transient`-Fehler.
- [ ] **Step 1: Failing tests:** `SyncEngineTest`: `missingPhotosAreUploadedBeforePush`, `wantedPhotoIsDownloadedAndLinkedWithoutOutboxEcho`, `photoNotOnServerStaysWanted` (Review Focus 4), `corruptDownloadIsRejected`; `SyncEndToEndTest`: `photoTravelsBetweenDevices` (A fotografiert → B hat Datei mit gleichem Hash und `imageUri`), `replacedPhotoReplacesOnOtherDevice` (Review Focus 1); alle mit `assertClean`.
- [ ] **Step 2–4:** rot → implementieren → `:sync-protocol:test :server:test :domain:test :app:testDebugUnitTest :app:assembleRelease` grün.
- [ ] **Step 5: Commit** `git commit -m "Sync: Fotos zwischen Geräten übertragen"`

## Abschluss
- [ ] Gesamtlauf JVM + Instrumented (Emulator 5558) + Release-Build grün; PR gegen `main` (Etappe 4 von 5) mit Auto-Merge.
