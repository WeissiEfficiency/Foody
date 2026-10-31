# V3 Phase 4: Designsystem

Stand: 2026-10-19 · Grundlage: [ADR 0009](../../../../adr/0009-neubau-v2-module-navigation.md),
[ADR 0011](../../../../adr/0011-v3-umfang-ein-nutzer.md), [`ToDoS/v3-plan.md`](../../../../../ToDoS/archiv/v3-plan.md) (Phase 4) · im Chat
abschnittsweise abgestimmt; Richtungen im Canvas „Foody App-Design“, Seite „V3-Richtungen“ (gewählt: A · Ofenwarm).

## Ziel

Die V3 bekommt ihr Aussehen als eigenes Modul `:core:designsystem`: Tokens (Farben, Schrift, Formen, Abstände, zwei
Glasstufen), ein warmer Verlauf-Hintergrund und die gemeinsamen Komponenten. Eine kleine Debug-App `:katalog` zeigt
alles in allen Zuständen. Das Aussehen wird **spürbar frischer** („warm und appetitlich“), Glas und Kacheln bleiben
(ADR 0009/0011).

**Fertig, wenn** jede Komponente im Katalog WCAG 2.2 AA einhält – auch auf Glas, auch ohne Unschärfe – und das per
Test belegt ist.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Richtung | A · Ofenwarm: Creme, Tomate, Oliv, Safran; Fraunces + Figtree (verworfen: B · Kräutergarten, C · Gewürzmarkt, v1-Look verfeinern) |
| Glas | echte Unschärfe mit der Bibliothek Haze (`dev.chrisbanes.haze`) (verworfen: nur Tönung wie v1; eigene Unschärfe) |
| Schriften | als Dateien gebündelt (`res/font`, OFL), nicht als Downloadable Fonts |
| Katalog | eigene Debug-App `:katalog` (`de.foody.katalog`), nie ausgeliefert; die V3-App entsteht in Phase 5 |
| AA-Nachweis | Kontrast-Unit-Test über eine Paartabelle + Screenshot-Tests (Roborazzi) + Compose-UI-Tests |
| Bildschirme | keine in Phase 4; die Bereiche (Phase 5/6) bauen aus den Komponenten |
| v1 | unverändert (`android/app`, `Theme.kt`, `Glass.kt`); Wechsel auf das neue Designsystem in Phase 8 |

## 1. Module und Aufbau

| Modul | Inhalt |
|---|---|
| `:core:designsystem` (`android/core/designsystem`, Paket `de.foody.core.designsystem`, Plugin `foody.android.library` mit Compose) | Tokens, `FoodyTheme`, `FoodyHintergrund`, Komponenten; keine Abhängigkeit zu `:core:data`/`:core:sync` |
| `:katalog` (`android/katalog`, App-ID `de.foody.katalog`, Plugin `foody.android.application`) | eine Activity mit allen Komponenten, Umschalter, Tablet-Zweispalter; Screenshot-Tests |

- **Tokens:** `FoodyFarben` (hell/dunkel), `FoodyTypo` (Fraunces für Überschriften ab `titleLarge`, Figtree für alles
  andere), `FoodyFormen` (Ecken 12/20/28 dp), `FoodyAbstaende` (4-dp-Raster), `FoodyGlas` (zwei Stufen).
  `FoodyTheme { … }` füllt `MaterialTheme` mit den Ofenwarm-Werten und stellt die eigenen Werte über CompositionLocals
  bereit; keine Dynamic Color.
- **Hintergrund:** `FoodyHintergrund` zeichnet Creme mit zwei Radialverläufen (Aprikose oben links, Oliv unten rechts)
  und ist die Quelle der Unschärfe (`hazeSource`).
- **Komponenten** bekommen nur einfache Werte (Texte, Zahlen, Slots, Callbacks), keine Datenbank- oder Sync-Typen.
- **Neue Abhängigkeiten** im Version Catalog: Haze; Roborazzi (Screenshot-Tests unter Robolectric). Versionen passend zu
  Compose-BOM 2026.09.00 und Kotlin; Lizenzen geprüft (Apache 2.0).

## 2. Farben, Schrift, Glas, Kontrast

**Hell**

