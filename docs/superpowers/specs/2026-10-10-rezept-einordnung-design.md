# Rezept-Einordnung und smarte Auswahl im Wochenplan – Design

Stand: 2026-10-10 · Status: zur Prüfung · Teilprojekt A von A–C

## 1. Ziel und Rahmen

**Ziel:** Beim Planen schnell ein passendes Essen finden. Jedes Rezept weiß, zu welchen **Mahlzeiten**
(Frühstück, Mittagessen, Abendessen, Snack) und welchen **Gängen** (Vorspeise, Hauptspeise, Nachspeise,
Brotzeit) es passt. Der Planer zeigt beim Hinzufügen nur Passendes; der Wochenvorschlag füllt eine gewählte
Mahlzeit mit passenden Rezepten.

**Vorgaben (vom Nutzer):**
- Einordnung wird **automatisch vermutet** (aus Tags und Name) und lässt sich im Rezept **manuell nachbessern**.
  Manuell Gesetztes wird nie überschrieben.
- Planer filtert **streng**: nur passende Rezepte, mit Schalter „Alle Rezepte zeigen“.
- Beide Dimensionen erlauben **mehrere Werte** je Rezept.
- Neue Code-Namen auf **Deutsch** (Klassen, Felder, Enum-Werte). Bestehender englischer Code wird nicht umbenannt.
- Erkennung und Migration **ignorieren Groß-/Kleinschreibung**.
- Nachspeisen gelten bei der Vermutung zusätzlich als Snack.

**Annahmen:**
- Rezepte kommen per Markdown-Import aus OpenCloud; erneute Importe überspringen vorhandene Rezepte
  (`Outcome.AlreadyImported` über `sourceUrl`), manuelle Einordnungen sind dadurch nicht gefährdet.
- Alle Geräte eines Haushalts werden zeitnah auf dieselbe App-Version aktualisiert.

**Nicht im Umfang:** Essenstracking (Teilprojekt B), Nährwerte per Foto/OCR (Teilprojekt C), Korrektur der
„Ø kcal pro Tag“-Anzeige im Planer (kommt mit B), Umbenennung bestehender englischer Bezeichner.

## 2. Datenmodell (`domain`)

```kotlin
enum class Mahlzeit { FRUEHSTUECK, MITTAGESSEN, ABENDESSEN, SNACK }
enum class Gang { VORSPEISE, HAUPTSPEISE, NACHSPEISE, BROTZEIT }
```

- Gespeichert werden immer die Enum-Namen; Anzeigetexte kommen aus `strings.xml`.
- Mengen werden als kommagetrennter Text gespeichert (`"MITTAGESSEN,ABENDESSEN"`). Hilfsfunktionen
  `Mahlzeit.mengeAus(text: String?): Set<Mahlzeit>?` / `Gang.mengeAus(...)` und `alsText(menge)`:
  - `null` → `null` (nicht festgelegt), `""` → leere Menge (bewusst keiner).
  - Unbekannte oder beschädigte Einträge werden beim Lesen übersprungen, nicht gelöscht
    (`"XYZ,ABENDESSEN"` → `{ABENDESSEN}`).
- `Mahlzeit.ausText(text: String): Mahlzeit?` ordnet Plan-Einträge zu: Enum-Name oder deutsche Bezeichnung
  („Frühstück“, „Mittagessen“, „Abendessen“, „Snack“), Groß-/Kleinschreibung egal (Kotlin `lowercase()`,
  nicht SQLite `lower()`, das Umlaute nicht kennt). Kein Treffer → `null` = „Sonstiges“.

### 2.1 Bedeutung von `null` und leer

| Gespeichert | Bedeutung | Ergebnis |
|---|---|---|
| `null` | nicht festgelegt | Vermutung gilt |
| `""` | bewusst keiner | leere Menge, keine Vermutung |
| `"ABENDESSEN"` | festgelegt | genau diese Werte |

Die Regel gilt je Dimension getrennt: Festgelegte Mahlzeiten und vermuteter Gang sind möglich.

### 2.2 `RezeptEinordnung` (reine Funktion)

```kotlin
data class Einordnung(
    val mahlzeiten: Set<Mahlzeit>,
    val gaenge: Set<Gang>,
    val mahlzeitenVermutet: Boolean,
    val gaengeVermutet: Boolean,
) {
    /** Leere Mahlzeitenmenge = passt überall. */
    fun passtZu(m: Mahlzeit) = mahlzeiten.isEmpty() || m in mahlzeiten
    val eingeordnet: Boolean get() = mahlzeiten.isNotEmpty()
}

object RezeptEinordnung {
    fun einordnen(name: String, tags: List<String>, mahlzeiten: Set<Mahlzeit>?, gaenge: Set<Gang>?): Einordnung
}
```

