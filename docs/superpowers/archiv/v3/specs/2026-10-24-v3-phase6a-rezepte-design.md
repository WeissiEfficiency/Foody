# V3 Phase 6a: Rezepte

Stand: 2026-10-24 · Grundlage: [ADR 0011](../../../../adr/0011-v3-umfang-ein-nutzer.md), [ADR 0012](../../../../adr/0012-designsystem-v3-ofenwarm-haze.md),
[`ToDoS/v3-plan.md`](../../../../../ToDoS/archiv/v3-plan.md) (Phase 6), [`ToDoS/v3-abnahme.md`](../../../../../ToDoS/archiv/v3-abnahme.md)
(„Rezepte“), Phase 5 ([Spec](2026-10-24-v3-phase5-heute-design.md)) · im Chat abschnittsweise abgestimmt.

## Ziel

Phase 6 baut die Bereiche der V3 neu, in fünf Teilprojekten nacheinander (je Spec, Plan, PR): **6a Rezepte** →
6b Kochen → 6c Woche → 6d Einkauf → 6e Einstellungen. 6a ersetzt den Platzhalter „Rezepte“: Liste, Detail, Editor,
Fotos und Markdown-Import, auf dem Tablet zweispaltig.

**Fertig, wenn** alle Abnahmepunkte unter „Rezepte“ außer „Rezept direkt auf die Einkaufsliste“ (6d) abgehakt sind,
kcal je Portion an allen Anzeigeorten übereinstimmen, keine Datei in `:feature:rezepte` über 300 Zeilen hat und die
Bereiche auf dem Emulator mit den Daten des Test-Servers laufen.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Zuschnitt Phase 6 | fünf Teilprojekte 6a–6e in dieser Reihenfolge (verworfen: Einstellungen zuerst; Rezepte zweiteilen) |
| Zutaten im Editor | **Freitext je Zeile** („200 g Mehl, gesiebt“), live zerlegt mit demselben Parser wie der Markdown-Import (verworfen: Felder je Zutat wie v1; beides umschaltbar) |
| Rezeptliste | **ein Bildraster mit Filterchips** (verworfen: Abschnitte wie eine Startseite) |
| Schichtung | Rechnen in `:domain`, Daten in `:core:data`, Oberfläche in neuem `:feature:rezepte` (verworfen: Parser/Speichern im Feature) |
| Bewertung (Sterne) | keine Oberfläche (nicht in der Abnahmeliste); gespeicherte Werte bleiben |
| v1, Server, Schema | unverändert; Datenbank bleibt Version 10 |

## 1. Aufbau und Daten

| Modul | Inhalt |
|---|---|
| `:domain` | `ZutatZeile.lesen(text): ImportedIngredient?` – eine Zeile mit Menge, Einheit, Name, Hinweis, „optional“; `ZutatZeile.schreiben(...)`: Zeile zurück in Text. `MarkdownRecipeImporter` nutzt dieselbe Zerlegung, Verhalten unverändert |
| `:core:data` | `RezeptRepository` (Lesen als Flows, Schreiben in Transaktionen), `RezeptImportDienst` (Markdown-Import, portiert aus v1 `RecipeImportRepository`) |
| `:feature:rezepte` (`android/feature/rezepte`, neu, Paket `de.foody.feature.rezepte`) | Liste, Detail, Editor, Import; je Bildschirm ein ViewModel (Hilt), jede Datei ≤ 300 Zeilen |
| `:app3` | Ziele „Rezepte“, `RezeptDetail(id)`, neu `RezeptNeu`, `RezeptBearbeiten(id)` |

**`RezeptRepository`:**
- Lesen: Liste aller Rezepte als `RezeptKarte` (id, Name, Bild, kcal je Portion, Eiweiß je Portion, Zeit, Favorit,
  „wie oft gekocht“ = Anzahl `meal_slot` mit `cookedAt` des Rezepts, Ernährungsmerkmale, Einordnung, Tags,
  archiviert, angelegt). Ein Rezept vollständig (Kopf, Zeilen mit Zutatnamen, Schritte, Nährwerte, Einordnung).
- kcal und Nährwerte immer über `RecipeProfiles`/`NutritionCalculator` aus `:domain` mit denselben Abbildungen wie
  `HeuteRepository` (`DomainAbbildung`) – Karte, Detail und „Heute“ zeigen dieselbe Zahl.
