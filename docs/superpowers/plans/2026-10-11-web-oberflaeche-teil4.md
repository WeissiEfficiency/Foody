# Web-Oberfläche Teil 4 (Vorrat, Zutatenkatalog, Tagebuch-Einträge) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vorrat, Zutatenkatalog und Tagebuch-Einträge im Browser pflegen – mit denselben Regeln wie in der App.

**Architecture:** Skalieren und freie Tagebuch-Werte wandern als reine Funktionen nach `shared/domain` (App und Server
rufen sie). Der Server liest über `HaushaltsDaten` und schreibt nur über `SyncService.push` in `VorratSchreiber`,
`ZutatSchreiber`, `TagebuchSchreiber`; Seiten mit kotlinx.html + htmx nach dem Muster von Teil 3.

**Tech Stack:** Kotlin, Ktor-Server, kotlinx.html, htmx, Room (App), kotlin.test, Robolectric (E2E).

**Spec:** `docs/superpowers/specs/2026-10-11-web-oberflaeche-teil4-design.md`

## Global Constraints

- Texte für Nutzer auf Deutsch; Bezeichner wie im umgebenden Code.
- Schreiben nur über `SyncService.push`, auf dem gerade gespeicherten Roh-JSON (unbekannte Felder wie `barcode`, `source` bleiben).
- Alle Routen über `webGet`/`webPost`; POST: `call.formular()` + CSRF (sonst 403); nach Erfolg 303 (`call.weiterleiten`).
- Fremde/unbekannte IDs → 404 (`call.nichtGefunden`), nichts geschrieben; inzwischen gelöscht → Hinweis „Eintrag gibt es nicht mehr“; `PushStatus.REJECTED` → `call.abgelehnt` (409).
- Zahlen über `ZahlGrenzen` (≥ 0, keine Exponentenschreibweise) – Parser wie `FormZeile.mengeZahl()`/`einheitWert()` aus Teil 2 wiederverwenden.
- Grenzen: Tagebuch-Portionen > 0 und ≤ 99 (Kommazahl erlaubt); kcal 0–10 000; Namen 1–200 Zeichen (getrimmt); Datum ±1 Jahr um heute (`PlanSeiten.datumErlaubt`); Mahlzeit nur `Mahlzeit.entries`.
- Größen: `/vorrat` mit 100 Einträgen und `/zutaten` mit 300 Zutaten < 30 KB.
- Ohne JavaScript bedienbar; htmx nur für Suchen.
- Instrumentierte App-Tests nur auf AVD `Foody_Test` (vorher `adb -s <serial> emu avd name` prüfen).
- PR gegen main mit Auto-Merge; im PR „Server aktualisieren? Ja“.

## Review Focus

1. Zutat auf den Namen einer anderen Zutat umbenennen (auch über Katalog-Vereinheitlichung, „Mehl“ → „Weizenmehl“) → „Gibt es schon“, nichts geschrieben (sonst führt der Server still zusammen) – Test in Task 4.
2. Tagebuch-Zutat ohne Nährwerte oder „1 Stk.“ ohne Stückgewicht → Hinweis „bitte frei eintragen“, nichts geschrieben – Test in Task 5.
3. Tagebuch-Eintrag ändern, dessen Rezept inzwischen gelöscht ist → skaliert die festgehaltenen Werte weiter, keine 500 – Test in Task 5.
4. Vorrat mit unbekanntem Zutatennamen → Zutat wird angelegt (vereinheitlicht), Sonderzeichen escaped – Test in Task 3.
5. „Gegessen“ für einen Planeintrag, dessen Rezept gelöscht ist → kein Vorschlag, POST → 404 statt 500 – Test in Task 5.

---

### Task 1: Gemeinsame Tagebuch-Regeln, App ruft sie

**Files:**
- Modify: `shared/domain/src/main/kotlin/de/foody/domain/Tagebuch.kt`
- Test: `shared/domain/src/test/kotlin/de/foody/domain/TagebuchTest.kt`
- Modify: `android/app/src/main/java/de/foody/app/ui/tagebuch/TagebuchViewModel.kt` (`bearbeiten`, `verhaeltnis`, `freiEintragen`)

**Interfaces:**
- Produces (in `object Tagebuch`):
  - `fun skaliert(art: TagebuchArt, werte: Naehrwerte, alt: BigDecimal?, neu: BigDecimal?): Naehrwerte` — `art == FREI`, `neu == null`, `alt == null`, `alt <= 0` oder `neu == alt` → `werte` unverändert; sonst jeder Wert × (neu ÷ alt, Skala 10, HALF_UP), `vollstaendig` bleibt.
  - `fun freiWerte(kcal: Int, eiweiss: BigDecimal?, kohlenhydrate: BigDecimal?, fett: BigDecimal?): Naehrwerte` — `energieKj = kcal × NutritionResult.KJ_JE_KCAL`, `vollstaendig = true`.

