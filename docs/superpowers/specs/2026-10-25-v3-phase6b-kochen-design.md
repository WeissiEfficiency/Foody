# V3 Phase 6b: Kochen

Teil von Phase 6 („Bereiche neu bauen“, Reihenfolge 6a Rezepte → **6b Kochen** → 6c Woche → 6d Einkauf → 6e
Einstellungen). Grundlage: `ToDoS/v3-plan.md` (Phase 6) und der Abschnitt „Kochen“ in `ToDoS/v3-abnahme.md`.

## Ziel

Der Kochmodus der V3-App in einem neuen Modul `:feature:kochen`: eine Seite zum Bereitlegen, danach ein Schritt pro
Seite mit „Du brauchst“ und Timern, die auch bei ausgeschaltetem Bildschirm und im Hintergrund weiterlaufen. Nach dem
Kochen: Plan-Eintrag als gekocht, Einladung zum eigenen Foto, „War etwas anders?“ öffnet den Rezept-Editor. Ersetzt den
Platzhalter der Route `Kochen(rezeptId, portionen?)` aus Phase 5.

**Fertig, wenn** der Abschnitt „Kochen“ der Abnahmeliste abgehakt ist, kein Screen über 300 Zeilen hat und die
Sichtprüfung auf dem Emulator (Timer bei ausgeschaltetem Bildschirm, Benachrichtigung antippen, Kochen fertig) klappt.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Änderungen beim Kochen | Erst am Ende: „War etwas anders? Rezept anpassen“ öffnet den Editor aus 6a; keine Bearbeitung im Kochmodus |
| „Fertig“ | Steht das Rezept heute im Plan, wird ein Eintrag als gekocht markiert (zählt für „Oft gekocht“); sonst nichts |
| Mise en place | Eigene erste Seite „Bereitlegen“ im Kochmodus; „Du brauchst“ zeigt, was schon bereitliegt |
| Timer | Vordergrunddienst mit Teil-Wakelock wie v1, laufende Timer zusätzlich gespeichert (überstehen Neustart) |
| Kochvorgang | Als Sitzung je Rezept gespeichert (Seite, Haken, Portionen), 12 Stunden fortsetzbar |
| Abschluss | Blatt über dem letzten Schritt statt eigener Seite |
| v1 / Server | Unverändert; DB bleibt v10 |

## 1. Aufbau und Daten

**`:feature:kochen`** (neu; Abhängigkeiten wie `:feature:rezepte`, nicht von anderen Features)

- `KochenRoute(rezeptId, portionen, onRezeptAnpassen, onSchliessen, vm)` – Bildschirm (Abschnitt 3).
- `KochenViewModel` – lädt das Rezept über `RezeptRepository.voll(id)` (Flow, aktualisiert sich bei Sync), führt
  Seite, Haken und Portionen und schreibt sie in die Sitzung (unten). Portionen: Route, sonst Sitzung, sonst Rezept;
  1–99.
- `kochSchritte(voll: RezeptVoll, portionen: Int): List<KochSchritt>` – reine Funktion. Je Schritt die erwähnten
  Zutaten über `StepIngredientMatcher.mentions` (Namen aus den Rezeptzeilen): Teilmenge aus dem Schritt („150 g von
  den 400 g“), sonst die Gesamtmenge, beide auf [portionen] skaliert (`mengeSkaliert`-Regel aus 6a, 6 Nachkommastellen,
  angezeigt mit `QuantityFormatter`); dazu die Timer des Schritts aus `StepTimerParser.find`, nur 1 s bis 24 h.
  `KochSchritt(text, zutaten: List<SchrittZutat>, timer: List<StepTimer>)`,
  `SchrittZutat(zeilenIndex, name, menge: String?, gesamt: String?)`.
- **Sitzung** – `KochSitzungSpeicher` (SharedPreferences, eine Sitzung je Rezept-ID): `rezeptId`, `portionen`,
  `seite`, `abgehakt: Set<Int>` (Zeilenindex), `geaendertAm`. Gilt 12 Stunden ab der letzten Änderung, danach
  verworfen. „Fertig“ und „Schließen“ löschen sie; Zurück behält sie.
