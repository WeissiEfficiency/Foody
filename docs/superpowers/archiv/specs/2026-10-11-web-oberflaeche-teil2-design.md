# Web-Oberfläche, Teil 2: Rezeptpflege im Browser

Stand: 2026-10-11 · Baut auf Teil 1 auf (`2026-10-11-web-oberflaeche-teil1-design.md`, Modul `:server-web`).
Gesamtvorhaben: 1 Grundgerüst + Lesen (fertig), **2 Rezeptpflege** (dieses Dokument), 3 Planen + Einkaufen,
4 Vorrat, Zutatenkatalog, Tagebuch-Einträge.

## Ziel und Erfolgskriterien

Rezepte im Browser anlegen, bearbeiten, archivieren/wiederherstellen und aus Markdown-Dateien importieren – mit
denselben Regeln wie in der App, sodass die Handys die Änderungen beim nächsten Abgleich unverändert übernehmen.

- Ein im Browser gespeichertes Rezept erscheint nach dem Abgleich unverändert auf dem Handy.
- Felder, die der Browser nicht bearbeitet (Foto, Favorit, Bewertung, Felder neuerer App-Versionen), gehen nie verloren.
- Eine gleichzeitige Änderung am Handy wird nie still überschrieben.
- Ein Markdown-Import liefert auf dem Server dasselbe Rezept (Portionen, Zutaten) wie in der App.
- Ressourcen wie Teil 1: Seiten < 30 KB, kein Build-Werkzeug, kein zusätzlicher Container.

Nicht in Teil 2: Fotos, endgültiges Löschen von Rezepten (bleibt in der App), Kopieren von Rezepten, Favorit/Bewertung.

## Voraussetzungen aus dem Review von Teil 1

- **Login-CSRF:** `POST /login` und `POST /registrieren` nehmen nur Anfragen derselben Website an: abgelehnt (403), wenn
  `Sec-Fetch-Site` vorhanden und nicht `same-origin`/`none` ist, oder wenn `Origin` vorhanden ist und nicht zum
  `Host` der Anfrage passt. Fehlen beide Header (ältere Browser, Tests), wird angenommen.
- **Fehlerseite:** Alle Web-Routen laufen durch einen gemeinsamen Rahmen. Unerwartete Ausnahmen → 500-Seite im
  Foody-Layout („Da ist etwas schiefgegangen.“), ohne Details; Protokoll wie bisher. `ApiException` aus den
  Kontofunktionen → passende Meldung statt JSON.

## Editor

- Seiten: `GET /rezept/neu`, `GET /rezept/{id}/bearbeiten`; Speichern `POST /rezept/speichern` (verstecktes Feld `id`,
  leer = neu). Funktioniert ohne JavaScript.
- Felder: Name (Pflicht, ≤ 200 Zeichen), Portionen (1–99), Vorbereitung und Kochzeit (Minuten, optional, 0–1440), Tags
  (Komma-getrennt), Quelle (URL, optional), Notizen, Zubereitung (Textfeld, ein Schritt je Zeile, leere Zeilen
  entfallen), Einordnung: Auswahl „automatisch vermuten“ oder „festlegen“ mit Kontrollkästchen für Mahlzeiten
  (Frühstück, Mittagessen, Abendessen, Snack) und Gänge (Vorspeise, Hauptspeise, Nachspeise, Brotzeit).
- **Zutaten als Zeilen:** Menge (leer = nach Bedarf, sonst Zahl mit Komma oder Punkt in den Grenzen von `ZahlGrenzen`,
  ≥ 0), Einheit (Auswahl aller `MeasureUnit`, Anzeige über `symbol`), Zutat (Freitext mit Vorschlägen aus dem Katalog
  des Haushalts per `<datalist>`), Hinweis, optional, entfernen. „Zeile hinzufügen“ per htmx (`GET /rezept/zeile`
  liefert eine leere Zeile); ohne JavaScript stehen immer 3 leere Zeilen unter den vorhandenen. Leere Zeilen (ohne
  Zutatennamen) werden ignoriert.
- Fehler (Pflichtfeld, Zahl ungültig, kein Name) → Formular mit allen Eingaben erneut, Feld markiert, Meldung oben.

## Speichern (`RezeptSchreiber`)

1. **Zutaten auflösen** je Zeile, in dieser Reihenfolge: exakter Name im Katalog des Haushalts (ohne Groß-/
   Kleinschreibung) → über `IngredientCatalog.canonicalName` vereinheitlichter Name → **neue Zutat** (ID = UUID, Name =
   kanonischer Name, Kategorie = `IngredientCatalog.guessCategory`). Neue Zutaten werden zuerst per
   `SyncService.push` gesendet; meldet der Server `MERGED` mit `canonicalId`, nutzt das Rezept diese ID.
