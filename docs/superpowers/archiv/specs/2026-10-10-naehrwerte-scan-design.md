# Nährwerte per Strichcode und Foto – Design

Stand: 2026-10-10 · Status: zur Prüfung · Teilprojekt C von A–C (B: `2026-10-10-essenstracking-design.md`)

## 1. Ziel und Rahmen

**Ziel:** Nährwerte einer Packung schnell und ohne Abtippen erfassen – per Strichcode (Online-Produktdatenbank) oder
per Foto der Nährwerttabelle (Texterkennung auf dem Gerät). Die Werte werden immer nur **vorausgefüllt und
markiert**, nie ungeprüft übernommen.

**Vorgaben (vom Nutzer):**
- Einsatz im **Zutatenkatalog** (Dialog „Zutat anlegen/bearbeiten“) **und** im **Tagebuch** (Reiter „Frei“) mit
  „Als Zutat im Katalog speichern“ (standardmäßig an).
- Texterkennung über **Google ML Kit, Variante Play-Dienste** (Modell wird nachgeladen, App bleibt klein).
- Erkannte Werte erscheinen **im vorhandenen Formular**, markiert, mit Hinweis „bitte prüfen“.
- Parser: **Zeilen über Position bilden, dann Stichwort und erste Zahl** (Ansatz ①).
- **Strichcode zuerst**, Foto als Ausweichweg; die Zutat **merkt sich den Strichcode** (DB v9).
- Online-Produktsuche über **Open Food Facts**, abschaltbar in „Mehr“, **standardmäßig an**.

**Annahmen:**
- Die App wird nicht aus dem Play Store installiert; Code Scanner und Texterkennung laden ihre Module beim ersten
  Gebrauch über die Play-Dienste nach.
- Geräte ohne Play-Dienste: Scan-Knopf ausgegraut mit Hinweis; alles andere funktioniert wie bisher.

**Nicht im Umfang:** Rezeptseiten per Foto einlesen, eigene Kameraansicht (CameraX), Beiträge zurück an Open Food
Facts, Nährwerte je Portion von der Packung (nur je 100 g/ml).

## 2. Ablauf „Von Packung scannen“

1. **Strichcode lesen** mit dem Google Code Scanner (`GmsBarcodeScanning`, Formate EAN-13, EAN-8, UPC-A, UPC-E).
   Keine Kamera-Berechtigung nötig. Abbruch → nichts passiert. Ist die Online-Produktsuche aus, entfällt Schritt 3.
2. **Im eigenen Katalog suchen** (`ingredient.barcode`). Treffer:
   - Tagebuch: Reiter „Zutat“ mit dieser Zutat vorausgewählt; nur noch Menge eingeben.
   - Katalog: Hinweis „Gibt es schon: ‚Joghurt‘“ mit Knopf „Öffnen“ (öffnet den Bearbeiten-Dialog dieser Zutat).
3. **Open Food Facts fragen** (`GET https://world.openfoodfacts.org/api/v2/product/{code}.json?fields=product_name,product_name_de,nutriments,nutrition_data_per`).
   Gefunden und mindestens Energie vorhanden → Name, Werte je 100 g/ml und Basis vorausgefüllt und markiert;
   beim Speichern bekommt die Zutat den Strichcode.
4. **Sonst Foto:** Hinweis „Nicht gefunden – Nährwerttabelle fotografieren?“ mit „Foto aufnehmen“ / „Aus Galerie“.
   Der gelesene Strichcode wird trotzdem beim Speichern an der Zutat gemerkt.
5. Zusätzlich gibt es im Menü des Scan-Knopfs immer **„Nährwerttabelle fotografieren“** direkt (ohne Strichcode).

## 3. Texterkennung und Parser

### 3.1 Parser in `domain` (`NaehrwertScan.kt`, reine Funktion)

