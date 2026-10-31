# ADR 0012: Designsystem V3 – „Ofenwarm“ und Glas mit Haze

Status: angenommen (2026-10-19). Präzisiert ADR 0009 („Design“) und ADR 0011 („Design nur in Compose“). Spec:
`docs/superpowers/archiv/v3/specs/2026-10-19-v3-phase4-designsystem-design.md`.

**Kontext:** Für die V3 sollte das Aussehen „spürbar frischer“ werden – Farben, Schrift, Glas und Tiefe, Dichte und
Formen –, in der Stimmung „warm und appetitlich“. Glas-Optik und Kacheln bleiben (ADR 0009/0011). Drei Richtungen
wurden im Canvas „Foody App-Design“ (Seite „V3-Richtungen“) verglichen: A · Ofenwarm, B · Kräutergarten,
C · Gewürzmarkt. Das Glas der v1 ist nur getönt, ohne Unschärfe.

**Entscheidung:**
- **Richtung A · Ofenwarm:** Creme `#FBF4EA`, Tomate `#A8341A`, Oliv `#5C6B2A`, Safran `#BD741B` (nur Grafik);
  dunkel Glutbraun `#1A1310` mit hellem Tomate `#F08A65`. Schrift Fraunces (Überschriften) und Figtree (Text), als
  Dateien im Modul (OFL), deutsche Silbentrennung.
- **Glasstufen** LEICHT (Navigation/Kopf/Status) und DICHT (Fließtext) mit echter Unschärfe über die Bibliothek
  **Haze** (`dev.chrisbanes.haze`, ab 2.0 mit dem Artefakt `haze-blur`), dazu BILD: fast deckende Leiste über Fotos,
  nie unscharf. Unschärfe nur auf wenigen festen Flächen; Kacheln in Listen sind nur getönt.
- **Kontrast-Regel:** WCAG 2.2 AA gilt **ohne** Unschärfe, gegen die ungünstigste Stelle des Verlaufs (Tönung per
  Alpha-Mischung). Ein Unit-Test rechnet jedes zugesagte Farbpaar nach. Sind Animationen systemweit aus, zeigt Glas nur
  die Tönung – und bleibt lesbar.
- **Ein Modul** `:core:designsystem` ist die einzige Quelle für das Aussehen; eine Debug-App `:katalog` zeigt alle
  Komponenten, Roborazzi-Screenshots sichern sie gegen unbeabsichtigte Änderungen.

**Verworfen:** B · Kräutergarten und C · Gewürzmarkt; den v1-Look nur verfeinern; Glas nur getönt wie in der v1 (zu
flach); eigene Unschärfe ohne Bibliothek (aufwendig, fehleranfällig); Downloadable Fonts (brauchen Play-Dienste, fehlen
beim ersten Start).

**Folgen:**
- Neue Abhängigkeiten: Haze (Apache 2.0), Roborazzi (Apache 2.0, nur Tests).
- Die v1 behält ihr Aussehen bis zum Umstieg in Phase 8; die Bereiche der V3 (Phase 5/6) bauen nur aus diesen
  Komponenten.
- Referenzbilder liegen im Repo (`android/katalog/src/test/screenshots/`); eine gewollte Änderung am Aussehen heißt,
  sie bewusst neu aufzunehmen (`./gradlew :katalog:recordRoborazziDebug`).
