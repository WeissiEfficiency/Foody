# Optimierungsplan – ganzes Repo

Stand: 2026-10-10 · Grundlage: Bestandsaufnahme von `main` (e94ffde) plus offener PR #74

## Ausgangslage (gemessen)

| Kennzahl | Wert |
|---|---|
| Code `app` / `domain` / `server` / `sync-protocol` | 13.207 / 1.873 / 1.777 / 503 Zeilen |
| Tests (gleiche Reihenfolge) | 6.827 / 1.498 / 1.734 / 280 Zeilen; Instrumentation zuletzt 133/133 |
| Größte Dateien | `RecipeDetailScreen` 535, `SyncEngine` 463, `PlannerScreen` 451, `RecipeListScreen` 428 Zeilen |
| Debug-APK | 26,5 MB (Release mit R8 + `shrinkResources` deutlich kleiner – noch zu messen) |
| Abhängigkeiten `app` | 32 `implementation`-Einträge, u. a. Ktor/OkHttp, Room, Hilt, WorkManager, Coil 3, Play-Dienste (ML Kit, Code Scanner) |
| Baseline-Profil | vom **2026-10-03** – deckt Einordnung, Tagebuch, Scan und neuen Planer **nicht** ab |
| Hintergrund-Sync | WorkManager alle 15 min (nur mit Netz, nur wenn Sync aktiv) + Beobachtung der Outbox |

**Messgerät fehlt:** Der Test-Emulator `Foody_Test` existiert nicht mehr. Für Phase 0 und 4 braucht es ein Gerät
(neue AVD oder dein Handy per USB) – Instrumentation-Tests laufen nie auf deinem Medium_Phone.

---

## Phase 0 – Messbasis schaffen (zuerst, ½ Tag)

Ohne Zahlen lässt sich „schneller/kleiner“ nicht belegen. Einmal messen, nach jeder Phase wieder.

1. **Release-APK-Größe** (`assembleRelease`, unsigniert reicht) und Aufschlüsselung mit dem APK Analyzer.
2. **Kaltstart** mit Macrobenchmark (`:baselineprofile` hat die Infrastruktur): Zeit bis „Was kochen wir?“ sichtbar.
3. **Ruckeln** beim Scrollen von Rezeptliste und Planer (`FrameTimingMetric`).
4. **Speicher** nach dem Öffnen von Rezeptliste → Detail → Tagebuch (`dumpsys meminfo`).
5. Ergebnisse als Tabelle hier eintragen.

**Ergebnis Phase 0** (2026-10-10, `main` 669fcf1, Emulator `Foody_Test` API 36, leere Datenbank):

| Messung | Wert | Bemerkung |
|---|---|---|
| Release-APK (unsigniert, R8) | **3,3 MB** | Code (`classes.dex`) 2,5 MB, `resources.arsc` 581 KB (v. a. Bibliotheks-Übersetzungen), Rest < 150 KB |
| Kaltstart `am start -W`, 10× (benchmarkRelease) | **Median ≈ 800 ms** (613–1180) | Emulator schwankt stark; Profil wird beim ersten Start installiert |
| Speicher nach Start | 50 MB PSS (Java 5 MB, Native 15 MB) | |
| Speicher nach allen Tabs | 63 MB PSS (Java 9 MB, Native 18 MB) | unkritisch |
| Macrobenchmark `StartupBenchmark` | nicht messbar | Emulator stürzt ab; zusätzlich übersprang das Plugin die Tests ohne `androidx.benchmark.enabledRules=Macrobenchmark` |
| Ruckeln (Frame-Timing) | offen | braucht Testdaten + eigenen Benchmark (Phase 4) |

**Neue Funde dabei:** Der Baseline-Profil-Generator tippt noch auf den Tab „Vorrat“ (gibt es seit dem Tagebuch nicht mehr)
und kennt Tagebuch, Planer-Dialog und Scan nicht – das Profil ist also doppelt veraltet.

## Phase 1 – Fehler finden (1–2 Tage)

### 1a. Bekannte offene Fehler (aus den Reviews zu A, B, C) – priorisiert

