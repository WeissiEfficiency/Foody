# ADR 0007: Tagebuch mit festgehaltenen Nährwerten

Status: angenommen (2026-10-10). Spec: `docs/superpowers/specs/2026-10-10-essenstracking-design.md`.

**Kontext:** Das Tagebuch soll zeigen, was gegessen wurde – aus dem Plan übernommen, aus Rezepten, Katalog-Zutaten
oder frei. Rezepte und Zutaten ändern sich später (Korrekturen, neue Nährwerte) oder werden gelöscht.

**Entscheidung:** Eigene Tabelle `tagebuch_eintrag`. Jeder Eintrag speichert beim Anlegen kcal (als kJ), Eiweiß,
Kohlenhydrate und Fett sowie `vollstaendig`. Verweise auf Rezept, Zutat und Plan-Eintrag sind lose (ohne
Fremdschlüssel). Bearbeiten rechnet bei Rezept- und Zutat-Einträgen aus Portionen bzw. Menge neu, sofern die Quelle
noch existiert.

**Verworfen:**
- *Nährwerte live berechnen:* Eine Rezeptkorrektur änderte rückwirkend den Verlauf; ein gelöschtes Rezept ließe
  Einträge leer zurück oder würde sie per Kaskade löschen.
- *Plan-Einträge um „gegessen“ erweitern:* Plan-Portionen meinen den Haushalt (für vier kochen), Tagebuch-Portionen
  eine Person; freie Einträge lägen in einer zweiten Tabelle.

**Folgen:** Wie die Einkaufsliste (ADR 0004) ein Snapshot. Der Verlauf bleibt stabil; Doppelungen der Werte sind
gewollt. Sync-Typ `tagebuch_eintrag` braucht einen aktualisierten Server (Server vor App ausrollen).
