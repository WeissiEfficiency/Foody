# V3 Phase 6c: Woche

Teil von Phase 6 („Bereiche neu bauen“, Reihenfolge 6a Rezepte → 6b Kochen → **6c Woche** → 6d Einkauf → 6e
Einstellungen). Grundlage: `ToDoS/v3-plan.md` (Phase 6) und der Abschnitt „Woche“ in `ToDoS/v3-abnahme.md`.

## Ziel

Der Wochenplan der V3 in einem neuen Modul `:feature:woche`: eine Woche mit Pfeilen zum Blättern, Mahlzeiten je Tag
mit freien Namen, Portionen, Verschieben, „Gekocht“, Nährwerte je Tag mit optionalem Tagesziel und „Leere Tage
füllen“ mit Vorschau. Ersetzt den Platzhalter des Tabs „Woche“ aus Phase 5.

**Fertig, wenn** der Abschnitt „Woche“ der Abnahmeliste abgehakt ist, kein Screen über 300 Zeilen hat und die
Sichtprüfung auf dem Emulator (Woche mit Serverdaten, Eintrag anlegen, Leere Tage füllen, Kochen → „Fertig“ markiert
den Plan-Eintrag – der offene Gerätebeleg aus 6b) klappt.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Ansicht | Eine Woche (Mo–So) als Liste der Tage, „← KW 44 →“ zum Blättern, „Heute“ springt zurück |
| Verschieben | Über das Mahlzeit-Blatt: Tag dieser Woche oder vorige/nächste Woche (kein Ziehen) |
| Leere Tage füllen | Mahlzeit wählen (Standard Abendessen), Vorschau je Tag abwählbar und einzeln neu ziehbar, „Neu mischen“, „Übernehmen“ |
| Logik | `WocheRepository` in `:core:data`; `:feature:woche` ist Oberfläche |
| KI-Vorschlag | Bleibt Phase 7 |
| v1 / Server | Unverändert; DB bleibt v10 |

## 1. Aufbau und Daten

**`:core:data`** (`woche/`)

- `WocheRepository(db, uhr: () -> Long)`:
  - `woche(montag: LocalDate): Flow<WochenAnsicht>` – sieben `WochenTag` (Datum, Einträge, `DayNutrition`; nicht zu verwechseln mit `PlanTag` aus `:domain`) und
    `Durchschnitt?` (`PlanDurchschnitt`). Einträge je Tag sortiert: Frühstück, Mittagessen, Snack, Abendessen (über
    `Mahlzeit.ausText(slotType)`), dann eigene Namen alphabetisch, bei Gleichstand nach ID. `PlanEintrag` (id, datum,
    mahlzeit-Text, rezeptId, rezeptName?, bild?, kcalJePortion?, portionen, gekocht). Rezept gelöscht → `rezeptName =
    null`, zählt nicht in die Nährwerte. kcal und Nährwerte über dieselben Abbildungen wie Karte, Detail, „Heute“
    (`RecipeProfiles`, `DomainAbbildung`); `DayNutrition` je Person (Portionen der Mahlzeit ändern daran nichts).
  - Ändern (je eine Transaktion; `updatedAt = uhr()`; der `meal_slot`-Trigger trägt alles in die Outbox):
    `hinzufuegen(datum, mahlzeit: String, rezeptId, portionen): String`, `portionen(id, n)` (1–99),
    `verschieben(id, datum)`, `umbenennen(id, mahlzeit)` (1–30 Zeichen, getrimmt), `entfernen(id): PlanEintragEntity?`
    (für „Rückgängig“), `wiederherstellen(e)` (dieselbe ID), `gekocht(id, an)` (setzt bzw. löscht `cookedAt`).
  - `vorschlagen(tage: List<LocalDate>, mahlzeit: Mahlzeit, seed: Long): Map<LocalDate, String>` – baut die Eingaben
    für `LeereTageFuellen.vorschlagen`: je nicht archiviertem Rezept Einordnung, Favorit, Zahl der gekochten Einträge,
    letzter Planeintrag (auch Zukunft), Merkmale über `Abwechslung.merkmale(name, Pflichtzutaten)`; `belegt` = Merkmale
    der Einträge von 14 Tagen vor bis 7 Tage nach dem Zeitraum.
  - `leereTage(montag, mahlzeit, heute): List<LocalDate>` – Tage der Woche ab heute ohne Eintrag dieser Mahlzeit.
  - `uebernehmen(vorschlag: Map<LocalDate, String>, mahlzeit: Mahlzeit)` – legt alle in einer Transaktion an
    (Portionen = Standardportionen des Rezepts); Rezepte, die es inzwischen nicht mehr gibt, werden übersprungen.
  - `rezeptAuswahl(): Flow<List<RezeptKarte>>` – die Karten wie `RezeptRepository.karten()`, ohne archivierte; Suche und
    Sortierung nach Eignung macht `:feature:woche`.