| Rolle | Wert |
|---|---|
| Grund | `#FBF4EA` |
| Text | `#2B1D14` |
| Nebentext | `#6B5547` |
| Primär / Text darauf | `#A8341A` / `#FFFFFF` (Startwert `#B5381C` hielt direkt auf dem Verlauf nur 4,2:1) |
| Primär-Behälter / Text darauf | `#FCE0D3` / `#7A220E` |
| Akzent (Oliv) | `#5C6B2A` |
| Wärme (Safran, nur Grafik) | `#BD741B` (Startwert `#C77A1C` hielt auf Glas LEICHT nur 2,8:1) |
| Balkenspur | `#EADCC9` |
| Verlauf | Aprikose `#FFB882` α 0,60 oben links · Oliv `#BAC480` α 0,50 unten rechts |

**Dunkel**

| Rolle | Wert |
|---|---|
| Grund | `#1A1310` |
| Text | `#F5EADF` |
| Nebentext | `#C9B6A7` |
| Primär / Text darauf | `#F08A65` / `#3A1206` |
| Primär-Behälter / Text darauf | `#5A2212` / `#FFDCCD` |
| Akzent (Oliv) | `#BCC67F` |
| Wärme (Safran, nur Grafik) | `#E3A653` |
| Balkenspur | `#3A2C24` |
| Verlauf | Glut `#8C3C19` α 0,45 oben links · Oliv `#46501E` α 0,40 unten rechts |

Die Werte sind der Ausgangspunkt; der Kontrast-Test entscheidet. Scheitert ein Paar, wird die Farbe bzw. die Tönung
angepasst, nie die Regel.

**Schrift:** Fraunces 600/700 (Überschriften ab `titleLarge`), Figtree 400/500/600/700; Größen nach den
Material-3-Stufen in `sp`, damit Systemschriftgröße bis 200 % greift. Augenbraue: Figtree 12 sp, 700, Laufweite 1,2 sp,
Großbuchstaben.

**Glas – zwei feste Stufen** (Startwerte):

| Stufe | Einsatz | Unschärfe | Tönung hell | Tönung dunkel |
|---|---|---|---|---|
| leicht | Navigation, Kopfzeile, Status-Chip | 24 dp | Creme `#FFFAF3` α 0,55 | `#2B201A` α 0,50 |
| dicht | alles mit Fließtext: Karten, Tagebuch, Kacheln | 24 dp | Creme `#FFFAF3` α 0,78 | `#2B201A` α 0,72 |

**Dritte Stufe BILD** (Nachtrag nach dem Gesamtreview): Leisten über Fotos (große Rezeptkarte) sind fast deckend (α 0,92 hell und dunkel) und **nie** unscharf – die Unschärfe-Quelle ist der Hintergrund, nicht das Foto, und über einem Foto ist von Schwarz bis Weiß alles möglich. Kacheln stehen in Listen und sind deshalb nur getönt (Leistung). Unschärfe tragen nur wenige feste Flächen (Navigation, Kopfzeile, Status).

Dazu eine Lichtkante 1 dp (hell: Weiß α 0,75–0,8; dunkel: Weiß α 0,12). Kann das Gerät keine Unschärfe oder sind
Animationen reduziert, zeichnet Haze nur die Tönung.

**Kontrast-Regel (WCAG 2.2 AA):** gemessen gegen die ungünstigste Stelle – Tönung per Alpha-Mischung über dem
dunkelsten bzw. hellsten Punkt des Verlaufs, **ohne** Unschärfe. Text ≥ 4,5:1, große Schrift (≥ 24 sp bzw. ≥ 18,66 sp
fett) ≥ 3:1, Grafik und Kanten von Bedienelementen ≥ 3:1 (die Lichtkante der Glasflächen ist Dekor und ausgenommen). Geprüft werden zusätzlich Primär direkt auf dem Verlauf (Text-/Umriss-Knöpfe, Links) und alles über Fotos (Leiste BILD, Abzeichen, Herz) gegen Schwarz, Weiß und den Platzhalter. Die Paartabelle (Vordergrund, Untergrund, Mindestwert) liegt
im Code neben den Tokens und wird vollständig getestet.

## 3. Komponenten

Alle Komponenten sind zustandslos, haben mindestens 48 dp Berührfläche, eine TalkBack-Beschreibung und brechen bei
200 % Schrift um bzw. wachsen in die Höhe, statt Text abzuschneiden.

