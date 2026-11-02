# Plan: Fotoerkennung Phase 6 – Portion und eigene Korrekturen

Spec: `docs/superpowers/specs/2026-11-02-foto-erkennung-phase6-design.md`. Ein PR gegen `main`, Server: nein.

1. **`:domain`:** `FotoPortion` (KLEIN 0,7 / NORMAL 1 / GROSS 1,4), `FotoVorschlag.mal(faktor)`,
   `FotoErkennung.schluessel(name)`; `FotoVorschlag` wird `Serializable`. Tests zuerst.
2. **`:core:erkennung`:** `FotoGedaechtnis` (Datei, `nachschlagen`, `merken`, Protokoll), Hilt-Bindung. Tests.
   `ErkennungLauf.onVorschlag` wird `suspend` (Nachschlagen auf IO).
3. **`:feature:heute`:** `FotoStand` in `Eingaben`, `fotoUebernehmen` mit Gedächtnis, `fotoPortion`, Merken nach
   dem Speichern; Chips und Hinweistext im Blatt. Tests, Referenzbilder neu.
4. **Doku:** Regel 29, `architecture.md`, `security.md` (Gedächtnis lokal), Projektplan Phase 6.
5. `check`, `ktlintPruefen`, PR mit Auto-Merge.
