# Web-Oberfläche Teil 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rezepte im Browser anlegen, bearbeiten, archivieren und per Markdown-Upload importieren – mit den Regeln der App und ohne Verlust von Feldern oder gleichzeitigen Änderungen.

**Architecture:** Wie Teil 1 im Modul `:server-web`. Schreiben nur über `SyncService.push`; das Rezept wird auf dem gespeicherten rohen JSON aufgebaut (nur Browser-Felder ersetzt). Konflikte erkennt der Browser selbst über die gesehene Revision. Die Import-Rechnung wandert nach `shared/domain` (`RezeptImport`) und wird von App und Server genutzt.

**Tech Stack:** Ktor 3.5.0 (html-builder, multipart), kotlinx.html, htmx 2.0.4, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-11-web-oberflaeche-teil2-design.md`

## Global Constraints

- Schreiben nur über `SyncService.push`; nur Datensätze des eigenen Haushalts (fremde ID → 404).
- Browser-Felder des Rezepts: `name`, `servings`, `prep`, `cook`, `notes`, `tags`, `sourceUrl`, `lines`, `steps`, `mahlzeiten`, `gaenge` – alles andere im rohen JSON bleibt.
- Grenzen: Name ≤ 200, Portionen 1–99, Zeiten 0–1440 Minuten, Mengen über `ZahlGrenzen` und ≥ 0; Import höchstens 50 Dateien, je 1 MB (1 048 576 Bytes), Endungen `.md`, `.markdown`, `.txt`; Vorgabe-Portionen 4, Vorgabe-Tag „Import“.
- CSRF wie Teil 1 auf allen POSTs mit Sitzung; Login/Registrieren mit „gleiche Website“-Prüfung (403 bei `Sec-Fetch-Site` ∉ {`same-origin`, `none`} oder `Origin`-Host ≠ `Host`).
- Texte exakt: „Inzwischen geändert (z. B. am Handy)“, „Aktuellen Stand laden“, „Trotzdem speichern“, „Da ist etwas schiefgegangen.“, Import-Hinweise „Quelle: %s“, „Portionenzahl war in der Datei nicht angegeben – aus den Mengen auf %d geschätzt, bitte prüfen.“ / „Portionenzahl war in der Datei nicht angegeben – auf %d gesetzt, bitte prüfen.“
- Seiten < 30 KB.

## Review Focus

1. Editor einer Zutat mit Namen, der nur in der Groß-/Kleinschreibung abweicht („reis“ vs. „Reis“) – nutzt die vorhandene Zutat, legt keine zweite an.
2. Speichern ohne eine einzige Zutatenzeile – erlaubt (leere Zutatenliste), Rezept gespeichert.
3. Rezept, das am Handy gelöscht wurde, während der Editor offen ist – Speichern zeigt den Konflikt statt eine Löschung still rückgängig zu machen.
4. Import-Datei ohne erkennbares Rezept (beliebige Textdatei) – „nicht erkannt“, kein Fehler.
5. Zwei Zeilen mit derselben neuen Zutat („Tofu“ zweimal) – eine neue Zutat, beide Zeilen verweisen darauf.

Tests dazu: 1, 2, 5 → Task 4 (`grossKleinschreibungNutztVorhandeneZutat`, `ohneZutatenErlaubt`, `gleicheNeueZutatNurEinmal`); 3 → Task 4 (`geloeschtWaehrendBearbeitungIstKonflikt`); 4 → Task 6 (`textOhneRezeptIstNichtErkannt`).

---

### Task 1: Gleiche-Website-Prüfung und Fehlerrahmen

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/WebRoute.kt`
- Modify: `WebOberflaeche.kt` (alle Routen über den Rahmen), `seiten/LoginSeite.kt`
- Test: `server/web/src/test/kotlin/de/foody/server/web/WebRahmenTest.kt`

**Interfaces:**
- Produces: `fun Route.webGet(pfad: String, block: suspend RoutingContext.() -> Unit)`, `fun Route.webPost(pfad: String, block: suspend RoutingContext.() -> Unit)` – fangen `ApiException` (→ Seite mit Meldung, Status aus der Ausnahme) und `Exception` (→ 500-Seite „Da ist etwas schiefgegangen.“, Protokoll per `call.application.log.error`); `fun ApplicationCall.gleicheWebsite(): Boolean`.
- Test des Rahmens über eine eigene Testroute: `testApplication` mit `routing { webGet("/kaputt") { error("geheim") } }`.

- [ ] Tests: `loginVonFremderWebsiteIst403` (`Sec-Fetch-Site: cross-site` → 403; `Origin: https://boese.example` bei `Host: localhost` → 403; ohne beide Header → wie bisher), `registrierenVonFremderWebsiteIst403`, `fehlerZeigtLayoutOhneDetails` (Status 500, enthält „Da ist etwas schiefgegangen.“, enthält nicht „geheim“), `apiExceptionWirdMeldung` (Testroute wirft `ApiException(NotFound, NOT_FOUND)` → 404-Seite im Layout, kein JSON).
- [ ] FAIL → implementieren → PASS (`./gradlew :server-web:test`).
- [ ] Commit „Web: Login nur von derselben Website, Fehlerseite im Layout“.