```kotlin
data class OcrElement(val text: String, val links: Int, val oben: Int, val unten: Int)
data class ScanErgebnis(val basis: NutrientBasis, val werte: Map<Nutrient, BigDecimal>)
object NaehrwertScan { fun auswerten(elemente: List<OcrElement>): ScanErgebnis }
```

- **Zeilen bilden:** Elemente (ML-Kit-`Line`s) werden zu einer Tabellenzeile zusammengefasst, wenn ihre vertikale
  Mitte innerhalb der halben mittleren Zeilenhöhe der Zeile liegt; innerhalb der Zeile nach `links` sortiert,
  Zeilen von oben nach unten.
- **Stichwörter** (kleingeschrieben, Teilwort):

| Nährwert | Stichwörter | Ausschluss in derselben Zeile |
|---|---|---|
| `ENERGY_KJ` | brennwert, energie, energy | – |
| `FAT_G` | fett, fat | gesättigt, saturated, fettsäure |
| `CARBS_G` | kohlenhydrat, carbohydrate | – |
| `SUGAR_G` | zucker, sugar | – |
| `FIBER_G` | ballaststoff, fibre, fiber | – |
| `PROTEIN_G` | eiweiß, eiweiss, protein | – |
| `SALT_G` | salz, salt | – |

- **Zahl:** die erste Zahl mit passender Einheit nach dem Stichwort in derselben Zeile (`g` für Gramm-Nährwerte;
  bei Energie `kJ` bevorzugt, sonst `kcal` × 4,184). Zahlformate: „3,5“ = 3,5; „1.234“ und „1 234“ = 1234;
  „<0,5“ / „< 0,5“ = 0,5; „Spuren“/„trace“ = 0. Steht die Zahl in der nächsten Zeile (Bezeichnung und Wert auf
  verschiedenen Höhen), wird nicht weitergesucht – lieber leer als falsch.
- **Basis:** `PER_100_ML`, wenn „100 ml“ vorkommt und „100 g“ nicht; sonst `PER_100_G`.
- **Plausibilität:** Energie ≤ 4000 kJ, Gramm-Nährwerte ≤ 100 g; Zucker ≤ Kohlenhydrate (sonst Zucker verwerfen).
  Verworfene Werte bleiben leer.

### 3.2 Erkennung in `app`

```kotlin
sealed interface ScanStatus {
    data class Erkannt(val ergebnis: ScanErgebnis) : ScanStatus
    data object WirdGeladen : ScanStatus      // Modul wird nachgeladen; Download angestoßen
    data object NichtVerfuegbar : ScanStatus  // keine Play-Dienste
    data object Fehler : ScanStatus
}
interface TabellenScanner { suspend fun scan(bild: Uri): ScanStatus }
interface StrichcodeLeser { suspend fun lesen(): String? }  // null = abgebrochen
```

- Umsetzung mit `com.google.android.gms:play-services-mlkit-text-recognition` (`TextRecognition.getClient(
  TextRecognizerOptions.DEFAULT_OPTIONS)`) und `com.google.android.gms:play-services-code-scanner`.
  Fehlendes Modul (`MlKitException.UNAVAILABLE`) → `ModuleInstallClient.installModules(…)` anstoßen, `WirdGeladen`.
- Play-Dienste prüfen mit `GoogleApiAvailability.isGooglePlayServicesAvailable`.
- Fotoquelle: System-Kamera (`TakePicture`) in eine Datei unter `cacheDir/scan/` oder Galerie
  (`PickVisualMedia`). Die Kameradatei wird nach der Erkennung **gelöscht**; Galeriebilder werden nur gelesen.
- Hilt stellt die Schnittstellen bereit; Tests ersetzen sie durch Fakes.

## 4. Open Food Facts

- Eigener `HttpClient` (Ktor, OkHttp-Engine wie der Sync, aber **ohne** die lokale CA-Vertrauensliste), Zeitlimit 8 s,
  Header `User-Agent: Foody/<versionName> (privat; Android)`.