- [ ] **Step 1: Tests** in `TagebuchTest`:
```kotlin
@Test fun skaliertImVerhaeltnis()      // REZEPT, 400 kJ/10 g Eiweiß, alt 1, neu 1,5 → 600 kJ / 15 g; vollstaendig bleibt false, wenn false
@Test fun skaliertNichtOhneAenderung() // neu == alt, alt null, alt 0 → gleiche Werte
@Test fun freiWirdNieSkaliert()        // FREI, alt 1, neu 2 → gleiche Werte
@Test fun freiWerteKcalInKj()          // freiWerte(100, null, null, null).energieKj == 100 × KJ_JE_KCAL, kcal == 100
```
- [ ] **Step 2:** `./gradlew :domain:test` · Expected: FAIL (unresolved)
- [ ] **Step 3:** Implementieren; `TagebuchViewModel.bearbeiten` nutzt `skaliert` (Rezept: Portionen, Zutat: Menge), `verhaeltnis` entfällt; `freiEintragen` nutzt `freiWerte`.
- [ ] **Step 4:** `./gradlew :domain:test :app:testDebugUnitTest` · Expected: PASS; auf Foody_Test `connectedDebugAndroidTest -P…class=de.foody.app.TagebuchViewModelTest` · Expected: alle PASS (u. a. `bearbeitenSkaliertFestgehalteneWerte`, `freiEintrag`)
- [ ] **Step 5:** Commit `Domain: Tagebuch-Skalierung und freie Werte gemeinsam`

### Task 2: Server liest Tagebuch- und Zutatenfelder, Navigation

**Files:**
- Modify: `server/web/.../PayloadZuDomain.kt`, `HaushaltsDaten.kt`, `Layout.kt` (Navi)
- Test: `server/web/src/test/.../PayloadZuDomainTest.kt`, `WebRahmenTest.kt` oder `RezeptSeitenTest.kt` (Navigation)

**Interfaces:**
- Produces:
  - `WebTagebuch(id, rev: Long, datum, mahlzeit, name, werte, art: TagebuchArt, rezeptId: String?, zutatId: String?, portionen: BigDecimal?, menge: BigDecimal?, einheit: MeasureUnit?, planEintragId: String?)` (neue Felder mit Vorgaben, unbekannte Art → `FREI`)
  - `internal data class WebZutat(val id: String, val rev: Long, val payload: IngredientPayload)`; `Haushalt.zutatenRoh: Map<String, WebZutat>`; `PayloadZuDomain.zutatRoh(r): WebZutat?`
  - `Navi.VORRAT("/vorrat", "Vorrat")` zwischen EINKAUF und TAGEBUCH.

- [ ] **Step 1: Tests:** `tagebuchEintragMitDetails` (Art ZUTAT, Menge 150, Einheit GRAM, rev), `zutatRohMitRev`, `navigationHatVorrat` (Startseite enthält `href="/vorrat"`).
- [ ] **Step 2:** `./gradlew :server-web:test` · Expected: FAIL
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5:** Commit `Web: Tagebuch- und Zutatenfelder lesen, Vorrat in der Navigation`

### Task 3: Vorrat

**Files:**
- Create: `server/web/.../VorratSchreiber.kt`, `seiten/VorratSeiten.kt`
- Modify: `WebOberflaeche.kt`, `seiten/EditorSeite.kt` (Link „Zutatenkatalog“)
- Test: `server/web/src/test/.../VorratTest.kt`

**Interfaces:**
- Consumes: `RezeptSchreiber.zutatenAufloesen(household, namen): List<String>` (Teil 2), `Schreiben`, `Haushaltssperre` (Teil 3).
- Produces: `VorratSchreiber(deps, daten)` mit `anlegen(household, zutatName: String, menge: BigDecimal, einheit: MeasureUnit, haltbarBis: LocalDate?): Schreiben`, `aendern(household, id, menge, einheit, haltbarBis): Schreiben`, `loeschen(household, id): Schreiben`. Routen: `GET /vorrat`, `POST /vorrat` (zutat, menge, einheit, mhd), `GET /vorrat/{id}`, `POST /vorrat/{id}` (menge, einheit, mhd), `POST /vorrat/{id}/loeschen`. Datalists `zutaten` (wie Editor) und `einheiten`.