- **Timer** (`timer/`):
  - `KochTimerSpeicher` (`@Singleton`): `timer: StateFlow<List<LaufenderTimer>>`, `starten(rezeptId, portionen,
    rezeptName, label, dauer)`, `entfernen(id)`; `LaufenderTimer(id, rezeptId, portionen, rezeptName, label, endeUm)`;
    gespeichert als JSON in SharedPreferences, beim Start der App wieder gelesen. Abgelaufene bleiben, bis man sie
    wegtippt (höchstens 12 h).
  - `KochTimerDienst` – Vordergrunddienst (`foregroundServiceType="specialUse"`), läuft, solange ein Timer läuft:
    laufende Benachrichtigung mit der nächsten Restzeit, Teil-Wakelock bis kurz nach dem nächsten Ende, am Ende eine
    Benachrichtigung mit Ton und Vibration („Bombay-Curry: 20 Minuten sind um“). Tipp öffnet
    `foody://kochen/{rezeptId}?portionen={n}`. Startet nach einem Neustart der App selbst wieder, wenn gespeicherte
    Timer noch laufen. Die Zeitlogik („was ist abgelaufen, wann ist das nächste Ende“) liegt in einer reinen Funktion
    mit vorgegebener Uhr.
  - Manifest des Moduls: Dienst, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
    `WAKE_LOCK`; zwei Kanäle „Laufende Timer“ (leise) und „Timer abgelaufen“ (Ton).

**`:core:data`**

- `KochenRepository.gekochtMarkieren(rezeptId: String, heute: LocalDate): Boolean` – setzt in einer Transaktion
  `cookedAt` und `updatedAt` beim ersten heutigen Plan-Eintrag dieses Rezepts ohne `cookedAt` (Reihenfolge nach
  Mahlzeit, dann ID); der Sync-Trigger von `meal_slot` trägt die Änderung in die Outbox. `true`, wenn ein Eintrag
  markiert wurde. Ohne passenden Eintrag: nichts.
- Foto über das vorhandene `RezeptRepository.fotoSetzen`, Kamerabild über `FotoAblage` wie im Editor (FileProvider
  `${applicationId}.rezeptfotos` aus `:feature:rezepte` – im App-Manifest zusammengeführt; `:feature:kochen` nutzt nur
  den Namen).

## 2. Ablauf und Sonderfälle

**Start:** aus der Detailansicht mit den gewählten Portionen, per Deep Link oder über eine Timer-Benachrichtigung.
Gibt es für das Rezept eine gültige Sitzung, geht es dort weiter (Seite, Haken, Portionen); öffnet man aus der
Detailansicht mit anderen Portionen, gelten die neuen Portionen, Seite und Haken bleiben.

**Fertig:** „Fertig“ auf dem letzten Schritt (bei Rezepten ohne Schritte auf der Seite „Bereitlegen“) markiert den
Plan-Eintrag (`gekochtMarkieren`), löscht die Sitzung und öffnet das Blatt „Guten Appetit!“:
- „Foto aufnehmen“ – nur, wenn das Rezept noch kein Foto hat; Kamera, dann `fotoSetzen`.
- „War etwas anders? Rezept anpassen“ – öffnet `RezeptBearbeiten(id, gekochtMit = portionen)`; weichen die Portionen
  vom Rezept ab, zeigt der Editor oben „Gekocht mit 4 statt 2 Portionen – Mengen beziehen sich auf 2“.
- „Schließen“ – zurück zum vorherigen Ziel.

| Lage | Verhalten |
|---|---|
| Rezept ohne Schritte | Nur „Bereitlegen“, dort „Fertig“ |
| Rezept ohne Zutaten | „Bereitlegen“ entfällt, es beginnt mit Schritt 1 |
| Rezept gelöscht / unbekannte ID (auch aus der Benachrichtigung) | „Rezept nicht gefunden“ mit „Zurück“; laufende Timer bleiben und lassen sich wegtippen; Sitzung wird gelöscht |
| Rezept ändert sich beim Kochen (Sync) | Inhalt aktualisiert sich; Seite wird auf die letzte begrenzt; Haken für Zeilen, die es nicht mehr gibt, fallen weg |
| Zeitangabe 0 oder über 24 h | Kein Timer |
| Benachrichtigungen abgelehnt | Timer läuft trotzdem; bei offener App Ton, Vibration und Anzeige „abgelaufen“; einmal der Hinweis „Ohne Benachrichtigungen meldet sich der Timer nur bei offener App“ |
| Timer im Hintergrund abgelaufen | Benachrichtigung; im Kochmodus steht der Timer als „abgelaufen“, bis man ihn wegtippt |
| Derselbe Timer zweimal gestartet | Zweiter läuft unabhängig (z. B. zwei Bleche) |
| „Fertig“ ohne heutigen Plan-Eintrag | Nichts markiert, Blatt erscheint trotzdem |
| „Fertig“ doppelt getippt | Höchstens ein Eintrag markiert |
| Portionen außerhalb 1–99 aus dem Deep Link | Auf 1–99 begrenzt |

## 3. Darstellung

