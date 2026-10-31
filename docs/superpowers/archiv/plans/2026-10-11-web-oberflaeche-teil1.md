# Web-Oberfläche Teil 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Im Browser anmelden, Haushalt wählen, Rezepte/Plan/Einkauf/Tagebuch lesen (mit denselben Zahlen wie die App) und Einkaufseinträge abhaken.

**Architecture:** Im Server gerendert (kotlinx.html + htmx) im Modul `:server-web`, eingehängt über `WebOberflaeche.routen`. Lesen: Datensätze des Haushalts → `sync-protocol`-Payloads → `domain`, je Haushalt bis zur nächsten Revision gecacht. Schreiben (nur Abhaken): `SyncService.push` mit `baseRev`.

**Tech Stack:** Ktor 3.5.0 (`ktor-server-html-builder`), kotlinx.html, htmx 2.0.4 (WebJar), SQLite, JUnit 5 + `ktor-server-test-host`.

**Spec:** `docs/superpowers/specs/2026-10-11-web-oberflaeche-teil1-design.md`

## Global Constraints

- Seite ohne htmx < 30 KB; kein Laden aus dem Internet; kein weiteres Build-Werkzeug.
- Cookie `foody_sitzung`: `HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=2592000`.
- CSRF: Feld `_csrf` / Header `X-Foody-CSRF` = Hex(HMAC-SHA256(key = Gerätetoken, msg = "foody-web-csrf")); POST ohne gültiges Token → 403.
- Gerätename `Browser (Firefox|Chrome|Safari|Edge)`, sonst `Browser`.
- Statische Dateien unter `/web/…` mit `Cache-Control: public, max-age=31536000, immutable`, Link mit `?v=<Version>`.
- Schreiben nur über `SyncService.push`; `:server-sync` kennt `:server-web` nicht.
- Texte deutsch; Zahlen wie in der App (`NutritionResult.kcal`, `RecipeScaler`, `DayNutrition`, `PlanDurchschnitt`, `Tagebuch.bilanz`).
- Zutat → `domain.Ingredient` genau wie `IngredientEntity.toDomain()` der App (Packungs-/Dosengewicht aus `IngredientCatalog`).

## Review Focus

1. Haushalt ohne Daten (frisch angelegt) – alle Seiten zeigen leere Zustände statt Fehler.
2. Payload eines neueren App-Formats (unbekannte Felder/Enum-Werte) – Datensatz wird übersprungen, Seite lädt.
3. Widerrufenes Gerät (in der App „Gerät entfernen“) mit noch gültigem Cookie – nächster Aufruf → `/login`.
4. Zwei Personen haken gleichzeitig ab – Konfliktregel von `SyncService` greift, Zeile zeigt den zusammengeführten Stand.
5. Rezept-ID in der URL, die zu einem anderen Haushalt gehört – 404, keine Daten aus fremdem Haushalt.

Tests dazu: 1 → Task 5–7 (`leererHaushalt…`), 2 → Task 2 (`unbekanntesFormatWirdUebersprungen`), 3 → Task 4 (`widerrufenesGeraetMussNeuAnmelden`), 4 → Task 7 (`gleichzeitigesAbhakenWirdZusammengefuehrt`), 5 → Task 5 (`rezeptAusFremdemHaushaltIst404`).

---

### Task 1: `RecordStore.lebende` und Modul-Gerüst

**Files:**
- Modify: `server/sync/src/main/kotlin/de/foody/server/sync/RecordStore.kt`, `gradle/libs.versions.toml` (`ktor-server-html-builder`, `webjars-htmx = "org.webjars.npm:htmx.org:2.0.4"`), `server/web/build.gradle.kts` (Abhängigkeiten + `testImplementation` wie `server/sync`)
- Test: `server/sync/src/test/kotlin/de/foody/server/LebendeTest.kt`

