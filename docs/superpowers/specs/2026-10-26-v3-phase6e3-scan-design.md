# V3 Phase 6e3: Packung scannen

Teil von Phase 6 („Bereiche neu bauen“), Abschnitt 6e Einstellungen: **6e1** Einstellungsseite (fertig, #106),
**6e2** Zutatenkatalog (fertig, #107), **6e3** „Packung scannen“ mit dem Schalter „Online-Produktsuche“ (diese Spec).
Grundlage: `ToDoS/v3-abnahme.md` („Packung scannen: Strichcode → Katalog → Open Food Facts (abschaltbar) → Foto der
Nährwerttabelle“, „Online-Produktsuche beim Scan an/aus“, der Scan-Teil der Zeile „Zutatenkatalog“) und ADR 0008
(Play-Dienste, Open Food Facts). Vorbild ist der Scan der v1 (`android/app/.../scan/`, `ui/common/PackungScanKnopf.kt`,
`ui/tagebuch/TagebuchDialoge.kt`).

## Ziel

Nährwerte einer Packung nicht mehr abtippen: Strichcode lesen, erst im eigenen Katalog, dann bei Open Food Facts
suchen, sonst das Foto der Nährwerttabelle auf dem Gerät auslesen. Werte werden nur vorausgefüllt und markiert;
Speichern ist die Bestätigung. Scannen geht im Tagebuch („Frei“), im Zutateneditor und beim Anlegen einer Zutat.
Die Online-Suche lässt sich in den Einstellungen abschalten.

**Fertig, wenn** die drei Abnahmezeilen erfüllt sind, kein Screen über 300 Zeilen hat und die Sichtprüfung (siehe 5.)
klappt.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Modul | Neues `:core:scan` (Ablauf, Open Food Facts, ML Kit, Einstellung, Scan-Knopf). Weicht von ADR 0011 ab, weil Tagebuch und Zutatenkatalog in zwei Features liegen, die sich nicht kennen dürfen → ADR 0013 |
| Werte-Typ | `Packungswerte` liegt in `:core:data` (sonst Zyklus: die Repositories dort brauchen ihn); `:core:scan` liefert ihn |
| Einstiege | Tagebuch „Frei“, Zutateneditor (Abschnitt „Nährwerte“), Dialog „Neue Zutat“ in der Zutatenliste |
| Ablauf | Wie v1/ADR 0008: Strichcode (Google Code Scanner, keine Kamera-Berechtigung) → eigener Katalog → Open Food Facts (wenn erlaubt) → Foto anbieten (Kamera oder Galerie, ML Kit Texterkennung über die Play-Dienste) |
| Tagebuch und Katalog | Wie v1 (der Plan übernimmt den v1-Umfang): Häkchen „Als Zutat speichern“ / „Zutat „…“ aktualisieren“; Vorgabe an nur bei neuem Namen; eine Wahl des Nutzers geht vor |
| Quelle | Der Scan setzt die Quelle („Open Food Facts“, „Packung (Foto)“), wie v1; Speichern mit dieser Quelle gilt nicht als „Eigene Angabe“ |
| Energie | kcal-Feld unverändert gegenüber dem Scan → der genaue kJ-Wert der Packung wird gespeichert |
| Online-Schalter | Abschnitt „Zutaten“ der Einstellungen; vorhandener v1-Schlüssel `Einstellungsschluessel.ONLINE_SUCHE` („onlineProduktsuche“, Prefs `foody`), Standard an |
| Ohne Play-Dienste | Scan-Knopf ausgegraut mit Hinweis; alles andere geht |
| v1 / Server / Protokoll | Unverändert; DB bleibt v10 (nur neue Abfragen); `:domain` unverändert (`NaehrwertScan`, `OcrElement`, `ScanErgebnis`, `Nutrient` sind da) |

## 1. Daten (`:core:data`)

**`zutaten/Packungswerte.kt`:**
- **`data class Packungswerte(name: String?, basis: NutrientBasis?, werte: Map<Nutrient, BigDecimal>, strichcode: String?, quelle: String)`**
  - `basis == null`: Das Foto hat keine Basis erkannt.
  - Hilfen: `kcal: Int?` (aus `ENERGY_KJ` über `NutritionResult.kcal`), `feldText(n: Nutrient): String` (ungerundet über
    `zahlText`) und `kcalText`.
- **`ZutatEntwurf.mitPackung(p): Pair<ZutatEntwurf, Set<ZutatFeld>>`:** Liefert den Entwurf mit den Werten der
  Packung und die Felder, die er markiert. Übernommen werden:
  - kcal und die sechs Makros, soweit die Packung sie hat;
  - die Basis, wenn `p.basis != null`;
  - die Quelle;
  - der Strichcode, wenn das Feld leer ist.
  
  Felder, die die Packung nicht hat, bleiben. Markiert werden nur die übernommenen Zahlen und der Strichcode.
- **`ZutatEntwurf.mitAenderungen` / `ZutatenRepository.speichern(id, vorher, nachher, scan: Packungswerte? = null)`**
  ergänzen die 6e2-Regeln um zwei Punkte, wenn `scan` gesetzt ist:
  - **Energie:** Gleicht der kcal-Text `scan.kcalText`, wird `energyKj` der genaue Wert der Packung (statt
    `kjAusKcal`).
  - **Quelle:** Gleicht `nachher.quelle` `scan.quelle`, greift die Regel „Nährwerte geändert und Quelle unverändert →
    ‚Eigene Angabe‘“ nicht. Das gilt auch beim erneuten Scan einer Zutat, die schon „Open Food Facts“ trägt.
- **Strichcode im Katalog:** neue Abfrage `ZutatDao.mitStrichcode(code): ZutatEntity?`.
- **Namenssuche** (`zutaten/ZutatSuche.kt`, gemeinsam für Tagebuch und „Neue Zutat“):
  `zutatMitNamen(alle, name)` vergleicht in Kotlin ohne Groß/klein (auch Umlaute, wie 6e2 `andereMitNamen`) zuerst
  den eingegebenen, dann den kanonischen Namen (`IngredientCatalog.canonicalName`). So findet „Mehl“ „Weizenmehl“.
- **`ZutatenRepository.anlegen(name, packung: Packungswerte? = null)`:** wie 6e2, mit Packung zusätzlich in einer
  Transaktion:
  - Basis (`null` → je 100 g), Werte und Quelle;
  - den Strichcode, wenn ihn keine andere Zutat trägt.
- **`TagebuchRepository.ausPackung(datum, mahlzeit, name, menge, einheit, werte: Packungswerte, alsZutat): String?`**
  läuft in einer Transaktion. `werte` sind die Werte je 100 g/ml, wie sie im Formular stehen. Für kcal gilt die
  Energie-Regel oben; Zucker, Ballaststoffe und Salz kommen unverändert aus der Packung.
  - **Ohne Häkchen:** Ein Eintrag „Frei“ mit den Werten für die Menge (`Tagebuch.naehrwerteZutat` mit einer Zutat aus
    diesen Werten, wie v1).
  - **Mit Häkchen und vorhandener Zutat** (`zutatMitNamen`): Die Zutat bekommt die Werte als Block. Das sind die Basis
    (falls bekannt), alle Werte der Packung und die Quelle; Nährwerte, die die Packung nicht hat, bleiben. Den
    Strichcode bekommt sie nur, wenn sie keinen hat und kein anderer ihn trägt.
  - **Mit Häkchen ohne Zutat:** Es wird eine neue angelegt (kanonischer Name, Kategorie geraten, Basis `null` → je
    100 g).
  - **Danach** folgt in beiden Häkchen-Fällen ein Eintrag „Zutat“ über `ausZutat` mit Menge und Einheit.

## 2. Modul `:core:scan`

Hängt von `:core:data`, `:core:designsystem` und `:domain` ab (kein Zyklus: `:core:data` kennt `:core:scan` nicht).
`settings.gradle.kts` bekommt `include(":core:scan")`. `:app3`, `:feature:heute`, `:feature:zutaten` und
`:feature:einstellungen` hängen davon ab. Die App-Version für den User-Agent liefert `:app3` über `AppModul`
(`ScanKonfiguration(appVersion)`, wie `SyncKonfiguration`).

**Übernommen aus v1** (umbenannt, sonst gleiches Verhalten): Schnittstellen `StrichcodeLeser`, `TabellenScanner`,
`ProduktSuche`, `KatalogSuche`, `PlayDienste`, `OnlineSucheErlaubt`; `ScanAusgang` (mit `Grund`); `PackungScan`
(Ablauf); `OpenFoodFacts.auswerten`; `OffProduktSuche` (eigener Ktor-Client ohne die lokale CA des Sync-Servers,
8 s Zeitlimit, gesendet werden nur die Produktnummer und ein User-Agent); `GmsStrichcodeLeser`,
`MlKitTabellenScanner`, `GooglePlayDienste` (vorladen, Modul nachladen).

Abweichungen von v1:
- **`ScanAusgang`:** liefert `Gefunden(Packungswerte)` sowie `ImKatalog(id, name, strichcode)` statt der v1-Entity.
- **`KatalogSuche`:** nutzt `ZutatDao.mitStrichcode`.
- **`PackungScan.online(code)`:** fragt Open Food Facts ohne den Katalog-Schritt („Online abrufen“, siehe Editor).
  Ist die Online-Suche aus, gibt es `NichtGefunden(ONLINE_AUS)`.
- **`ScanEinstellung`** (Singleton, `OnlineSucheErlaubt`): `onlineSuche: StateFlow<Boolean>`, `setzen(an)`; Prefs
  `Einstellungsschluessel.PREFS`, Schlüssel `Einstellungsschluessel.ONLINE_SUCHE`.

**Scan-Knopf `PackungScanKnopf(scan, onPackung, onImKatalog, modifier)`:**
- **Bedienung:** Der Knopf „Packung scannen“ mit Symbol öffnet ein Menü: „Strichcode“, „Foto der Nährwerttabelle“,
  „Aus der Galerie“. Während der Erkennung zeigt er einen Fortschritt.
- **Meldungen** stehen unter dem Knopf (siehe 4.). Nach „nicht gefunden“ und „keine Tabelle“ bietet er die beiden
  Foto-Wege als Textknöpfe an. Der dabei gelesene Strichcode hängt sich an die Foto-Werte. Ein neuer Versuch über das
  Menü vergisst ihn.
- **Zustand:** Er liegt in `ScanKnopfZustand` (rein testbar) und übersteht Drehen und Prozessende über einen `Saver`
  (Meldung, Foto anbieten, Strichcode, Pfad des Kamerafotos).
- **Kamerafoto:**
  - Es liegt kurz in `cache/scan/` und wird gelöscht nach der Erkennung, bei abgebrochener Kamera und beim Verlassen
    während der Erkennung.
  - Reste früherer Versuche räumt der Knopf weg, sobald er angezeigt wird.
  - Eigener FileProvider: `class ScanFotoProvider : FileProvider()`, Authority `${applicationId}.scan`, `res/xml` mit
    `<cache-path name="scan" path="scan/"/>`. Ein zweiter Eintrag mit `androidx.core.content.FileProvider` würde sich
    im Manifest mit dem aus `:feature:rezepte` stoßen.
- **Vorladen:** Sobald der Knopf sichtbar ist, lässt er die Module vorladen.

**Markierte Felder:** `:core:designsystem` bekommt eine gemeinsame Darstellung für vorausgefüllte Felder
(`vorausgefuellt: Boolean`: Rahmen und Beschriftung in der Akzentfarbe). Das Tagebuch und der Zutateneditor nutzen
sie.

## 3. Einstiege

### Tagebuch „Frei“ (`:feature:heute`)
- Über den Feldern von „Frei“ steht der Scan-Knopf.
- **`ImKatalog`:** Der Weg wechselt zu „Zutat“, die Zutat ist ausgewählt, die Suche steht auf ihrem Namen (sonst
  stünde sie oft nicht unter den ersten 30), der Fokus liegt auf der Menge (`FocusRequester` am `Zahlfeld`).
- **`Gefunden`:** „Frei“ zeigt die Packung (eigene Datei `PackungEingabe.kt`). Oben steht „Werte aus Open Food Facts
  – bitte prüfen“ bzw. „Werte aus dem Foto – bitte prüfen“.
  - **Name:** der Produktname über `nameBereinigt`, gekürzt auf 200 Zeichen, sonst leer.
  - **Menge:** mit Einheit g bzw. ml nach der Basis der Packung. Fehlt die Basis (Foto), stehen Chips „g“/„ml“ zur
    Wahl, Vorgabe g; die Wahl wird zur Basis.
  - **Werte:** darunter „je 100 g“/„je 100 ml“ und die Felder kcal, Eiweiß, Kohlenhydrate, Fett. Sie sind ungerundet
    vorbelegt (`feldText`) und markiert, solange sie dem Scanwert gleichen.
  - **Vorschau:** „= 359 kcal“ für die eingegebene Menge.
  - **Häkchen:**
    - Text: „Als Zutat speichern“ bzw. „Zutat „Haferflocken“ aktualisieren“ (`zutatMitNamen`).
    - Vorgabe: an, wenn es keine Zutat mit dem Namen gibt.
    - Ändert sich der Name, wird die Vorgabe neu bestimmt, solange der Nutzer das Häkchen nicht selbst gesetzt hat.
  - **„Zurück zu Frei“** verwirft die Packung.
- **Prüfung** (eigener Zweig in `gueltig()`, Regeln wie der 6e2-Editor):
  - Name 1–200 Zeichen.
  - Menge > 0.
  - kcal Pflicht, 0–1.000.
  - Makros leer oder 0–100.

  Speichern ist gesperrt, solange etwas fehlt. Ein Doppeltipp legt nichts doppelt an (`gesperrt` wie bisher).
- **Speichern:** über `TagebuchRepository.ausPackung`.
- **Zustand:** Die Packung liegt in den `Eingaben` im ViewModel und übersteht Drehen. Nach einem Prozessende ist sie
  weg, wie die übrigen Eingaben des Blatts heute.

### Zutateneditor (`:feature:zutaten`)
- Der Scan-Knopf steht im Abschnitt „Nährwerte“ über den Feldern.
- **`Gefunden`:**
  - `ZutatEntwurf.mitPackung` übernimmt die Werte, und das ViewModel merkt sich `scan` (die Packungswerte) und
    `markiert: Set<ZutatFeld>`. Darüber steht „Werte aus Open Food Facts – bitte prüfen“.
  - **Ändern:** `aendern` hebt die Markierung jedes Feldes auf, dessen Text nicht mehr dem Scanwert gleicht.
  - **Speichern:** übergibt `scan` an `ZutatenRepository.speichern`, damit genaue kJ und die Quelle gelten.
  - **Drehen:** Markierung und Scanwerte überstehen es (ViewModel). Nach einem Prozessende sind sie weg, wie der
    Entwurf.
- **`ImKatalog` mit dieser Zutat:** Meldung „Diese Packung gehört schon zu dieser Zutat“. Ist die Online-Suche an,
  gibt es dazu den Knopf „Online abrufen“ (`PackungScan.online`). Er liefert neue Werte wie `Gefunden`.
- **`ImKatalog` mit einer anderen Zutat:** Dialog „Diesen Strichcode hat schon „Zwiebel“.“ mit „Zusammenführen …“
  (die Bestätigung aus 6e2 mit diesem Ziel) und „Abbrechen“.

### „Neue Zutat“ (`:feature:zutaten`)
- Im Dialog „Neue Zutat“ steht unter dem Namensfeld der Scan-Knopf. Die Packung liegt im `ZutatenListeViewModel` und
  übersteht so Drehen.
- **`Gefunden`:** Ist das Namensfeld leer, füllt der Produktname es vor (bereinigt, auf 200 Zeichen gekürzt). Darunter
  steht „Werte aus Open Food Facts werden übernommen“.
- **„Anlegen“:** `ZutatenRepository.anlegen(name, packung)`, danach öffnet sich der Editor.
- **„Gibt es schon“ → „Öffnen“:** öffnet den Editor der vorhandenen Zutat. Die Packung geht dabei mit, und ihre Werte
  stehen dort markiert wie nach einem Scan.
- **`ImKatalog`:** Der Dialog schließt, der Editor dieser Zutat öffnet sich mit der Meldung „Schon im Katalog“.
- **Weitergabe an den Editor:** Packung bzw. Meldung laufen über `ZutatUebergabe` (Singleton in `:feature:zutaten`,
  wie `ZutatenMeldungen`). `ZutatEditorViewModel.laden(id)` holt sie einmal ab.

### Einstellungen (`:feature:einstellungen`)
- Im Abschnitt „Zutaten“ steht unter „Zutatenkatalog“ der Schalter „Online-Produktsuche“, Untertitel „Open Food Facts;
  gesendet wird nur die Produktnummer“.
- Er wirkt sofort und gilt für alle Einstiege.
- `EinstellungenViewModel` kombiniert dann sechs Flows (Listen-Variante von `combine`).

## 4. Meldungen und Fehlerfälle

| Lage | Verhalten |
|---|---|
| Keine Play-Dienste | Knopf ausgegraut, darunter „Scannen braucht die Google-Play-Dienste“ |
| Modul wird geladen | „Scanner wird vorbereitet – gleich noch einmal versuchen“ |
| Abgebrochen | nichts |
| Strichcode unbekannt | „Produkt nicht gefunden – Foto der Nährwerttabelle?“ + Foto-Knöpfe |
| Offline / Zeitüberschreitung / Serverfehler | „Keine Verbindung – Foto der Nährwerttabelle?“ + Foto-Knöpfe |
| Online-Suche aus | „Online-Suche ist aus – Foto der Nährwerttabelle?“ + Foto-Knöpfe |
| Open Food Facts ohne Energiewert oder mit unplausiblen Werten | wie unbekannt (`NaehrwertScan.plausibel`) |
| Foto ohne Tabelle | „Keine Nährwerttabelle erkannt – bitte näher und gerade fotografieren“ + Foto-Knöpfe |
| Sonstiger Fehler | „Scannen hat nicht geklappt“ |
| Bildschirm während der Erkennung verlassen | Erkennung bricht ab, Kamerafoto wird gelöscht, nichts übernommen |
| Strichcode gehört schon einer anderen Zutat (Anlegen, Tagebuch-Häkchen) | Zutat ohne Strichcode, sonst wie gewohnt |
| Foto ohne erkannte Basis | Editor: Basis bleibt; neue Zutat: je 100 g; Tagebuch: Chips g/ml (Vorgabe g) |
| Prozessende während des Kamerafotos | Knopf-Zustand kommt zurück (Saver); im Tagebuch ist das Ergebnis verloren (das Blatt startet neu), das Foto wird beim nächsten Anzeigen des Knopfs weggeräumt |

## 5. Tests

- **`:core:data`:**
  - `PackungswerteTest`: `mitPackung` mit und ohne Basis, Strichcode nur bei leerem Feld, Markierungen.
  - `ZutatenRepository`:
    - `speichern` mit `scan`: kcal unverändert → genaue kJ; Quelle bleibt „Open Food Facts“, auch beim erneuten Scan;
      kcal geändert → `kjAusKcal`.
    - `anlegen(name, packung)`: Werte, Quelle, Basis `null` → je 100 g, Strichcode frei/vergeben, Name vergeben.
  - `ZutatSucheTest`: roher und kanonischer Name, Umlaute.
  - `TagebuchRepository.ausPackung`:
    - ohne Häkchen → „Frei“ mit Werten der Menge;
    - mit Häkchen → neue Zutat bzw. vorhandene als Block aktualisiert („Mehl“ findet „Weizenmehl“);
    - Strichcode nur wenn frei; genaue kJ bei unverändertem kcal; Eintrag „Zutat“.
- **`:core:scan`:**
  - Aus v1 übernommen und angepasst: `PackungScanTest` (dazu `online(code)`), `OpenFoodFactsTest`,
    `OffProduktSucheTest` (Ktor `MockEngine`).
  - Neu: `ScanEinstellungTest` (Standard an, v1-Schlüssel, Umschalten) und `ScanKnopfZustandTest` (Meldungen, Foto
    anbieten, Strichcode an den Foto-Werten, neuer Versuch übers Menü vergisst ihn, Saver).
- **`:feature:heute`:**
  - ViewModel: Packung übernehmen, Name gekürzt, Häkchen-Vorgabe neu/vorhanden/Namensänderung/Nutzerwahl,
    Prüfregeln, ImKatalog → Weg „Zutat“ mit Suche, Doppeltipp.
  - Bildschirm mit einem Fake-Scan: Knopf, Packungsansicht, Chips bei fehlender Basis, „Zurück zu Frei“, Schrift 200 %.
  - Roborazzi: „Frei“ mit Packung hell/dunkel.
- **`:feature:zutaten`:**
  - Editor übernimmt die Packung und markiert sie, Markierung endet beim Ändern, Speichern mit genauen kJ.
  - ImKatalog: mit dieser Zutat inkl. „Online abrufen“, mit anderer Zutat → Zusammenführen.
  - „Neue Zutat“ mit Packung, „Gibt es schon → Öffnen“ mit Packung, ImKatalog → Editor mit Meldung.
  - Roborazzi: Editor mit übernommenen Werten, „Neue Zutat“ mit Packung.
- **`:feature:einstellungen`:** Der Schalter zeigt den Stand, Umschalten schreibt; Bilder aktualisiert.
- **`:app3`:** baut mit `:core:scan`. Das zusammengeführte Manifest enthält beide FileProvider.
- **Sichtprüfung** auf `Foody_Test` (Play-Store-Abbild):
  - Foto einer echten Nährwerttabelle aus der Galerie, einmal im Tagebuch, einmal im Editor; Werte prüfen, speichern,
    Tagebuch-Eintrag und Zutat ansehen.
  - Online-Schalter aus → „Online-Suche ist aus“.
  - Code Scanner öffnen und abbrechen.
  - Strichcode-Treffer bei Open Food Facts: im Emulator nur, wenn sich ein Code einlesen lässt; sonst auf Stefans Gerät
    (vorher fragen).

## Nicht in 6e3

- KI-Lesen der Tabelle (Phase 7).
- Eigene Kameraansicht (CameraX).
- Werte für mehrere Packungsgrößen.
- Strichcode im Rezept.
- `SavedStateHandle` für Editor und Tagebuch-Blatt: Nach einem Prozessende sind Eingaben weiter verloren, wie seit 6e2.

## Risiken

- **Open Food Facts:** Die Werte sind gemeinschaftlich gepflegt, also nur ein Vorschlag. Sie werden markiert,
  Speichern ist die Bestätigung.
- **Emulator:** Code Scanner und Texterkennung kommen aus den Play-Diensten. Einen Strichcode zu zeigen ist im
  Emulator schwer; die Sichtprüfung belegt den Strichcode-Weg dort eventuell nur bis „abgebrochen“.
- **Neues `:core:scan` gegen ADR 0011:** bewusste Abweichung (ADR 0013).
- **Block-Aktualisierung im Tagebuch:** Mit Häkchen überschreibt die Packung die Nährwerte einer vorhandenen Zutat.
  Das macht der Häkchen-Text („Zutat „…“ aktualisieren“) sichtbar; die Vorgabe dafür ist aus.
