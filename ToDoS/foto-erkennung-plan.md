# Foody – Mahlzeit aus Foto (auf dem Gerät)

Stand: 2026-11-01. Phase 0 erledigt (Modell gewählt, [ADR 0017](../docs/adr/0017-fotoerkennung-auf-dem-geraet.md)),
weiter mit Phase 1. Eigenes Vorhaben neben der KI-Hilfe ([`ki-plan.md`](ki-plan.md)).
Die Phasen werden der Reihe nach abgearbeitet; die nächste beginnt, wenn die vorige abgehakt ist.

**Oberfläche wartet auf das neue Design:** Phase 1 und 2 haben keine Oberfläche und laufen sofort. Phase 3 (Knopf und
Vorschau im Eintragen-Blatt) und Phase 4 (Einstellungen) beginnen erst, wenn die Umsetzung des Milchglas-Entwurfs für
Heute und Einstellungen in `main` ist; vorher kurz beim Nutzer nachfragen.

## Ziel

Nach dem Essen ein Foto machen: Foody erkennt, was es ist und wie viele kcal es hat, und schlägt einen freien
Tagebucheintrag für den Tag vor (Name, kcal, Eiweiß, KH, Fett). Ein Häkchen speichert ihn. Die Werte sind geschätzt und
in der Vorschau als „bitte prüfen“ markiert wie beim Scan.

Kein Abgleich mit eigenen Rezepten oder Zutaten (Nutzer, 2026-11-02): Es soll nur das Bild erkannt und eingetragen
werden. Die anfangs geplanten Wege „Rezept“ und „Teller“ sind gestrichen; das Modell hat in der Messung auf dem 13R
ohnehin fast nie „keins davon“ gewählt.

Packungen laufen weiter über den bestehenden Scan (`:core:scan`, ML Kit + Open Food Facts).

## Rahmen

- **Alles auf dem Handy.** Erkennung und Rechnung laufen auf dem Gerät, kein Claude, kein Server, keine Cloud-KI.
  Das Foto verlässt das Gerät nie und wird nicht gespeichert (nur für die Erkennung im Cache, danach gelöscht).
- **Server bleibt, wie er ist.** Kein neuer Endpunkt, kein Protokoll- oder Schemawechsel. Gesynct werden nur die
  fertigen Tagebucheinträge, wie heute. Server-Rebuild: nein.
- **Das Modell rechnet keine Nährwerte, wenn es eine Quelle gibt.** Es sagt nur „was“ und „wie viel“. Die Werte
  kommen aus Rezept oder Katalog und werden wie bisher in `:domain` berechnet. Nur bei Gekauftem ohne Treffer im
  Katalog stammen die Werte vom Modell.
- **Immer Vorschau, nie still speichern.** Jeder Vorschlag ist bearbeitbar; gespeichert wird erst mit dem Häkchen.
- **Offline nach der Einrichtung.** Das Modell wird einmal geladen (Download oder Datei aus OpenCloud, siehe Phase 2)
  und liegt dann im App-Speicher. Ohne Modell ist der Knopf ausgeblendet.
- **Nicht in die APK.** Das Modell (1,55 GB) wird nachgeladen; die APK wächst nur um die Laufzeit.
- **Zeit je Foto:** bis 60 s ist in Ordnung, Ziel 15–30 s (Nutzer, 2026-11-01).
- **Nur auf dem Handy** (OnePlus 13R, Snapdragon 8 Gen 3, 12 GB RAM). Es bleibt eine APK für beide Geräte; auf dem
  Tablet wird einfach kein Modell installiert, dann ist der Knopf dort ausgeblendet. Keine Messung und keine
  Anpassung fürs Tablet.

## Modellwahl: nur auf Essen trainierte Modelle

Entschieden (2026-11-01): Es kommen nur Modelle in Frage, die auf Essen und Nährwerte trainiert sind. Größen und
Leistung sind Angaben der Modellkarten oder Schätzungen; gemessen wird in Phase 0. Keines der Modelle ist unabhängig
geprüft, deshalb entscheidet erst die Messung in Phase 0.