### Task 2: `RezeptImport` in `shared/domain`

**Files:**
- Create: `shared/domain/src/main/kotlin/de/foody/domain/RezeptImport.kt`, Test `shared/domain/src/test/kotlin/de/foody/domain/RezeptImportTest.kt`
- Modify: `android/app/src/main/java/de/foody/app/data/repo/RecipeImportRepository.kt` (`importParsed` nutzt `RezeptImport.vorbereiten`)

**Interfaces:**
- Produces: `object RezeptImport { data class Zeile(val zutatId: String, val menge: BigDecimal, val einheit: MeasureUnit, val hinweis: String?, val optional: Boolean); data class Ergebnis(val portionen: Int, val geschaetzt: Boolean, val zeilen: List<Zeile>); fun vorbereiten(rezept: ImportedRecipe, zutaten: List<Ingredient>, vorgabePortionen: Int): Ergebnis; fun hinweis(quelle: String?, portionen: Int, geschaetzt: Boolean): String }` – `zutaten[i]` gehört zu `rezept.ingredients[i]`. Rechnung genau wie heute in `importParsed` (feste Gramm ohne Volumen, kcal über `NutritionCalculator`, Fleischstück aus Kategorie „Fleisch & Fisch“, `ServingsEstimator.estimate`).

- [ ] Tests: `geschaetztePortionen` (Rezeptname „Gulasch“, 1 kg Rindfleisch → wie `ServingsEstimator`), `vorgabeWennNichtSchaetzbar` (nur „Salz“ ohne Menge → 4, `geschaetzt = false`), `zeilenWieInDerApp` (Menge null → 0, Einheit null → PIECE, Hinweis „Für den Teig · gehackt“), `hinweisTexte` (beide Varianten, mit/ohne Quelle, Zeilen mit `\n`).
- [ ] FAIL → implementieren → PASS (`./gradlew :domain:test`); App umstellen; `./gradlew :app:testDebugUnitTest` → grün.
- [ ] Commit „Import-Rechnung nach domain (RezeptImport), App nutzt sie“.

### Task 3: `RezeptFormular`

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/RezeptFormular.kt`, Test `RezeptFormularTest.kt`

**Interfaces:**
- Produces: `internal data class FormZeile(val menge: String, val einheit: String, val zutat: String, val hinweis: String, val optional: Boolean)`; `internal data class RezeptFormular(val id: String?, val rev: Long?, val erzwingen: Boolean, val name: String, val portionen: String, val vorbereitung: String, val kochzeit: String, val tags: String, val quelle: String, val notizen: String, val schritte: String, val einordnungFest: Boolean, val mahlzeiten: Set<Mahlzeit>, val gaenge: Set<Gang>, val zeilen: List<FormZeile>)` mit `companion fun aus(p: Parameters): RezeptFormular` (Zeilen aus `zeile_menge`, `zeile_einheit`, `zeile_zutat`, `zeile_hinweis`, `zeile_optional_<i>` – parallele Listen; leere Zutat = Zeile ignoriert) und `fun pruefen(): Map<String, String>` (Feldname → Meldung); `companion fun aus(r: WebRezept, h: Haushalt): RezeptFormular` (zum Bearbeiten).

- [ ] Tests: `liestZeilenUndIgnoriertLeere`, `pruefenMeldetFehler` (Name leer, Portionen „0“/„100“/„x“, Kochzeit „2000“, Menge „-1“/„1e5“ → je Meldung), `kommaUndPunktAlsDezimal` („1,5“ und „1.5“ gültig), `ausRezeptUndZurueck` (Rezept → Formular → gleiche Felder).
- [ ] FAIL → implementieren → PASS.
- [ ] Commit „Web: Rezeptformular lesen und prüfen“.

### Task 4: `RezeptSchreiber`

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/RezeptSchreiber.kt`, Test `RezeptSchreiberTest.kt`

**Interfaces:**
- Consumes: `RezeptFormular` (Task 3), `HaushaltsDaten`/`Haushalt` (Teil 1).
- Produces: `internal sealed interface Speichern { data class Ok(val id: String) : Speichern; data class Konflikt(val aktuelleRev: Long?) : Speichern; data class Fehler(val meldungen: Map<String, String>) : Speichern }`; `internal class RezeptSchreiber(deps: ServerDeps, daten: HaushaltsDaten) { fun speichern(household: String, f: RezeptFormular): Speichern; fun archivieren(household: String, id: String, archiviert: Boolean): Boolean /* false = nicht gefunden */; fun zutatenAufloesen(household: String, namen: List<String>): List<String> /* IDs, gleiche Reihenfolge */ }`.