| Prio | Bereich | Fehler | Aufwand |
|---|---|---|---|
| ~~hoch~~ | Scan | #74 noch nicht gemergt: bekannter Strichcode überschreibt vorhandene Zutat | ✅ #74 |
| mittel | Scan | Fehlgeschlagener Modul-Download → dauerhaft „wird vorbereitet“ (Ergebnis von `installModules` auswerten) | ✅ |
| mittel | Scan | Basis wird beim Foto immer gesetzt (Getränk ohne „100 ml“ im Bild → g) | ✅ |
| mittel | Scan | Name im Tagebuch nicht über `IngredientCatalog` normalisiert („Mehl“ ≠ „Weizenmehl“) | ✅ |
| mittel | Tagebuch | kurzes Flackern alter Vorschläge beim Tageswechsel (`combine` über verschiedene Tage) | ✅ |
| klein | Scan | Kamera-Temp-Datei bleibt bei Abbruch; alter Strichcode hängt am späteren Foto; Meldung bei Online-Suche aus | ✅ |
| klein | Scan | englisch „1,046 kJ“ als 1,046 gelesen | ✅ |
| klein | Tagebuch | kcal je Zeile ±1 zur Summe (doppelte Rundung) | ✅ |
| klein | Planer | Ø erkennt alte Freitext-Abendessen nicht; Wochenvorschlag neben „Abendbrot“ (offen, genauer beschreiben) | klein |
| klein | A | „Overnight Oats“ im Namen ✅; Editor entfernt unbekannte Einordnungswerte ✅; Suchtext im Planer-Dialog (offen, genauer beschreiben) | klein |
| klein | UI | „Mehr“ im Vorrat nicht hervorgehoben | ✅ |

### 1b. Bisher **nicht** reviewte Bereiche – gezielte Fehlersuche

Die Abschluss-Reviews deckten nur die neuen Features ab. Älterer Code wurde nie systematisch geprüft:

| Bereich | Warum riskant | Wie |
|---|---|---|
| **Sync** (`SyncEngine`, `SyncApplier`, `SyncLocalStore`) | größte Logik, Datenverlust-Gefahr bei Konflikten, Voll-Abgleich löscht lokal | Code-Review mit Fokus Konflikte/Abbruch/Wiederanlauf + Test „Voll-Abgleich mit ungesendeten Änderungen“ |
| **Sicherung/Import** (`BackupRepository`, `RecipeImportRepository`) | Fremddaten, Fotos, große Dateien | Review + Fuzz-Test mit kaputten/übergroßen Dateien |
| **Einkaufsliste** (`Shopping.kt`, `ShoppingViewModel`) | Mengenrechnung, Neu-Berechnen-Diff, Vorratsabzug | Review der Domain-Regeln 9–21 gegen den Code |
| **Kochmodus/Timer** (`CookTimerService`) | Hintergrunddienst, Wakelock | Review Lebenszyklus, Wakelock-Freigabe in allen Pfaden |

Vorgehen je Bereich: `/code-review` auf den Ordner, Funde wie bisher mit Test, der vorher fehlschlägt.

**Übrige Bereiche (2026-10-11):** Sicherung/Import und Kochtimer durchgesehen – ohne Befund (Sync-Sperre für Löschen/Import vorhanden, alle Nutzerdaten in der Sicherung, Zip-Slip/Größengrenzen, Wakelock nur bis zum nächsten Ende und in allen Pfaden freigegeben). Einkaufsliste laut Stefan in Ordnung, nicht weiter geprüft.

**Ergebnis Sync (2026-10-10), 10 Funde:**

