# Plan: Fotoerkennung Phase 1 (`:domain`)

Spec: `docs/superpowers/specs/2026-11-01-foto-erkennung-phase1-design.md`. Testgetrieben, jeder Schritt zuerst mit
einem Test, der fehlschlägt. Prüfen mit `./gradlew :domain:test ktlintPruefen`.

1. **`ModellJson`** (`shared/domain/.../ModellJson.kt`): Werttypen (`Objekt`, `Liste`, `Text`, `Zahl`, `Wahr/Falsch`,
   `Leer`), `lesen(text): Objekt?`. Tests: reines JSON, Codezaun, Text davor, Zahl mit Einheit, Komma vor `}`,
   verschachtelt, kaputt → `null`.
2. **`FotoVorschlag`/`FotoKontext`** (`FotoErkennung.kt`): Typen.
3. **Rezept:** `choice` → Rezept; Groß-/Kleinschreibung; Plan gewinnt bei gleichem Namen; „none“ → kein Rezept;
   Portionen gerundet und begrenzt.
4. **Teller:** zwei Katalogzutaten (Liste und `portion_size`-Tabelle), Plural/Synonym über `canonicalName`; eine
   unbekannte Zutat → Frei; Gramm begrenzt.
5. **Frei:** Name, kcal (auch verschachtelt), Makros; Grenzen; ohne JSON → `null`.
6. **Doku:** Regel 29 in `docs/domain-rules.md` mit Tests, Haken in `ToDoS/foto-erkennung-plan.md`, Abschnitt in
   `docs/architecture.md` (`:domain`).
