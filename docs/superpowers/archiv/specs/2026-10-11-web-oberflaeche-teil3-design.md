# Web-Oberfläche, Teil 3: Planen und Einkaufen im Browser

Stand: 2026-10-11 · Baut auf Teil 1 und 2 auf (`2026-10-11-web-oberflaeche-teil1-design.md`,
`2026-10-11-web-oberflaeche-teil2-design.md`, Modul `:server-web`).
Gesamtvorhaben: 1 Grundgerüst + Lesen (fertig), 2 Rezeptpflege (fertig), **3 Planen + Einkaufen** (dieses Dokument),
4 Vorrat, Zutatenkatalog, Tagebuch-Einträge.

## Ziel und Erfolgskriterien

Wochenplan und Einkaufslisten im Browser so pflegen wie in der App: einplanen, ändern, Vorschläge übernehmen,
Liste aus dem Plan erzeugen und neu berechnen. Die Handys übernehmen alles beim nächsten Abgleich.

- Browser und App rechnen identisch: gleiche Einkaufsmengen, gleiche Vorschläge bei gleichem Seed, gleicher Vorratsabzug
  beim Kochen. Erreicht dadurch, dass jede Regel genau einmal in `shared/domain` steht.
- Gleichzeitige Änderungen am Handy gehen nicht verloren; ein inzwischen gelöschter Eintrag ist kein Fehler.
- Ressourcen wie bisher: Seiten < 30 KB (Plan mit 14 Tagen; Liste mit 75 Einträgen in der Einkaufsansicht – offener
  Punkt aus Teil 1, Markup dafür straffen); Bearbeiten-Modus mit 75 Einträgen < 50 KB. Kein
  Build-Werkzeug, kein zusätzlicher Container. Alles funktioniert ohne JavaScript; htmx nur für Abhaken (gibt es) und
  die Rezeptsuche auf der Auswahlseite.

Nicht in Teil 3: Katalog-Kacheln für Schnellartikel, Teilen/Exportieren der Liste, Rückgängig nach Löschen, Ändern der
Reihenfolge von Einträgen, Vorrat bearbeiten (Teil 4).

## Gemeinsame Regeln nach `shared/domain` (Ansatz A)

Vier Regeln stehen heute nur in den App-Repositories (Room-Code gemischt mit Logik). Sie werden als reine Funktionen
nach `shared/domain` gezogen; App **und** Server rufen sie auf. Die App verhält sich danach unverändert.

1. **Abgleich anwenden** (bisher `ShoppingRepository.applyDiff`): Bei GEÄNDERT bleibt ein abgehakter Eintrag nur
   abgehakt, wenn die neue Menge dieselbe Dimension hat und nicht größer ist als die alte. ENTFÄLLT löscht nur nicht
   abgehakte Einträge. NEU hängt Einträge hinten an (`sortOrder` ab max + 1). Die Listen-`version` steigt um 1.
2. **Rezept auf die Liste** (bisher `ShoppingRepository.addRecipe`): Bedarf eines Rezepts für N Portionen (ohne
   „nach Bedarf“, ohne optionale, ohne `IngredientCatalog.neverBuy`) wird zu einem passenden Eintrag addiert (manuell,
   nicht abgehakt, gleiche Zutat, Menge vorhanden, gleiche Dimension; Ergebnis in Basiseinheit), sonst als neuer
   manueller Eintrag angehängt.
3. **Vorratsabzug beim Kochen** (bisher `PlanRepository.markCooked`): Je Pflichtzeile die skalierte Menge vom Vorrat
   derselben Zutat abziehen (umrechnen nur, wenn sicher möglich; nie unter 0; Rest in der Einheit des Vorrats). Ergebnis:
   neue Mengen je geändertem Vorratseintrag. Schon gekochte Einträge werden nicht erneut abgezogen.
4. **Freitext auf der Liste** (bisher `ShoppingViewModel.addFromSearch`/`tapCatalog`): Bekannter Artikel
   (`ShoppingCatalog.find`) offen auf der Liste → abhaken; nur gekauft → zurück auf die Liste; sonst neu mit Abteilung
   `ShoppingCatalog.sectionFor(null, name)`. Unbekannter Text → neuer Eintrag in `ShoppingCatalog.OWN_ITEMS`.

Die Vorschau-Rechnung selbst (`GenerateShoppingListUseCase`, `ShoppingDiff`, `MealSuggestions`, `PantryCoverage`,
`PlanAuswahl`) ist schon gemeinsam. Die Zusammenstellung der Eingaben dafür (neverBuy-Ausschluss, nur Einträge mit
`toBuy > 0`, Vorrat nur mit Menge > 0 für die Abdeckung) wird ebenfalls als gemeinsame Funktion gefasst, damit App und
Server dieselben Eingaben bilden.

