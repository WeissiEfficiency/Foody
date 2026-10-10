# Essenstracking (Tagebuch) – Design

Stand: 2026-10-10 · Status: zur Prüfung · Teilprojekt B von A–C (A: `2026-10-10-rezept-einordnung-design.md`)

## 1. Ziel und Rahmen

**Ziel:** Sichtbar machen, was über den Tag gegessen wurde – je Mahlzeit (Frühstück, Mittagessen, Snack, Abendessen)
mit kcal und Makros, Tagessumme gegen das Tagesziel und einem kurzen Verlauf über 7 Tage.

**Vorgaben (vom Nutzer):**
- Geplante Mahlzeiten des Tages erscheinen als Vorschlag und werden mit „Gegessen“ übernommen (Portionen anpassbar);
  zusätzlich Rezepte, Katalog-Zutaten mit Menge oder freie Einträge.
- **Ein gemeinsames Tagebuch** für den Haushalt, synchronisiert wie der Plan, gerechnet **pro Person** (eine Portion je
  Eintrag = was eine Person isst); keine Personen.
- Freie Einträge: Zutat aus dem Katalog mit Menge (Werte aus dem Katalog) oder frei (Name + kcal, optional
  Eiweiß/Kohlenhydrate/Fett).
- Eigener Tab **„Tagebuch“** an der Stelle von „Vorrat“; **„Vorrat“ wandert unter „Mehr“**.
- Tagesansicht mit Wochenleiste (7 Balken).
- Nährwerte werden beim Eintragen **festgehalten** (Ansatz ①).
- Planer-„Ø kcal pro Tag“ wird mit korrigiert (Regel in 2.4).

**Annahmen:**
- Alle Geräte des Haushalts und der Sync-Server werden zeitnah aktualisiert; der Server zuerst.
- Tagesziel = vorhandenes kcal-Ziel (`GoalPreferences.dailyKcal`), kein neues Feld.

**Nicht im Umfang:** Nährwerte per Foto/OCR (Teilprojekt C), Personen/Ziele je Person, Statistikseite
(Wochen-/Monatsschnitt), Ziele je Mahlzeit.

## 2. Datenmodell und Logik

### 2.1 Tabelle `tagebuch_eintrag` (DB v8)

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | TEXT PK | UUID |
| `datum` | TEXT (LocalDate) | Tag des Essens |
| `mahlzeit` | TEXT | `Mahlzeit`-Enum-Name (Teil A) |
| `art` | TEXT | `REZEPT`, `ZUTAT`, `FREI` (`enum class TagebuchArt`) |
| `name` | TEXT | festgehaltener Anzeigename („Gemüsecurry“, „Joghurt“, „Apfel“) |
| `rezeptId` | TEXT? | bei `REZEPT`; lose Referenz, **kein** Fremdschlüssel |
| `planEintragId` | TEXT? | gesetzt, wenn aus einem Plan-Eintrag übernommen; lose Referenz |
| `portionen` | TEXT? (BigDecimal) | bei `REZEPT`; 0,5–10 in 0,5-Schritten, Standard 1 |
| `zutatId` | TEXT? | bei `ZUTAT`; lose Referenz |
| `menge` | TEXT? (BigDecimal) | bei `ZUTAT` |
| `einheit` | TEXT? (`MeasureUnit`) | bei `ZUTAT` |
| `energieKj` | TEXT? (BigDecimal) | festgehalten; bei `FREI` Pflicht (> 0) |
| `eiweiss`, `kohlenhydrate`, `fett` | TEXT? (BigDecimal) | festgehalten; `null` = unbekannt |
| `vollstaendig` | INTEGER (Boolean) | `false`, wenn beim Berechnen Energiewerte fehlten → Anzeige „≥“ |
| `createdAt`, `updatedAt` | INTEGER | wie überall |

Indizes: `datum`, `planEintragId`. Lose Referenzen, damit Löschen eines Rezepts, einer Zutat oder eines
Plan-Eintrags das Tagebuch nie verändert (vgl. ADR 0004 Einkaufs-Snapshot).

### 2.2 Logik in `domain` (`Tagebuch.kt`)