| Modell | Basis | Trainiert auf | Ausgabe | Laufzeit auf Android | Größe (ca.) | Rolle |
|---|---|---|---|---|---|---|
| **Qwen3-VL-2B „food-analysis“** | Qwen3-VL-2B-Instruct + LoRA | Gerichte und Nährwerte | JSON: Gericht, Zutaten, Nährwerte | llama.cpp (GGUF, Bild über `mtmd`) | ~1,5 GB (4 Bit) | **Hauptkandidat:** neueste Basis, klein genug für das 13R |
| NutriScan-3B | Qwen2.5-VL-3B-Instruct + QLoRA | MM-Food-100K | JSON: kcal, Makros, Portion | llama.cpp (GGUF) | ~2 GB (4 Bit) | Vergleich: größer, evtl. genauer bei Mengen; Lizenz der Basis prüfen |
| SmolVLM2-500M + CaLoRAify | SmolVLM2-500M-Instruct + LoRA | CalData (330 K Bild-Text-Paare) | JSON: Zutaten, Portionen, kcal, Makros | llama.cpp (GGUF) | ~0,5 GB | Rückfall, wenn die großen zu langsam sind |
| CalorieCLIP | CLIP ViT-B/32, Regression | Nutrition5k | nur eine kcal-Zahl | ONNX Runtime oder LiteRT | ~0,2 GB | **Ergänzung:** zweite Meinung für kcal bei „Frei“; erkennt selbst nichts |

Alle drei Bild-Sprach-Modelle laufen über dieselbe Laufzeit (llama.cpp); die Modelldatei ist austauschbar, ohne dass
sich der App-Code ändert. Die LoRA-Adapter werden in Phase 0 mit ihrem Basismodell zusammengeführt und nach GGUF
(4 Bit) gewandelt.

**Nicht gewählt:** Gemma 4 und Gemini Nano (allgemeine Modelle, nicht auf Essen trainiert; Gemini Nano läuft laut
Google-Geräteliste ohnehin nicht auf dem 13R) sowie einfache Klassifikatoren wie Food-101 (feste Liste, keine Mengen,
deutsche Hausmannskost fehlt meist).

### Ergebnis Phase 0 (2026-11-01)

Gewählt: **Qwen3-VL-2B + „food-analysis“**, 4 Bit (1,55 GB), höchstens 512 Bild-Tokens, llama.cpp auf der CPU.
Einzelheiten und Zahlen in [ADR 0017](../docs/adr/0017-fotoerkennung-auf-dem-geraet.md).

- **Erkennen klappt, Schätzen kaum:** Aus einer Liste wählt das Modell in 73–82 % das richtige Gericht. kcal und Gramm
  liegen bei allen Modellen im Median um etwa zwei Drittel daneben und sind fast immer runde Werte (300, 500).
  Für „Gekauftes“ und „Teller“ sind sie nur ein Startwert; die Vorschau muss sie deutlich als geschätzt zeigen und
  leicht änderbar machen.
- **Training auf Essen bringt wenig:** Das unveränderte Qwen3-VL-2B ist fast gleich gut; es bleibt der Rückfall.
- **Ausgeschieden:** NutriScan-3B (schwächer, keine Gramm, Research License), SmolVLM2 + CaLoRAify (untrainiert),
  CalorieCLIP (bei deutschen Gerichten nicht besser, zweite Laufzeit).
- **Handy (13R, nur CPU):** 11 s je Foto bei 256 Bild-Tokens, 25 s im Median bei voller Größe, 5–7 s Laden,
  2,6–3,5 GB RAM. GPU (OpenCL) stürzt beim Bildteil ab, NPU ist für fremde Programme gesperrt.
- **Abbruchgrenze** (≥ 80 %) nur knapp erreicht; gebaut wird trotzdem, entscheidend ist die Abnahme im Alltag (Phase 5).
- Antworten anderer Modelle enthalten Zahlen mit Einheit (`"fat": 15g`); der Leser in Phase 1 muss das reparieren.