| Fund | Stand |
|---|---|
| Vom Server abgelehntes Foto (4xx/413, z. B. Proxy-Limit) hielt das Rezept für immer in der Outbox, jeder Lauf lud die Bytes erneut hoch | behoben: Rezept geht ohne Foto mit `photo_unsyncable` raus |
| `missing_reference` löste sich nie, wenn das Verweisziel nur lokal existiert (z. B. Startzutat nach „nur herunterladen“) | behoben: lokale Ziele werden nachgereicht |
| „Trennen“ während eines Laufs: der Lauf schrieb nach dem Aufräumen Revisionen/Probleme zurück | behoben: gemeinsame Lauf-Sperre im `SyncLocalStore`, `deactivate` wartet |
| Foto-Wünsche überlebten das Trennen, beim Verbinden mit anderem Server ging ein fremder Hash raus | behoben: `deactivate` leert `sync_photo_wanted` |
| `TooLarge` beim Foto-Download brach den Lauf dauerhaft ab | behoben |
| Token wurde je HTTP-Anfrage per Keystore entschlüsselt | behoben: Cache solange der gespeicherte Wert gleich ist |
| `isStillNeeded`/Löschen je Typ doppelt in Engine und Applier; Rezept-Payload doppelt dekodiert; Problemtabelle je Rezept geladen | behoben |
| Pull puffert alle Seiten vor dem Anwenden | bewusst so (Eltern vor Kindern über Seitengrenzen), Test `pullIsAppliedOnceAfterAllPages`; erst bei großen Haushalten angehen |
| `hashRecipePhotos` läuft je Batch über die ganze Outbox | geringer Nutzen (Hash-Cache greift), offen |
| Test „Voll-Abgleich mit ungesendeten Änderungen“ | war schon da (`fullResyncKeeps…`, `cursorExpiredTriggersFullResync`) |

## Phase 2 – Logik bewerten (1 Tag)

1. **Rechenregeln gegeneinander prüfen:** Nährwerte erscheinen an vier Stellen (Rezeptdetail, Rezeptliste,
   Planer, Tagebuch) mit drei Rundungswegen. Eine gemeinsame Funktion „kJ → angezeigte kcal“ in `domain`, Tests
   „Zeilen summieren sich zur Anzeige“.
2. **Domain-Regeln vs. Code:** `docs/domain-rules.md` (26 Regeln) Punkt für Punkt mit Testnamen verknüpfen;
   Regeln ohne Test bekommen einen.
3. **Einordnungs-Wortliste** gegen die echte Rezeptsammlung (114 Rezepte) neu messen (Stand: 6 ohne Treffer) und
   Fehlzuordnungen (z. B. Gruyère-Kuchen) aufnehmen.
4. **Grenzwerte vereinheitlichen:** Plausibilität (Scan), `parseNichtNegativ` (UI), Validator (Server) und
   `decimal()` (Sicherung) nutzen eigene Grenzen – eine gemeinsame Quelle in `sync-protocol`/`domain`.

**Ergebnis Phase 2 (2026-10-11):**
1. ✅ `NutritionResult.kcal(kj)` rundet einmal; alle Anzeigen nutzen es, der Faktor 4,184 steht nur noch dort
   (vorher vier Kopien und doppelte Rundung: 349,46 → 350).
2. ✅ Alle 26 Regeln in `docs/domain-rules.md` mit Tests verknüpft; neu belegt: Regel 8 (Planen ändert Vorrat nicht)
   und 17 (direkt auf die Liste zieht keinen Vorrat ab).
3. ✅ Zweite Messung (114 Rezepte): 4 ohne Treffer (Eigennamen wie „La Garbure“); behoben: herzhafte Kuchen
   (Gruyère-Kuchen), gefüllte Pfannkuchen, Rösti.
4. ✅ `ZahlGrenzen` in `sync-protocol` für Validator, Sicherung und Eingabefelder; die Scan-Plausibilität bleibt eigen
   (physikalisch sinnvolle Werte, keine Format-Grenze).

## Phase 3 – Ressourcen minimieren (1–2 Tage)