```kotlin
data class Naehrwerte(val energieKj: BigDecimal?, val eiweiss: BigDecimal?, val kohlenhydrate: BigDecimal?,
                      val fett: BigDecimal?, val vollstaendig: Boolean)

object Tagebuch {
    fun naehrwerteRezept(rezept: Recipe, zutaten: Map<String, Ingredient>, portionen: BigDecimal): Naehrwerte
    fun naehrwerteZutat(zutat: Ingredient, menge: BigDecimal, einheit: MeasureUnit): Naehrwerte?
    fun bilanz(eintraege: List<EintragWerte>): TagesBilanz
    fun offeneVorschlaege(planEintraege: List<MealSlot>, uebernommenePlanIds: Set<String>): List<MealSlot>
}
```

- `naehrwerteRezept`: Werte je Portion (`NutritionCalculator`) × `portionen`. `vollstaendig` = Energie vollständig.
- `naehrwerteZutat`: Menge über die vorhandene Umrechnung (`UnitConverter`, Dichte, Stückgewicht) in g bzw. ml der
  Nährwert-Basis, dann Werte je 100 × Menge/100. Nicht umrechenbar oder keine Nährwerte → `null`.
- `bilanz`: Summen je Mahlzeit und Tag (kcal gerundet, Makros in g gerundet); `vollstaendig` ist false, sobald ein
  Eintrag unvollständig ist oder keine Energie hat.
- `offeneVorschlaege`: Plan-Einträge des Tages, deren `id` nicht in `uebernommenePlanIds` steht.

### 2.3 Sync und Backup

- `RecordType.FOOD_LOG` (`"food_log"`), `TagebuchPayload` mit allen Feldern außer `id`/Zeitstempeln
  (Zahlen als Strings).
- `SyncTriggers`: `tagebuch_eintrag` als Wurzeltabelle (Insert/Update/Delete vormerken).
- `SyncApplier`/`SyncMapper`: Eingang nach den Plan-Einträgen; Last-Writer-Wins wie üblich.
- **Server:** neuer Enum-Wert in `sync-protocol` genügt; Payload ist für ihn undurchsichtig. Reihenfolge beim Ausrollen:
  **Server vor App** – ein alter Server lehnt den unbekannten Typ ab, eine alte App bricht beim Lesen ab.
- Sicherung: `BackupDto.tagebuch: List<TagebuchDto> = emptyList()` (ältere Sicherungen bleiben lesbar).

### 2.4 Planer: „Ø kcal pro Tag“

Der Durchschnitt zählt nur Tage, an denen **Abendessen und mindestens eine weitere Mahlzeit** geplant sind
(`Mahlzeit.ausText`); ist bei einem gezählten Tag `complete == false`, steht „≥“ vor dem Wert. Gibt es keinen solchen
Tag, entfällt die Zeile. Reine Funktion in `domain`:

```kotlin
data class PlanTag(val slotTypes: List<String>, val naehrwerte: DayNutrition)
data class Durchschnitt(val kcal: Int, val tage: Int, val vollstaendig: Boolean)
object PlanDurchschnitt { fun kcal(tage: List<PlanTag>): Durchschnitt? }
```

## 3. Oberfläche

### 3.1 Navigation
- Tabs: Rezepte · Planer · Einkauf · **Tagebuch** · Mehr. Icon `Icons.AutoMirrored.Outlined.MenuBook` (o. ä.).
- „Mehr“ (Einstellungen) bekommt den Eintrag **„Vorrat“** (wie „Zutaten & Nährwerte“), der `PantryRoute` öffnet.

### 3.2 Tagebuch-Bildschirm (`TagebuchScreen`, `TagebuchViewModel`)
1. **Kopf** (`ScreenHeader`): „TAGEBUCH“, Datum; Aktionen Zurück · Heute · Vor (wie Planer).
2. **Wochenleiste:** 7 Balken (6 Tage davor + gewählter Tag), Höhe = kcal; Tagesziel als gestrichelte Linie;
   über Ziel korallenrot (`tertiary`); gewählter Tag hervorgehoben; Tipp wechselt den Tag.
3. **Tagessumme:** „1.840 / 2.000 kcal“ (ohne Ziel nur „1.840 kcal“), Fortschrittsbalken, darunter E/K/F in g;
   „≥“, wenn unvollständig.
4. **Vier Abschnitte** Frühstück · Mittag · Snack · Abend mit Summe:
   - **Vorschläge aus dem Plan** (blasse Karte, „Geplant: …“) mit Knopf **„Gegessen“** → Eintrag mit 1 Portion;
     langes Drücken → Portionen-Dialog. Ein Vorschlag steht im Abschnitt, auf den `Mahlzeit.ausText(slotType)`
     zeigt; alte Freitexte ohne Treffer stehen unter **Abend** (früherer Standard des Planers) und werden auch
     als Abendessen übernommen.
   - **Einträge:** Name, Menge/Portionen, kcal (mit „≥“); Tipp → Bearbeiten (Portionen bzw. Menge, Mahlzeit,
     bei `FREI` auch Werte); Menü → Löschen.
   - **„+ Hinzufügen“** je Abschnitt.
