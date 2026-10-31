# V3 Phase 6e1: Einstellungen

Teil von Phase 6 („Bereiche neu bauen“: 6a Rezepte → 6b Kochen → 6c Woche → 6d Einkauf → **6e Einstellungen**). 6e ist
geteilt: **6e1** = die Einstellungsseite (diese Spec), **6e2** = Zutatenkatalog mit „Packung scannen“ und dem Schalter
„Online-Produktsuche“. Grundlage: `ToDoS/v3-plan.md` (Phase 6) und der Abschnitt „Einstellungen“ in
`ToDoS/v3-abnahme.md`.

## Ziel

Eine Einstellungsseite der V3 in `:feature:einstellungen` statt der heutigen reinen „Server verbinden“-Seite: Konto,
Darstellung, Tagesziel, Sicherung erstellen und wiederherstellen (auch v1-Dateien), alle Daten löschen, Hinweise.
„Server verbinden“ bleibt als eigene Seite erhalten.

**Fertig, wenn** in der Abnahmeliste „Sicherung als ZIP …, Wiederherstellung (auch v1-Dateien), vollständige lokale
Löschung“, „Darstellung hell / dunkel / System“ und „Tagesziel“ abgehakt sind, kein Screen über 300 Zeilen hat und die
Sichtprüfung auf dem Emulator (siehe 4.) klappt.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Zuschnitt | 6e1 Einstellungsseite, 6e2 Zutatenkatalog + Scan + Online-Schalter |
| Aufbau | Eine scrollbare Seite mit Glas-Abschnitten; „Server verbinden“ bleibt eigene Seite |
| Wiederherstellen/Löschen bei verbundenem Sync | Gesperrt mit Hinweis „Zuerst unter Konto die Verbindung trennen“ (wie v1) |
| Nach „Alle Daten löschen“ | Startkatalog wird neu eingespielt; Einstellungen (Darstellung, Tagesziel, Verbindung) bleiben |
| Speicherung | v1-Schlüssel in den Prefs `foody`: `themeMode` (`SYSTEM`/`LIGHT`/`DARK`), `dailyKcalGoal` |
| „Erneut anmelden“ nach v1-Update | Seit Phase 5 vorhanden (`SyncKonto.istWiederanmeldung`); in 6e1 nur Gerätebeleg, falls ein v1-Stand verfügbar ist |
| v1 / Server | Unverändert; DB bleibt v10 |

## 1. Daten (`:core:data`)

- `enum class Darstellung { SYSTEM, HELL, DUNKEL }` und `DarstellungSpeicher @Inject constructor(@ApplicationContext)`:
  `darstellung(): Flow<Darstellung>` (meldet Änderungen, wie `TageszielSpeicher.tagesziel()`), `setzen(d: Darstellung)`.
  Gespeichert unter `Einstellungsschluessel.DARSTELLUNG` als `SYSTEM`/`LIGHT`/`DARK` (v1-Werte); fehlend oder
  unbekannt = `SYSTEM`.
- `TageszielSpeicher.setzen(kcal: Int?)`: `null` entfernt das Ziel; 500–10.000 wird gespeichert; andere Werte →
  `IllegalArgumentException`, nichts geändert. Konstanten `TAGESZIEL_MIN = 500`, `TAGESZIEL_MAX = 10_000`.
- `Sicherung.allesLoeschen(): LoeschErgebnis` (`Fertig` | `SyncAktiv`): bei aktivem Sync nichts ändern und `SyncAktiv`;
  sonst alle Tabellen in einer Transaktion leeren (derselbe Weg wie beim Wiederherstellen, mit leeren Daten), eigene
  Fotodateien entfernen (`FotoAblage.unbenutzteEntfernen(emptyList())`), die geladene Startdaten-Version zurücksetzen
  und den Startkatalog neu einspielen (`StartdatenLader.ladenWennNoetig()`). Sync-Zustand, Outbox und Prefs bleiben.
  Scheitert das Einspielen, holt der nächste App-Start es nach (Version ist zurückgesetzt).
- `Sicherung.schreiben` und `Sicherung.lesen` bleiben unverändert.

## 2. Oberfläche (`:feature:einstellungen`) und Navigation (`:app3`)

**Navigation:** Das Ziel `Einstellungen` (Profilsymbol in Heute) zeigt `EinstellungenRoute(onServer, onZurueck)`. Neues
Ziel `ServerVerbinden` zeigt die bisherige `VerbindenRoute`. `MainActivity` liest `DarstellungSpeicher.darstellung()`:
`SYSTEM` → `isSystemInDarkTheme()`, `HELL` → hell, `DUNKEL` → dunkel; Änderungen wirken sofort.

**Seite** – Kopf „EINSTELLUNGEN“ (Augenbraue) / „Einstellungen“ mit Zurück-Pfeil, Inhalt höchstens 720 dp breit,
mittig; Abschnitte als Glasflächen mit Augenbraue:

| Abschnitt | Inhalt |
|---|---|
| Konto | Verbunden: „Verbunden mit <Adresse>“, darunter „Zuletzt synchronisiert: …“ (wie die Verbinden-Seite); nicht verbunden: „Nicht verbunden“, darunter „Server verbinden“. Ganze Zeile mit „›“ → `ServerVerbinden` |
| Darstellung | Chips „System“, „Hell“, „Dunkel“ |
| Tagesziel | Zahlenfeld mit „kcal“, Hinweis „Leer lassen = kein Ziel“; gültige Eingabe wird sofort gespeichert, sonst Fehler „500 bis 10.000 kcal“ am Feld, das alte Ziel bleibt |
| Sicherung | Hinweis „Rezepte, Zutaten, Plan, Einkauf, Tagebuch und Fotos als ZIP-Datei.“; „Sicherung erstellen“ (Dateidialog, Name `foody-sicherung-<JJJJ-MM-TT>.zip`) → „Sicherung gespeichert“; „Wiederherstellen“ (Dateiauswahl ZIP/JSON) → Rückfrage „Alle Daten ersetzen?“ → „12 Rezepte, 230 Zutaten, 5 Fotos wiederhergestellt“ (bei v1 zusätzlich „ · Vorrat übersprungen: n“) oder der Fehlergrund |
| Daten | „Alle Daten löschen“ → Rückfrage „Alle Rezepte, Pläne, Einkaufslisten, Tagebuch und Fotos auf diesem Gerät löschen? Der Startkatalog wird neu eingespielt.“ → „Alle Daten gelöscht“ |
| Hinweise | „Nährwerte sind rechnerische Näherungswerte und ersetzen keine ernährungsmedizinische Beratung.“ und ein Datenschutzhinweis: ohne Server bleibt alles auf dem Gerät, mit eigenem Server werden die Daten nur dorthin übertragen |

- Bei verbundenem Sync sind „Wiederherstellen“ und „Alle Daten löschen“ deaktiviert; darunter „Zuerst unter Konto die
  Verbindung trennen.“ „Sicherung erstellen“ geht immer.
- Während einer Aktion sind alle drei Knöpfe deaktiviert, der laufende zeigt „Läuft …“.
- Snackbars ohne Aktion, Standarddauer.

**`EinstellungenViewModel @Inject constructor(darstellung: DarstellungSpeicher, ziel: TageszielSpeicher, sicherung:
Sicherung, konto: KontoZugriff, clock: Clock)`:** Zustand `EinstellungenZustand(darstellung, tagesziel: Int?, konto:
VerbindungsZustand?, laeuft: Aktion?)`; `darstellungSetzen`, `tageszielEingabe(text): Boolean` (gültig/ungültig),
`sicherungErstellen(uri)`, `wiederherstellen(uri)`, `allesLoeschen()`, `dateiname(): String`; Ergebnisse als
`meldungen: Flow<String>`. Die Arbeit läuft im `viewModelScope` (Drehen bricht sie nicht ab); eine zweite Aktion,
solange eine läuft, wird ignoriert. Uri → Stream über `ContentResolver` im ViewModel (Application-Kontext), die
Dateidialoge (`ActivityResultContracts.CreateDocument`/`OpenDocument`) liegen in der Route.

## 3. Fehlerfälle

| Lage | Verhalten |
|---|---|
| Sicherung erstellen scheitert | „Sicherung fehlgeschlagen: <Grund>“ |
| Datei kaputt, fremd, zu groß | Grund aus `ImportErgebnis.Fehler`; nichts geändert |
| Sync wurde zwischen Öffnen und Bestätigen verbunden | `SyncAktiv` → „Zuerst unter Konto die Verbindung trennen.“; nichts geändert |
| Dateidialog abgebrochen | Nichts, keine Rückfrage |
| Tagesziel außerhalb 500–10.000 oder keine Zahl | Fehler am Feld, altes Ziel bleibt |
| Prozessende beim Löschen | Datenbank ganz gelöscht oder unverändert; Startkatalog spätestens beim nächsten Start |
| Zweiter Tipp während einer Aktion | Ignoriert |

## 4. Tests

- `:core:data`: `DarstellungSpeicherTest` (Lesen, Schreiben, v1-Werte `LIGHT`/`DARK`, unbekannt → `SYSTEM`, Flow meldet
  Änderung), `TageszielSpeicherTest` (`setzen`: Grenzen 500/10.000, `null` entfernt, ungültig ändert nichts),
  `SicherungLoeschenTest` (alle Tabellen leer, eigene Fotos entfernt, Startkatalog wieder da, Prefs bleiben, bei
  aktivem Sync `SyncAktiv` und nichts geändert).
- `:feature:einstellungen`: ViewModel (Darstellung, Ziel, Erstellen/Wiederherstellen/Löschen mit Meldungen, Sperre bei
  Sync, zweite Aktion ignoriert), Bildschirm (Abschnitte, Chips, Feld mit Fehler, Rückfragen, deaktiviert bei Sync,
  Schrift 200 %, Drehen), Roborazzi: Seite hell/dunkel × Handy/Tablet; CI vergleicht
  `:feature:einstellungen:verifyRoborazziDebug`.
- `:app3`: Navigation (Einstellungen → Konto → `ServerVerbinden`), Darstellung „Dunkel“ schaltet das Theme.
- Sichtprüfung auf `Foody_Test` gegen den Test-Server: Darstellung umschalten, Tagesziel setzen und in Heute sehen,
  Sicherung erstellen, Löschen/Wiederherstellen gesperrt solange verbunden; trennen, alle Daten löschen (Startkatalog
  da), aus der eben erstellten Sicherung wiederherstellen, wieder verbinden mit „Zusammenführen“ (nicht als neues
  Gerät, sonst doppelte Datensätze). „Erneut anmelden“ nach v1-Update nur, wenn ein v1-Stand auf dem Testgerät
  vorhanden ist.

## Nicht in 6e1

- Zutatenkatalog, „Packung scannen“, Online-Produktsuche (6e2).
- Sicherung auf dem Server, automatische Sicherungen.

## Risiken

- **Wiederherstellen und danach falsch verbinden:** Verbindet man ein wiederhergestelltes Gerät als neues Gerät statt
  mit „Zusammenführen“, entstehen doppelte Datensätze. Die Verbinden-Seite fragt das bereits ab; die Sichtprüfung
  belegt den richtigen Weg.
- **Große Sicherungen:** `Sicherung` begrenzt Größe und liest im Hintergrund; bei vielen Fotos dauert es – „Läuft …“
  zeigt das.