- [ ] **Step 1: Tests:**
```kotlin
@Test fun anlegenAendernLoeschen()          // Reis 500 g mhd 2026-12-01 → WebVorrat; ändern auf 0,75 kg → menge 0.75 KILOGRAM; löschen → leer
@Test fun unbekannteZutatWirdAngelegt()     // „Zwiebeln“ → Zutat „Zwiebel“ (canonicalName) mit Kategorie, Vorrat zeigt darauf (Review Focus 4)
@Test fun sonderzeichenEscaped()            // Zutat „<b>Brot</b>“ → &lt;b&gt;
@Test fun abgelaufenMarkiert()              // mhd gestern → „abgelaufen“ (heute = Testuhr 11.10.)
@Test fun ungueltigeEingaben()              // menge „1e5“, „-1“, Einheit „Eimer“, Name leer, mhd +2 Jahre → Meldung, nichts geschrieben
@Test fun fremdeIdsSind404UndCsrf403()
@Test fun inzwischenGeloeschtGibtHinweis()
@Test fun hundertEintraegeUnter30KB()
```
- [ ] **Step 2:** `./gradlew :server-web:test --tests '*VorratTest*'` · Expected: FAIL
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5:** Commit `Web: Vorrat pflegen`

### Task 4: Zutatenkatalog

**Files:**
- Create: `server/web/.../ZutatSchreiber.kt`, `seiten/ZutatSeiten.kt`
- Modify: `WebOberflaeche.kt`, `seiten/VorratSeiten.kt` (Link)
- Test: `server/web/src/test/.../ZutatenTest.kt`

**Interfaces:**
- Produces:
  - `internal data class ZutatFormular(name, kategorie, dichte, stueckgewicht, basis, kj, eiweiss, kh, fett, ballaststoffe, zucker, salz: String)` mit `pruefen(): Map<String, String>` (Feldname → Meldung) und `aus(Parameters)`, `aus(WebZutat)` (Zahlen unrund mit Komma wie Editor).
  - `ZutatSchreiber(deps, daten)`: `speichern(household, id: String?, f: ZutatFormular): ZutatSpeichern` mit `sealed interface ZutatSpeichern { Ok(id); GibtEsSchon(id, name); Fehler(meldungen); NichtGefunden; Abgelehnt }`; `verwendungen(household, id): List<String>`; `loeschen(household, id): Schreiben` (bei Verwendung → nicht aufrufen; Seite zeigt Meldung).
  - Name: `IngredientCatalog.canonicalName(name.trim())`; Dublette = andere lebende Zutat mit gleichem Namen (ohne Groß/Klein). Ohne Basis: alle Nährwertfelder als `null` geschrieben.
  - Routen: `GET /zutaten` (q), `GET /zutaten/liste` (htmx-Teil), `GET /zutat/neu`, `GET /zutat/{id}`, `POST /zutat/speichern` (Feld `id`, leer = neu), `POST /zutat/{id}/loeschen`.
  - Verwendungsmeldung: „Wird noch verwendet in: A, B, C, D, E und N weitere“ (Rezeptnamen, „Vorrat“, Listennamen).

- [ ] **Step 1: Tests:**
```kotlin
@Test fun anlegenUndBearbeiten()                 // neu „Haferflocken“, Basis PER_100_G, kJ 1550 → Liste zeigt „370 kcal“; bearbeiten Salz 0,02
@Test fun strichcodeUndQuelleBleiben()           // Zutat mit barcode/source bearbeiten → beide unverändert
@Test fun dubletteAbgelehnt()                    // „Mehl“ umbenennen/neu, wenn „Weizenmehl“ existiert → „Gibt es schon“ + Link, nichts geschrieben (Review Focus 1)
@Test fun ohneBasisKeineNaehrwerte()             // Basis leer, kJ 100 → energyKj null
@Test fun loeschenNurOhneVerwendung()            // in Rezept / Vorrat / Einkaufseintrag verwendet → Meldung mit Namen, nichts gelöscht; unbenutzt → gelöscht
@Test fun ungueltigeZahlen()                     // kJ „abc“, Dichte „-1“ → Meldung am Feld
@Test fun fremdeIdsSind404UndCsrf403()
@Test fun dreihundertZutatenUnter30KB()
```
- [ ] **Step 2:** `./gradlew :server-web:test --tests '*ZutatenTest*'` · Expected: FAIL
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5:** Commit `Web: Zutatenkatalog`

### Task 5: Tagebuch-Einträge

**Files:**
- Create: `server/web/.../TagebuchSchreiber.kt`, `seiten/TagebuchSeiten.kt` (Eintragen, Ändern); Anzeige in `PlanTagebuchSeiten.tagebuch` bekommt Vorschläge, „+ Eintragen“ je Mahlzeit und Links je Eintrag
- Modify: `WebOberflaeche.kt`
- Test: `server/web/src/test/.../TagebuchSchreibenTest.kt`

