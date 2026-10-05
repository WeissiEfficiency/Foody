# ADR 0006: Optionaler selbst gehosteter Sync

Status: angenommen. Löst ADR 0001 für den optionalen Fall teilweise ab.

## Kontext

Foody ist offline-first (ADR 0001, 0002). Mehrere Personen eines Haushalts wollen aber Rezepte, Planer, Vorrat und
Einkaufsliste auf eigenen Geräten teilen; im Laden Abgehaktes soll beim Partner ankommen, offline gemachte
Änderungen sollen nicht verloren gehen. Ein Betreiber-Dienst scheidet aus (Datenminimierung, kein Konto bei Dritten).

## Entscheidung

- **Opt-in:** Ohne Verbindung bleibt alles wie bisher (kein Konto, keine Netzwerkzugriffe, Sync-Trigger inaktiv).
  Verbunden wird in Einstellungen → Synchronisierung → „Server verbinden“; „Trennen“ lässt alle Daten auf dem Gerät.
- **Room bleibt Quelle der Wahrheit.** Die UI liest nie vom Server. SQLite-Trigger (`sync_*`) schreiben jede lokale
  Änderung in derselben Transaktion in die Outbox (`sync_outbox`); keine Schreibstelle kann das vergessen.
- **Server speichert und verteilt nur** (Ktor + SQLite, Docker hinter Traefik): Datensätze als validierte
  Payloads mit je Haushalt steigender Revision, keine Fachlogik. Ein Lauf: Fotos, Push, Pull, Foto-Download.
- **Konten und Haushalte:** Eigene Konten (Argon2id), keine offene Registrierung (Admin per Umgebungsvariable,
  weitere per Einladungscode), Daten gehören einem Haushalt; jedes Gerät hat ein eigenes, widerrufbares Token.
- **Nur HTTPS.** Klartext ist per `network_security_config` verboten; ausschließlich der Debug-Build erlaubt `http://`
  für `10.0.2.2` und `localhost` (Emulator, Entwicklung).
- **Fotos inhaltsadressiert:** JPEG-Blobs über ihren SHA-256, getrennt von den Datensätzen übertragen.
- **Konflikte:** Last-Writer-Wins nach Ankunft am Server; Einkaufseinträge feldweise (`checked` nach jüngerem
  `checkedChangedAt`); Löschen gewinnt gegen Bearbeiten ohne Kenntnis der Löschung.

Verworfene Alternativen:
- **Cloud-Dienst Dritter** (z. B. Firebase): Fremdkonto, Datenabfluss, Abhängigkeit; widerspricht dem Datenschutzziel.
- **CRDT/Operationslog:** korrekt für jedes Feld, aber hohe Komplexität; für wenige Personen reicht LWW mit Feld-Merge.
- **Sync per Gesamt-Sicherung** (ZIP in einem Cloud-Ordner): einfach, überschreibt aber Änderungen des anderen Geräts.

## Folgen

- Neue Module `:sync-protocol` (gemeinsame DTOs, Version) und `:server`; die App braucht `INTERNET`.
- Datenbank v4–v6 mit Sync-Tabellen und Triggern; Trigger-Regeln (`recursive_triggers`, `@Upsert` statt `REPLACE`)
  stehen in `docs/architecture.md`.
- Der Betreiber sichert Datenbank und Foto-Volume des Servers (`server/README.md`).
- Bearbeiten zwei Geräte gleichzeitig dasselbe Feld, gewinnt der später eintreffende Stand.