2. **Rezept-Datensatz:** Ausgangspunkt ist das gespeicherte rohe JSON (bei neuen Rezepten leer). Der Browser ersetzt
   nur seine Felder (`name`, `servings`, `prep`, `cook`, `notes`, `tags`, `sourceUrl`, `lines`, `steps`, `mahlzeiten`,
   `gaenge`); alles andere (`photo`, `favorite`, `rating`, `archivedAt`, unbekannte Felder) bleibt. Neue Rezepte:
   `favorite = false`. Zeilen-IDs und Schritt-IDs werden neu vergeben (wie beim Speichern in der App).
   Einordnung: „vermuten“ → `mahlzeiten`/`gaenge` = `null`; „festlegen“ → `alsText(menge, bisher, entries)` (unbekannte
   Werte bleiben, wie in der App).
3. **Gleichzeitige Änderung:** Das Formular trägt die beim Öffnen gesehene Revision (`rev`). Ist die gespeicherte
   Revision beim Speichern eine andere, wird **nichts** geschrieben; die Seite zeigt „Inzwischen geändert (z. B. am
   Handy)“ mit „Aktuellen Stand laden“ (Editor neu) und „Trotzdem speichern“ (gleiches Formular mit `erzwingen=1` und
   der neuen Revision). Bei Erfolg → `303` auf `/rezept/{id}`.
4. **Archivieren/Wiederherstellen:** `POST /rezept/{id}/archivieren` bzw. `/wiederherstellen` setzt `archivedAt` = jetzt
   bzw. `null` (rohes JSON, Rest unverändert). Rezeptliste: Umschalter „Archiv“ (`/?archiv=1`).

Alle Schreib-Routen: Sitzung mit Haushalt, CSRF wie Teil 1, Datensätze nur des eigenen Haushalts (fremde ID → 404).

## Markdown-Import (`/import`)

- `GET /import`: Formular mit Dateiauswahl (`multiple`, `.md,.markdown,.txt`), Tag (Vorgabe „Import“ wie in der App).
- `POST /import` (multipart, CSRF): höchstens **50 Dateien**, je höchstens **1 MB** (wie `MAX_FILE_BYTES` der App);
  Überschreitung → Meldung, nichts importiert.
- **Gemeinsame Logik in `shared/domain`:** neues `RezeptImport` mit der reinen Rechnung aus
  `RecipeImportRepository.importParsed` der App – Portionen schätzen (`ServingsEstimator` mit festen Gramm, kcal und
  Fleischstückzahl), Zeilen bilden (Menge leer → 0, Einheit leer → Stück, Hinweis aus Gruppe und Notiz), Hinweistext
  („Quelle: …“, „Portionenzahl war in der Datei nicht angegeben – aus den Mengen auf N geschätzt, bitte prüfen.“ bzw.
  „… – auf N gesetzt, bitte prüfen.“). Die App nutzt danach dieselbe Funktion; ihr Verhalten bleibt gleich.
- Vorgabe-Portionen 4 (wie `DEFAULT_IMPORT_SERVINGS`). Quelle (URL) schon als Rezept vorhanden (auch archiviert) →
  übersprungen. Zutaten wie beim Editor aufgelöst.
- Ergebnisseite: importiert (mit Links), übersprungen, nicht erkannt.

## Dateien

In `server/web/src/main/kotlin/de/foody/server/web/`:

| Datei | Aufgabe |
|---|---|
| `WebRoute.kt` | gemeinsamer Rahmen (Fehlerseite, `ApiException`), Prüfung „gleiche Website“ |
| `RezeptSchreiber.kt` | Zutaten auflösen, Rezept-Datensatz bauen, Konfliktprüfung, Archivieren |
| `RezeptFormular.kt` | Formulardaten ↔ Felder, Validierung |
| `seiten/EditorSeite.kt` | Editor, Zeilen-Fragment, Konfliktseite |
| `seiten/ImportSeite.kt` | Upload und Ergebnis |

In `shared/domain`: `RezeptImport.kt`. In der App: `RecipeImportRepository` nutzt `RezeptImport`.

## Tests

- Domain: `RezeptImportTest` (Portionen geschätzt/gesetzt, Zeilen, Hinweistext) – Werte wie bisher in der App.
- App: bestehende Import-Tests (`MarkdownImportTest`, Instrumentation `FavoritesAndDedupTest`) bleiben grün.
- Web: Editor anlegen/bearbeiten (Felder kommen an), unveränderte Felder bleiben (Foto, Favorit, unbekanntes Feld),
  neue und vorhandene Zutaten, „Mehl“ → vorhandenes „Weizenmehl“, Konflikt (Handy ändert zwischendurch → nichts
  gespeichert, „Trotzdem speichern“ speichert), Validierungsfehler behalten Eingaben, CSRF auf allen neuen POSTs,
  fremdes Rezept → 404, Archivieren/Wiederherstellen und Archiv-Filter, Login von fremder Website → 403, Fehlerseite
  ohne Details, Import (eine im Test hinterlegte Beispieldatei im Chefkoch-Format → gleiche Portionen und Zutaten wie die App-Funktion,
  doppelte Quelle übersprungen, zu große Datei/zu viele Dateien abgelehnt).
- Ende-zu-Ende (App): im Browser bearbeitetes Rezept kommt per Abgleich auf dem Handy an.

## Server aktualisieren

Ja – nach dem Merge `git pull && docker compose up -d --build` auf dem Server.