- [ ] Tests: `neuesRezeptMitNeuerUndVorhandenerZutat`, `grossKleinschreibungNutztVorhandeneZutat`, `mehlWirdWeizenmehl` (vorhandenes „Weizenmehl“), `gleicheNeueZutatNurEinmal`, `ohneZutatenErlaubt`, `bearbeitenBehaeltFotoFavoritUndUnbekanntesFeld` (gespeichertes JSON mit `photo`, `favorite = true`, `"zukunft": 1`), `einordnungVermutenSetztNull`, `unbekannteEinordnungBleibt` (`mahlzeiten = "ABENDESSEN,BRUNCH"` → festgelegt nur Abendessen → `"ABENDESSEN,BRUNCH"`), `konfliktSpeichertNichts` (Revision geändert → `Konflikt`, gespeicherter Stand unverändert), `erzwingenSpeichert`, `geloeschtWaehrendBearbeitungIstKonflikt`, `archivierenUndWiederherstellen`.
- [ ] FAIL → implementieren → PASS.
- [ ] Commit „Web: Rezepte speichern und archivieren über SyncService“.

### Task 5: Editor-Seiten und Archiv

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/seiten/EditorSeite.kt`
- Modify: `WebOberflaeche.kt` (`GET /rezept/neu`, `GET /rezept/{id}/bearbeiten`, `POST /rezept/speichern`, `GET /rezept/zeile`, `POST /rezept/{id}/archivieren`, `POST /rezept/{id}/wiederherstellen`), `seiten/RezeptSeiten.kt` (Knöpfe „Bearbeiten“, „Archivieren“/„Wiederherstellen“, „Neues Rezept“, „Import“, Archiv-Umschalter `?archiv=1`)
- Test: `EditorSeitenTest.kt`

- [ ] Tests: `neuAnlegenUeberFormular` (POST → 303 auf `/rezept/{id}`, Rezept in der Liste), `bearbeitenZeigtWerte`, `fehlerBehaeltEingaben` (Name leer → 200, Formular enthält die übrigen Eingaben, Meldung), `konfliktSeiteMitZweiKnoepfen` (Texte aus den Global Constraints), `zeileFragment` (`GET /rezept/zeile` → eine Zeile, < 2 KB), `neuePostsOhneCsrfSind403` (speichern, archivieren, wiederherstellen), `fremdesRezeptBearbeitenIst404`, `archivFilter` (archiviertes nur mit `?archiv=1`), `editorUnter30KB` (Rezept mit 30 Zeilen).
- [ ] FAIL → implementieren → PASS.
- [ ] Commit „Web: Rezept-Editor, Archivieren und Archiv-Filter“.

### Task 6: Markdown-Import

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/seiten/ImportSeite.kt`, Test `ImportTest.kt` (Beispieldatei als String im Test, Chefkoch-Format: `# Titel`, `_Quelle: https://www.chefkoch.de/rezepte/1/test.html_`, `## Zutaten` mit Mengen-/Namenszeilen, `## Zubereitung`)
- Modify: `WebOberflaeche.kt` (`GET /import`, `POST /import`), `server/web/build.gradle.kts` falls Multipart-Abhängigkeit nötig

**Interfaces:**
- Consumes: `RezeptImport` (Task 2), `RezeptSchreiber.zutatenAufloesen` (Task 4).

- [ ] Tests: `importWieInDerApp` (Portionen und Zeilen = `RezeptImport.vorbereiten` mit denselben Zutaten; Tag „Import“; Notiz mit Quelle), `doppelteQuelleUebersprungen`, `textOhneRezeptIstNichtErkannt`, `zuGrosseDateiAbgelehnt` (1 048 577 Bytes → Meldung, nichts importiert), `zuVieleDateienAbgelehnt` (51), `importOhneCsrfIst403`.
- [ ] FAIL → implementieren → PASS.
- [ ] Commit „Web: Markdown-Import per Upload“.

### Task 7: Ende-zu-Ende, Doku, Sichtprüfung

**Files:**
- Modify: `android/app/src/test/java/de/foody/app/sync/SyncEndToEndTest.kt`, `docs/architecture.md`, `server/README.md`

- [ ] Test `rezeptImBrowserBearbeitetKommtAufsHandy`: Gerät A legt Rezept an und gleicht ab; Browser ändert Namen und eine Menge über `POST /rezept/speichern`; A gleicht ab → neuer Name und Menge lokal, Foto-/Favoritenfeld unverändert. FAIL → (nichts zu ändern erwartet; falls doch: beheben) → PASS.
- [ ] Doku ergänzen (Abschnitt Web-Oberfläche: Editor, Konflikt, Import).
- [ ] `./gradlew check` → exit 0; lokaler Server mit Testdaten, im Browser-Pane: anlegen, bearbeiten, Konflikt, archivieren, Import einer Datei aus `E:\OpenCloud\Persönlich (2)\Dev\Crawler\reports\kochbuch\chefkoch.de_liste\texte`.
- [ ] Commit „Web: Ende-zu-Ende-Test und Doku zu Teil 2“.
