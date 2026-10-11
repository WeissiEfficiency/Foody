# Web-Oberfläche, Teil 1: Grundgerüst, Anmeldung und Lesen

Stand: 2026-10-11 · Gesamtvorhaben: alle App-Funktionen im Browser, sehr ressourcensparend (ohne Fotos, ohne
Strichcode/Scan, ohne Kochtimer). Teile: **1 Grundgerüst + Lesen** (dieses Dokument), 2 Rezeptpflege (Anlegen,
Bearbeiten, Markdown-Import per Upload), 3 Planen + Einkaufen, 4 Vorrat, Zutatenkatalog, Tagebuch-Einträge.
Voraussetzung: Projektstruktur aus `2026-10-11-projektstruktur-design.md` (`server/web`, Hook `foodyModule(deps, erweiterungen)`).

## Ziel und Erfolgskriterien

Im Browser anmelden, Haushalt wählen und Rezepte, Wochenplan, Einkaufsliste und Tagebuch so sehen wie in der App –
mit denselben Zahlen (Nährwerte, Mengen, Summen), weil dieselbe `domain`-Logik rechnet. Einkaufseinträge lassen sich
abhaken; die Handys sehen das beim nächsten Abgleich.

- Jede Seite ohne htmx **< 30 KB** übertragen; htmx (eingebettet, einmal geladen und gecacht) ~ 50 KB unkomprimiert.
- Zusätzlicher Arbeitsspeicher des Servers für einen Haushalt mit ~100 Rezepten **< 30 MB**.
- Kein Build-Werkzeug außer Gradle, kein zweiter Container, nichts aus dem Internet geladen.

## Architektur (Ansatz A: im Server gerendert)

- Modul `:server-web` (`server/web`), Paket `de.foody.server.web`. Hängt sich über `WebOberflaeche.routen` in
  `foodyModule` ein (außerhalb von `/api/v1`). Abhängigkeiten: `:server-sync`, `:domain`,
  `io.ktor:ktor-server-html-builder` (kotlinx.html, gleiche Ktor-Version).
- HTML mit kotlinx.html; Interaktion mit **htmx 2.0.4** (eine Datei in `server/web/src/main/resources/web/`), eigenes
  `foody.css` (~5 KB, hell/dunkel nach System, mobil zuerst). Statische Dateien unter `/web/…` mit
  `Cache-Control: public, max-age=31536000, immutable` und Versions-Parameter im Link.
- **Lesen:** Datensätze des Haushalts aus der Datenbank → `sync-protocol`-Payloads → `domain`-Objekte. **Schreiben:**
  nur über `SyncService.push` als Datensatz mit `baseRev` – dieselben Regeln, Revisionen und Konflikte wie vom Handy.

### Dateien in `server/web/src/main/kotlin/de/foody/server/web/`

| Datei | Aufgabe |
|---|---|
| `WebOberflaeche.kt` | `routen`: Login/Logout, Haushaltswahl, Seiten, statische Dateien |
| `Sitzung.kt` | Cookie ↔ Geräte-Token, CSRF-Token, Weiterleitung zu `/login` |
| `HaushaltsDaten.kt` | Datensätze laden, in `domain` überführen, je Haushalt bis zur nächsten Revision cachen |
| `PayloadZuDomain.kt` | JSON-Payloads (`RecipePayload`, `IngredientPayload`, …) → `domain`-Typen |
| `Layout.kt` | HTML-Grundgerüst, Navigation (Rezepte · Plan · Einkauf · Tagebuch), Meldungen |
| `seiten/LoginSeite.kt`, `HaushaltSeite.kt`, `RezepteSeite.kt`, `RezeptSeite.kt`, `PlanSeite.kt`, `EinkaufSeite.kt`, `TagebuchSeite.kt` | je eine Seite |

In `:server-sync` neu: `RecordStore.lebende(c, household): Pair<List<SyncRecord>, Long>` (alle nicht gelöschten
Datensätze und die höchste Revision des Haushalts).

## Anmeldung und Sitzung

- `/login` (GET Formular, POST Prüfung) nutzt `AccountStore.authenticate` und `LoginThrottle` wie `/auth/login`.
  `/registrieren` mit Einladungscode wie `/auth/register`.
- Erfolgreiche Anmeldung legt über `AccountStore.createDevice` ein **Gerät** an, Name `Browser (<Browserfamilie>)`
  (aus dem User-Agent: Firefox, Chrome, Safari, Edge, sonst „Browser“). Das Token steht im Cookie
  `foody_sitzung`: `HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=2592000` (30 Tage). Das Gerät erscheint in der
  Geräteliste der App und lässt sich dort widerrufen.
