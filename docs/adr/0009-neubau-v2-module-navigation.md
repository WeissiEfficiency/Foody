# ADR 0009: Neubau v2 – Module, Navigation, Designsystem

Status: angenommen (2026-10-13), teilweise abgelöst durch ADR 0011 (Umfang, Web-App, Navigation, Tokens). Plan: `ToDoS/archiv/v2-plan.md`, Abnahme: `ToDoS/archiv/v2-abnahme.md`.

**Kontext:** Die Handy-App ist ein einziges Modul `:app` (13.319 Zeilen, 84 Dateien); einzelne Screens haben über
500 Zeilen. Vorrat und Zutaten liegen versteckt unter „Mehr“, das Tagebuch hat einen eigenen Tab. Web-Oberfläche
(`foody.css`) und App (`Glass.kt`, `Theme.kt`) pflegen ihr Aussehen getrennt. Foody soll nach v2 weiter ausgebaut
werden; neue Funktionen sollen dabei keine bestehenden Bereiche berühren.

**Entscheidung:**
- **Neubau statt Umbau:** App und Web-Oberfläche werden neu aufgebaut, der Funktionsumfang bleibt gleich
  (`ToDoS/archiv/v2-abnahme.md`). Neue Funktionen folgen erst nach dem Release 2.0. `:domain` bleibt mit allen Tests und wird
  nur ergänzt.
- **Module** (höchstens zwei Ordnerebenen, `ToDoS/archiv/projektstruktur-regeln.md`):
  `:app` (nur Navigation und Hilt-Zusammenbau) → `:feature:{heute, rezepte, kochen, woche, einkauf, kueche,
  einstellungen}` → `:core:{designsystem, data, sync, scan, timer, testing}` → `shared/` (`:domain`, `:api-protocol`,
  `:design-tokens`). Features hängen nicht voneinander ab; jedes veröffentlicht seine typisierte Route und eine
  `NavGraphBuilder`-Erweiterung. Kein Feature greift auf Room zu, nur auf Repositories aus `:core:data`.
  Gemeinsame Gradle-Einstellungen liegen als Convention-Plugins in `build-logic/`.
- **Navigation:** Tabs Heute · Rezepte · Woche · Einkauf · Küche. Das Tagebuch ist Teil von „Heute“; „Küche“ bündelt
  Vorrat, Zutatenkatalog und Scan; Einstellungen über das Profilsymbol.
- **Design:** Glas-Optik und Kacheln bleiben und werden optimiert (zwei feste Glasstufen, Kontrast gegen die dunkelste
  Stelle des Verlaufs, ein Kachelraster für Einkauf, Vorrat und Katalog). Eine Token-Datei
  `shared/design-tokens/tokens.json` erzeugt `FoodyTokens.kt` (Compose) und `tokens.css` (Web).
- **Plattform:** nur Android und Web. `minSdk` 36, `targetSdk` 37 (alle Geräte im Haushalt laufen mit dem neuesten
  Android).
- **Web-App** bleibt im Server gerendert (kotlinx.html + htmx), mobil zuerst, installierbar per Web-Manifest.

**Verworfen:**
- *Schrittweiser Umbau des bestehenden `:app`:* hielte alte Strukturen (große Screens, verstreute Komponenten) am Leben;
  ein Neubau mit Abnahmeliste ist bei gleichem Funktionsumfang übersichtlicher.
- *Kotlin Multiplatform / iOS / Desktop:* nicht gewünscht.
- *Web-App als Single-Page-App (React o. Ä.):* Build-Werkzeug und größere Seiten ohne Nutzen gegenüber htmx.
- *Glas-Optik durch flache Flächen ersetzen:* Nutzerentscheidung, das Aussehen bleibt.

**Folgen:** Die v2 entsteht neben der v1; bis zum Umstieg läuft die Alltagsversion unverändert, die v2 als
„Foody Debug“ gegen einen Test-Server. Geräte unter Android 16 werden nicht mehr unterstützt. Nach dem Umstieg wird der
v1-Code entfernt. Die Room-Datenbank wird migriert (v9 → v10), nie neu angelegt.