**Interfaces:**
- Produces: `fun RecordStore.lebende(c: Connection, household: String): Pair<List<SyncRecord>, Long>` – nicht gelöschte Datensätze (beliebige Typen) und die höchste Revision des Haushalts (0 ohne Datensätze; gelöschte zählen für die Revision mit).

- [ ] Test `lebende`: Haushalt A mit 2 lebenden + 1 gelöschtem Datensatz, Haushalt B mit 1 → `lebende(A)` liefert 2 Datensätze, Revision = höchste Revision von A (inkl. des gelöschten); B taucht nicht auf; leerer Haushalt → `(emptyList(), 0)`. Datensätze über `SyncService.push` anlegen (wie `SyncTest`).
- [ ] Run `./gradlew :server-sync:test --tests "*LebendeTest"` → FAIL (unbekannte Funktion); implementieren; → PASS.
- [ ] Abhängigkeiten in `server/web` eintragen; `./gradlew :server-web:compileKotlin` → OK.
- [ ] Commit „Web: RecordStore.lebende und Modul-Abhängigkeiten“.

### Task 2: `PayloadZuDomain`

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/PayloadZuDomain.kt`
- Test: `server/web/src/test/kotlin/de/foody/server/web/PayloadZuDomainTest.kt`

**Interfaces:**
- Produces (alle `internal` im Modul):
  - `data class WebRezept(val id: String, val rev: Long, val payload: RecipePayload, val domain: Recipe, val einordnung: Einordnung)`
  - `data class WebPlanEintrag(val id: String, val datum: LocalDate, val mahlzeit: String, val rezeptId: String, val portionen: Int)`
  - `data class WebEinkaufsliste(val id: String, val name: String, val geaendert: Long /* SyncRecord.updatedAt; neueste = größter Wert */)` und `data class WebEintrag(val id: String, val rev: Long, val listId: String, val payload: ShoppingItemPayload)`
  - `data class WebTagebuch(val id: String, val datum: LocalDate, val mahlzeit: Mahlzeit, val name: String, val werte: Naehrwerte)`
  - `object PayloadZuDomain { fun zutat(r: SyncRecord): Ingredient?; fun rezept(r: SyncRecord): WebRezept?; fun planEintrag(r: SyncRecord): WebPlanEintrag?; fun liste(r: SyncRecord): WebEinkaufsliste?; fun eintrag(r: SyncRecord): WebEintrag?; fun tagebuch(r: SyncRecord): WebTagebuch? }` – `null` bei falschem Typ oder unlesbarem Payload (Serialization-, IllegalArgument-, DateTimeParse-Fehler).

- [ ] Tests: `zutatWieInDerApp` (Basis, kJ, Dichte, Packungsgewicht aus `IngredientCatalog` für „Dosentomaten“), `rezeptMitZeilenUndSchritten` (Zeilen nach `sortOrder`, Einheit `GRAM`, optional), `planEintrag`, `einkaufseintrag`, `tagebuchEintrag` (Mahlzeit über `Mahlzeit.ausText`, Freitext → `ABENDESSEN` wie in der App), `unbekanntesFormatWirdUebersprungen` (Einheit `"PRISE_NEU"` → `null`).
- [ ] FAIL → implementieren → PASS (`./gradlew :server-web:test --tests "*PayloadZuDomainTest"`).
- [ ] Commit „Web: Payloads in domain überführen“.

### Task 3: `HaushaltsDaten` mit Cache

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/HaushaltsDaten.kt`
- Test: `server/web/src/test/kotlin/de/foody/server/web/HaushaltsDatenTest.kt`, `server/web/src/test/kotlin/de/foody/server/web/WebTestSupport.kt` (Server mit `foodyModule(deps, WebOberflaeche.routen)`, Benutzer/Haushalt anlegen, Datensätze pushen – nach dem Muster von `server/sync/.../TestServer.kt`)