| Komponente | Inhalt | Zustände / Varianten |
|---|---|---|
| `GlasFlaeche(stufe, form, inhalt)` | Haze-Effekt + Lichtkante; Basis aller Glas-Elemente | leicht, dicht; mit/ohne Unschärfe |
| `Rezeptkarte` | Bild-Slot, Augenbraue, Titel, Metazeile, optionale Aktion, Abzeichen (Favorit, Zeit, kcal) | `gross` (Glas-Leiste über dem Bild), `raster` (quadratisches Bild, Titel darunter); ohne Bild: warmer Verlauf mit Besteck-Symbol |
| `Kachel` | Monogramm-Kreis oder Symbol-Slot, Name, Menge, optional Untertitel | normal, abgehakt (durchgestrichen, gedimmt, Kontrast ≥ 4,5:1), gedrückt |
| `Naehrwertbalken` | kcal gegen Tagesziel, Aufteilung Eiweiß (Oliv) / KH (Safran) / Fett (Tomate), Legende mit Grammzahlen | mit Ziel, ohne Ziel (nur Aufteilung), über Ziel (Balken und Text sagen es) |
| `SyncStatus` | Chip mit Symbol und Text | synchron, läuft (Drehung entfällt bei reduzierten Animationen), offline/wartet, Fehler mit Kurztext |
| `LeererZustand` | Text, Aktion; Fläche zusätzlich Symbol und Titel | `zeile` (gestrichelte Kante), `flaeche` (ganzer Bereich) |
| Grundbausteine | `FoodyKnopf` (primär, Umriss, Text), `FoodyChip` (Filter an/aus), `Abschnittskopf` (Titel + optionaler Link), `Augenbraue`, `FoodyNavigation` (schwebende Glas-Leiste, vier Ziele; Tablet: Rail) | ausgewählt/nicht ausgewählt, deaktiviert |

Die Zuordnung der Sync-Kennungen aus `:core:sync` zu `SyncStatus` und das Laden echter Fotos (Coil) folgen in Phase 5.

**Nicht in Phase 4:** Bildschirme, Listenlogik, Bildladen, Übergänge zwischen Bildschirmen.

## 4. Katalog, Tests, Abschluss

**`:katalog`:** eine Seite je Gruppe (Farben und Schrift, Glas, Rezeptkarte, Kachel, Nährwertbalken, Sync-Status, leere
Zustände, Grundbausteine), alle Zustände über dem echten Hintergrund. Umschalter: hell/dunkel/System, Schrift
100 %/200 %, „Animationen reduziert“ (Glas ohne Unschärfe). Tablet: Gruppen links, Inhalt rechts. Beispieldaten
erfunden, nie aus echten Datenbanken.

**Tests**

- **Kontrast-Test** (`:core:designsystem`, JVM): jede Zeile der Paartabelle, hell und dunkel, inklusive Glas über dem
  ungünstigsten Verlaufspunkt; Ausgabe des gemessenen Werts bei Fehlschlag.
- **Screenshot-Tests** (Roborazzi unter Robolectric, `:katalog`): jede Katalogseite hell und dunkel, Handy 411×891 dp
  und Tablet 1280×800 dp, zusätzlich 200 % Schrift auf dem Handy. Referenzbilder im Repo, CI vergleicht. Robolectric
  rendert keine Unschärfe – die Bilder zeigen den Fall ohne Unschärfe, den der Kontrast-Test absichert.
- **Compose-UI-Tests:** Berührflächen ≥ 48 dp, TalkBack-Beschreibungen vorhanden, Kachel/Chip melden Zustand
  („ausgewählt“, „abgehakt“).
- **Sichtprüfung:** Katalog auf dem eigenen Emulator `Foody_Test` mit echter Unschärfe, hell und dunkel; Screenshots
  für den Nutzer.

**Abschluss:** `./gradlew check` und alle Tests grün; Doku: `docs/architecture.md` (Abschnitt „Designsystem V3“),
`ToDoS/v3-plan.md` (Phase 4 abhaken mit Ergebnis), neue ADR 0012 „Designsystem V3: Ofenwarm und Glas mit Haze“; PR
gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

## Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Haze passt nicht zur Compose-Version | passende Version pinnen; sonst bleibt die Tönung, die allein schon AA hält |
| Unschärfe kostet auf schwachen Geräten Leistung | Glas nur auf wenigen festen Flächen, nie hinter jedem Listeneintrag |
| Screenshot-Tests schwanken (Schriftglättung, JDK) | Robolectric-Native-Grafik fest, Toleranz klein und festgeschrieben; Referenzbilder nur bewusst neu erzeugen |
| Schriftlizenzen | OFL; Lizenzdateien neben den Fonts im Modul |
| Kontraste auf Glas überschätzt | Messung ohne Unschärfe gegen den ungünstigsten Verlaufspunkt, nicht gegen die Durchschnittsfarbe |