- Ist eine Dimension festgelegt (nicht `null`), wird sie unverändert übernommen (`…Vermutet = false`).
- Sonst Vermutung über Wortlisten; Abgleich gegen Tags (ganzes Tag) und Wörter des Namens, jeweils
  kleingeschrieben. Stichwörter ab 5 Zeichen treffen auch als Teilwort („Gemüsecurry“ enthält „curry“,
  „Kartoffelsalat“ enthält „salat“); kürzere (eis, dip, brot) nur als ganzes Wort, sonst träfe „eis“ auf
  „Reis“, „Fleisch“ und „Speise“. Mehrere Treffer werden vereinigt. Startumfang der Regeln:

| Stichwörter | Mahlzeiten | Gänge |
|---|---|---|
| frühstück, müsli, porridge, pfannkuchen, pancake, rührei, omelett, granola, overnight oats | Frühstück | – |
| dessert, nachspeise, nachtisch, kuchen, torte, tiramisu, mousse, pudding, eis, crumble, muffin | Snack | Nachspeise |
| vorspeise, suppe, salat | Mittagessen, Abendessen | Vorspeise, Hauptspeise |
| brotzeit, brot, aufstrich, dip | Abendessen | Brotzeit |
| snack, riegel, smoothie, joghurt | Frühstück, Snack | – |
| curry, pasta, nudel, spaghetti, lasagne, auflauf, risotto, eintopf, gulasch, pfanne, burger, pizza, braten, schnitzel, hauptgericht, flammkuchen, zwiebelkuchen, tortellini, tortelloni | Mittagessen, Abendessen | Hauptspeise |

- Kein Treffer → leere Mengen (Mahlzeit: passt überall; nicht eingeordnet).
- Die Vermutung wird **nie gespeichert**, sondern bei jedem Lesen berechnet. Regeländerungen wirken sofort auf
  alle nicht festgelegten Rezepte.

### 2.3 `MealSuggestions`

- `Candidate` bekommt `einordnung: Einordnung`.
- `suggest(candidates, days, mahlzeit, seed)` berücksichtigt nur Kandidaten mit `passtZu(mahlzeit)`.
- Die Bestimmung der zu füllenden Tage („Tag hat für diese Mahlzeit noch keinen Eintrag“) liegt im
  ViewModel; die übrigen Regeln (Vorrat, Favoriten, Wiederholungsabstand, Zufall) bleiben unverändert.

## 3. Datenbank, Sync, Backup (`app`, `sync-protocol`)

### 3.1 Migration v6 → v7 (`MIGRATION_6_7`)

1. `ALTER TABLE recipe ADD COLUMN mahlzeiten TEXT` und `ALTER TABLE recipe ADD COLUMN gaenge TEXT`
   (nullable, bestehende Rezepte → `null` → Vermutung).
2. Plan-Einträge umstellen: alle `meal_slot`-Zeilen (`id`, `slotType`) lesen, in Kotlin mit
   `Mahlzeit.ausText` zuordnen, bei Treffer `slotType` auf den Enum-Namen setzen. Kein Treffer → unverändert.
3. Sync-Trigger bleiben aktiv: Bei eingerichtetem Sync werden umgestellte Plan-Einträge einmal hochgeladen,
   andere Geräte erhalten die neuen Schlüssel. Gewollt.

`RecipeEntity` erhält `mahlzeiten: String? = null`, `gaenge: String? = null`. `slotType` bleibt `String`, damit
alte Freitexte („Sonstiges“) erhalten bleiben.

### 3.2 Sync und Backup

- `RecipePayload` und `BackupDto.Recipe`: `mahlzeiten: String? = null`, `gaenge: String? = null`.
  Durch `explicitNulls = false` werden nicht festgelegte Felder nicht übertragen.
- Eingang (`SyncApplier`/`SyncMapper`, Backup-Import): `slotType` läuft durch `Mahlzeit.ausText`; Treffer
  werden als Enum-Name gespeichert. So bringen ältere Daten keinen Freitext zurück.
- Server: keine Änderung (Payload ist für ihn undurchsichtiges JSON).
- **Bekannte Grenze:** Bearbeitet ein Gerät mit alter App-Version ein Rezept, sendet es die neuen Felder nicht
  mit; die manuelle Einordnung dieses Rezepts geht verloren und es gilt wieder die Vermutung. Wird in
  `docs/architecture.md` (Sync-Abschnitt) vermerkt.

## 4. Oberfläche

### 4.1 Rezept-Editor
- Neuer Block „Einordnung“ unter den Tags: zwei Reihen `FilterChip` (Mehrfachauswahl) für Mahlzeit und Gang.
- Nicht festgelegt: vermutete Chips vorausgewählt, aber abgeschwächt, Hinweis „vermutet“. Erstes Antippen
  übernimmt die angezeigte Auswahl samt Änderung als festgelegt.
