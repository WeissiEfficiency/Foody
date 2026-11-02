# Plan: Fotoerkennung Phase 3 und 4 (Oberfläche)

Spec: `docs/superpowers/specs/2026-11-02-foto-erkennung-phase3-4-design.md`. Ein PR gegen `main`, Server: nein.

1. **FileProvider:** `KameraDatei.MAHLZEIT = "mahlzeit"`, `cache-path` in `kamera_pfade.xml`.
2. **ViewModel „Eintragen“:** `MahlzeitErkenner` und `ErkennungEinstellung` injiziert; `fotoLauf = ErkennungLauf(…)`,
   `fotoKnopf: StateFlow<Boolean>`; `fotoUebernehmen(vorschlag)` füllt die freien Felder und merkt sich die Werte
   (`Eingaben.foto`); `oeffnen` mit neuem Auftrag bricht ab. Tests zuerst (`EintragenFotoTest`).
3. **Knopf:** `FotoErkennungKnopf` in `:feature:heute` (Menü Kamera/Galerie, Fortschritt, „Abbrechen“, Meldung,
   Aufräumen); `FreiEingabe` zeigt ihn neben dem Scan, markiert die Felder und zeigt den Hinweis. Blatt-Tests.
4. **Einstellungen:** `ModellVerwaltung` und `ErkennungEinstellung` im ViewModel, `Aktion.MODELL_INSTALLIEREN` /
   `MODELL_ENTFERNEN`, Abschnitt „Fotoerkennung“, Dateiwahl in der Route (ersetzbar im Test), Rückfrage beim Entfernen.
   Tests.
5. **Abhängigkeiten:** `:feature:heute`, `:feature:einstellungen`, `:app` → `:core:erkennung`.
6. **Bilder:** `FotoScreenshotTest` (hell/dunkel), Einstellungen neu aufnehmen; `check`, `ktlintPruefen`.
7. **Release-Kurztest:** Release-Build mit R8 auf dem Emulator: Eintragen › Frei ohne Modell, Einstellungen-Abschnitt.
8. **Doku:** Projektplan (Phase 3/4 abhaken, „Ergebnis“), `architecture.md`, README falls nötig, PR mit Auto-Merge.