### Rechenzeit / Datenbank
| Fund | Wirkung | Maßnahme |
|---|---|---|
| Rezeptliste berechnet `RecipeProfiles` für **alle** Rezepte bei jeder Änderung an Rezepten, Zeilen oder **irgendeiner** Zutat | bei Sync-Läufen und Imports wiederholt | Ergebnis je Rezept cachen (Schlüssel: `recipe.updatedAt` + Zutaten-Version), nur Geändertes neu |
| Planer, Rezeptliste, Tagebuch beobachten jeweils `observeAll()` von Rezepten **und** Zutaten getrennt | doppelte Abfragen/Objekte | gemeinsamer, app-weit geteilter `StateFlow` im Repository (`shareIn` im App-Scope) |
| Tagebuch-State trägt alle Rezepte + alle Zutaten (für den Dialog) bei jeder Emission | unnötige Arbeit beim Blättern | Listen erst im Dialog laden |
| Suche ohne `debounce`: jeder Buchstabe startet `LIKE`-Abfrage mit Unterabfrage auf Zutaten | spürbar erst bei großen Sammlungen | `debounce(150 ms)` für die Datenbank-Abfrage (Eingabefeld bleibt sofort) |
| `shrinkAll()` liest bei **jedem** Start alle Fotos (Bounds) | wächst mit der Fotozahl | einmalig je App-Version (Flag wie `seedVersion`) |

### Akku / Netz
| Fund | Maßnahme |
|---|---|
| Periodischer Sync alle 15 min, zusätzlich Outbox-Beobachtung | Intervall auf 60 min, lokale Änderungen weiter sofort (Outbox) – Pull beim App-Öffnen statt im Viertelstundentakt |
| Open-Food-Facts-Abfragen ohne Cache | Antwort je Strichcode merken (ist durch `ingredient.barcode` schon weitgehend abgedeckt) |

### App-Größe
| Fund | Maßnahme |
|---|---|
| Release-Größe unbekannt | Phase 0 messen; R8-Regeln prüfen (unnötige `-keep`), `resConfigs("de")` für Sprachressourcen der Bibliotheken |
| Ktor + OkHttp nur für Sync und eine GET-Abfrage | lassen (geteilt); `:server` ist bereits nur `testImplementation` |

### Oberfläche
| Fund | Maßnahme |
|---|---|
| `items(state.tags)` ohne `key` (Rezeptliste) | `key = { it }` |
| große Screens (535/451/428 Zeilen) | aufteilen, wo Teile unabhängig neu zeichnen sollen (Stabilität der Parameter prüfen, Compose-Compiler-Report) |

**Ergebnis Phase 3 (2026-10-11):**

| Maßnahme | Stand |
|---|---|
| `localeFilters = de` (Übersetzungen der Bibliotheken) | ✅ Release-APK 3,43 → 2,91 MB (−15 %), `resources.arsc` 595 → 82 KB |
| `shrinkAll()` bei jedem Start | ✅ nur einmal je Verkleinerungs-Version; Fotos aus Sicherungen verkleinert der Import |
| Suche ohne `debounce` | ✅ 150 ms für die Abfrage, Eingabe sofort, Leeren ohne Verzögerung |
| `RecipeProfiles` für alle Rezepte bei jeder Änderung | ✅ Cache je Rezept (Schlüssel: Rezept, Zeilen, verwendete Zutaten) |
| `items(state.tags)` ohne `key` | ✅ |
| Sync alle 15 min | ✅ stündlich + beim Öffnen der App; `UPDATE` bringt das Intervall auf bestehende Installationen |
| Geteilter `observeAll()`-Flow, Listen erst im Tagebuch-Dialog | ✗ bewusst nicht: ein app-weit geteilter Flow hielte die Abfragen dauerhaft aktiv (Speicher), der State trägt nur Referenzen |
| große Screens aufteilen | ✗ ohne gemessenes Problem; erst bei Rucklern (Compose-Compiler-Report) |
| Open-Food-Facts-Cache | ✗ durch `ingredient.barcode` abgedeckt |

## Phase 4 – Vorladen / Start beschleunigen (½–1 Tag)

1. **Baseline-Profil neu erzeugen** (Stand 2026-10-03): Abläufe ergänzen – Planer mit Dialog, Tagebuch öffnen und
   blättern, Zutaten-Dialog. Größter Hebel für Start und erstes Scrollen.
2. **Startup-Profil** (DEX-Layout) aus demselben Generator aktivieren.