- **Kopf:** Schließen-Symbol, Rezeptname; darunter Fortschrittsbalken und „Bereitlegen“ bzw. „Schritt 3 von 7“.
- **Bereitlegen:** alle Zutaten (Menge skaliert, Name, Hinweis, „optional“) mit Haken-Flächen ≥ 48 dp; oben „4 von 9
  bereit“ und „Alle zurücksetzen“; Portionen-Stepper wie im Detail – ändern skaliert alle Mengen.
- **Schritt:** Text in `headlineSmall` mit normalem Gewicht; darunter „Du brauchst“ als Glasfläche (Teilmenge groß,
  „von 400 g“ klein, Haken-Symbol bei bereitgelegten Zutaten); Timer-Chips „▶ 20 Min.“.
- **Unten:** Leiste der laufenden Timer (Rezept bei fremden Timern, Restzeit, „abgelaufen“, Abbrechen/Wegtippen),
  darunter „Zurück“ (Umriss) und „Weiter“ / „Fertig“ (gefüllt). Wischen wechselt die Seite.
- **Tablet ab 840 dp:** links eine schmale Liste aller Seiten zum Springen, rechts die Seite; Inhalt höchstens 720 dp.
- Display bleibt an, solange der Kochmodus offen ist. Vollbild-Ziel ohne untere Navigationsleiste.
- Hell/dunkel, Schrift bis 200 % ohne abgeschnittenen Text, Drehen ohne Verlust.

## 4. Navigation und Tests

**`:app3`:** `Kochen` zeigt `KochenRoute`; „Rezept anpassen“ navigiert zu `RezeptBearbeiten(id, gekochtMit)` (neuer
optionaler Parameter, `:feature:rezepte` zeigt den Hinweis); „Schließen“ geht zurück. `Bildschirme` bekommt `kochen`
(echt und Platzhalter für Navigationstests). Der Timer-Dienst wird beim App-Start wieder aufgenommen, wenn noch Timer
laufen.

**Tests**

- `kochSchritte`: Teilmenge aus dem Schritt, Gesamtmenge ohne Teilmenge, Skalierung, Zutat ohne Menge, „Salz und
  Pfeffer“, Schritt ohne Zutat, Timer-Grenzen (0, 24 h + 1 s).
- `KochenRepository.gekochtMarkieren`: nur heute, nur dieses Rezept, nur ein Eintrag (auch bei zwei Aufrufen),
  Outbox-Eintrag, kein Eintrag → `false`.
- `KochSitzungSpeicher`: speichern/lesen, Ablauf nach 12 h, löschen.
- `KochTimerSpeicher` und Zeitlogik: starten, ablaufen, entfernen, wiederherstellen nach Neustart, nächstes Ende,
  Abgelaufene nach 12 h weg.
- Bildschirm (Robolectric, echtes ViewModel): Bereitlegen → Schritte → Fertig-Blatt; Sitzung fortsetzen; Rezept
  gelöscht; ohne Schritte / ohne Zutaten; Portionen ändern skaliert; Schrift 200 %; Drehen.
- `:app3`: Navigation Detail → Kochen → „Rezept anpassen“ → Editor mit Hinweis; Deep Link `foody://kochen/…`.
- Roborazzi: Bereitlegen, Schrittseite, Fertig-Blatt × hell/dunkel × Handy/Tablet; CI vergleicht
  `:feature:kochen:verifyRoborazziDebug`.
- Sichtprüfung auf dem Emulator Foody_Test gegen den Test-Server: Timer starten, Bildschirm aus, Benachrichtigung
  kommt, antippen öffnet den Schritt; Kochen fertig mit Plan-Eintrag.

## Nicht in 6b

- Rezept direkt auf die Einkaufsliste (6d).
- Bearbeiten im Kochmodus, Kochnotizen (bewusst gestrichen).
- Sprachsteuerung, Vorlesen.
- Tagebuch-Eintrag nach dem Kochen (bleibt bei „Heute“ – „Gegessen?“).

## Risiken

- **Hintergrund-Grenzen von Android:** Vordergrunddienste vom Typ `specialUse` brauchen bei der Play-Veröffentlichung
  eine Begründung; Foody wird nicht über Play verteilt. Hersteller mit aggressivem Energiesparen können Dienste
  beenden – die gespeicherten Timer werden beim nächsten App-Start wieder aufgenommen, die Benachrichtigung kann dann
  aber zu spät kommen.
- **Robolectric** kann Dienst und Benachrichtigung nur eingeschränkt prüfen; die Zeitlogik ist deshalb rein und
  getestet, der Rest wird in der Sichtprüfung belegt.
