# Plan: Fotoerkennung Phase 2 (`:core:erkennung`)

Spec: `docs/superpowers/specs/2026-11-01-foto-erkennung-phase2-design.md`. Prüfen mit
`./gradlew :domain:test :core:erkennung:testDebugUnitTest :core:erkennung:lintDebug ktlintPruefen`.

1. **`FotoAnweisung`** (`:domain`): Trainingsanweisung. Die zuerst gebaute Rezept-Auswahlliste ist nach der Messung
   wieder gestrichen, ebenso Rezept und Teller in `FotoErkennung` (nur noch Name, kcal, Makros).
2. **Modul** `:core:erkennung` (`foody.android.library`, Hilt): Schnittstellen `MahlzeitErkenner`, `BildSprachModell`,
   `ModellSitzung`, `BildVorbereitung`, `FreierSpeicher`; Eintrag in `settings.gradle.kts`, README, CLAUDE.md.
3. **JNI-Hülle** `src/main/cpp/` (CMake, llama.cpp b12024 per `FetchContent` mit SHA-256, statisch, nur CPU);
   `nativUebernehmen` legt die Bibliothek nach `nativ/`; `.cxx/` ignoriert (Git und ktlint).
4. **`ModellDateien`** und **`GgufKopf`** (TDD): Import zweier Dateien, Bildteil am Kopf erkannt, Prüfsumme, `.teil`.
5. **`ModellErkenner`** (TDD mit Fake-Modell): Ablauf, Zeitlimit, Abbrechen, eine Erkennung zur Zeit.
6. **Messung** auf dem 13R mit `MessungTest`; Ergebnis in den Projektplan, Schwellen nachziehen.
7. **Doku:** Architektur, Regel 29 (Tests), Haken und Ergebnis in `ToDoS/foto-erkennung-plan.md`.
