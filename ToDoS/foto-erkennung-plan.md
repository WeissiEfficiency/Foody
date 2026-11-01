# Foody – Mahlzeit aus Foto (auf dem Gerät)

Stand: 2026-11-01. Arbeitsplan, noch nichts umgesetzt. Eigenes Vorhaben neben der KI-Hilfe ([`ki-plan.md`](ki-plan.md)).
Die Phasen werden der Reihe nach abgearbeitet; die nächste beginnt, wenn die vorige abgehakt ist.

## Ziel

Nach dem Essen ein Foto machen, und Foody schlägt den Tagebucheintrag vor. Ein Häkchen speichert ihn. Drei Fälle:

| Fall | Beispiel | Vorschlag | Nährwerte aus |
|---|---|---|---|
| Eigenes Rezept gekocht | Linsencurry | Weg „Rezept“: Rezept + Portionen (z. B. 1,5) | dem Rezept (genau); geschätzt ist nur die Portion |
| Etwas Gekauftes | Leberkässemmel, Breze | Weg „Frei“: Name, kcal, Eiweiß, KH, Fett | dem Modell (geschätzt, „bitte prüfen“ wie beim Scan) |
| Teller aus Zutaten | Reis, Hähnchen, Brokkoli | mehrere Einträge über „Zutat“ mit Gramm | dem Katalog; geschätzt sind nur die Gramm |

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
- **Nicht in die APK.** Das Modell (0,2–2 GB) wird nachgeladen; die APK wächst nur um die Laufzeit.
- **Nur auf dem Handy** (OnePlus 13R, Snapdragon 8 Gen 3, 12 GB RAM). Es bleibt eine APK für beide Geräte; auf dem
  Tablet wird einfach kein Modell installiert, dann ist der Knopf dort ausgeblendet. Keine Messung und keine
  Anpassung fürs Tablet.

## Modellwahl: nur auf Essen trainierte Modelle

Entschieden (2026-11-01): Es kommen nur Modelle in Frage, die auf Essen und Nährwerte trainiert sind. Größen und
Leistung sind Angaben der Modellkarten oder Schätzungen; gemessen wird in Phase 0. Keines der Modelle ist unabhängig
geprüft, deshalb entscheidet erst der eigene Testsatz.

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

**Welches meiner Rezepte?** Das Modell bekommt die Rezeptnamen und den heutigen Wochenplan als Auswahlliste in die
Anweisung („Aus dem Plan“ ist meist schon richtig). Ein Bildvergleich mit den Rezeptfotos kommt erst, wenn die
Auswahlliste in Phase 0 nicht reicht.

## Phasen

### Phase 0 – Messung (Wegwerf-Probe)

Kein Produktcode. Ergebnis ist eine Entscheidung, keine App. Eigene Fotos mit Sollwerten gibt es nicht; gemessen wird
deshalb mit öffentlichen Daten, die Genauigkeit im Alltag prüft Phase 5.

- [ ] Testsätze (lokal, nicht im Repo):
      - **Mengen und kcal:** ~100 Gerichte aus dem Test-Teil von **Nutrition5k** (Google; Fotos mit gewogenen Zutaten
        und kcal). Nur der Test-Teil, weil CalorieCLIP auf Nutrition5k trainiert ist.
      - **Deutsche Gerichte:** 20–30 frei lizenzierte Fotos (Wikimedia Commons), z. B. Leberkässemmel, Breze,
        Schnitzel, Käsespätzle, Currywurst – nur „richtig erkannt?“, plausible kcal-Spanne aus Nährwerttabellen.
      - **Eigene Rezepte:** die Rezepte mit Foto aus dem Katalog (falls vorhanden) – „richtiges Rezept aus der
        Auswahlliste?“.
      Nicht verwenden: MM-Food-100K und CalData, damit trainieren NutriScan bzw. CaLoRAify.