## Lesen und Schreiben auf dem Server

- **Lesen** wie bisher über `HaushaltsDaten`; neu auch Vorratseinträge (`PANTRY_ITEM` → `PantryItem` in
  `PayloadZuDomain`).
- **Schreiben** nur über `SyncService.push`, in zwei neuen Klassen:
  - `PlanSchreiber`: einplanen, Portionen ändern, um einen Tag verschieben, löschen, gekocht (mit Vorratsabzug),
    Vorschlag übernehmen.
  - `EinkaufSchreiber`: Liste aus Plan anlegen, neu berechnen + übernehmen, Rezept auf Liste, Freitext, Eintrag
    löschen, Notiz setzen, Liste löschen (Liste und ihre Einträge als Löschmarkierungen).
- Jeder Datensatz wird aus dem **aktuell gespeicherten** Roh-JSON gebaut (unbekannte Felder bleiben erhalten), nicht
  aus dem, was die Seite beim Anzeigen sah. Keine Konfliktseite wie bei Rezepten: Plan- und Listeneinträge sind klein,
  Abhaken hat den Merge des Servers.
- Reihenfolge beim Push: Liste vor Einträgen, Planeintrag vor Vorrat. Jeder Datensatz ist eine eigene Transaktion
  (wie beim App-Abgleich); bricht etwas dazwischen ab, sieht man einen Teilstand, den „Neu berechnen“ heilt.
- Neue Einträge: neue UUIDs; Herkunft (`sources`) wie in der App mit Rezeptname, Datum, Menge in Basiseinheit.
- **„Heute“** rechnet der Server künftig in der Zeitzone `FOODY_ZEITZONE` (Standard `Europe/Berlin`) statt UTC; gilt
  für alle Web-Seiten (offener Punkt aus Teil 1).

## Seiten

### Wochenplan (`/plan`)

- Bestehende Ansicht (Tage, Navigation ‹ Heute ›) bekommt die Wahl 7/14 Tage (`tage=7|14`).
- **Jeder Planeintrag** hat ein Formular `POST /plan/{id}` mit Knöpfen `aktion=minus|plus|frueher|spaeter|gekocht|loeschen`
  (Portionen −1/+1 in 1–99, Datum ±1 Tag). Danach 303 zurück auf `/plan?ab=…&tage=…#tag-JJJJ-MM-TT`. „Gekocht“ ist bei
  schon gekochten Einträgen nicht sichtbar; sie tragen „gekocht“.
- **„+ Hinzufügen“** je Tag → `GET /plan/neu?tag=…`: Mahlzeit-Auswahl (Vorgabe: erste Mahlzeit in der Reihenfolge
  Frühstück, Mittag, Abend, Snack, die an dem Tag fehlt; sonst Abendessen), darunter die passenden aktiven Rezepte
  (`PlanAuswahl.filtern`, Link „Alle zeigen“), Suchfeld wie in der Rezeptliste (htmx, ohne JS per Absenden). Je Rezept
  ein kleines Formular `POST /plan/einplanen` (tag, mahlzeit, rezept, portionen; Vorgabe die Portionen des Rezepts).
- **Rezeptseite** bekommt „Einplanen“: Tag (Vorgabe heute), Mahlzeit (Vorgabe Abendessen), Portionen → dasselbe
  `POST /plan/einplanen`, danach zurück zum Plan dieses Tages.
- **„Vorschlagen“** mit Mahlzeit-Auswahl → `GET /plan/vorschlag?ab=…&tage=…&mahlzeit=…&seed=…`: für die Tage ab heute im
  Zeitraum, an denen die Mahlzeit fehlt, `MealSuggestions.suggest` mit denselben Eingaben wie in der App (Favoriten,
  fehlende Pflichtzutaten laut Vorrat, zuletzt eingeplant, Einordnung). Anzeige „Tag → Rezept“, „Neu mischen“
  (seed + 1), „Übernehmen“ (`POST /plan/vorschlag`) mit den angezeigten Paaren als Felder `tag_i`/`rezept_i`. Der
  Server prüft jedes Paar (Rezept aktiv und im Haushalt, Tag im Zeitraum ±1 Jahr) und plant mit den Portionen des
  Rezepts ein. Keine passenden Tage oder Rezepte → Hinweis „Nichts vorzuschlagen“.

