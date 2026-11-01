# Fotoerkennung Phase 1: Modellantwort verstehen (`:domain`)

Stand: 2026-11-01. Teil von `ToDoS/foto-erkennung-plan.md`, Modell und Messung in ADR 0017. Reines Kotlin, ohne
Oberfläche und ohne Modell; das Modell läuft erst in Phase 2 (`:core:erkennung`).

## Ziel

Aus der Textantwort des Bild-Sprach-Modells wird ein **Vorschlag** für das Eintragen-Blatt: ein eigenes Rezept mit
Portionen, ein freier Eintrag mit geschätzten Werten oder ein Teller aus Katalogzutaten mit Gramm. Was nicht sicher
zugeordnet werden kann, wird nicht geraten.

## Eingabe

- `antwort`: der rohe Text des Modells. Gemessen (Phase 0) kommen JSON mit Codezaun (```` ```json ````), Zahlen mit
  Einheit (`"fat": 15g`), verschachtelte Nährwerte (`nutritional_summary.calories_kcal`), Zutaten als Liste
  (`ingredients: [{name, grams}]`) oder als Tabelle (`portion_size: {"rice": 200}`), Auswahl als `choice` und `portions`.
- `kontext`: Rezepte (Id, Name), Rezept-Ids aus dem heutigen Plan, Katalogzutaten (Id, Name).

## Ausgabe

`FotoVorschlag` (sealed):
- `Rezept(rezeptId, portionen)` – Nährwerte später aus dem Rezept (Regel 24).
- `Teller(zeilen: [TellerZeile(zutatId, name, gramm?)])` – Nährwerte aus dem Katalog.
- `Frei(name, kcal?, eiweiss?, kohlenhydrate?, fett?)` – Werte vom Modell, in der Oberfläche als geschätzt markiert.

`null`, wenn die Antwort kein lesbares JSON-Objekt enthält.

## Regeln

1. **Leser:** erstes JSON-Objekt im Text; Codezaun und Text davor/danach werden ignoriert. Zahlen mit Einheit
   (`15g`, `250 kcal`, `1,5`) und ein Komma vor `}`/`]` werden repariert. Schlüssel ohne Rücksicht auf Groß-/Kleinschreibung.
2. **Rezept:** `choice` (auch `recipe`, `auswahl`) gleicht einem Rezeptnamen – ohne Rücksicht auf Groß-/Kleinschreibung
   und Leerzeichen, sonst exakt. Haben zwei Rezepte den Namen, gewinnt das aus dem heutigen Plan. „none“, leer oder
   unbekannt → kein Rezept.
3. **Teller:** mindestens zwei Zutaten, und **jede** gehört über `IngredientCatalog.canonicalName` zu einer Katalogzutat
   (gleicher kanonischer Name; Regel 15 – nie über Ähnlichkeit). Eine einzige unbekannte Zutat → kein Teller.
4. **Frei:** sonst. Name aus `dish_name` (auch `dish`, `name`); fehlt er, bleibt er leer.
5. **Grenzen** (sonst bleibt der Wert leer): Portionen 0,25–6, auf Viertel gerundet, ohne Angabe 1; Gramm je Zeile
   1–1500; kcal 1–3000; Eiweiß, Kohlenhydrate, Fett je 0–300 g.

## Nicht in Phase 1

- Die Anweisung an das Modell und die Reihenfolge der Auswahlliste (Plan zuerst, „keins davon“) – das ist Phase 2,
  weil sie zum Modell gehört und dort gemessen wird.
- Englische Namen: Das Modell antwortet frei meist englisch. Für Rezept und Teller bekommt es die deutschen Namen als
  Liste (Phase 2); eine englische Synonymliste ist erst nötig, wenn der Teller daran scheitert.

## Tests

`ModellJsonTest` (Leser), `FotoErkennungTest` (Zuordnung und Grenzen); die Regeln kommen als Nr. 29 in
`docs/domain-rules.md`.
