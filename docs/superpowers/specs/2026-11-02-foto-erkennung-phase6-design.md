# Spec: Fotoerkennung Phase 6 – genauer durch Portion und eigene Korrekturen

Stand 2026-11-02. Plan: `ToDoS/foto-erkennung-plan.md`, Phase 6. Baut auf Phase 3/4 auf
(`2026-11-02-foto-erkennung-phase3-4-design.md`). Kein Server, kein Schema, nichts wird gesynct.

## Warum

Die Messung (ADR 0017) zeigt: Den Namen trifft das Modell oft, die kcal liegen im Median um 65 % daneben. Der Fehler
steckt in der **Menge**, nicht in der Energiedichte. Geprüft an den 100 Nutrition5k-Bildern: Gramm des Modells mal
*perfekte* kcal-Dichte ergäbe 67 % Median-Fehler, nicht besser als die kcal des Modells (65 %). Deshalb **kein**
Umrechnen über den Zutatenkatalog (Idee 3 verworfen), sondern zwei Hilfen, die die Menge treffen:

1. **Eigene Korrekturen merken:** Was nach dem Foto gespeichert wurde, merkt sich Foody unter dem Namen, den das Modell
   genannt hat. Nennt es denselben Namen wieder, kommen die gespeicherten Werte statt der Schätzung.
2. **Portions-Chips:** „klein · normal · groß“ (× 0,7 / 1 / 1,4) rechnen kcal und Makros des Vorschlags um.

## Gedächtnis (`FotoGedaechtnis`, `:core:erkennung`)

- Schlüssel: der Name des Modells, bereinigt (`FotoErkennung.schluessel`: getrimmt, klein, Leerraum zu einem
  Leerzeichen). Wert: Name, kcal und Makros, wie gespeichert, plus Zeitpunkt.
- Gemerkt wird beim Speichern eines freien Eintrags, der aus einem Foto kam (auch nach Korrektur oder Portionswahl).
  Ein späteres Speichern unter demselben Modellnamen ersetzt den Wert.
- Höchstens 300 Einträge; darüber fällt der älteste weg. Datei `filesDir/fotoerkennung/gedaechtnis.json`
  (`org.json`), nur auf diesem Gerät, nicht gesynct, nicht in der Sicherung. Eine kaputte Datei zählt als leer.
- Für die Abnahme (Phase 5) hängt jedes Speichern eine Zeile an `filesDir/fotoerkennung/protokoll.jsonl`:
  Vorschlag des Modells, ob er aus dem Gedächtnis kam, Portion, gespeicherte Werte. Höchstens 500 Zeilen.

## Ablauf im Blatt

- Kommt ein Vorschlag und kennt das Gedächtnis den Namen: Felder mit den gemerkten Werten füllen, Hinweis
  „Wie beim letzten Mal – bitte prüfen“. Sonst wie bisher „Geschätzt aus dem Foto – bitte prüfen“.
- Unter dem Hinweis die Chips „klein“, „normal“ (gewählt), „groß“. Ein Chip rechnet aus dem Ausgangswert neu
  (`FotoVorschlag.mal`: kcal ganzzahlig, Makros auf 0,1 g, kaufmännisch gerundet). Nur Felder, die noch dem
  bisher angezeigten Wert gleichen, bekommen den neuen; selbst Getipptes bleibt.
- Die Chips stehen da, solange der Vorschlag gilt (bis zum nächsten Blatt). Markierung wie bisher: gleich dem
  angezeigten Wert → markiert.
- `Eingaben.foto` wird ein `FotoStand(modell, basis, ausGedaechtnis, portion)` und übersteht ein Prozessende.

## Regeln

Regel 29 bekommt einen Zusatz: Portion × 0,7 / 1 / 1,4 mit Rundung wie oben; das Gedächtnis gilt nur für genau
denselben bereinigten Modellnamen und liefert gespeicherte Werte unverändert.

## Tests

- `:domain`: `FotoErkennungTest` (Portion, Rundung, Schlüssel).
- `:core:erkennung`: `FotoGedaechtnisTest` (merken/nachschlagen, Ersetzen, Grenze, kaputte Datei, Protokoll).
- `:feature:heute`: `EintragenFotoTest` (Gedächtnis füllt, Portion lässt Getipptes stehen, Speichern merkt,
  Prozessende), `EintragenBlattFotoTest` (Chips, Hinweistext), Referenzbilder `foto_*` neu.
