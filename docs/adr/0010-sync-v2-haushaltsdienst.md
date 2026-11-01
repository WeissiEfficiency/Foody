# ADR 0010: Sync v2 mit Haushaltsdienst und feldweisem Zusammenführen

Status: weitgehend abgelöst durch ADR 0011 (2026-10-17): kein Haushaltsdienst, kein feldweises Zusammenführen, keine Haushalte. Ursprünglich angenommen (2026-10-13). Löst die Konfliktregeln und das Protokoll aus ADR 0006 ab; Opt-in, Konten,
Haushalte, HTTPS und inhaltsadressierte Fotos bleiben. Plan: `ToDoS/archiv/v2-plan.md` (seit 2026-11-01 nur im Git-Verlauf).

**Kontext:** Der v1-Server speichert und verteilt Datensätze, ohne sie fachlich zu prüfen. Deshalb braucht die
Web-Oberfläche eigene Schreiber (`RezeptSchreiber`, `PlanSchreiber`, `EinkaufSchreiber`, `VorratSchreiber`,
`ZutatSchreiber`, `TagebuchSchreiber`), die rohes JSON bauen und Revisionen selbst prüfen. Konflikte löst
Last-Writer-Wins je Datensatz; nur Einkaufseinträge werden feldweise gemischt. Ändern zwei Geräte verschiedene Felder
desselben Rezepts, geht eine Änderung verloren.

**Entscheidung:**
- **Haushaltsdienst** (`:server-dienst`): Jede Änderung, vom Handy oder aus dem Browser, läuft durch ihn. Er prüft mit
  den Regeln aus `:domain` (Mengen, Einheiten, Einkaufsabgleich, Vorratsabzug, Zutaten vereinheitlichen) und führt
  zusammen. Die Web-App ruft ihn direkt im Prozess auf; die Schreiber-Klassen entfallen.
- **API v2 nur für die App** (`:server-api`, `/api/v2`): Konto, Haushalt, Geräte, Einladungen, Sync, Fotos. Keine
  öffentliche Schnittstelle für andere Programme. DTOs in `:api-protocol` (bisher `:sync-protocol`).
- **Feldweises Zusammenführen für alle Typen:** Jedes Feld trägt einen Zeitstempel (Hybrid Logical Clock: physische
  Zeit + Zähler + Geräte-ID, robust gegen falsch gehende Uhren). Gesendet werden nur geänderte Felder; beim selben Feld
  gewinnt der jüngere Stempel. **Listen** (Zutaten, Arbeitsschritte) zählen als ein Feld. Löschen gewinnt weiter gegen
  Bearbeiten ohne Kenntnis der Löschung. Die Merge-Regel liegt in `:domain` und ist per JVM-Test abgedeckt.
- **App bleibt offline-first** (ADR 0001, 0002): Room ist die Quelle, Änderungen sammeln sich lokal und werden gesendet,
  sobald Netz da ist. Die App liest nie direkt vom Server.
- **Umstieg an einem Tag, ohne `/api/v1`:** Sicherungen ziehen → Server v2 → Server-Daten einmalig umwandeln (jedes
  vorhandene Feld bekommt die Revisionszeit als Stempel) → alle Apps auf v2. Vorher Probelauf mit Kopien.

**Verworfen:**
- *API v1 erweitern und parallel betreiben:* nur nötig für fremde oder nicht aktualisierbare Clients; im Haushalt
  werden alle Geräte gemeinsam aktualisiert.
- *App liest online über REST-Ressourcen:* jeder Screen bräuchte Netz; widerspricht offline-first.
- *Vollständiges CRDT/Operationslog:* korrekt auch für Listen, aber viel Komplexität; Listen als Ganzes reichen.
- *Nachfragen bei jedem Konflikt:* lästig; feldweises Mischen löst die meisten Fälle ohne Rückfrage.
- *Öffentliche API (Home Assistant, Kurzbefehle):* nicht gewünscht.

**Folgen:** Room-Schema v10 speichert Stempel je Feld; die Migration 9 → 10 übernimmt alle Daten. Der Server hat
Fachlogik und muss bei Änderungen in `:domain` neu gebaut werden (wie bisher). Ein Gerät mit App v1 kann nach dem
Umstieg nicht mehr synchronisieren und meldet „Bitte App aktualisieren“. Ändern zwei Geräte dieselbe Zutatenliste,
gewinnt weiterhin die jüngere Liste als Ganzes.