## Phasen

### Phase 0 – Messung (Wegwerf-Probe)

Kein Produktcode. Ergebnis ist eine Entscheidung, keine App. Eigene Fotos mit Sollwerten gibt es nicht; gemessen wird
deshalb mit öffentlichen Daten, die Genauigkeit im Alltag prüft Phase 5.

- [x] Testsätze (lokal, nicht im Repo): 100 Nutrition5k-Testbilder, 22 deutsche Gerichte; eigene Rezeptfotos gab es nicht
      - **Mengen und kcal:** ~100 Gerichte aus dem Test-Teil von **Nutrition5k** (Google; Fotos mit gewogenen Zutaten
        und kcal). Nur der Test-Teil, weil CalorieCLIP auf Nutrition5k trainiert ist.
      - **Deutsche Gerichte:** 20–30 frei lizenzierte Fotos (Wikimedia Commons), z. B. Leberkässemmel, Breze,
        Schnitzel, Käsespätzle, Currywurst – nur „richtig erkannt?“, plausible kcal-Spanne aus Nährwerttabellen.
      - **Eigene Rezepte:** die Rezepte mit Foto aus dem Katalog (falls vorhanden) – „richtiges Rezept aus der
        Auswahlliste?“.
      Nicht verwenden: MM-Food-100K und CalData, damit trainieren NutriScan bzw. CaLoRAify.
- [x] Modelle vorbereiten: LoRA mit Basis zusammenführen, nach GGUF wandeln, CalorieCLIP nach ONNX.
- [x] Probe auf dem 13R: statt einer Wegwerf-App die vorgebaute Android-Ausgabe von llama.cpp per adb; Genauigkeit
      mit denselben Dateien auf dem PC gemessen.
- [x] Messen je Modell: Gericht/Rezept richtig erkannt, Abweichung kcal und Gramm (Nutrition5k), Zeit je Foto,
      RAM-Spitze, Akku, gültiges JSON in wie viel % der Fälle. CalorieCLIP zusätzlich: kcal-Abweichung allein und
      als Mittel mit dem Bild-Sprach-Modell.
- [x] **Abbruchgrenze:** Wenn kein Modell unter ~60 s bleibt (vom Nutzer von 15 s angehoben) und deutsche Gerichte bzw. eigene Rezepte in ≥ 80 %
      richtig erkennt, wird die Funktion nicht gebaut (oder nur „Rezept aus Foto“).
- [x] ADR 0017: gewähltes Modell, Laufzeit, Lizenzen (Basis und Adapter), Speicherort, warum nicht Claude/Server
      (Abgrenzung zu ADR 0011).

### Phase 1 – `:domain`: Ergebnis verstehen

Reines Kotlin, testgetrieben.

- [x] Typen `FotoVorschlag` (Rezept / Frei / Teller mit Zeilen) und `FotoKontext`. Ein eigener Typ `Sicherheit`
      entfällt: was nicht sicher zugeordnet ist, wird Frei, unplausible Werte bleiben leer.
- [x] Toleranter Leser für die Modellantwort (`ModellJson`: JSON aus Text und Codezaun herauslösen, Komma vor `}`,
      Zahlen mit Einheit wie `15g`, Schlüssel ohne Groß-/Kleinschreibung).
- [x] Zuordnung zu Rezept und Zutaten (`FotoErkennung`): Rezeptname gleich, heutiger Plan zuerst; Zutaten über
      `IngredientCatalog.canonicalName` (Synonyme, Plural). Keine Tippfehler-Zuordnung – Regel 15 erlaubt keine
      Ähnlichkeit; ohne Treffer → Frei.
- [x] Plausibilitätsgrenzen (Portionen 0,25–6, Gramm 1–1.500 je Zeile, kcal 1–3.000, Makros 0–300 g), sonst Feld leer.
- [x] Regel 29 in `docs/domain-rules.md` mit Tests (`FotoErkennungTest`, `ModellJsonTest`).