- [ ] Modelle vorbereiten: LoRA mit Basis zusammenführen, nach GGUF wandeln, CalorieCLIP nach ONNX.
- [ ] Probe auf dem 13R: kleine Wegwerf-App mit llama.cpp; gleiche Anweisung und gleiches JSON-Schema für alle drei.
- [ ] Messen je Modell: Gericht/Rezept richtig erkannt, Abweichung kcal und Gramm (Nutrition5k), Zeit je Foto,
      RAM-Spitze, Akku, gültiges JSON in wie viel % der Fälle. CalorieCLIP zusätzlich: kcal-Abweichung allein und
      als Mittel mit dem Bild-Sprach-Modell.
- [ ] **Abbruchgrenze:** Wenn kein Modell unter ~15 s bleibt und deutsche Gerichte bzw. eigene Rezepte in ≥ 80 %
      richtig erkennt, wird die Funktion nicht gebaut (oder nur „Rezept aus Foto“).
- [ ] ADR 0017: gewähltes Modell, Laufzeit, Lizenzen (Basis und Adapter), Speicherort, warum nicht Claude/Server
      (Abgrenzung zu ADR 0011).

### Phase 1 – `:domain`: Ergebnis verstehen

Reines Kotlin, testgetrieben.

- [ ] Typen `FotoVorschlag` (Rezept / Frei / Teller mit Zeilen) und `Sicherheit`.
- [ ] Toleranter Leser für die Modellantwort (JSON aus Text herauslösen, fehlende Felder, Kommazahlen, Einheiten).
- [ ] Zuordnung zu Rezept und Zutaten: Namensabgleich mit Synonymen und Tippfehlern über den Katalog
      (vorhandene Einordnung nutzen), heutiger Plan zuerst; ohne Treffer → Frei.
- [ ] Plausibilitätsgrenzen (Portionen 0,25–6, Gramm 1–1.500 je Zeile, kcal 0–3.000), sonst Feld leer lassen.
- [ ] Regeln in `docs/domain-rules.md` mit Tests.

### Phase 2 – `:core:erkennung`: Modell auf dem Gerät

Neues Modul (`foody.android.library`), Aufbau wie `:core:scan` (ADR 0013): Schnittstellen, echte Umsetzung, Fakes für Tests.

- [ ] Schnittstelle `MahlzeitErkenner { suspend fun erkennen(bildUri, kontext): ErkennungStatus }`; Kontext =
      Rezeptnamen, Plan des Tages, häufige Zutaten (begrenzt auf ~200 Namen, damit die Anweisung kurz bleibt).
- [ ] llama.cpp als native Bibliothek einbinden (NDK/CMake im Modul, nur `arm64-v8a`), dünne JNI-Hülle;
      Version im Versionskatalog festnageln.
- [ ] Modellverwaltung: Modelldatei per SAF importieren (z. B. aus OpenCloud) **oder** einmal herunterladen;
      Prüfsumme, Speicherort `filesDir`, Größe anzeigen, entfernen. Kein Download ohne Knopfdruck.
- [ ] Bild vorher verkleinern; Erkennung auf eigenem Dispatcher, abbrechbar, mit Zeitlimit; Modell nach Gebrauch
      aus dem RAM werfen.
- [ ] Fehlerfälle: kein Modell, zu wenig RAM, Zeitlimit, unlesbare Antwort.
- [ ] R8-Regeln für die JNI-Hülle und ein Release-Kurztest (wie `ReleaseStartTest`, E4).

### Phase 3 – `:feature:heute`: Knopf und Vorschau

- [ ] Knopf „Foto“ im Eintragen-Blatt neben dem Scan; Kamera über `TakePicture` wie `ScanFotos` (ohne Kamera-Berechtigung).
- [ ] Während der Erkennung Fortschritt mit Abbrechen; danach öffnet sich der passende Weg vorausgefüllt,
      Schätzfelder markiert („bitte prüfen“, wie `PackungEingabe`).
- [ ] Teller: neue Vorschau mit mehreren Zeilen (Zutat, Gramm, entfernen, hinzufügen); Speichern legt alle Einträge
      in einer Transaktion an (`TagebuchRepository`, neue Methode für mehrere Zutaten).
- [ ] Foto-Uri und Vorschlag im `SavedStateHandle` (Prozessende während der Erkennung), Test mit `nachProzessende()`.
- [ ] Robolectric-Tests mit Fake-Erkenner, Referenzbilder für die neuen Zustände.

