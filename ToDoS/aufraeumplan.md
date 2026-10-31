# Aufräum- und Optimierungsplan nach dem Umstieg

Stand: 2026-10-28, `main` 71c887c. Server, Handy und Tablet laufen auf der V3 (Umstieg am 28.10., #137; danach ein
Import, Server jetzt bei Revision 890). Damit braucht vieles nicht mehr zu existieren, was für die v1, die Web-App und den Umstieg selbst
gebaut wurde. Dieser Plan sammelt alles an einer Stelle: Altlasten, Doku und offene Review-Befunde
(Gesamt-Review vom 28.10., IDs **W…/N…** wie im Chat „Zutatenkatalog V3 Phase 6e2“).

**Rückweg-Frist:** Was den Rückweg zur v1 offenhält, bleibt bis **2026-11-25** (4 Wochen Alltag ohne Probleme).
In diesem Plan steht es unter Stufe 3. Alles in Stufe 1 und 2 kann sofort weg.

Legende: 👤 = machst du (Gerät, Homeserver), 🤖 = Claude, PR = eigener kleiner PR gegen `main`. ✅ = erledigt.

**Stand 2026-10-31** (Nachprüfung, `main` nach #139): Stufe 1 noch offen (10 Worktrees, 61 lokale `claude/*`-Branches,
Build-Reste und zwei Checkouts). Stufe 2 erledigt (#139, #140 und der PR zu B7–B9; B5 entfällt). Stufe 4 und 5 offen.
Zusätzlich erledigt: **Z1** Release stürzte bei „Frei“ im Tagebuch ab – R8 entfernte die Konstruktoren der
ML-Kit-Registrare (`firebase-components` 16.1.0 behält nur die Klasse); Keep-Regel in `proguard-rules.pro` (#139).

---

## Stufe 1 – Sofort, ohne Risiko (kein Code)

| ID | Was | Wer | Hinweis |
|---|---|---|---|
| A1 | Alte Apps auf den Geräten deinstallieren, falls noch da: die bis 18.10. verbundene App auf dem Handy und die bis 27.10. verbundene auf dem Tablet (beide haben sich beim Umstieg nicht als Update gemeldet) | 👤 | Vorher kurz öffnen: Liegt dort seitdem etwas Ungesendetes, das du brauchst? Mit der Deinstallation ist es weg |
| A2 | Lokale Build-Reste im Repo-Ordner löschen: `app/`, `domain/`, `sync-protocol/` (nur `build/`, von Git nicht verfolgt) | 🤖 | Rest der alten Modulpfade |
| A3 | Alte Worktrees entfernen: `.claude/worktrees/` (3 Stück, Stand v2) und der Temp-Worktree `v3-befunde` | 🤖 | `git worktree remove`, danach `git worktree prune` |
| A4 | Lokale Branches löschen, deren Inhalt in `main` ist: alle 16 `claude/*` (web-teil1–4, v2-phase0, projektstruktur, aufraeumen, v3-befunde-review …) | 🤖 | Squash-Merges, Git hält sie deshalb für „nicht gemergt“; vorher je Branch prüfen, dass der PR gemergt ist |
| A5 | Remote-Branches `origin/claude/phase8-probelauf` und `…/phase8-umstieg` löschen | 🤖 | PRs gemergt |
| A6 | Probelauf-Ordner `~/foody-probelauf-20261028-1829/` auf dem Homeserver löschen | 👤 / 🤖 | Enthält eine Kopie der echten Datenbank – auch aus Datenschutzsicht besser weg |
| A7 | Zwei Checkouts → einer: Gearbeitet wird in `C:\Users\Stefan\Downloads\Foody`, gezogen in `E:\OpenCloud\…\Foody`. Festlegen, welcher bleibt, und den anderen löschen | 👤 | Spart den doppelten `git pull` nach jedem Merge und Verwechslungen; im OpenCloud-Ordner synchronisiert jeder Build Hunderte MB mit |

## Stufe 2 – Repo aufräumen (Doku, Build), je ein PR

| ID | Was | Wer | Folge, wenn man es lässt |
|---|---|---|---|
| ✅ B1 | **Alte Pläne archivieren (= W6):** `docs/superpowers/` (44 Dateien) – Sync-Etappen, Essenstracking, Scan, Einordnung, Projektstruktur, Web-Teil 1–4 nach `docs/superpowers/archiv/`; ebenso `ToDoS/v2-plan.md`, `v2-abnahme.md`, `web-frontend-plan.md`, `optimierungsplan.md`, `projektstruktur-regeln.md` nach `ToDoS/archiv/` | 🤖 PR | Eine neue Session findet bei „Tagebuch“ oder „Sync“ den alten Plan mit Haushalt und Vorrat und baut danach (dieser PR) |
| ✅ B2 | `docs/STRUKTUR-CHECK.md` (Stand 27.10., vor dem Umstieg) ins Archiv oder löschen; die offenen Punkte stehen in `v3-plan.md` (jetzt hier, R6) | 🤖 PR | Doppelte, veraltete Befundliste (dieser PR) |
| ✅ B3 | **Doku auf „nach dem Umstieg“:** `CLAUDE.md`, `README.md` und `server/README.md` sind mit #137 schon nachgezogen. Offen: `docs/architecture.md:16`, `docs/roadmap.md` (Stand 17.10.), `docs/installation.md` (Abschnitt „Umstieg von der v1“ auf einen Satz + Verweis auf die Git-Historie kürzen) | 🤖 PR | Neue Sessions arbeiten mit falschem Stand (dieser PR) |
| ✅ B4 | Phase 8 in `ToDoS/v3-plan.md` abschließen: Umstieg ist mit #137 abgehakt; offen ist das Abschluss-Review – abhaken, Befunde W1–W6/N1–N5 eintragen und auf diesen Plan verweisen | 🤖 PR | Plan zeigt das Review als offen, die Befunde stehen nur im Chat (dieser PR) |
| ~~B5~~ | Ungenutzte Einträge im Versionskatalog entfernen: `androidx-lifecycle-runtime-compose`, `compose-ui-tooling`, `compose-ui-tooling-preview` (laut Suche in allen `*.kts` nicht referenziert – vor dem Löschen per Build bestätigen) | 🤖 PR | Nur Ballast, Renovate/Updates prüfen sie mit — **entfällt:** `build-logic` (`AndroidFeatureConventionPlugin`) bindet alle drei für jedes Compose-Modul ein |
| ✅ B6 | `proguard-rules.pro:1`: Kommentar „Nur für die Variante vorschau“ ist falsch, die Regeln gelten auch fürs Release | 🤖 PR | Wer dem Kommentar glaubt und die Regeln löscht, bricht Sicherung und Sync im Release (#139) |
| ✅ B7 | Room-Schemata `1.json`–`8.json` in `android/core/data/schemas/` löschen; `9.json` (Migrationstest) und `10.json` bleiben | 🤖 PR | ~8 Dateien Ballast; die V3 kennt nur die Migration 9 → 10 (dieser PR) |
| ✅ B8 | `Platzhalter.kt` und `Bildschirme.platzhalter` aus dem Produktivcode in die Tests von `:app` verschieben; Text „Kommt in Phase 6“ entfällt | 🤖 PR | Bauzeit-Gerüst landet im Release (R8 entfernt es zwar, aber es liest sich wie unfertiger Code) (dieser PR) |
| ✅ B9 | Doppelte Konstante `SEEDED_ALT` (`Einstellungsschluessel.kt:15` und `StartdatenLader.kt:74`) auf eine reduzieren | 🤖 PR | Klein (dieser PR) |

## Stufe 3 – Rückweg zur v1 schließen (ab 2026-11-25)

Erst, wenn vier Wochen nichts schiefging. Danach gibt es keinen Weg zurück zur v1 mehr – das ist gewollt.

| ID | Was | Wer | Hinweis |
|---|---|---|---|
| C1 | Branch `v1-server` auf GitHub und Forgejo löschen (vorher Tag `v1-final` setzen, damit der Stand auffindbar bleibt) | 🤖 | `CLAUDE.md` und `server/README.md` (Rückweg) nennen ihn – im selben Schritt anpassen |
| C2 | `foody-v1.apk` aus OpenCloud löschen; die v1-Sicherungsdateien der Geräte noch behalten oder ins Archiv | 👤 | |
| C3 | Server: Sicherung `vor-migration-2-*.db` im Daten-Volume löschen | 👤 / 🤖 | Enthält den Stand vor der Umwandlung |
| C4 | Server: Altlast-Route `/api/v1/{...}` für Protokoll 1/2 (`Application.kt:100`) entfernen | 🤖 PR, Server-Rebuild | Gibt alten Apps „Bitte aktualisieren“; nach A1 gibt es keine mehr |
| C5 | Server: Migration 1 → 2 (Haushalt → Konto, `Migrations.kt:69-180`), Vorprüfung, `haushalt_umzug` und `FotoUmzug` samt Tests entfernen. Die Migrationsliste beginnt dann mit einem Grundschema in Version 2 | 🤖 PR, Server-Rebuild | ~250 Zeilen Code + Tests weniger. Danach lässt sich eine Server-Sicherung von vor dem 28.10. nicht mehr einspielen – deshalb erst nach C3 |
| C6 | App: Abmelden der v1-WorkManager-Aufträge (`SyncAusloeser.kt:60-61`, `ALTER_AUFTRAG`, `ALTER_EINMALAUFTRAG`) entfernen | 🤖 PR | Beide Geräte sind mehrfach gestartet, die Aufträge sind längst weg |
| C7 | App: Lesen des alten Schlüssels `seeded` (`StartdatenLader.kt`) entfernen | 🤖 PR | Wie C6 |
| C8 | App: Einspielen von **v1-Sicherungen** (Vorrat überspringen, alte Feldnamen in `SicherungFormat.kt`/`Sicherung.kt`) prüfen – nur entfernen, wenn du die v1-Sicherungsdateien nicht mehr brauchst | 🤖 PR | Bewusst die letzte Stufe: Das ist dein letzter Rettungsweg für Daten von vor dem Umstieg |

**Bewusst behalten (auch nach der Frist):**
- Migration 9 → 10 in der App (`Migration9auf10.kt`) samt Tests: Wird eine alte „Foody Debug“ oder eine v1-Datenbank
  aus einer Gerätesicherung je wieder geöffnet, verhindert sie Datenverlust. Kostet nur ~80 Zeilen.
- Die Namen der SharedPreferences und Schlüssel aus der v1 (`Einstellungsschluessel.kt`): Sie *sind* jetzt die
  Schlüssel der V3, ein Umbenennen bräuchte wieder eine Migration.
- ADRs zu v1/v2: Entscheidungen bleiben Geschichte, sie werden nicht archiviert.

## Stufe 4 – Server-Betrieb nach dem Umstieg

| ID | Was | Wer | Folge, wenn man es lässt |
|---|---|---|---|
| D1 | Drei verwaiste Anmeldungen (Handy und Tablet vom 11.10., „Browser (Chrome)“ vom 18.10.) widerrufen. Dafür fehlt ein Befehl: `foody-admin abmelden-ungenutzt <tage>` (widerruft Tokens ohne Anfrage seit n Tagen) | 🤖 PR, Server-Rebuild | Alte Tokens bleiben unbegrenzt gültig; geht ein altes Gerät verloren, hat es weiter Zugriff |
| D2 | Leeres Konto „stefan“ löschen; dafür ebenfalls ein Admin-Befehl `konto-loeschen` (nur für leere Konten) | 🤖 PR, Server-Rebuild | Ein zweites, ungenutztes Login am Server |
| D3 | Offenes aus dem ersten Review: `backup.sh` mit `PRAGMA integrity_check` und eine Restore-Anleitung | 🤖 PR | Eine kaputte Sicherung fällt erst beim Einspielen auf |
| D4 | **W5** vor der ersten Wiederherstellung des Servers: Admin-Befehl, der nach dem Einspielen alle Gerätestände ungültig macht, und App sendet Datensätze mit höherer Revision erneut statt sie zu löschen | 🤖 PR, Server-Rebuild | Nach einer Wiederherstellung gehen Daten auf den Geräten verloren |
| D5 | **N3** Speicher im Container: `docker inspect` auf OOMKilled prüfen; Healthcheck ohne zweite JVM (z. B. `wget`/`curl`) | 👤 / 🤖 | Bei 512 MB knapp, mit 296 Rezepten mehr Last als vorher |

## Stufe 5 – Optimierung (Review-Befunde aus dem Alltag)

Reihenfolge wie im Gesamt-Review empfohlen; jede Korrektur mit einem Test, der vorher fehlschlägt.

| ID | Was | Warum jetzt |
|---|---|---|
| ✅ **W1** | Einkauf: Einheit je Zutat unabhängig von der Reihenfolge der Planzeilen wählen (`Shopping.kt:53`), Regel 7/10 in `docs/domain-rules.md`. **Erledigt:** feste Rangfolge Stück → Packung → Dose → Gewicht → Volumen (Regel 7) | Gekauftes erscheint nach einer Planänderung wieder als „neu“ – bei Zwiebel, Ei, Knoblauch der Normalfall |
| ✅ **W2** | Sync: abgelehnte Datensätze unter Einstellungen → Konto anzeigen (`SyncDao.probleme()` ist schon da), fehlgeschlagene Abholungen wiederholen, bei vollem Speicher abbrechen. **Erledigt:** Liste unter „Konto“, erneutes Holen nach einem Tag, Abbruch mit `speicher_voll` | Datensätze bleiben still auf einem Gerät liegen; nach dem Import von 435 Rezepten wahrscheinlicher |
| ✅ **W3 + W4** | Nährwerte nicht auf dem Haupt-Thread: `flowOn(rechnen)` in `HeuteRepository.kt:44`, `RezeptRepository.kt:38/51`, Rezept-Flow teilen; `WhileSubscribed(5_000)` statt `Eagerly` in `EintragenViewModel`/`EditorViewModel`, fester Schlüssel für den Tablet-Editor. **Erledigt:** beide Repositories mit `flowOn`, „Heute“ und Eintragen teilen den Rezept-Flow; Eintragen nur noch sichtbar; Tablet-Editor mit festem Schlüssel (ein ViewModel, neuer Auftrag beginnt frisch) – der Editor bleibt `Eagerly`, weil `speichern()` `zustand.value` liest. Gemessen: ~20 ms je Neuberechnung auf dem PC (300 Rezepte, 500 Zutaten), bisher auf dem Haupt-Thread und auf „Heute“ doppelt | Mit jetzt 296 Rezepten und 494 Zutaten spürbar; vorher einmal mit dem Baseline-Profil messen (Start, Wechsel auf „Heute“), damit der Gewinn belegt ist |
| N1 | Server: nach `compact` kein Voll-Download bei jedem Start (`SyncService.kt:43`) | Akku/Datenvolumen |
| N2 | Speichern-Knöpfe nach Drehen nicht gesperrt lassen (`EditorViewModel.kt:162`, `KochenViewModel.kt:167`, `EintragenViewModel.kt:224`) | Selten, aber ärgerlich |
| N4 | Login-Bremse auch je IP, Fehlversuche loggen (`LoginThrottle.kt:19`) | Heimnetz, geringe Wirkung |
| N5 | Rezeptkopf ohne feste Höhe 260 dp (`DetailBereiche.kt:96`) | Bei großer Schrift abgeschnitten |
| E1 | CI: doppelt laufende Tests zusammenlegen; Basis-Image und Gradle-Download pinnen | Schnellere, reproduzierbare CI |
| E2 | Gradle-Deprecations (`--warning-mode all`) und die Manifest-Warnung zu `WorkManagerInitializer` (`AndroidManifest.xml:39`) beheben | Vor dem nächsten Gradle-Hauptversionswechsel |
| E4 | **CI: Release einmal starten**, nicht nur bauen: Kurztest der R8-Variante auf dem Emulator (Start, „Frei“ im Tagebuch öffnen) | Z1 fiel erst auf dem Handy auf – der Release-Build war grün |
| E5 | Referenzbilder (111 PNG, 36 MB) in Git LFS oder kleiner aufnehmen | Jedes Neuaufnehmen vergrößert das Repo dauerhaft; im OpenCloud-Checkout wird alles mitsynchronisiert |
| E6 | `:core:data` (6.000 Zeilen, ein Drittel der App) beobachten; bei weiterem Wachstum die Sicherung als `:core:sicherung` abtrennen | Jede Änderung dort baut alle Features neu – heute noch kein Handlungsbedarf |
| E3 | Rest der Liste „Weitere aus dem Review“ (erstes V3-Review): Kochmodus rekomponiert sekündlich, Tagesziel speichert Zwischenstände, verwaiste Fotos nach Sync, 4 A11y-Lücken (SeitenListe, Timer-Chip, Quellen-Link, Überschriften), falscher Leer-Grund bei „Leere Tage füllen“, Argon2 auf Request-Threads, Docker ohne Gradle-Cache, toter `RecordStore.lebende()` (nur noch im Test), ktlint/detekt | Beim nächsten Anfassen der Bereiche |

### Nachprüfung 2026-10-31 (neu, klein)

| ID | Was | Warum |
|---|---|---|
| N6 | `KochenViewModel.fertig()`: `fertigGetippt` wird vor `gekochtMarkieren` gesetzt; wirft die Datenbank, bleibt „Fertig“ gesperrt, ohne dass etwas markiert wurde. Zusammen mit N2 beheben | Selten, dann aber ohne Ausweg bis zum Neustart |
| N7 | Server `Compactor.kt:42-58`: Änderungszeit der Fotos wird vor der Referenzabfrage gelesen; ein `/photos/missing` dazwischen verhindert das Löschen nicht. Zeit direkt vor `delete` erneut prüfen | Sehr kleines Zeitfenster; dann fehlt ein Foto dauerhaft auf dem anderen Gerät |
| N8 | `SyncSpeicher.vollabgleich`: unbenutzte Zutaten, die der Server nicht kennt (unveränderte Startzutaten nach „Zusammenführen“, alle nach „Ersetzen“), werden nach einem `410` still gelöscht | Nur nach Wiederherstellung des Servers oder > 90 Tagen offline |
| N9 | `docs/security.md:75`: Datenbank und Sicherungs-ZIP als „keine sensiblen Daten“ eingestuft, enthalten aber das Ernährungstagebuch (Gesundheitsdaten). Einstufung korrigieren, optional Passwort für die Sicherung | Sicherung liegt unverschlüsselt im Cloud-Ordner |

### Aus V3-Plan und Abnahme (R6, 2026-10-31)

Beim Archivieren von `v3-plan.md` und `v3-abnahme.md` noch offen:

| ID | Was | Wer |
|---|---|---|
| V1 | Android-Studio-Profiler auf Handy und Tablet (Start, Scrollen, Speicher); das Baseline-Profil vom 28.10. stammt vom Emulator. Zusammen mit der Messung vor W3+W4 | 👤 |
| V2 | TalkBack-Durchlauf und Schriftgröße 200 % (dazu N5 und die A11y-Lücken aus E3) | 👤 / 🤖 |
| V3 | Abnahme „Opt-in: ohne Verbindung keine Netzwerkzugriffe“ prüfen: frisch installiert, nicht angemeldet – kein Aufruf an Server oder Open Food Facts außer auf Knopfdruck | 🤖 |
| V4 | Abnahme „Betrieb“: Speicherlimit (512 MB) ist im Compose; nachzuweisen sind der nächtliche Lauf von `backup.sh` (Cron) und dass die Sicherung im OpenCloud-Ordner ankommt; dazu D3 und D5 | 👤 |
| V5 | Abnahme „Signierte Release-APK im OpenCloud-Ordner“ (`:app:apkNachOpenCloud`): ablegen und abhaken, sobald 3.0.0 dort liegt | 👤 |
| V6 | **R2-N3** Maschinenspezifisches (JAVA_HOME, ö-Pfad, Emulator) aus `CLAUDE.md` in eine nicht eingecheckte `CLAUDE.local.md`? | 👤 entscheidet |

## Stufe 6 – Gesamtprüfung 2026-10-31: Best Practice und Altbestand

Bisherige Reviews (27.10. R, 28.10. R2 und Gesamt-Review W/N, 31.10. Nachprüfung) suchten Fehler und Sicherheitslücken.
Diese Prüfung schaut auf Aufbau, Wiederverwendung und Hardcoding im ganzen V3-Code (App, `:domain`, Server) und auf
Altbestand. Werkzeug: Suche über alle `src/main`-Dateien; ein Linter (ktlint/detekt, E3) würde das künftig automatisch
prüfen.

### Teil 1 – Code-Qualität

| ID | Befund | Vorschlag | Prio |
|---|---|---|---|
| Q1 | **Abstände hart kodiert:** `FoodyAbstaende` (4-dp-Raster, `FoodyFormen.kt:14`) wird nirgends benutzt; in Features und `:app` stehen 313 feste `.dp`, davon 8.dp 84×, 16.dp 78×, 12.dp 33×, 24.dp 15× | Werte des Rasters durch `FoodyAbstaende.s1…s6` ersetzen (deckt ~230 Stellen), Rest bleibt bewusst | mittel |
| Q2 | **Breiten mehrfach:** 840 dp (zwei Spalten/Rail) 4× als eigene Konstante (`Hauptgeruest`, `EinkaufScreen`, `KochenScreen`, `RezepteBereich`), 720 dp (Inhaltsbreite) 3× zusätzlich zu `INHALT_MAX_BREITE` im Designsystem | Eine Stelle im Designsystem (`FoodyBreiten.zweispaltig`, `.inhalt`) | mittel |
| ✅ Q3 | **`STOPP_MS = 5_000L` 5× definiert**, dazu `SharingStarted.Eagerly` an 17 Stellen in 8 ViewModels | Eine gemeinsame Konstante in `:core`, `Eagerly` → `WhileSubscribed` (zusammen mit W4). **Erledigt:** `SolangeSichtbar` in `:core:designsystem`, Eintragen umgestellt. Im Folge-PR (Wunsch des Nutzers: bei offener App darf alles laufen und gespeichert bleiben, geschlossen nichts) daraus `SolangeAppOffen`: läuft, solange die App offen ist oder jemand zusieht; jetzt in allen ViewModels außer dem Kochmodus (`Eagerly`, hält den Bildschirm an). Einkauf liest die angezeigte Liste in Aktionen frisch. Der Sync plant nur bei offener App; beim Schließen geht eine wartende Änderung einmal sofort hinaus | mittel |
| Q4 | **Doppelte Hilfen:** `kcalText` 2× und **uneinheitlich** (Tagebuch „1.234 kcal“, Zutaten „1234 kcal“), `zahlText` 2× gleich (`ZutatEntwurf`, `EintragZeile`), `tagKurz` 2× mit verschiedenem Format, `neueId` 2×, Textfeld `Feld` 3× (Verbinden, Rezept-Editor, Zutat-Editor), `LoeschenFrage` 2× | Zahlen- und kcal-Texte an einer Stelle (`:core:data` bzw. `:domain`), Textfeld und Frage-Dialog ins Designsystem | mittel |
| Q5 | **Dispatcher fest verdrahtet:** `Dispatchers.IO/Default` direkt in 14 Dateien, nur `EinkaufRepository` und `WocheRepository` nehmen einen `rechnen`-Kontext an | Hilt-Qualifier `@Rechnen`/`@EinAusgabe` und überall injizieren; macht W3 einfach und Tests schneller | mittel |
| Q6 | **UI-Texte im Code:** keine `strings.xml`-Texte, rund 70 `Text("…")` plus Texte als Parameter | Für eine deutschsprachige Ein-Nutzer-App vertretbar; als ADR festhalten statt umbauen | niedrig |
| Q7 | `:baselineprofile` setzt `compileSdk/minSdk/targetSdk` und Java 17 selbst statt über `build-logic` | Werte aus dem Convention-Plugin nehmen | niedrig |
| Q8 | Schrift `fraunces.ttf` 312 KB (Variable Font, alle Schriftsysteme) | Auf Latein beschneiden (~60 KB) | niedrig |

Gut: Build-Cache, Configuration-Cache und parallele Builds an, alle Versionen im Katalog, keine festen Farben
außerhalb des Designsystems, keine `.sp` außerhalb, nur zwei feste URLs (Open Food Facts, lokaler Healthcheck),
Ressourcen schlank (381 KB), Features ohne Querverweise.

### Teil 2 – Was weg kann

**Code ohne Nutzen in der V3** (nur noch von Tests oder gar nicht benutzt; mit den Tests entfernen):

| ID | Was | Hinweis |
|---|---|---|
| ✅ R1 | `:domain`: `RecipeSort`, `IngredientCatalog.assumedAtHome`, `RecipeProfile.carbsPerServing`/`reliable`, `NutritionResult.overallCompleteness`/`missingIngredientIds`/`lineGaps`/`lineEnergyPerServingKj`, `Mahlzeit.vorschlagFuer`, `DateRange.days` | Reste der v1-Oberfläche; vor dem Löschen je Regel in `docs/domain-rules.md` prüfen (dieser PR) |
| ✅ R2 | `SyncDao.loeschenRevision`, `Einstellungsschluessel.EINKAUF_ANSICHT` | ungenutzt (dieser PR) |
| R3 | `FoodyAbstaende` | nur wenn Q1 nicht umgesetzt wird |
| R4 | B5, B7–B9 und Stufe 3 (C1–C8, ab 25.11.) | stehen oben |

**Doku:**

| ID | Was | Vorschlag |
|---|---|---|
| ✅ R5 | 22 Pläne und Specs der V3-Phasen 2–6e3 (3.900 Zeilen) – alle umgesetzt, beschreiben Zwischenstände (`app3`, Platzhalter) | nach `docs/superpowers/archiv/v3/`; `architecture.md` beschreibt den Ist-Stand |
| ✅ R6 | `ToDoS/v3-plan.md` (430 Zeilen, 6 offene Punkte) und `v3-abnahme.md` (3 offen) | übernommen unter „Aus V3-Plan und Abnahme“, beide in `ToDoS/archiv/` |
| ✅ R7 | `docs/architecture.md` (451 Zeilen): Abschnitte nach Phasen benannt, Tabelle „Datenbank: Vorgeschichte“ der v1 | nach Bereichen (Modulen) gegliedert, v1-Tabelle als Anhang ans Ende; kürzen mit Stufe 3 |
| ✅ R8 | ADR 0005 (Bring!) nennt `ShoppingListExporter`, den es seit der V3 nicht mehr gibt (Teilen als Text gestrichen) | Status „abgelöst durch ADR 0011“ |
| R9 | `server/README.md`, `installation.md`: Rückweg zur v1 | mit Stufe 3 entfernen |
| R10 | `PortionenNachtrag` (`:core:data`) samt Hilt-Bereitstellung: in der App ohne Knopf (geplant für Phase 6, nie gebaut). Re-Importe laufen über die Dublettenerkennung | 👤 entscheidet: Knopf in den Einstellungen bauen oder entfernen (auch `RezeptImport.portionenNachtragen` in `:domain`) |

**Lokal (kein Repo):** `.claude\worktrees\offene-fehler` (283 MB, lose v1-Kopie vom 11.10.), Build-Reste `app/`, `domain/`,
`sync-protocol/` (125 MB), sechs geparkte Worktrees archivierter Sessions, der Checkout in `Downloads` (A7).

## Empfohlene Reihenfolge

1. **Diese Woche:** ✅ Doku-PR B1–B4 (#140), ✅ Z1 (#139), A3/A4 größtenteils erledigt. ✅ B7–B9, R1–R2 (B5 entfällt),
   ✅ Doku-PR R5–R8 (#143).
2. **Danach:** W1 und W2 (je ein PR), dann W3+W4 zusammen mit Q3 und Q5 (gleiche Stellen), mit Messung vorher/nachher.
   Q1, Q2 und Q4 als ein Designsystem-PR (Bildvergleich neu aufnehmen).
3. **Server, ein Rebuild für alles:** D1–D4 zusammen, D5 prüfen.
4. **Ab 25.11.:** Stufe 3 (C1–C7), C8 nur auf deinen Wunsch.
5. Danach ist die Bahn frei für die KI-Hilfe (`ToDoS/ki-plan.md`).

**Geschätzter Gewinn (geschätzt, nicht gemessen):** ~45 Doku-Dateien aus dem Suchpfad neuer Sessions, ~300 Zeilen
Server- und ~60 Zeilen App-Code weniger, 3 Katalog-Einträge und 8 Schemadateien weniger, 16 Branches und 4
Worktrees weg, ein Checkout statt zwei.