**Ergebnis (2026-11-01):** `ModellJson` und `FotoErkennung` in `:domain`, 25 Tests. Seit Phase 2 ohne Rezept und
Teller: `FotoErkennung.vorschlag(antwort)` liefert nur noch den freien Eintrag. Spec
`docs/superpowers/specs/2026-11-01-foto-erkennung-phase1-design.md`, Plan `docs/superpowers/plans/2026-11-01-foto-erkennung-phase1.md`.
Die Anweisung ans Modell und die Reihenfolge der Liste kommen in Phase 2.

### Phase 2 – `:core:erkennung`: Modell auf dem Gerät

Neues Modul (`foody.android.library`), Aufbau wie `:core:scan` (ADR 0013): Schnittstellen, echte Umsetzung, Fakes für Tests.

- [x] Schnittstelle `MahlzeitErkenner { suspend fun erkennen(bildUri): ErkennungStatus }` – ohne Kontext, weil es
      keinen Rezeptabgleich gibt.
- [x] llama.cpp b12024 selbst gebaut (statisch, nur CPU, nur `arm64-v8a`, 5,5 MB statt > 140 MB der vorgebauten
      Ausgabe), dünne JNI-Hülle; fertig gebaut in `android/core/erkennung/nativ/`, die CI braucht kein NDK.
      Höchstens 512 Bild-Tokens.
- [x] Modellverwaltung (`ModellDateien`): zwei GGUF-Dateien importieren (Bildteil am Kopf erkannt), SHA-256 gegen
      die Dateien aus Phase 0, Speicherort `filesDir/modell`, Größe, entfernen. Kein Download: Das zusammengeführte
      Modell gibt es nur als eigene Datei, Import per SAF (z. B. aus OpenCloud) reicht; die Oberfläche kommt in Phase 4.
- [x] Bild verkleinern (höchstens 1024 px); Erkennung auf eigenem Thread, abbrechbar, Zeitlimit; Modell danach frei.
- [x] Fehlerfälle: kein Modell, zu wenig RAM (unter 2 GB frei), Zeitlimit (60 s), unlesbare Antwort.
- [ ] Zeit je Foto in der App auf dem 13R messen (Ziel 15–30 s) – mit nur einer Frage noch offen.
- [x] R8-Regel für die JNI-Hülle (`consumer-rules.pro`). Der Release-Kurztest kommt mit Phase 3, wenn `:app` das
      Modul einbindet.

**Messung (2026-11-01, 13R, in der App, mit Laden):** mit zwei Fragen (Rezeptauswahl und Beschreibung) 20–26 s je
deutschem Gericht, 14 s je Nutrition5k-Bild. Die Rezeptauswahl wählte bei Gerichten außerhalb der Liste trotzdem
immer ein Rezept → gestrichen. Mit 3 GB Speicherschwelle scheiterte jede Erkennung nach der ersten (2,5–2,8 GB frei);
die 1,1 GB Gewichte liegen per mmap im Dateicache, die Schwelle ist jetzt 2 GB.

### Phase 3 – `:feature:heute`: Knopf und Vorschau

- [ ] Knopf „Foto“ im Eintragen-Blatt neben dem Scan; Kamera über `TakePicture` wie `ScanFotos` (ohne Kamera-Berechtigung).
- [ ] Während der Erkennung Fortschritt mit Abbrechen; danach öffnet sich „Frei“ vorausgefüllt (Name, kcal, Makros),
      Schätzfelder markiert („bitte prüfen“, wie `PackungEingabe`).
- [ ] Foto-Uri und Vorschlag im `SavedStateHandle` (Prozessende während der Erkennung), Test mit `nachProzessende()`.
- [ ] Robolectric-Tests mit Fake-Erkenner, Referenzbilder für die neuen Zustände; Release-Kurztest (R8, E4).