**Interfaces:**
- Consumes: Task 1 `Tagebuch.skaliert`, `Tagebuch.freiWerte`; `Tagebuch.naehrwerteRezept/naehrwerteZutat/offeneVorschlaege`; `Haushaltssperre`; `RezeptSeiten.trifft`.
- Produces: `TagebuchSchreiber(deps, daten)` mit `gegessen(household, planEintragId): Schreiben` (Planeintrag fremd/Rezept fehlt → `NichtGefunden`; schon übernommen → `Ok` ohne Schreiben; Werte für 1 Portion, Datum/Mahlzeit des Planeintrags), `rezept(household, tag, mahlzeit, rezeptId, portionen: BigDecimal): Schreiben`, `zutat(household, tag, mahlzeit, zutatName, menge, einheit): Schreiben?` (null = nicht berechenbar), `frei(household, tag, mahlzeit, name, kcal: Int, eiweiss, kh, fett): Schreiben`, `aendern(household, id, mahlzeit, portionen?, menge?, name?, kcal?, eiweiss?, kh?, fett?): Schreiben`, `loeschen(household, id): Schreiben`. Payload wie die App (`TagebuchPayload`, `art`-Namen, Zahlen als `toPlainString`).
  Routen: `POST /tagebuch/gegessen` (plan), `GET /tagebuch/neu?tag&mahlzeit&q`, `GET /tagebuch/neu/liste` (htmx), `POST /tagebuch/rezept`, `POST /tagebuch/zutat`, `POST /tagebuch/frei`, `GET /tagebuch/{id}`, `POST /tagebuch/{id}`, `POST /tagebuch/{id}/loeschen`. Danach 303 auf `/tagebuch?tag=<datum>`.

- [ ] **Step 1: Tests:**
```kotlin
@Test fun gegessenEinmal()                    // Planeintrag heute → Vorschlag sichtbar; zweimal POST → ein Eintrag; Werte == naehrwerteRezept(…, 1)
@Test fun gegessenGleichzeitig()              // 8 parallele POSTs, 3 Runden → je ein Eintrag
@Test fun gegessenOhneRezept()                // Rezept gelöscht → kein Vorschlag, POST → 404 (Review Focus 5)
@Test fun rezeptZutatFreiEintragen()           // Werte wie Tagebuch.naehrwerteRezept/naehrwerteZutat/freiWerte; Art, Portionen, Menge gesetzt
@Test fun zutatNichtBerechenbar()             // Zutat ohne Nährwerte bzw. 1 Stk. ohne Stückgewicht → Hinweis „bitte frei eintragen“, nichts geschrieben (Review Focus 2)
@Test fun aendernSkaliert()                    // Rezept-Eintrag 1 → 2 Portionen verdoppelt; Rezept vorher gelöscht → trotzdem skaliert (Review Focus 3); frei: Name/kcal ändern
@Test fun loeschen()
@Test fun ungueltigeEingaben()                // Portionen 0 / 100 / „abc“, kcal 10001, Name leer, Mahlzeit BRUNCH → Meldung, nichts geschrieben
@Test fun fremdeIdsSind404UndCsrf403()
@Test fun inzwischenGeloeschtGibtHinweis()
```
- [ ] **Step 2:** `./gradlew :server-web:test --tests '*TagebuchSchreibenTest*'` · Expected: FAIL
- [ ] **Step 3:** Implementieren; Hinweis „Einträge legst du in der App an …“ auf der Tagebuchseite entfällt.
- [ ] **Step 4:** `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5:** Commit `Web: Tagebuch-Einträge`

### Task 6: Ende-zu-Ende und Doku

**Files:**
- Modify: `android/app/src/test/java/de/foody/app/sync/SyncEndToEndTest.kt`, `docs/architecture.md`, `server/README.md`

- [ ] **Step 1: Test** `vorratUndTagebuchAusDemBrowserKommenAufsHandy`: Browser legt Vorrat (Reis 500 g) und einen Rezept-Eintrag (1,5 Portionen) an; Handy synchronisiert → `pantryDao` enthält Reis 500 g; Tagebuch-Eintrag mit `energieKj` == `Tagebuch.naehrwerteRezept(App-Rezept, App-Zutaten, 1.5).energieKj`.
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*SyncEndToEndTest*'` · Expected: PASS
- [ ] **Step 3:** Doku: Architektur (Teil 4), README (neue Seiten).
- [ ] **Step 4:** `./gradlew check` · Expected: BUILD SUCCESSFUL
- [ ] **Step 5:** Commit `E2E: Vorrat und Tagebuch aus dem Browser; Doku`
