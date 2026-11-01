# ADR 0017: Fotoerkennung auf dem Gerät mit Qwen3-VL-2B „food-analysis“

Status: angenommen (2026-11-01). Ergebnis von Phase 0 in `ToDoS/foto-erkennung-plan.md`. Ergänzt ADR 0011.

**Kontext:** Nach dem Essen soll ein Foto genügen, und Foody schlägt den Tagebucheintrag vor: ein eigenes Rezept mit
Portionen, etwas Gekauftes mit geschätzten Werten oder einen Teller aus Zutaten. Der Nutzer will, dass das nur auf dem
Handy läuft (OnePlus 13R, Snapdragon 8 Gen 3, 12 GB RAM). Der Server soll bleiben, wie er ist, und kein Foto soll das
Gerät verlassen. ADR 0011 sieht KI dagegen nur über den eigenen Server vor (`POST /ki/…`). Das gilt weiter für die
KI-Hilfe (`ToDoS/ki-plan.md`), nicht für die Fotoerkennung. Es kamen nur auf Essen trainierte Modelle in Frage.
Gemessen wurde mit öffentlichen Daten, weil es keine eigenen Fotos mit Sollwerten gibt: 100 Testbilder aus Nutrition5k
(gewogene Zutaten und kcal) und 22 frei lizenzierte Fotos deutscher Gerichte (Wikimedia Commons). Messskripte,
Rohantworten und Protokolle liegen außerhalb des Repos (`E:\foody-messung`).

**Messung (PC, gleiche 4-Bit-Dateien wie auf dem Handy):**

| Modell | Gericht aus Liste gewählt | frei richtig benannt | kcal-Fehler (Median) | Gramm-Fehler (Median) |
|---|---|---|---|---|
| **Qwen3-VL-2B + „food-analysis“** (eigene Trainingsanweisung) | 73–82 % | 45 % | 65 % | 67 % |
| Qwen3-VL-2B ohne Adapter | 82 % | 36 % | 67 % | 75 % |
| NutriScan-3B (Qwen2.5-VL-3B) | 64 % | 23 % | 81 % | gibt keine Gramm |
| SmolVLM2-500M + CaLoRAify | 0 % | 14 % | – | – |
| CalorieCLIP (nur kcal) | – | – | 17 %* | – |

\* CalorieCLIP ist auf Nutrition5k trainiert, die Testbilder kennt es vermutlich. Bei den deutschen Gerichten liegen
nur 59 % seiner Werte in einer plausiblen Spanne. Der CaLoRAify-Adapter ist nach seinem Trainingsstand nur 6 Schritte
trainiert. Die Spanne 73–82 % kommt aus mehreren Läufen und Bildgrößen; bei 22 Bildern ist ein Treffer 4,5 Punkte.

**Handy (OnePlus 13R, llama.cpp b12024, nur CPU, 6 Threads):** Laden 5–7 s, Arbeitsspeicher 2,6–3,5 GB. Je Foto
11 s bei 256 Bild-Tokens, 25 s im Median bei voller Bildgröße (bis 85 s bei großen Fotos). Die Adreno-GPU (OpenCL)
stürzt beim Bildteil ab; die Hexagon-NPU lässt Android fremde Programme nicht nutzen. Grenze laut Nutzer: bis 60 s je
Foto, Ziel 15–30 s.

**Entscheidung:**
- Modell: **Qwen3-VL-2B-Instruct mit dem LoRA-Adapter „food-analysis“**, zusammengeführt, Sprachteil `Q4_K_M`
  (1,1 GB), Bildteil `Q8_0` (0,45 GB), zusammen 1,55 GB. Anweisung wie im Training des Adapters.
- Bildgröße: höchstens **512 Bild-Tokens**. Gleiche Genauigkeit wie volle Größe, auf dem Handy geschätzt ~20 s.
  Gemessen wird das in Phase 2 in der App.
- Laufzeit: **llama.cpp** (`libllama`, `libmtmd`) auf der **CPU**, ohne GPU und NPU.
- Das Modell rechnet Nährwerte nur dort, wo es keine Quelle gibt. Rezept und Katalog liefern die Werte, das Modell
  wählt nur aus und schätzt die Menge. kcal und Gramm des Modells sind in der Vorschau als geschätzt markiert.
- Für „welches Rezept?“ bekommt das Modell eine Auswahlliste mit „keins davon“. Der heutige Wochenplan steht vorne.
- Die Abbruchgrenze aus dem Plan (≥ 80 % richtig) wird mit 73–82 % nur knapp erreicht. Gebaut wird trotzdem, weil der
  Vorschlag immer eine Vorschau ist und der Wochenplan als Vorschlag dazukommt. Die eigentliche Prüfung ist die
  Abnahme im Alltag (Phase 5).
- Modelldatei wird nicht mit der APK ausgeliefert, sondern einmal importiert (SAF, z. B. aus OpenCloud) und liegt in
  `filesDir`. Nur das Handy bekommt sie; auf dem Tablet bleibt der Knopf ausgeblendet.

**Verworfen:**
- *Claude oder ein anderes Cloud-Modell über die API:* deutlich genauer, aber das Foto verließe das Gerät, und es
  kostet je Foto. Der Nutzer will alles auf dem Handy.
- *Über den eigenen Server (ADR 0011):* Server soll unverändert bleiben; die Erkennung hinge am Netz.
- *NutriScan-3B:* schwächer beim Erkennen, gibt keine Gramm, Basis unter der Qwen Research License.
- *SmolVLM2 + CaLoRAify:* praktisch untrainiert, erkennt fast nichts.
- *CalorieCLIP als zweite kcal-Meinung:* nicht besser als das Bild-Sprach-Modell bei deutschen Gerichten; ein zweites
  Modell und eine zweite Laufzeit (ONNX) lohnen sich nicht.
- *Gemma 4, Gemini Nano, Klassifikatoren (Food-101):* nicht auf Essen trainiert, auf dem 13R nicht verfügbar bzw. ohne Mengen.

**Lizenzen:** Qwen3-VL-2B-Instruct steht unter Apache 2.0, der Adapter „food-analysis“ unter OpenRAIL (Nutzung erlaubt,
mit Nutzungsbeschränkungen gegen Missbrauch). Für die private App passt beides. llama.cpp steht unter MIT.

**Folgen:**
- Neues Modul `:core:erkennung` mit llama.cpp als nativer Bibliothek (nur `arm64-v8a`) und einer dünnen JNI-Hülle.
  Die vorgebaute Android-Ausgabe von llama.cpp liefert `libllama.so`, `libmtmd.so` und `libggml*.so`.
- Die App braucht beim Erkennen etwa 3 GB Arbeitsspeicher und gibt das Modell danach wieder frei.
- `docs/security.md` bekommt einen Abschnitt: Fotos bleiben auf dem Gerät; einmaliger Import der Modelldatei.
- Fällt der Adapter weg (Lizenz, Pflege), ist das unveränderte Qwen3-VL-2B fast gleich gut und eine reine Dateitausch-Frage.
