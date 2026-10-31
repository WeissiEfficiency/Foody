# V3 Phase 6e2: Zutatenkatalog

Teil von Phase 6 („Bereiche neu bauen“), Abschnitt 6e Einstellungen: **6e1** Einstellungsseite (fertig, #106),
**6e2** Zutatenkatalog (diese Spec), **6e3** „Packung scannen“ mit dem Schalter „Online-Produktsuche“. Grundlage:
`ToDoS/v3-plan.md` (Phase 6) und `ToDoS/v3-abnahme.md` („Zutatenkatalog: Dichte, Stückgewicht, Synonyme, Dubletten
zusammenführen, Strichcode an der Zutat, Scan“ – der Scan folgt in 6e3).

## Ziel

Den Zutatenkatalog in der V3 ansehen und pflegen: suchen und filtern, eine Zutat bearbeiten (Name, Kategorie,
Nährwerte, Dichte, Stückgewicht, Strichcode), neu anlegen, löschen, Dubletten zusammenführen und Namen an den Katalog
angleichen. Eigenes Modul `:feature:zutaten`, Einstieg aus den Einstellungen.

**Fertig, wenn** die Abnahmezeile bis auf „Scan“ erfüllt ist, kein Screen über 300 Zeilen hat, Zusammenführen über
den Sync auf einem zweiten Gerät ankommt (Test) und die Sichtprüfung (siehe 4.) klappt.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Zuschnitt | 6e2 Katalog, 6e3 Packung scannen + Online-Schalter |
| Umfang | Anzeigen und vereinheitlichen: Liste, Editor, Anlegen, Löschen, Zusammenführen, „Namen vereinheitlichen“ |
| Filter | „Ohne Nährwerte“, „Eigene“, „Unbenutzt“, „Mit Strichcode“ |
| Aufbau | `ZutatenRepository` in `:core:data`, Modul `:feature:zutaten` (Weg A) |
| Synonyme | Nur anzeigen (aus `IngredientCatalog.synonymsOf`), nicht bearbeiten |
| Kategorie | Chips der sechs Abteilungen aus `IngredientCatalog` + „Keine“; ein freier v1-Wert bleibt als eigener Chip |
| Energie | Eingabe in kcal, gespeichert in kJ (`NutritionResult.kcalToKj`) |
| Gelöschte Startzutat kehrt zurück | Bekannte Grenze (siehe Risiken), keine Grabsteine |
| v1 / Server / Protokoll | Unverändert; DB bleibt v10 |

## 1. Daten (`:core:data`, Paket `zutaten`)

### Gemeinsames Zusammenführen

Heute gibt es die Ergänzungsregel zweimal (`ergaenztUm` in `SyncSpeicher`, `ergaenztAus` in `StartdatenLader`) und die
Schritte des Zusammenführens einmal im Sync (`zutatSchreiben`). Beides wird gemeinsam genutzt:

- `internal fun ZutatEntity.ergaenztUm(andere: ZutatEntity): ZutatEntity` (eine Datei im Paket `zutaten`): Nährwerte
  als Block nur, wenn die Zutat keine hat (`nutrientBasis == null`), samt Quelle; sonst einzeln Kategorie, Dichte,
  Stückgewicht, Strichcode, wenn leer. `SyncSpeicher` und `StartdatenLader` nutzen sie statt ihrer privaten Fassungen.
- `internal suspend fun zusammenfuehrenSchritte(db: FoodyDatenbank, quelle: ZutatEntity, ziel: ZutatEntity, jetzt: Long)`:
  Rezeptzeilen, Einkaufsartikel und Tagebucheinträge umhängen (`SyncDao.rezeptZeilenUmhaengen`,
  `einkaufsartikelUmhaengen`, `tagebuchUmhaengen`), Quelle löschen, Ziel mit `ergaenztUm(quelle)` und `updatedAt = jetzt`
  schreiben (nur wenn es sich ändert). Ohne eigene Transaktion – der Aufrufer hält sie. `SyncSpeicher.zutatSchreiben`
  ruft sie zwischen seinem Umbenennen und der Outbox-Nachbearbeitung auf; sein Verhalten bleibt gleich.

### `ZutatenRepository(db: FoodyDatenbank, uhr: () -> Long)`

Bereitgestellt in `DatenModul` wie die übrigen Repositories. Jede schreibende Methode ist eine Transaktion; die
Trigger legen alles in die Outbox.

**Lesen**
- `liste(): Flow<List<ZutatKurz>>` – je Zutat `id`, `name`, `kategorie`, `kcal: Int?` (je 100 g/ml, aus kJ gerundet,
  `null` ohne Nährwerte), `rezepte: Int` (Anzahl Rezepte mit der Zutat, neue gruppierte Abfrage), `strichcode`,
  `eigen` (ID beginnt nicht mit `seed-`), sortiert nach Name ohne Groß/klein.
- `anzahl(): Flow<Int>` für die Zeile in den Einstellungen.
- `zutat(id): Flow<ZutatEntity?>` und `verwendetIn(id): Flow<List<String>>` (Rezeptnamen, sortiert).
- Reine Funktion `filtern(liste, suche, filter: Set<ZutatFilter>): List<ZutatKurz>` mit
  `enum class ZutatFilter { OHNE_NAEHRWERTE, EIGENE, UNBENUTZT, MIT_STRICHCODE }`. Mehrere Filter gelten zusammen.
  Suche: Teilstring im Namen ohne Groß/klein, außerdem Treffer, wenn `IngredientCatalog.canonicalName(suche)` dem Namen
  entspricht („Paradeiser“ findet „Tomate“).
- `synonyme(name): List<String>`: `IngredientCatalog.synonymsOf(name)` ohne den Namen selbst.

**Editor-Entwurf.** Der Editor arbeitet mit Texten: `ZutatEntwurf(name, kategorie, basis: NutrientBasis?, kcal,
eiweiss, kohlenhydrate, zucker, fett, ballaststoffe, salz, dichte, stueckgewicht, strichcode, quelle)` und
`ZutatEntwurf.aus(entity)` (Zahlen mit Komma, ohne unnötige Nullen; kcal aus kJ ganzzahlig gerundet).
`pruefen(vorher, nachher): Map<ZutatFeld, String>` (rein) prüft **nur geänderte Felder**:

| Feld | Regel | Fehlertext |
|---|---|---|
| Name | getrimmt, Leerraum zusammengefasst, 1–200 Zeichen | „Name fehlt“ / „Höchstens 200 Zeichen“ |
| kcal | Zahl 0–1.000 | „0 bis 1.000“ |
| Eiweiß … Salz | Zahl 0–100 | „0 bis 100“ |
| Dichte | Zahl > 0 und ≤ 5 | „Mehr als 0, höchstens 5“ |
| Stückgewicht | Zahl > 0 und ≤ 10.000 | „Mehr als 0, höchstens 10.000“ |
| Strichcode | leer oder 8–14 Ziffern | „8 bis 14 Ziffern“ |

Komma und Punkt als Dezimalzeichen; leeres Feld = kein Wert.

**Schreiben** (Ergebnisse als `sealed interface ZutatErgebnis`)
- `anlegen(name): ZutatErgebnis` – neue UUID, Kategorie `IngredientCatalog.guessCategory(name)`; Name vergeben →
  `NameVergeben(id, name)`; sonst `Gespeichert(id)`.
- `speichern(id, vorher, nachher): ZutatErgebnis` – ungültig → `Ungueltig(fehler)`, nichts geschrieben. Sonst werden
  **nur die geänderten Felder** auf den *aktuellen* Stand der Zeile geschrieben. Daraus folgt:
  - unverändertes kcal-Feld → kJ bleibt exakt (kein Rundungsdrift bei Startwerten);
  - ein Sync-Stand, der während des Bearbeitens kam, bleibt in den übrigen Feldern erhalten;
  - alte Werte außerhalb der Regeln blockieren das Speichern nicht.
  Nährwerte: Hat danach mindestens ein Nährwert einen Wert, gilt `basis` (Standard `PER_100_G`); sind alle leer, werden
  Basis und Quelle `null`. Wurde ein Nährwert geändert und das Quellfeld nicht, wird die Quelle „Eigene Angabe“.
  Name vergeben (andere Zutat, gleicher Name ohne Groß/klein, auch Umlaute) → `NameVergeben(id, name)`; Strichcode an
  einer anderen Zutat → `StrichcodeVergeben(id, name)`; Zeile weg → `NichtGefunden`. Eine Constraint-Verletzung am
  Namensindex (gleichzeitiges Anlegen) wird zu `NameVergeben`.
- `loeschen(id): ZutatErgebnis` – ohne Rezeptzeilen gelöscht (`Geloescht`); sonst `InVerwendung(rezepte: Int)`, nichts
  gelöscht. Einkaufsartikel und Tagebucheinträge behalten Name und Werte (wie bei einer Löschung über den Sync).
- `zusammenfuehren(vonId, nachId, entwurf: Pair<ZutatEntwurf, ZutatEntwurf>? = null): ZutatErgebnis` – mit Entwurf
  werden zuerst dessen geänderte Felder (ohne Name) auf die Quelle geschrieben, damit Eingaben im Editor ins Ziel
  wandern; dann `zusammenfuehrenSchritte`. Alles in einer Transaktion. Gleiche IDs oder eine fehlende Zutat →
  `NichtGefunden`, nichts geändert. Ergebnis `Zusammengefuehrt(nachId, nachName)`.
- `vereinheitlichenVorschau(): List<Angleichung>` und `vereinheitlichen(): Int`. Beide nutzen die reine Funktion
  `angleichungen(zutaten): List<Angleichung>` mit `Umbenennen(id, alt, neu)`, `Zusammenfuehren(vonId, vonName, nachId,
  nachName)` und `KategorieErgaenzen(id, name, kategorie)`, wie v1 `harmonizeNames`. Die Zutaten werden nach Name
  (ohne Groß/klein, bei Gleichstand nach ID) nacheinander betrachtet, jeweils auf dem Stand nach den bisherigen
  Schritten: Ziel ist `IngredientCatalog.canonicalName(name)`. Gibt es eine *andere* Zutat mit diesem Namen (ohne
  Groß/klein), wird in sie zusammengeführt; sonst wird umbenannt, wenn sich der Name unterscheidet. Fehlt danach die
  Kategorie, wird `guessCategory` ergänzt (falls es eine gibt).
  `vereinheitlichen()` berechnet die Liste in der Transaktion neu (nicht die evtl. veraltete Vorschau), führt sie aus
  und gibt die Zahl der Schritte zurück.

### Zusammenspiel mit den Startdaten

Bisher überschreibt `StartdatenLader` einen „unveränderten“ Starteintrag (gleiche ID, gleicher Name, Startquelle)
vollständig. Mit dem Editor wären Änderungen an Startzutaten beim nächsten `Startdaten.VERSION` verloren. Deshalb:

- Nährwerte: Durch „Eigene Angabe“ als Quelle gilt eine bearbeitete Startzutat als verändert; der Lader füllt nur noch
  Lücken.
- Kategorie, Dichte, Stückgewicht, Strichcode: Beim Überschreiben behält der Lader vorhandene Werte; die Startdaten
  füllen nur Lücken. (Folge: Korrekturen dieser vier Felder in neuen Startdaten erreichen bestehende Installationen nur,
  wo das Feld leer ist.)

## 2. Oberfläche (`:feature:zutaten`) und Navigation (`:app3`)

**Navigation:** Neue Ziele `Zutaten` (Liste) und `Zutat(id: String)` (Editor). Die Einstellungen bekommen zwischen
„Tagesziel“ und „Sicherung“ den Abschnitt „Zutaten“ mit der Zeile „Zutatenkatalog“, rechts die Anzahl und „›“
(`EinstellungenRoute(onServer, onZutaten, onZurueck)`; Anzahl aus `ZutatenRepository.anzahl()`).

**Liste** – Kopf „EINSTELLUNGEN“ / „Zutatenkatalog“ mit Zurück-Pfeil und Menü (⋮ „Namen vereinheitlichen“), Inhalt
höchstens 720 dp breit:
- Suchfeld, darunter die vier Filterchips; darunter „249 Zutaten · 12 ohne Nährwerte“ (für die gefilterte Liste).
- Zeile: Kategorie klein darüber, Name, rechts „359 kcal“ bzw. „– kcal“, darunter „in 3 Rezepten“ / „in 1 Rezept“ /
  „unbenutzt“. Tipp → `Zutat(id)`.
- Leere Treffer: „Keine Zutat gefunden“.
- „+“ unten rechts → Dialog „Neue Zutat“ mit Namensfeld → `anlegen` → Editor. Name vergeben → im Dialog „Gibt es schon“
  mit „Öffnen“.
- „Namen vereinheitlichen“ → Dialog mit der Vorschau (bis 8 Zeilen „Zwiebeln → Zwiebel“, „Tomaten → Tomate
  (zusammenführen)“, dann „und n weitere“; Kategorien nur als Zahl „n Kategorien ergänzt“), Knöpfe „Abbrechen“ /
  „Anwenden“ → Meldung „12 Zutaten angepasst“. Leere Vorschau → Dialog „Alle Namen sind schon einheitlich.“ mit „OK“.

**Editor** – Kopf „ZUTAT“ / Name, Zurück-Pfeil, Menü (⋮ „Zusammenführen mit …“, „Löschen“); „Speichern“ unten fest:
- Name; darunter „Auch: zwiebeln, zwiebel“ (Synonyme), wenn vorhanden.
- Kategorie-Chips.
- Nährwerte mit Umschalter „je 100 g“ / „je 100 ml“; Felder kcal, Eiweiß, Kohlenhydrate, davon Zucker, Fett,
  Ballaststoffe, Salz (Dezimaltastatur); Quelle.
- Umrechnung: Dichte (g/ml), Stückgewicht (g).
- Strichcode (Zifferntastatur).
- „Verwendet in: Reispfanne, Curry und 1 weiteres“ bzw. „Noch in keinem Rezept“.
- Feldfehler erst nach „Fertig“ oder beim Verlassen des Feldes; „Speichern“ zeigt alle.
- Zurück mit Änderungen → „Änderungen verwerfen?“ (vorhandener Dialog aus dem Rezepteditor).
- Gespeichert → zurück zur Liste.
- `NameVergeben` → Dialog „„Zwiebel“ gibt es schon. Diese Zutat damit zusammenführen?“ → Bestätigung wie unten.
- „Zusammenführen mit …“ → Blatt mit Suchfeld und Liste der übrigen Zutaten → Bestätigung „„Zwiebeln“ in „Zwiebel“
  zusammenführen? 3 Rezepte werden umgestellt, „Zwiebeln“ wird gelöscht.“ (ohne Rezepte nur der zweite Teil) →
  zurück zur Liste, Meldung „Zusammengeführt in „Zwiebel““. Der aktuelle Entwurf wandert mit (siehe 1.).
- „Löschen“ → unbenutzt: „„Zwiebeln“ löschen?“ → zurück, Meldung „Gelöscht“; benutzt: Dialog „In 3 Rezepten
  verwendet. Zum Entfernen mit einer anderen Zutat zusammenführen.“ mit „Zusammenführen …“ / „Abbrechen“.
- Zutat verschwindet (anderes Gerät) → Meldung „Diese Zutat gibt es nicht mehr“, zurück zur Liste.

**ViewModels:** `ZutatenListeViewModel` (Suche, Filter, Liste, Vorschau, Anlegen) und `ZutatEditorViewModel` (Entwurf,
Fehler, Speichern, Löschen, Zusammenführen; Ergebnisse als Ereignisse für Dialoge und Navigation). Arbeit im
`viewModelScope`; ein zweiter Tipp während einer Aktion wird ignoriert.

## 3. Fehlerfälle

| Lage | Verhalten |
|---|---|
| Zutat auf anderem Gerät gelöscht, Editor offen | „Diese Zutat gibt es nicht mehr“, zurück |
| Ziel beim Zusammenführen inzwischen weg | `NichtGefunden` → „Die Zutat gibt es nicht mehr“, nichts geändert |
| Sync läuft gleichzeitig | Transaktionen laufen nacheinander; keine Sperre nötig |
| Gleicher Name gleichzeitig angelegt | Constraint-Verletzung → `NameVergeben` |
| Ungültige Eingabe | Feldfehler, nichts gespeichert |
| Prozessende beim Zusammenführen oder Vereinheitlichen | Eine Transaktion: ganz oder gar nicht |
| Zusammenführen kommt auf Gerät B an | `anwenden` schreibt erst Rezepte, dann Löschungen; hat B eine offene Änderung an einem Rezept mit der Quelle, bleibt die Quelle und wird wiederbelebt (`nochGebraucht`) – nichts geht verloren |

## 4. Tests

- `:core:data`:
  - `ZutatErgaenzenTest`: Nährwerte als Block nur bei leerer Basis, Einzelfelder nur wenn leer.
  - `ZutatEntwurfTest`: `aus` (Komma, kcal-Rundung), `pruefen` (jede Regel, nur geänderte Felder).
  - `ZutatenFilterTest`: jeder Filter, Kombination, Suche ohne Groß/klein, Synonymsuche.
  - `AngleichungenTest`: umbenennen, Kollision → zusammenführen, zwei Schreibweisen ohne Katalogeintrag → eine bleibt,
    Kategorie ergänzen, schon einheitlich → leer.
  - `ZutatenRepositoryTest` (Room im Speicher): anlegen (Kategorie geraten, Name vergeben); speichern (nur geänderte
    Felder, kcal unverändert → kJ exakt, Nährwerte geändert → „Eigene Angabe“, alle leer → Basis/Quelle `null`, Name
    vergeben auch „Äpfel“/„äpfel“, Strichcode vergeben, Zeile weg); löschen (unbenutzt, benutzt); zusammenführen
    (Rezeptzeilen, Einkaufsartikel, Tagebuch umgehängt, Lücken gefüllt, Quelle weg, Outbox enthält die Rezepte und die
    Löschung, Entwurf wandert mit, gleiche/fehlende IDs); vereinheitlichen (Ergebnis = Vorschau, Zahl der Schritte).
  - `StartdatenLaderTest`: bearbeitete Nährwerte einer Startzutat überleben eine neue Version; Kategorie, Dichte,
    Stückgewicht, Strichcode ebenso; Lücken werden gefüllt.
  - Bestehende Sync-Tests bleiben grün (gemeinsame Zusammenführung).
- `:core:sync` (`SyncLaufTest`, zwei Geräte gegen den Server im Speicher): Zusammenführen auf A kommt auf B an (Rezept
  zeigt aufs Ziel, Quelle weg); mit offener Rezeptänderung auf B bleibt die Quelle erhalten und nichts geht verloren.
- `:feature:zutaten`: ViewModels (Filter, Vorschau/Anwenden, Anlegen; Editor: geändert ja/nein, Speichern, Fehler,
  Ergebnisse → Ereignisse, Zutat verschwindet, Doppeltipp); Bildschirm (Chips, Dialoge, Blatt, „Verwerfen?“, Schrift
  200 %); Roborazzi: Liste hell/dunkel, Liste gefiltert leer, Editor Handy/Tablet, Vorschau-Dialog, Zielwahl-Blatt,
  „In Verwendung“-Dialog. CI vergleicht `:feature:zutaten:verifyRoborazziDebug`.
- `:feature:einstellungen`: Abschnitt „Zutaten“ mit Anzahl, Tipp ruft `onZutaten`; Bilder aktualisiert.
- `:app3`: Navigation Einstellungen → Zutatenkatalog → Editor → zurück.
- Sichtprüfung auf `Foody_Test` gegen den Test-Server: Filter und Suche, Startzutat bearbeiten (kcal unverändert
  lassen, Dichte ändern), eigene Zutat anlegen, löschen (unbenutzt) und „In Verwendung“, eine Dublette per Hand und eine
  per „Namen vereinheitlichen“ zusammenführen, danach synchronisieren und den Stand am Server prüfen.

## Nicht in 6e2

- „Packung scannen“, Online-Produktsuche und ihr Schalter (6e3).
- Synonyme bearbeiten, Nährwerte online suchen.
- Grabsteine für gelöschte Startzutaten.

## Risiken

- **Gelöschte Startzutat kehrt zurück:** Löscht man eine Startzutat oder führt sie in eine andere zusammen, legt der
  Lader sie beim nächsten Anstieg von `Startdaten.VERSION` (heute 8) wieder an – er findet sie weder per Name noch per
  ID. Sie ist dann unbenutzt und über „Unbenutzt“ zu finden. Eine echte Lösung bräuchte Grabsteine über alle Geräte
  (Schema- oder Protokolländerung).
- **Ignorierte Abgleich-Vorschläge:** Ihr Schlüssel enthält die Zutat-ID (`<id>|<Dimension>`). Nach dem Zusammenführen
  gilt ein ignorierter Vorschlag der Quelle nicht mehr; der Vorschlag fürs Ziel kann einmal neu erscheinen.
- **Umbau im Sync:** `zutatSchreiben` nutzt die gemeinsame Funktion. Die vorhandenen Sync-Tests zur
  Namenskollision sichern das ab; das Verhalten (inkl. „selbst vorgemerkt“) bleibt unverändert.