### Einkauf (`/einkauf`)

- **Einkaufsansicht** bleibt schlank; neu oben ein Feld „Was willst du einkaufen?“ (`POST /einkauf/{liste}/eingabe`,
  Regel 4). Ohne Liste legt die Eingabe eine leere Liste „Einkauf“ an.
- **„Neue Liste aus Plan“** → `GET /einkauf/neu?von=…&bis=…&vorrat=1`: Vorgabe die aktuelle Planwoche ab heute, Vorrat
  abziehen „ja“. „Vorschau“ zeigt je Zutat Menge zu kaufen, Bedarf und Vorratsabzug, je ein Kontrollkästchen `mit=<zutatId>`
  (vorausgewählt), Name (Vorgabe „Einkauf 12.–18.10.“). `POST /einkauf/neu` rechnet mit den abgewählten Zutaten als
  Ausschluss neu und legt die Liste an; Mengen aus dem Formular werden nicht übernommen. Nichts zu kaufen → Hinweis
  statt leerer Liste.
- **Listen mit Zeitraum:** „Neu berechnen“ → `GET /einkauf/{liste}/abgleich?vorrat=1` mit den Abschnitten Neu /
  Geändert (alt → neu) / Entfällt (abgehakte als „bleibt, schon gekauft“). „Übernehmen“ (`POST`) rechnet frisch und
  wendet Regel 1 an. Kein Unterschied → „Liste ist aktuell“.
- **Bearbeiten-Modus** `?bearbeiten=1`: je Eintrag Löschen (`POST /einkauf/eintrag/{id}/loeschen`), Notiz
  (`POST /einkauf/eintrag/{id}/notiz`, leer = entfernen) und Herkunft („Linsensuppe, Mo 12.10.“). „Liste löschen“ →
  Bestätigungsseite → `POST /einkauf/{liste}/loeschen`.
- **Rezeptseite** bekommt „Auf die Einkaufsliste“: Portionen + Liste (Vorgabe die zuletzt geänderte, höchste `rev`;
  keine Liste → neue „Einkauf“) → `POST /einkauf/rezept` (Regel 2), danach zur Liste mit Hinweis „N Zutaten hinzugefügt“.

## Fehler und Grenzen

- Alle neuen Routen über `webGet`/`webPost`: CSRF bei jedem POST (sonst 403), Formulargrenze 256 KB, Fehlerseite.
- Fremde oder unbekannte IDs (Planeintrag, Liste, Eintrag, Rezept) → 404, nichts geschrieben.
- Eingaben: Portionen 1–99; Datum ±1 Jahr um heute; von ≤ bis, höchstens 31 Tage; Listenname 1–200 Zeichen (getrimmt);
  Freitext 1–200 Zeichen; Notiz ≤ 1000 Zeichen; Mahlzeit nur die vier Werte. Fehler → Formular mit Eingaben und Meldung.
- Eintrag inzwischen gelöscht (z. B. am Handy): zurück zur Seite mit Hinweis „Eintrag gibt es nicht mehr“, kein Fehler.
- Server lehnt einen Datensatz ab (z. B. `MISSING_REFERENCE`, weil das Rezept inzwischen gelöscht ist): Seite
  „Das ging nicht – bitte neu laden.“, kein Teilschreiben über den abgelehnten Datensatz hinaus.

## Tests

- `shared/domain`: Unit-Tests für die vier Regeln und die gemeinsame Eingabebildung (abgehakt bleibt / kommt zurück,
  Entfällt nur offene, Aufaddieren in Basiseinheit, Vorratsabzug mit Umrechnung und nie unter 0, Freitext in allen drei
  Fällen).
- App: bestehende Tests bleiben grün; Repositories rufen die gemeinsamen Funktionen.
- `server/web`: je Seite wie in Teil 2 — einplanen, Portionen, verschieben, löschen, gekocht mit Vorratsabzug, Vorschlag
  mit Seed reproduzierbar und Übernahme genau der Paare, Liste aus Plan (mit Ausschluss), neu berechnen + übernehmen,
  Freitext, Löschen/Notiz, Liste löschen, Rezept auf Liste; CSRF 403 auf allen neuen POSTs; fremde IDs 404;
  Größen (Plan 14 Tage und Liste mit 75 Einträgen < 30 KB, Bearbeiten-Modus < 50 KB); Zeitzone für „heute“.
- E2E (`SyncEndToEndTest`, im Prozess, nicht auf dem Emulator): im Browser einplanen und Liste erzeugen → beides auf dem
  Handy, Mengen gleich wie in der App gerechnet.