- Auswertung als reine Funktion `OpenFoodFacts.auswerten(json: JsonObject): Produkt?` in `app` (JVM-getestet):
  - `status != 1` oder kein `product` → `null`.
  - Name: `product_name_de`, sonst `product_name`; leer → `null` im Namen (Feld bleibt leer).
  - Basis: `nutrition_data_per == "100ml"` → `PER_100_ML`, sonst `PER_100_G`.
  - Energie: `energy-kj_100g`; sonst `energy-kcal_100g` × 4,184; sonst `energy_100g` (OFF liefert es in kJ).
  - Weitere: `fat_100g`, `carbohydrates_100g`, `sugars_100g`, `fiber_100g`, `proteins_100g`, `salt_100g`.
  - Dieselben Plausibilitätsgrenzen wie 3.1. Ohne Energie → Produkt gilt als „nicht gefunden“ (Foto anbieten).
- Fehler (offline, Zeitüberschreitung, HTTP ≠ 200/404) → „Keine Verbindung – Nährwerttabelle fotografieren?“.
- Einstellung „Online-Produktsuche (Open Food Facts)“ in „Mehr“ (`ScanPreferences`, SharedPreferences, Standard an)
  mit Hinweis „Sendet nur die Produktnummer“.

## 5. Daten

### 5.1 DB v8 → v9
- `ingredient.barcode TEXT` (nullable) mit Index `index_ingredient_barcode` (nicht eindeutig: zwei Zutaten dürfen
  denselben Code haben, z. B. nach einem Zusammenführen; die Suche nimmt die zuerst angelegte).
- `MIGRATION_8_9`: `ALTER TABLE` + `CREATE INDEX`. Sync-Trigger unverändert (Tabelle `ingredient` ist schon Wurzel).
- `IngredientPayload.barcode: String? = null`, `BackupDto.Ingredient.barcode: String? = null`. Validator: höchstens
  14 Ziffern. Keine neue Protokoll-Version (optionales Feld; ältere Apps ignorieren es, verlieren es aber beim
  Bearbeiten – wie in Teil A).
- Zusammenführen von Zutaten (`IngredientRepository.mergeInto`): Ziel übernimmt den Code der Quelle, wenn es keinen hat.

### 5.2 Quelle der Werte
- `nutrientSource` = „Open Food Facts“ bzw. „Packung (Foto)“, wenn gescannte Werte gespeichert werden; bei
  manueller Änderung eines vorausgefüllten Feldes bleibt die Quelle stehen (Prüfen ist gewollt).

## 6. Oberfläche

### 6.1 Zutatenkatalog (`IngredientDialog`)
- Knopf **„Von Packung scannen“** über den Nährwertfeldern mit Menü: „Strichcode scannen“, „Nährwerttabelle
  fotografieren“, „Aus Galerie“.
- Während der Erkennung/Abfrage: Fortschrittsanzeige im Knopf.
- Ergebnis: Felder vorausgefüllt; Hinweis oben „Von der Packung übernommen – bitte prüfen“ (Quelle genannt);
  erkannte Felder mit farbiger Umrandung und Zusatz „erkannt“. Name nur bei leerem Namensfeld aus Open Food Facts.
  Zweiter Scan überschreibt nur Felder, die er erkennt.
- Speichern übernimmt (inkl. Strichcode und Quelle), Abbrechen verwirft.

### 6.2 Tagebuch, Reiter „Frei“
- Knopf **„Von Packung scannen“** (gleiches Menü).
- Ergebnis → **Packungsmodus**: Name (Pflicht; aus Open Food Facts vorbefüllt), Menge + Einheit (g bzw. ml nach
  Basis), Werte je 100 (kcal, Eiweiß, KH, Fett; markiert, änderbar), Vorschau „≈ … kcal“ für die Menge.
- Häkchen **„Als Zutat im Katalog speichern“** (Standard an) → Zutat mit Werten je 100, Basis, Strichcode, Quelle
  anlegen; Eintrag als `ZUTAT`. Gibt es den Namen schon (gleicher `canonicalName`), heißt das Häkchen „Werte bei
  ‚…‘ aktualisieren“ und ist **aus**; der Eintrag nutzt die gescannten Werte, die Zutat wird nur bei gesetztem
  Häkchen aktualisiert (Strichcode wird ergänzt, wenn sie keinen hat).