**`:feature:woche`** (neu; hängt nur von `:core:*` und `:domain` ab)

- `WocheViewModel` – Montag der gezeigten Woche im `SavedStateHandle` (Start: Montag der aktuellen Woche), Tagesziel
  aus `TageszielSpeicher.tagesziel()`, Aktionen auf das Repository, „Rückgängig“ für Entfernen.
- Füllen-Zustand im `WocheViewModel`: Mahlzeit, Seed, Vorschlag, abgewählte Tage; „Neu mischen“ = neuer
  Seed, „↻“ an einem Tag = neuer Vorschlag nur für diesen Tag (die übrigen Vorschläge bleiben gesperrt);
  `uebernehmen()` mit Sperre gegen Doppeltipp.
- `WocheScreen`, `TagKarte`, `MahlzeitBlatt`, `MahlzeitWahl` (Frühstück/Mittagessen/Snack/Abendessen/eigener Name),
  `RezeptAuswahlBlatt`, `FuellenBlatt`.

**`:app3`**: Tab „Woche“ wird echt; Kochen → `Kochen(rezeptId, portionen)`, Rezept → `RezeptDetail(id)`.

## 2. Abläufe und Sonderfälle

- **Blättern:** Woche Mo–So; Pfeile ±1 Woche, „Heute“ springt zur aktuellen; die Woche übersteht Drehen und
  Tab-Wechsel.
- **Hinzufügen:** „+ Mahlzeit“ am Tag → Mahlzeit wählen (oder eigener Name, 1–30 Zeichen) → Rezeptauswahl mit Suche
  (passende Rezepte zuerst: `einordnung.passtZu`) → Portionen anfangs die des Rezepts. Mehrere Einträge je Mahlzeit
  sind erlaubt.
- **Mahlzeit-Blatt:** Portionen ±, Gekocht (umschalten), Kochen, Rezept ansehen, Verschieben (Mo–So dieser Woche,
  vorige/nächste Woche = gleicher Wochentag ±7), Umbenennen, Entfernen mit Snackbar „Rückgängig“.

| Lage | Verhalten |
|---|---|
| Rezept des Eintrags gelöscht (Sync) | „Rezept gelöscht“, nur Entfernen; zählt nicht in die kcal |
| Rezept archiviert | Bleibt im Plan; die Rezeptauswahl zeigt keine archivierten |
| Tag in der Vergangenheit | Bearbeitbar (Gekocht nachtragen); Füllen nimmt nur heute und später |
| Verschieben auf belegte Mahlzeit | Erlaubt, zwei Einträge |
| Füllen ohne passende Rezepte | „Keine passenden Rezepte – alles schon in den letzten 14 Tagen geplant“, nichts angelegt |
| Füllen ohne leere Tage | „Alle Tage ab heute haben schon ein <Mahlzeit>“ |
| Einzelner Tag ohne Vorschlag | „kein Vorschlag“, wird nicht angelegt |
| „Übernehmen“ doppelt | Genau einmal angelegt |
| Tag zwischen Vorschau und Übernehmen per Sync belegt | Wird trotzdem angelegt (zweiter Eintrag) |
| „Gekocht“ erneut | Nimmt die Markierung zurück |
| Portionen < 1 / > 99 | Knöpfe begrenzen auf 1–99 |
| Kein Tagesziel | kcal ohne Balken |

**Nährwerte:** je Tag kcal mit Eiweiß/Kohlenhydraten/Fett je Person; unvollständig → „mind.“ vor den kcal; mit
Tagesziel Balken „1.920 / 2.000 kcal“. „Ø kcal/Tag“ der Woche nach `PlanDurchschnitt`; `null` → nicht gezeigt.