**Ergebnis 4.1/4.2 (2026-10-10):**
- Generator tippte noch auf „Vorrat“ (seit Teil B unter „Mehr“). Neu: Planer-Dialog „Gericht planen“, Tagebuch-Eintrag
  mit den Reitern Rezept/Zutat/Frei, unter „Mehr“ Vorrat und Zutatenkatalog.
- Profil 24 357 → 28 218 Regeln; App-eigene 1 311 → 2 534 (Tagebuch 0 → 343, Planer 114 → 214, Zutaten 6 → 126,
  Vorrat 42, Scan 39).
- Kaltstart (`am start -W`, speed-profile) bleibt im Rauschen des Emulators (Median 850–1 200 ms je nach Lauf); der
  Startpfad war schon im alten Profil. Gewinn: erstes Öffnen der neuen Bildschirme ohne JIT-Ruckler.
- **Startup-Profil verworfen:** +150 KB APK, im Emulator kein messbarer Unterschied (A/B: 1 222/1 071 ms mit, 1 128 ms
  ohne). Auf einem echten Gerät mit langsamem Speicher erneut messen.
- **Stolperstein Erzeugung:** Benchmark 1.5 schreibt nur `…-startup-prof.txt`, und AGP 9.4 löscht ihn beim
  Deinstallieren, bevor er geholt wird – `generateReleaseBaselineProfile` endet „erfolgreich“, das Profil fehlt aber.
  Ablauf steht in `baselineprofile/build.gradle.kts`.
3. **Datenbank früh öffnen:** Room im App-Start auf einem Hintergrund-Thread anstoßen (erste Abfrage), damit der
   erste Bildschirm nicht auf das Öffnen wartet.
4. **Play-Dienste-Module vorladen:** Beim ersten Öffnen des Zutaten-Dialogs bzw. Reiters „Frei“ `ModuleInstall`
   im Hintergrund anstoßen (nur WLAN) – dann ist „Scanner wird vorbereitet“ beim ersten Scan meist schon vorbei.
5. **Bilder:** „Rezept des Tages“ und die ersten Karten per Coil vorladen (`ImageLoader.enqueue`), Speicher-Cache
   bewusst begrenzen.

**Ergebnis 4.3–4.5 (2026-10-11):**
- 4.3 Datenbank früh öffnen: war schon erfüllt – `FoodyApp.onCreate` liest im Hintergrund `sync_state` und öffnet
  damit die Datenbank (samt Migrationsprüfung), bevor der erste Bildschirm sie braucht.
- 4.4 ✅ Scanner-Module: Sobald der Knopf „Von Packung scannen“ zu sehen ist, `ModuleInstall.deferredInstall` für
  Code Scanner und Texterkennung (Play-Dienste wählen den Zeitpunkt, kein Dialog; einmal je Prozess).
- 4.5 ✗ Fotos vorladen: Das „Rezept des Tages“ lädt ohnehin sofort auf dem Startbildschirm; früheres Laden brächte
  Millisekunden und kostete Speicher für Bilder, die vielleicht nie zu sehen sind.

## Phase 5 – Absichern (laufend)

**Zurückgestellt (2026-10-11):** Phase 5 kommt später. Start-Messung lieber einmal auf dem echten Gerät vor einem
Release als in der CI (auf dem Emulator stürzte der Macrobenchmark ab, Messwerte schwanken stark).

- Macrobenchmark in der CI (oder manuell vor Releases) mit Grenzwerten aus Phase 0.
- Test-Emulator dauerhaft klären (eigene AVD oder Gerät) – sonst fehlen lokale Instrumentation-Läufe.
- Ein PR je Phase/Bereich, wie bisher mit Auto-Merge.

## Empfohlene Reihenfolge

1. #74 mergen (läuft).
2. **Phase 0** (Messbasis) – braucht ein Testgerät.
3. **Phase 1b Sync-Review** – höchstes Risiko (Datenverlust), bisher ungeprüft.
4. **Phase 4.1 Baseline-Profil** – größter spürbarer Gewinn bei kleinem Aufwand.
5. Phase 1a (offene Fehler, „mittel“ zuerst), dann Phase 3, dann Phase 2.