- Häkchen aus → `FREI`-Eintrag mit für die Menge berechneten Werten.
- „Zurück zu frei“ verlässt den Packungsmodus.
- Strichcode bereits im Katalog → Reiter „Zutat“ mit dieser Zutat (siehe 2.).

### 6.3 Meldungen

| Fall | Anzeige |
|---|---|
| Modul wird geladen | „Scanner wird vorbereitet – gleich noch einmal versuchen“ |
| Keine Play-Dienste | Knopf ausgegraut, Hinweis „Braucht Google-Play-Dienste“ |
| Produkt nicht gefunden | „Nicht in Open Food Facts – Nährwerttabelle fotografieren?“ |
| Offline / Zeitüberschreitung | „Keine Verbindung – Nährwerttabelle fotografieren?“ |
| Foto ohne Tabelle | „Keine Nährwerttabelle erkannt – bitte näher und gerade fotografieren“ |
| Teilweise erkannt | Erkanntes vorausgefüllt, Rest leer |

## 7. Fehlerfälle

| Fall | Verhalten |
|---|---|
| Kamera/Scanner abgebrochen | nichts passiert, Formular unverändert |
| Unsinniger Wert | verworfen (3.1/4), Feld leer |
| Zweiter Scan | überschreibt nur erkannte Felder |
| Strichcode an mehreren Zutaten | erste (älteste) wird genommen |
| Online-Produktsuche aus | Scan-Knopf: Strichcode wird nur im eigenen Katalog gesucht, sonst Foto |

## 8. Datenschutz
- Foto bleibt auf dem Gerät; Kameradatei wird nach der Erkennung gelöscht.
- An Open Food Facts geht nur die Produktnummer (kein Konto, keine Gerätekennung); abschaltbar.
- Play-Dienste laden einmalig die Module (Code Scanner, Texterkennung) – kein Foto verlässt das Gerät.
- Dokumentiert in `docs/security.md` und ADR 0008.

## 9. Tests

**`domain` (JVM):** `NaehrwertScanTest` mit nachgebauten OCR-Elementen: einspaltige Tabelle; zweispaltige Tabelle
(Bezeichnungen und Werte als getrennte Elemente gleicher Höhe); nur kcal; „<0,5 g“ und „Spuren“; Tausenderpunkt;
deutsch/englisch; Basis ml; „davon gesättigte Fettsäuren“ ≠ Fett; Ausreißer verworfen; Zucker > KH verworfen;
leere Eingabe → leere Werte.

**`app` (JVM):** `OpenFoodFactsTest` mit gespeicherten Beispielantworten (vollständig, nur kcal, ml, ohne Energie,
`status 0`).

**`app` (Instrumentation):** `MigrationTest.migrate8To9…`; `SyncApplierTest`/`BackupZipTest` mit Strichcode;
`TagebuchViewModelTest`: Packung mit/ohne „als Zutat speichern“, vorhandener Name wird nicht überschrieben,
Strichcode im Katalog → keine Online-Abfrage (Fakes); Compose-Test `IngredientDialog` mit Fake-Scanner (Felder
gefüllt und markiert).

**Echte Fotos:** optional 2–3 Fotos des Nutzers als zusätzliche Testfälle; sonst Sichtprüfung im Emulator mit einer
erzeugten Testgrafik einer Nährwerttabelle.

## 10. Dokumentation
- `docs/adr/0008-scan-play-dienste-open-food-facts.md`
- `docs/architecture.md` (DB v9, Scan-Bausteine), `docs/security.md` (Foto, Produktnummer), `docs/domain-rules.md`
  (Übernahme gescannter Werte), `ToDoS/einkaufsliste-und-essenstracking.md` Abschnitt 3 erledigt.