### Phase 4 – Einstellungen

- [ ] Bereich „Fotoerkennung“: an/aus, Modell installieren/entfernen, Größe, Hinweis „läuft nur auf diesem Gerät“.
- [ ] Getrennt je Gerät (nicht gesynct), weil das Modell lokal liegt; auf dem Tablet bleibt es einfach aus.

### Phase 5 – Doku und Abnahme

- [ ] `docs/architecture.md` (Abschnitt `:core:erkennung`), `README.md` (Modultabelle), `CLAUDE.md` (Testbefehl).
- [ ] `docs/security.md`: Foto bleibt auf dem Gerät, einmaliger Modell-Download, keine Übermittlung.
- [ ] Abnahme im Alltag auf dem Handy: die ersten ~30 Foto-Einträge auswerten – Vorschlag gegen das, was nach deiner
      Korrektur gespeichert wurde (nur lokal, ohne neues Feld: Vorschlag und Ergebnis im Debug-Log bzw. einer
      lokalen Datei, abschaltbar). Ziel: Rezepte fast immer richtig, kcal bei Gekauftem meist ohne große Korrektur.

## Bewusst nicht in der ersten Version

- Kein neues Feld „aus Foto“ / „geschätzt“ im Tagebucheintrag: das wäre eine Schemaänderung samt Sync-Protokoll und
  Server-Rebuild. Erst nachziehen, wenn es im Alltag fehlt.
- Kein Mengenabgleich über Tiefensensor oder Referenzobjekt (Teller, Gabel).
- Kein eigenes Nachtrainieren (Fine-Tuning) mit den eigenen Fotos – erst, wenn Phase 0 zeigt, dass es daran scheitert.
- Keine Packungserkennung per Modell; das macht der Scan.

## Risiken

| Risiko | Gewicht | Gegenmaßnahme |
|---|---|---|
| Gramm-Schätzung aus einem Foto zu ungenau (20–40 % sind üblich) | hoch | Rezept-Fall zuerst, Vorschau mit leicht änderbaren Gramm, Abbruchgrenze in Phase 0 |
| Community-Modelle ohne Pflege, ohne unabhängige Messung | mittel | eigener Testsatz entscheidet; drei Kandidaten über dieselbe Laufzeit, Modelldatei austauschbar |
| Modell zu langsam oder zu speicherhungrig | mittel | Messung vorab auf dem 13R, SmolVLM2 als kleiner Rückfall |
| Öffentliche Testdaten passen nicht zum eigenen Essen (Nutrition5k: US-Kantine) | mittel | deutsche Gerichte als eigener Testsatz, Auswertung im Alltag (Phase 5) |
| llama.cpp als native Abhängigkeit (Build, Updates, R8) | mittel | nur `arm64-v8a`, Version festgenagelt, Release-Kurztest |
| Deutsche Gerichte (Leberkäse, Schäufele) unbekannt oder falsch benannt | mittel | Auswahlliste aus Rezepten und Katalog, Testsatz mit typischen Gerichten |
| Lizenzen (Basis Qwen2.5-VL-3B, Adapter) | gering | in Phase 0 prüfen und im ADR festhalten; Nutzung nur privat |

## Quellen (Recherche 2026-11-01)

- [Ateeqq/food-analysis (Qwen3-VL-2B)](https://huggingface-proxy.pages.dev/Ateeqq/food-analysis)
- [NutriScan-3B](https://huggingface.co/HackerAditya56/NutriScan-3B/blob/main/README.md)
- [CaLoRAify (arXiv)](https://arxiv.org/abs/2412.09936v1)
- [CalorieCLIP](https://huggingface.co/jc-builds/CalorieCLIP/blob/main/README.md)
- Nutrition5k (Google Research): [github.com/google-research-datasets/Nutrition5k](https://github.com/google-research-datasets/Nutrition5k)
- Gemini Nano / ML Kit GenAI, Geräteliste: [developers.google.com/ml-kit/genai](https://developers.google.com/ml-kit/genai)