### Phase 4 – Einstellungen

- [ ] Bereich „Fotoerkennung“: an/aus, Modell installieren/entfernen, Größe, Hinweis „läuft nur auf diesem Gerät“.
- [ ] Getrennt je Gerät (nicht gesynct), weil das Modell lokal liegt; auf dem Tablet bleibt es einfach aus.

### Phase 5 – Doku und Abnahme

- [ ] `docs/architecture.md` (Abschnitt `:core:erkennung`), `README.md` (Modultabelle), `CLAUDE.md` (Testbefehl).
- [ ] `docs/security.md`: Foto bleibt auf dem Gerät, einmaliger Modell-Download, keine Übermittlung.
- [ ] Abnahme im Alltag auf dem Handy: die ersten ~30 Foto-Einträge auswerten – Vorschlag gegen das, was nach deiner
      Korrektur gespeichert wurde (nur lokal, ohne neues Feld: Vorschlag und Ergebnis im Debug-Log bzw. einer
      lokalen Datei, abschaltbar). Ziel: Gericht meist richtig benannt, kcal meist ohne große Korrektur.

## Bewusst nicht in der ersten Version

- Kein neues Feld „aus Foto“ / „geschätzt“ im Tagebucheintrag: das wäre eine Schemaänderung samt Sync-Protokoll und
  Server-Rebuild. Erst nachziehen, wenn es im Alltag fehlt.
- Kein Mengenabgleich über Tiefensensor oder Referenzobjekt (Teller, Gabel).
- Kein eigenes Nachtrainieren (Fine-Tuning) mit den eigenen Fotos – erst, wenn Phase 0 zeigt, dass es daran scheitert.
- Keine Packungserkennung per Modell; das macht der Scan.

## Risiken

| Risiko | Gewicht | Gegenmaßnahme |
|---|---|---|
| kcal-Schätzung aus einem Foto ungenau (Messung: Median etwa zwei Drittel daneben) | hoch | Vorschau mit leicht änderbaren Werten, als geschätzt markiert; Abnahme im Alltag (Phase 5) |
| Community-Modelle ohne Pflege, ohne unabhängige Messung | mittel | Messung in Phase 0 entscheidet; drei Kandidaten über dieselbe Laufzeit, Modelldatei austauschbar |
| Modell zu langsam oder zu speicherhungrig | mittel | Messung vorab auf dem 13R, SmolVLM2 als kleiner Rückfall |
| Öffentliche Testdaten passen nicht zum eigenen Essen (Nutrition5k: US-Kantine) | mittel | deutsche Gerichte als eigener Testsatz, Auswertung im Alltag (Phase 5) |
| llama.cpp als native Abhängigkeit (Build, Updates, R8) | mittel | nur `arm64-v8a`, Version festgenagelt, Release-Kurztest |
| Deutsche Gerichte (Leberkäse, Schäufele) unbekannt oder englisch benannt | mittel | Name in der Vorschau änderbar, Testsatz mit typischen Gerichten |
| Lizenzen (Basis Qwen2.5-VL-3B, Adapter) | gering | in Phase 0 prüfen und im ADR festhalten; Nutzung nur privat |

## Quellen (Recherche 2026-11-01)

- [Ateeqq/food-analysis (Qwen3-VL-2B)](https://huggingface-proxy.pages.dev/Ateeqq/food-analysis)
- [NutriScan-3B](https://huggingface.co/HackerAditya56/NutriScan-3B/blob/main/README.md)
- [CaLoRAify (arXiv)](https://arxiv.org/abs/2412.09936v1)
- [CalorieCLIP](https://huggingface.co/jc-builds/CalorieCLIP/blob/main/README.md)
- Nutrition5k (Google Research): [github.com/google-research-datasets/Nutrition5k](https://github.com/google-research-datasets/Nutrition5k)
- Gemini Nano / ML Kit GenAI, Geräteliste: [developers.google.com/ml-kit/genai](https://developers.google.com/ml-kit/genai)
