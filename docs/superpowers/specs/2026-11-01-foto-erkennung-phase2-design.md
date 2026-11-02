# Spec: Fotoerkennung Phase 2 (`:core:erkennung`)

Stand 2026-11-01. Plan: `ToDoS/foto-erkennung-plan.md`, Phase 2; Entscheidung: ADR 0017. Keine Oberfläche (Phase 3/4
warten auf das neue Design), kein Server, kein Schema.

## Ablauf einer Erkennung

`MahlzeitErkenner.erkennen(bildUri, kontext): ErkennungStatus`, eine Erkennung zur Zeit (Mutex):

1. Modell da? Sonst `KeinModell`. Freier Arbeitsspeicher ≥ 2 GB (`ActivityManager.MemoryInfo.availMem`)? Sonst
   `ZuWenigSpeicher`. Die 1,1 GB Gewichte liegen per mmap im Dateicache und zählen nicht; mit 3 GB scheiterte auf dem
   13R jede zweite Erkennung direkt nach der ersten (2,5–2,8 GB frei).
2. Bild vorbereiten: über `ImageDecoder` lesen (dreht nach EXIF), längste Seite höchstens 1024 px, als JPEG nach
   `cache/erkennung/`. Nach der Erkennung gelöscht. Unlesbares Bild → `Fehler`.
3. Auf einem eigenen Thread, Zeitlimit 60 s für alles: Modell laden (mmap, nur CPU, 6 Threads), Bild einmal
   auswerten (höchstens 512 Bild-Tokens), dann Fragen stellen. Jede Frage setzt hinter dem Bild neu an, das Bild wird
   nicht zweimal gerechnet.
   - Erst die Auswahl (`FotoAnweisung.auswahl`, nur wenn es Rezepte gibt) → `Rezept` oder nichts.
   - Dann immer die Trainingsanweisung (`FotoAnweisung.GERICHT`) → `FotoErkennung.vorschlag` → Teller oder Frei.
     Grund (Messung auf dem 13R): Das Modell antwortet bei der Auswahl praktisch nie „none“; fünf Gerichte, die nicht
     in der Liste standen, bekamen alle ein Rezept. Die Vorschau zeigt deshalb das Rezept und bietet die Beschreibung
     als Alternative an.
4. Ergebnis `Erkannt(vorschlag, alternative, dauerMs)`: mit Rezept ist `vorschlag` das Rezept und `alternative` Teller
   oder Frei; ohne Rezept `vorschlag` Teller oder Frei. Ohne lesbares JSON `Unlesbar`; nach 60 s `Zeitlimit`;
   Ladefehler `Fehler`.
   Danach wird das Modell freigegeben (Speicher zurück).

Abbrechen (Coroutine abgebrochen) und Zeitlimit setzen ein Abbruchsignal, das die native Seite beim Laden und nach
jedem Rechenschritt prüft. Nur das Auswerten des Bildes selbst (wenige Sekunden) lässt sich nicht unterbrechen.

## llama.cpp

- Version **b12024** (gemessen in Phase 0), Quelle als Zip von GitHub mit SHA-256 (`FetchContent`).
- Statisch gebaut, nur `llama`, `ggml` (CPU) und `mtmd`: keine Tools, kein OpenMP, keine GPU/NPU, nur `arm64-v8a`,
  `-march=armv8.6-a+dotprod+fp16` (Snapdragon 8 Gen 3; das Tablet lädt die Bibliothek nie). Ergebnis: eine
  `libfoody_erkennung.so` mit der JNI-Hülle.
- Die Bibliothek liegt fertig gebaut im Repo (`android/core/erkennung/nativ/arm64-v8a/`). So braucht die CI kein NDK
  und baut llama.cpp nicht bei jedem Lauf (sonst +10–15 min). Neu bauen mit
  `./gradlew :core:erkennung:nativUebernehmen -Pfoody.nativ` (NDK 30.0.16248370, CMake 3.31.6, lädt Gradle selbst).
- JNI: `neu()`, `laden(h, modell, bildteil, bild, threads, maxBildTokens)`, `fragen(h, anweisung, maxTokens)`,
  `abbrechen(h)`, `freigeben(h)`. Chat-Format von Qwen (`<|im_start|>user\n<Bild>Anweisung<|im_end|>\n<|im_start|>assistant\n`),
  gierig (Temperatur 0) wie in der Messung, höchstens 400 Antwort-Tokens.

## Modelldateien

`ModellDateien` in `filesDir/modell/`: `sprache.gguf` und `bild.gguf`.
- `importieren(quellen)`: zwei Dateien (z. B. per SAF aus OpenCloud). Welche der Bildteil ist, steht im GGUF-Kopf
  (`general.architecture = "clip"`). Erst als `.teil` kopieren, mit SHA-256, dann umbenennen. Kein GGUF oder nicht genau
  ein Bild- und ein Sprachteil → abgelehnt, nichts bleibt liegen. Die Prüfsummen der Dateien aus Phase 0 sind bekannt
  (`bekannt = true`); andere GGUF-Dateien (z. B. das unveränderte Qwen3-VL-2B) gehen auch.
- `stand()`: `Fehlt` oder `Bereit(bytes)`; `entfernen()`.
- Kein Download: Das zusammengeführte Modell gibt es nur als eigene Datei (Phase 0). Import per SAF reicht.

## Nicht in Phase 2

- Oberfläche (Knopf, Vorschau, Einstellungen): Phase 3/4.
- `:app` hängt noch nicht an `:core:erkennung`; die Bibliothek kommt erst mit Phase 3 in die APK. Deshalb auch der
  Release-Kurztest (R8) erst dort.
- Englische Zutatennamen (das Modell antwortet englisch) → im Alltag prüfen (Phase 5).

## Tests

- `:domain`: `FotoAnweisungTest` (Plan vorne, Namen einmal, höchstens 200, Maskierung).
- `:core:erkennung` (JVM): `ModellErkennerTest` mit Fake-Modell (Rezept mit Gericht als Alternative, Gericht nach
  „none“, ohne Rezepte keine Auswahl, kein Modell, zu wenig Speicher, Zeitlimit und Abbrechen lösen `abbrechen()` aus,
  Ladefehler, unlesbar, Bild wird gelöscht), `GgufKopfTest`, `ModellDateienTest`.
- Auf dem 13R: `MessungTest` (androidTest, nur mit Instrumentation-Argumenten), misst Laden und Zeit je Foto.