- Jede Seite prüft das Cookie mit `AccountStore.deviceForToken`; ungültig/fehlend → `302 /login`.
- Mehrere Haushalte → `/haushalt` zur Auswahl (`AccountStore.selectHousehold`); einer → direkt.
- `/logout` (POST) widerruft das Gerät und löscht das Cookie.
- **CSRF:** jedes Formular trägt `_csrf` = HMAC-SHA256(Gerätetoken, fester Text „foody-web-csrf“) als Hex; htmx sendet es
  als Header `X-Foody-CSRF`. POST ohne gültiges Token → 403. Zusätzlich schützt `SameSite=Strict`.

## Daten lesen

- `HaushaltsDaten.fuer(household)` liefert ein unveränderliches `Haushalt`-Objekt (Rezepte, Zutaten, Planpositionen,
  Einkaufslisten mit Einträgen, Vorrat, Tagebuch-Einträge – als `domain`-Typen bzw. schlanke Web-Datenklassen, wo
  `domain` keinen Typ hat). Cache: `ConcurrentHashMap<household, Pair<Revision, Haushalt>>`; ist die höchste Revision
  in der Datenbank unverändert, wird der gecachte Stand genutzt.
- Unlesbare Payloads (neueres Format) werden übersprungen, nicht als Fehlerseite gezeigt.

## Seiten

| Pfad | Inhalt (Rechnung aus `domain`) |
|---|---|
| `/` Rezepte | Suche (Name, Tags, Zutaten; htmx mit `delay:150ms`), Filter Mahlzeit/Gang (`RezeptEinordnung`), Liste mit kcal je Portion (`RecipeProfiles`), archivierte ausgeblendet |
| `/rezept/{id}` | Portionen ±, skalierte Zutaten (`RecipeScaler`), Schritte, Nährwerte je Portion (`NutritionCalculator`, `NutritionResult.kcal`) |
| `/plan?ab=YYYY-MM-DD` | 7 Tage ab Datum, Mahlzeiten je Tag in Tagesreihenfolge (`Mahlzeit.reihenfolge`), kcal je Tag (`DayNutrition`), „Ø kcal pro Tag“ (`PlanDurchschnitt`) |
| `/einkauf` | neueste Liste (Auswahl, wenn mehrere), Einträge nach Abteilung (`ShoppingCatalog`), offen oben, **Abhaken** per htmx |
| `/tagebuch?tag=YYYY-MM-DD` | Einträge je Mahlzeit, Tagessumme (`Tagebuch.bilanz`), Wochenleiste mit kcal je Tag, Tagesziel aus dem Haushalt fehlt (bleibt App-Einstellung) |

**Abhaken (einzige Schreibaktion in Teil 1):** `POST /einkauf/{itemId}/abhaken` (htmx, CSRF) baut aus dem gespeicherten
Eintrag einen `SyncRecord` mit umgeschaltetem `checked`, neuem `checkedChangedAt` = jetzt, `baseRev` = gespeicherte
Revision und schickt ihn über `SyncService.push`. Antwort: die neu gerenderte Zeile. Bei Konflikt (MERGED) wird die
Zeile mit dem Stand nach der Zusammenführung gezeigt.

## Fehler

- Sitzung abgelaufen/widerrufen → `/login` mit Hinweis „Bitte erneut anmelden“.
- Rezept/Liste nicht gefunden → 404-Seite im Layout.
- Unerwarteter Fehler → 500-Seite ohne interne Details (Log wie bisher über `StatusPages`).
- Ohne JavaScript: Lesen funktioniert vollständig; Abhaken fällt auf ein normales Formular mit Weiterleitung zurück.

## Tests (`server/web/src/test`)

- `PayloadZuDomainTest`: Rezept mit Zeilen/Schritten, Zutat mit Nährwerten, Planposition, Einkaufseintrag,
  Tagebuch-Eintrag – gleiche Beispielwerte wie `SyncMapperTest` der App.
- `HaushaltsDatenTest`: zweiter Abruf ohne Änderung nutzt den Cache; nach `SyncService.push` neuer Stand.
- `SitzungTest`: ohne Cookie → 302 `/login`; falsches Passwort → Fehlermeldung, Drossel greift; Anmeldung → Cookie mit
  `HttpOnly`, `SameSite=Strict`; Gerät erscheint in `/api/v1/devices`; Logout widerruft; POST ohne CSRF → 403.
- Seiten-Tests je Seite mit vorbereiteten Datensätzen: erwartete Texte (Rezeptname, „350 kcal“, Abteilung …) und
  **Größe < 30 KB**.
- `AbhakenTest`: Abhaken erzeugt neue Revision; die App-Seite sieht sie per `/api/v1/sync/pull`.
- In der App (`SyncEndToEndTest`, In-Prozess-Server): ein vom Handy gepushtes Rezept erscheint auf `/` des Servers mit
  `WebOberflaeche.routen`.

## Nicht in Teil 1

Bearbeiten von Rezepten/Plan/Vorrat/Zutaten/Tagebuch (Teile 2–4), Fotos, Scan, Timer, Tagesziel-Einstellung im Web,
Mehrsprachigkeit (nur Deutsch).