**Interfaces:**
- Consumes: `RecordStore.lebende` (Task 1), `PayloadZuDomain` (Task 2).
- Produces: `class Haushalt(val revision: Long, val zutaten: Map<String, Ingredient>, val rezepte: Map<String, WebRezept>, val plan: List<WebPlanEintrag>, val listen: List<WebEinkaufsliste>, val eintraege: List<WebEintrag>, val tagebuch: List<WebTagebuch>)`; `class HaushaltsDaten(db: Database, store: RecordStore) { fun fuer(household: String): Haushalt; var ladevorgaenge: Int /* nur für Tests lesbar */ }`.

- [ ] Tests: `zweiterAbrufNutztDenCache` (`ladevorgaenge` bleibt 1), `nachPushNeuerStand` (push → `fuer` enthält neues Rezept, `ladevorgaenge` = 2), `haushalteGetrennt`.
- [ ] FAIL → implementieren (Revision per kleiner Abfrage `MAX(rev)`; nur bei Änderung `lebende` laden) → PASS.
- [ ] Commit „Web: Haushaltsdaten mit Cache je Revision“.

### Task 4: Anmeldung, Sitzung, CSRF, Layout

**Files:**
- Create: `Sitzung.kt`, `Layout.kt`, `seiten/LoginSeite.kt`, `seiten/HaushaltSeite.kt` (unter `server/web/src/main/kotlin/de/foody/server/web/`), `server/web/src/main/resources/web/foody.css`
- Modify: `WebOberflaeche.kt` (Routen: `GET/POST /login`, `GET/POST /registrieren`, `GET/POST /haushalt`, `POST /logout`, `GET /web/foody.css`, `GET /web/htmx.min.js` aus dem WebJar)
- Test: `server/web/src/test/kotlin/de/foody/server/web/SitzungTest.kt`

**Interfaces:**
- Produces: `suspend fun ApplicationCall.sitzungOderLogin(deps: ServerDeps): DevicePrincipal?` (leitet selbst nach `/login` um und liefert dann `null`); `fun csrfToken(geraeteToken: String): String`; `fun ApplicationCall.pruefeCsrf(token: String): Boolean`; `fun HTML.seite(titel: String, aktiv: Navi?, csrf: String?, inhalt: MAIN.() -> Unit)` mit `enum class Navi { REZEPTE, PLAN, EINKAUF, TAGEBUCH }`.

- [ ] Tests: `ohneCookieZumLogin` (GET `/` → 302 `/login`), `falschesPasswortZeigtFehlerUndDrosselt` (6. Versuch → Text „Zu viele Versuche“), `anmeldungSetztSicheresCookie` (Set-Cookie enthält `HttpOnly`, `Secure`, `SameSite=Strict`), `browserErscheintAlsGeraet` (`/api/v1/devices` mit App-Token listet `Browser (Firefox)` bei Firefox-User-Agent), `logoutWiderruft` (danach Cookie ungültig → 302), `widerrufenesGeraetMussNeuAnmelden`, `postOhneCsrfIst403`, `mehrereHaushalteFuehrenZurAuswahl`, `registrierenMitEinladung`.
- [ ] FAIL → implementieren → PASS.
- [ ] Commit „Web: Anmeldung, Sitzung, CSRF und Layout“.

### Task 5: Seiten Rezepte und Rezept

**Files:**
- Create: `seiten/RezepteSeite.kt`, `seiten/RezeptSeite.kt`; Modify: `WebOberflaeche.kt` (`GET /`, `GET /rezepte/liste?q=&mahlzeit=&gang=` als htmx-Teilstück, `GET /rezept/{id}?portionen=`)
- Test: `server/web/src/test/kotlin/de/foody/server/web/RezeptSeitenTest.kt`

- [ ] Tests: `listeZeigtRezepteMitKcal` (Rezept „Curry“, 200 g Reis à 1464 kJ/100 g, 2 Portionen → „350 kcal“), `sucheFiltertNachNameUndZutat`, `filterNachMahlzeit`, `archivierteAusgeblendet`, `rezeptSkaliertPortionen` (`?portionen=4` → „400 g“ Reis), `rezeptAusFremdemHaushaltIst404`, `leererHaushaltZeigtHinweis`, `seiteUnter30KB` (Body-Bytes < 30 000 bei 100 Rezepten).
- [ ] FAIL → implementieren → PASS.
- [ ] Commit „Web: Rezeptliste und Rezeptansicht“.