- Je Reihe „Zurücksetzen“ (nur sichtbar, wenn festgelegt) → `null` → wieder Vermutung.
- Alle Chips abgewählt bei festgelegter Reihe → `""` (bewusst keiner).

### 4.2 Rezeptdetail
- In den Meta-Pills zusätzlich Mahlzeiten und Gänge (z. B. „Mittag · Abend · Hauptspeise“); vermutete Werte
  abgeschwächt dargestellt.

### 4.3 Rezeptliste
- Filterleiste: Chips für die Gänge und ein Chip „Nicht eingeordnet“ (Rezepte mit `eingeordnet == false`).
  Wirken im Speicher wie die vorhandenen Ernährungsfilter.

### 4.4 Planer – Dialog „Hinzufügen“
1. Mahlzeit-Chips Frühstück · Mittag · Abend · Snack (einfach); freies Textfeld entfällt. Vorauswahl nach
   Uhrzeit: vor 10 Uhr Frühstück, vor 14 Uhr Mittag, sonst Abend.
2. Gang-Chips (Mehrfachauswahl, optional; keine Auswahl = kein Gangfilter).
3. Rezeptliste zeigt nur Rezepte mit `passtZu(mahlzeit)` und – falls Gänge gewählt – mindestens einem
   gewählten Gang. Rezepte ohne vermuteten Gang fallen bei gewähltem Gangfilter heraus.
4. Schalter „Alle Rezepte zeigen“ hebt beide Filter auf. Suche wirkt zusätzlich wie bisher.
5. Leeres Ergebnis: Hinweis „Keine passenden Rezepte“ mit Knopf „Alle zeigen“.

### 4.5 Planer – Wochenvorschlag
- Im Vorschlagsdialog Mahlzeit wählbar (Standard Abendessen); „Neu mischen“ behält die Wahl.
- Gefüllt werden Tage im Zeitraum ab heute, die für diese Mahlzeit noch keinen Eintrag haben.
- Übernommene Einträge erhalten den gewählten Enum-Namen als `slotType`.

### 4.6 Planer – Tageskarte
- Einträge sortiert Frühstück → Mittag → Snack → Abend → Sonstiges (alphabetisch nach Freitext).
- Etikett: deutscher Name der Mahlzeit; bei „Sonstiges“ der gespeicherte Freitext.

## 5. Fehlerfälle

| Fall | Verhalten |
|---|---|
| Beschädigte Liste `"XYZ,ABENDESSEN"` | gültige Werte nutzen, Rest ignorieren, nichts zurückschreiben |
| Unbekannter `slotType` | „Sonstiges“, Freitext bleibt erhalten und sichtbar |
| Filter ergibt nichts | Hinweis mit „Alle zeigen“ |
| Keine Rezepte passen zum Wochenvorschlag | vorhandener Leer-Zustand des Vorschlagsdialogs |

## 6. Tests

**`domain` (JVM):**
- `RezeptEinordnungTest`: Wortlisten-Treffer, Teilwort im Namen, kurze Stichwörter nur als ganzes Wort
  („Gebratener Reis“ ist keine Nachspeise), Groß-/Kleinschreibung und Umlaute,
  Vereinigung mehrerer Treffer, festgelegt schlägt Vermutung je Dimension, `""` gegenüber `null`,
  `passtZu` bei leerer Menge.
- `MahlzeitTest`: `ausText` („Frühstück“, „FRÜHSTÜCK“, „FRUEHSTUECK“, „Brunch“ → `null`), `mengeAus`/`alsText`
  inkl. beschädigter Einträge.
- `MealSuggestionsTest`: Filter nach Mahlzeit; nicht passende Kandidaten werden nie vorgeschlagen.

**`app` (Instrumentation):**
- `MigrationTest` 6 → 7: Freitext-Slots („Abendessen“, „frühstück“, „Brunch“) → Enum-Namen bzw. unverändert;
  neue Spalten `null`.
- `SyncApplierTest`, `BackupZipTest`: `mahlzeiten`/`gaenge` überstehen Hin- und Rückweg; Freitext-`slotType`
  wird beim Eingang umgestellt.
- `PlannerSuggestTest`: Vorschlag für Frühstück enthält nur passende Rezepte und füllt Tage ohne Frühstück.

## 7. Dokumentation
- `docs/domain-rules.md`: Abschnitt Einordnung (Regeln aus 2.1–2.2).
- `docs/architecture.md`: DB v7, neue Payload-Felder, bekannte Grenze aus 3.2.
- `ToDoS/einkaufsliste-und-essenstracking.md`: Abschnitte 5 und 6 als erledigt markieren.
