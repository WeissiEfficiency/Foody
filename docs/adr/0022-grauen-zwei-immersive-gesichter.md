# ADR 0022: Kosmisches Grauen mit zwei immersiven Gesichtern

Status: angenommen (2026-11-25). Ändert ADR 0020 für einen Stil. Entwurf:
`docs/superpowers/specs/2026-11-25-grauen-zwei-gesichter-design.md`, Plan:
`docs/superpowers/plans/2026-11-25-grauen-zwei-gesichter.md`.

**Kontext:** ADR 0020 gibt jedem Stil die Varianten Immersiv (Szenen) und Ruhig (schlicht) und vier Akzentfarben.
Beim Kosmischen Grauen wollte der Nutzer mehr Lovecraft als Buch (Necronomicon), aber das ominöse Grün nicht
verlieren. Eine schlichte Variante braucht er beim Grauen nicht.

**Entscheidung:**
- **Zwei Gesichter statt Immersiv und Ruhig:** Beim Grauen zeigt `StilVariante.RUHIG` das grüne Grauen
  („Ominös“), `StilVariante.IMMERSIV` das Buch („Necronomicon“). Beide sind voll inszeniert: Szenen, Wörter,
  Zeichnungen, Animationen.
- **Gespeichert wird wie bisher** (`stilVariante`). Es gibt keine Migration: Aus Ruhig wird Ominös, aus Immersiv wird
  Necronomicon.
- **Keine Akzentfarbe beim Grauen.** Die Wahl entfällt, die Farben sind je Gesicht fest.
- **Gleicher Aufbau wie alle Stile:** Leiste unten, gleiche Seiten. Nur Material, Farben, Schmuck und Bedienelemente
  unterscheiden sich.
- **Im Code:** Neben `immersiv(stil)` und `inszeniert` steht eine Abfrage für das Gesicht (`StilWerte.grauenBuch`). Für
  das Grauen ist `inszeniert` in beiden Varianten `true`. Die Bezeichner der Varianten bleiben, nur die Anzeigenamen
  hängen vom Stil ab.

**Folgen:**
- Das bisherige „Grauen Ruhig“ (Pergament, schlicht) fällt weg, samt Bildern und Tests.
- Neue Bilder und Kontrasttests für das Necronomicon.
- Andere Stile, Daten und Server sind nicht betroffen.