- Schreiben, je eine Transaktion: anlegen und ändern aus einem `RezeptEntwurf` (Kopf, Zeilen als Text-Zerlegung,
  Schritte, Notizen, Quelle, Einordnung, Foto-Link), duplizieren (neue IDs für Rezept, Zeilen, Schritte; Name
  „… (Kopie)“), archivieren und wiederherstellen (`archivedAt`), Favorit umschalten. Jede Änderung setzt `updatedAt`
  und `version + 1`; die Sync-Trigger merken sie vor.
- Unbekannte Zutaten legt das Speichern im Katalog an, mit Namen und Kategorie wie v1
  (`IngredientCatalog.canonicalName`, `IngredientCatalog.guessCategory`); Treffer per Name ohne Groß/klein, dann über
  den vereinheitlichten Namen.

**Fotos:** über die vorhandene `FotoAblage` (verkleinert, `file:`-Link wie v1); ein ersetztes oder entferntes Foto wird
aufgeräumt, wenn kein Rezept es mehr nutzt. Der Sync überträgt Fotos schon (Phase 3).

## 2. Liste und Detail

**Liste** (Tab „Rezepte“):
- Suchfeld: Name, Tags und Zutaten, Teilstring ohne Groß/klein.
- Chips: Favoriten · Oft gekocht · Ernährung (vegetarisch, vegan, eiweißreich, Low Carb, leicht – `DietRules`/
  `RecipeProfile.diets`) · Mahlzeit · Tags · Archiv. Sortierung: Name, Neueste, kcal aufsteigend, Eiweiß, Oft
  gekocht. Ohne Archiv-Chip sind archivierte Rezepte ausgeblendet, mit ihm nur sie.
- Ein Bildraster aus `RezeptkarteRaster` mit kcal je Portion und Favoritenherz.
- Leer: `LeererZustandFlaeche` „Noch keine Rezepte“ mit „Anlegen“ und „Importieren“; ohne Treffer „Nichts gefunden“
  mit „Filter zurücksetzen“.
- Menü oben rechts: „Neues Rezept“, „Markdown importieren“.

**Detail** (`RezeptDetail(id)`):
- Foto oben (sonst `RezeptPlatzhalter`) mit Titelleiste auf Glas BILD: Name, Metazeile (Zeit, kcal je Portion).
- Portionen-Stepper (Schritt 1, 1–99): skaliert Zutatmengen und Nährwerte „je Rezept“; „je Portion“ bleibt.
- Zutaten (Menge, Einheit, Hinweis, „optional“), Arbeitsschritte nummeriert, Notizen, Quelle als Link, Einordnung
  (Mahlzeit/Gang, „vermutet“ bei automatischer Zuordnung).
- Nährwerte je Portion und je Rezept: kJ/kcal, Eiweiß, Kohlenhydrate, Fett, Ballaststoffe, Zucker, Salz;
  Vollständigkeit („Für 2 von 9 Zutaten fehlen Werte“).
- Aktionen: „Kochen“ → `Kochen(id, portionen)` (bis 6b Platzhalter), Favorit, Bearbeiten, Duplizieren, Archivieren
  (Snackbar „Rezept archiviert“ · „Rückgängig“); archiviert: Hinweis und „Wiederherstellen“.

**Tablet** (ab 840 dp): Liste links, Detail bzw. Editor rechts; die Auswahl bleibt beim Filtern stehen, solange das
Rezept in der Trefferliste ist.

## 3. Editor, Fotos, Import

**Editor** (`RezeptNeu`, `RezeptBearbeiten(id)`), eine scrollende Seite:
- Name (Pflicht), Foto (Photo-Picker, Kamera, entfernen), Portionen (1–99), Vorbereitungs- und Kochzeit (Minuten,
  0–1440), Tags (Komma), Mahlzeit/Gang als Chips (automatisch vermutet vorbelegt, „Automatisch“ setzt zurück).
- Zutaten: ein mehrzeiliges Textfeld, eine Zeile je Zutat. Darunter live je Zeile: Menge · Einheit · Zutat · Hinweis
  · „optional“ und „im Katalog“ bzw. „neu“. Markiert werden Zeilen ohne erkannte Zutat und Mengen außerhalb von
  `ZahlGrenzen`.