## 3. Darstellung

- **Kopf:** „Woche“; darunter „← KW 44 · 26.10.–1.11. →“ mit Pfeilsymbolen und Chip „Heute“ (nur aktiv, wenn eine
  andere Woche gezeigt wird); rechts „Ø 1.850 kcal/Tag“.
- **Tag:** Glasfläche je Tag – Kopf „MO 26.10.“ (heute hervorgehoben), rechts kcal; mit Tagesziel ein dünner Balken
  wie `Naehrwertbalken`. Einträge als Zeilen: Mahlzeit klein, Rezeptname, „2 P.“, Haken bei gekocht, kleines Foto oder
  Platzhalter; Tipp öffnet das Mahlzeit-Blatt. „+ Mahlzeit“ als Textknopf am Ende des Tages.
- **Unten:** „Leere Tage füllen“ (gefüllt) über der Navigationsleiste.
- **Blätter:** Mahlzeit-Blatt (Rezeptname als Titel, Portionen ±, Aktionen als Knöpfe; Verschieben als Chips Mo–So +
  „Vorige Woche“/„Nächste Woche“); Rezeptauswahl (Suchfeld, Liste mit Name und kcal); Füllen-Blatt (Mahlzeit-Chips, je
  Tag „DI 27.10. · Bombay-Curry“ mit Haken und „↻“, unten „Neu mischen“ und „Übernehmen (n)“).
- **Tablet ab 840 dp:** Tage in zwei Spalten (Mo–Do | Fr–So), Inhalt höchstens 1.100 dp breit.
- Hell/dunkel, Schrift bis 200 % ohne abgeschnittenen Text.

## 4. Tests

- `WocheRepository` (Room im Speicher): Woche mit Reihenfolge der Mahlzeiten und Tages-kcal, gelöschtes Rezept zählt
  nicht, Hinzufügen/Verschieben/Umbenennen/Entfernen + Wiederherstellen (dieselbe ID), Gekocht an/aus, jede Änderung in
  der Outbox, `leereTage` nur ab heute ohne diese Mahlzeit, Vorschlag mit gleichem Seed gleich, 14-Tage-Sperre,
  archivierte nie, ohne passende Rezepte leer, `uebernehmen` in einer Transaktion und überspringt gelöschte Rezepte.
- `WocheViewModel`: Blättern, „Heute“, Übernehmen mit Doppeltipp-Sperre, einzelner Tag neu, Abwählen, Entfernen mit
  Rückgängig, Montag übersteht Neuaufbau.
- Bildschirm (Robolectric, echtes ViewModel): Woche mit Einträgen und kcal, „+ Mahlzeit“ mit eigenem Namen, Mahlzeit-
  Blatt (Portionen, Verschieben auf Mittwoch, Entfernen + Rückgängig), Füllen (Vorschau, Abwählen, Übernehmen),
  Schrift 200 %, Drehen, Tablet zweispaltig.
- `:app3`: Tab „Woche“ echt; Kochen und Rezept aus dem Mahlzeit-Blatt.
- Roborazzi: Woche, Mahlzeit-Blatt, Füllen-Blatt × hell/dunkel × Handy/Tablet; CI vergleicht
  `:feature:woche:verifyRoborazziDebug`.
- Sichtprüfung auf `Foody_Test` gegen den Test-Server: Woche mit Serverdaten, Eintrag anlegen, Leere Tage füllen,
  heutigen Eintrag kochen → „Fertig“ markiert ihn (Gerätebeleg aus 6b).

## Nicht in 6c

- Ziehen und Ablegen, Wochenvorlagen/Wiederholen, KI-Vorschlag (Phase 7).
- Einkaufsliste aus dem Plan (6d).
- Tagesziel einstellen (6e; hier nur lesen).

## Risiken

- **Vorschlagsqualität mit echten Daten:** `LeereTageFuellen` ist mit Testdaten geprüft; ob die Auswahl mit 112
  Rezepten passt, zeigt erst die Sichtprüfung – Gewichte werden nicht in 6c nachjustiert, sondern notiert.
- **Viele Einträge je Tag** (z. B. fünf Snacks) verlängern die Liste; keine Begrenzung, die Tageskarte wächst mit.