5. **Leer:** „Noch nichts eingetragen. Tippe auf + bei einer Mahlzeit.“

### 3.3 Dialog „Hinzufügen“ (Mahlzeit vorgegeben)
- Reiter **Rezept:** Rezeptauswahl mit `PlanAuswahl.filtern` (Teil A) und Schalter „Alle“; Portionen-Stepper 0,5er.
- Reiter **Zutat:** Suche im Zutatenkatalog, Menge + Einheit, Vorschau „≈ 95 kcal“; nicht berechenbar →
  „Keine Nährwerte – frei eintragen?“ (wechselt zu Frei, Name übernommen).
- Reiter **Frei:** Name, kcal (Pflicht, > 0), E/K/F optional.
- Gespeichert werden immer die berechneten bzw. eingegebenen Werte (kJ = kcal × 4,184).

## 4. Fehlerfälle

| Fall | Verhalten |
|---|---|
| Zutat ohne Nährwerte / Einheit nicht umrechenbar | keine Werte; Hinweis mit Wechsel zu „Frei“, Name bleibt |
| Rezept mit unvollständigen Nährwerten | `vollstaendig = false`, „≥“ in Zeile, Mahlzeit und Tag |
| Rezept/Zutat später geändert oder gelöscht | Eintrag behält seine festgehaltenen Werte |
| Plan-Eintrag nach Übernahme gelöscht | Eintrag bleibt |
| Plan-Eintrag auf anderen Tag verschoben | Vorschlag wandert mit; übernommener Eintrag bleibt am Tag des Essens |
| Frei: kcal leer oder ≤ 0 | „Hinzufügen“ deaktiviert |
| Portionen außerhalb 0,5–10 | Stepper begrenzt |

## 5. Migration

`MIGRATION_7_8`: Tabelle und Indizes anlegen; `SyncTriggers.drop(db)` + `SyncTriggers.create(db)` (neue Wurzeltabelle).
Vorhandene Daten unverändert. Schema `8.json` exportieren.

## 6. Tests

**`domain` (JVM):**
- `TagebuchTest`: Rezept × Portionen (1, 0,5, 2); Zutat in g, ml (Dichte), Stück (Stückgewicht); nicht umrechenbar →
  `null`; `bilanz` je Mahlzeit/Tag inkl. „≥“-Weitergabe; `offeneVorschlaege` ohne Übernommene.
- `PlanDurchschnittTest`: Tag nur mit Abendessen zählt nicht; Abend + Mittag zählt; unvollständig → „≥“; kein Tag → null.

**`app` (Instrumentation):**
- `MigrationTest.migrate7To8…`: Tabelle, Indizes, Trigger vorhanden; Daten unverändert.
- `TagebuchViewModelTest`: Vorschlag „Gegessen“ → Eintrag mit `planEintragId`, Vorschlag verschwindet; Frei-Eintrag;
  Zutat-Eintrag mit berechneten kcal; Löschen; Rezept löschen lässt Eintrag unverändert.
- `SyncApplierTest`, `BackupZipTest`: Tagebuch-Einträge hin und zurück.
- Navigation: Tab „Tagebuch“, „Vorrat“ über „Mehr“ erreichbar.

**`server`:** `SyncTest`: `food_log` wird gespeichert und ausgeliefert.

**Sichtprüfung im Emulator:** Tagebuch hell/dunkel, Wochenleiste, Dialog „Hinzufügen“, Vorrat unter „Mehr“.

## 7. Dokumentation
- `docs/adr/0007-tagebuch-festgehaltene-naehrwerte.md`: Entscheidung ① mit Alternativen ② (live) und ③ (Plan-Flag).
- `docs/architecture.md`: DB v8, Sync-Typ `food_log`, Ausroll-Reihenfolge Server vor App.
- `docs/domain-rules.md`: Regeln zu Tagebuch (pro Person, festgehalten) und Planer-Ø.
- `ToDoS/einkaufsliste-und-essenstracking.md`: Abschnitte 2, 4 und 7 (offener Punkt Ø) als erledigt markieren.