### Task 6: Seiten Plan und Tagebuch

**Files:**
- Create: `seiten/PlanSeite.kt`, `seiten/TagebuchSeite.kt`; Modify: `WebOberflaeche.kt` (`GET /plan?ab=`, `GET /tagebuch?tag=`)
- Test: `server/web/src/test/kotlin/de/foody/server/web/PlanTagebuchTest.kt`

- [ ] Tests: `planZeigtTageInReihenfolge` (Frühstück vor Abendessen; 7 Tage ab `ab`), `planTagesKcalUndSchnitt` (Werte aus `DayNutrition`/`PlanDurchschnitt`), `tagebuchSummeWieInDerApp` (zwei Einträge à 100,4 kcal → „200 kcal“), `tagebuchWochenleiste`, `leererHaushaltZeigtLeerenPlanUndTagebuch`.
- [ ] FAIL → implementieren → PASS.
- [ ] Commit „Web: Wochenplan und Tagebuch“.

### Task 7: Einkauf und Abhaken

**Files:**
- Create: `seiten/EinkaufSeite.kt`; Modify: `WebOberflaeche.kt` (`GET /einkauf?liste=`, `POST /einkauf/{itemId}/abhaken`)
- Test: `server/web/src/test/kotlin/de/foody/server/web/EinkaufTest.kt`

**Interfaces:**
- Consumes: `SyncService.push(household, listOf(record))`; Datensatz = gespeicherter `ShoppingItemPayload` mit `checked = !checked`, `checkedChangedAt = clock.millis()`, `baseRev = rev`.

- [ ] Tests: `neuesteListeNachAbteilung` (Abteilungen aus `ShoppingCatalog.sectionFor`, offen vor erledigt), `abhakenErzeugtNeueRevision` (App sieht per `/api/v1/sync/pull` `checked = true`), `abhakenOhneJavascriptLeitetZurueck` (ohne `HX-Request` → 303 `/einkauf`), `gleichzeitigesAbhakenWirdZusammengefuehrt` (anderes Gerät hakt vorher ab → Antwortzeile zeigt den zusammengeführten Stand), `abhakenOhneCsrfIst403`, `leererHaushaltOhneListe`.
- [ ] FAIL → implementieren → PASS.
- [ ] Commit „Web: Einkaufsliste mit Abhaken“.

### Task 8: Einbinden, Ende-zu-Ende, Dokumentation

**Files:**
- Modify: `android/app/src/test/java/de/foody/app/sync/SyncEndToEndTest.kt` (+ `testImplementation(project(":server-web"))` in `android/app/build.gradle.kts`), `docs/architecture.md` (Abschnitt Web-Oberfläche), `server/README.md` (Aufruf im Browser, Anmeldung), `README.md` (Funktionen), `server/web/build.gradle.kts` (`api`-Bereinigung aus dem Struktur-Review nur, wenn nötig)
- Test: `SyncEndToEndTest.rezeptVomHandyErscheintImBrowser`

- [ ] Test: Gerät A pusht Rezept „Linsensuppe“; Browser-Anmeldung am selben Testserver (mit `WebOberflaeche.routen`); `GET /` enthält „Linsensuppe“. FAIL → (Abhängigkeit/Verdrahtung) → PASS.
- [ ] Doku ergänzen.
- [ ] Run `./gradlew check` → exit 0; `FOODY_SERVER_ONLY=1 ./gradlew :server:installDist` → OK; installierten Server starten, `/login` im Browser-Pane öffnen, anmelden (Testbenutzer per `foody-admin`-Pfad/`FOODY_ADMIN_USER`), Rezepte, Plan, Einkauf, Tagebuch ansehen.
- [ ] Commit „Web: Einbindung, Ende-zu-Ende-Test und Doku“.