- Schritte: ein Textfeld, eine Zeile je Schritt (leere Zeilen zählen nicht). Notizen, Quelle (Link).
- Beim Bearbeiten werden Zeilen mit `ZutatZeile.schreiben` zu Text; Text → Speichern → Text bleibt gleich.
- „Speichern“ nur bei gültiger Eingabe (Name, Portionen, Zeiten, alle Zeilen erkannt); Zurück mit ungespeicherten
  Änderungen fragt „Änderungen verwerfen?“. Eingaben überstehen Drehen und Dunkelmodus.

**Markdown-Import** (Blatt über der Liste):
- „Dateien wählen“ (mehrere `.md`) oder „Ordner wählen“ (Storage Access Framework, z. B. OpenCloud-App).
- Verhalten wie v1: `MarkdownRecipeImporter`, `RezeptImport.vorbereiten` (Portionen aus der Datei oder geschätzt,
  Hinweis in den Notizen), Gruppen und „nach Bedarf“ wie dort; Rezepte mit schon vorhandener Quelle (`sourceUrl`)
  werden übersprungen.
- Zusammenfassung: „n importiert · m übersprungen (schon vorhanden) · k Fehler: Datei.md“; ein Fehler bricht den Rest
  nicht ab.

## 4. Navigation, Fehlerfälle, Tests

**Navigation:** Tab „Rezepte“ → Liste; `RezeptDetail(id)` aus Liste, „Heute“ und Deep Link `foody://rezept/{id}`;
`RezeptNeu`, `RezeptBearbeiten(id)`; der Tab „Rezepte“ gilt für alle diese Ziele als aktiv, wenn sie aus der Liste
kommen.

**Fehlerfälle:**
- Unbekannte oder per Sync gelöschte ID → „Rezept nicht gefunden“ mit „Zurück“.
- Während der Ansicht archiviert → Hinweis mit „Wiederherstellen“.
- Kamera abgebrochen oder Bild unlesbar → Hinweis, altes Foto bleibt.
- Import: Fehler je Datei in der Zusammenfassung.
- Ungültige Zeilen oder Mengen → „Speichern“ gesperrt, Zeile markiert.

**Tests:**
- `:domain`: `ZutatZeile` (Mengen mit Komma/Bruch/Spanne, Einheiten, „Prise“, „nach Bedarf“, Hinweis nach Komma,
  „(optional)“, ohne Menge, Rundweg `schreiben` → `lesen`); die bestehenden `MarkdownRecipeImporter`-Tests bleiben grün.
- `:core:data` (Room im Speicher): anlegen mit neuer Zutat, ändern, duplizieren, archivieren/wiederherstellen,
  Favorit, „oft gekocht“, Suche nach Zutat, Outbox bei aktivem Sync, Foto aufräumen; Import (portiert aus v1:
  Duplikat-Quelle, Ordner, Fehlerdatei, Portionen aus der Datei); gleiche kcal in Karte, Detail und `HeuteRepository`.
- `:feature:rezepte`: Bildschirmtests mit echtem ViewModel (Suche, Filter, Sortierung, Archiv mit Rückgängig,
  Editor-Validierung und Rundweg, Verwerfen-Rückfrage, nicht gefunden, Tablet zweispaltig, Schrift 200 %).
- Roborazzi: Liste, Detail, Editor – hell/dunkel × Handy/Tablet, erfundene Daten.
- Sichtprüfung auf dem Emulator mit den Daten des Test-Servers: Liste, Filter, Detail, neues Rezept mit Foto, Import
  einer Markdown-Datei.

**Abschluss:** `./gradlew check` grün; CI vergleicht die neuen Screenshots; Doku (`docs/architecture.md`,
`ToDoS/v3-plan.md`, `ToDoS/v3-abnahme.md`); PR gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

## Nicht in 6a

Kochmodus (6b) · Woche (6c) · Einkauf und „Rezept auf die Einkaufsliste“ (6d) · Zutatenkatalog mit Nährwerten (6e) ·
Bewertungen · KI-Erfassung (Phase 7).

## Risiken

- **Freitext-Zerlegung erkennt Zeilen falsch** → die Live-Anzeige je Zeile macht es sichtbar, bevor gespeichert wird;
  Tests mit vielen echten Zeilenformen aus dem Rezeptkorpus.
- **Import-Verhalten weicht von v1 ab** (erneuter Import aus OpenCloud verlässt sich auf das Überspringen) → v1-Tests
  portieren, Quelle als Schlüssel unverändert.
- **kcal-Abweichungen zwischen Anzeigeorten** → eine Rechenstelle, ein Test über alle Orte.
