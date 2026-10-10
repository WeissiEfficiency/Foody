# Frontend-Umsetzungsplan für Foody

## Ziel
Ein leichtes, modernes Web-Frontend bauen, das den bestehenden Foody-Server sauber nutzt, aber bewusst ressourcenschonend und einfach zu betreiben ist. Das Frontend soll schnell, verständlich, mobilfreundlich und unkompliziert in Docker laufen.

## Grundprinzipien

### 1. Einfachheit vor Komplexität
Es soll kein überladenes Enterprise-Frontend werden. Der Fokus liegt auf:
- klaren Views
- wenig State-Komplexität
- wenigen, gut verständlichen Abhängigkeiten
- schnellen Builds und kleinen Laufzeitkosten

### 2. Server ist die Quelle der Wahrheit
Das Frontend darf keine eigene zweite Datenlogik aufbauen. Es soll die API des Servers als System-of-Record nutzen.

### 3. Ressourcen schonen
Das Frontend soll bewusst leicht bleiben:
- keine großen UI-Frameworks mit viel Ballast
- keine übertriebene Client-Logik
- geringe Bundle-Größe
- leichtes Docker-Image
- wenig Memory- und CPU-Verbrauch

## Empfohlener Stack

### Minimal und modern
- React + TypeScript
- Vite
- React Router
- TanStack Query
- Tailwind CSS
- Zustand für UI-State
- Zod für Validierung

### Warum genau dieser Stack?
- React ist leistungsgerecht, weit verbreitet und schnell produktiv
- TypeScript reduziert Laufzeitfehler und macht API-Contracts sauber
- TanStack Query ist der Standard für serverseitige Daten und lädt ressourcenschonend
- Zustand ist leicht, klein und genug für UI-State
- Tailwind hält das Styling klein und konsistent
- Vite erzeugt schnelle Builds und kleine Entwicklungs-Workflows

## Architektur und Struktur

```text
frontend/
  src/
    app/
      routes/
      providers/
      layout/
    features/
      auth/
      recipes/
      shopping/
      pantry/
      planner/
      household/
      sync/
    shared/
      api/
      components/
      hooks/
      utils/
      types/
```

## Best-Practice-Pattern

### 1. Server-State vs. UI-State trennen
- Server-State: Rezepte, Einkaufsliste, Vorrat, Haushalte, Sync-Status
- UI-State: aktive Filter, Suchtext, Modalstatus, ausgewählter Tag, Formularzustand

Empfohlene Kombination:
- TanStack Query für Serverdaten
- Zustand für UI- und Session-State

### 2. Feature-orientierte Organisation
Jedes Feature bekommt seine eigene Struktur:
- API-Calls
- Hooks
- Komponenten
- Zustandslogik

Das verhindert ein monolithisches Frontend mit zu viel Abhängigkeit zwischen Bereichen.

### 3. Kleine, verständliche Abstraktionen
- API-Client in einer zentralen Datei oder Datei-Gruppe
- keine „God Components“
- keine tief verschachtelte Business-Logik in Render-Funktionen

## Kernfunktionen und Reihenfolge

### Phase 1 – leichtes MVP
- Login / Session
- Haushalt auswählen
- Rezeptliste anzeigen
- Rezeptdetails anzeigen
- Einkaufsliste basic
- Vorrat basic
- Sync-Status anzeigen

### Phase 2 – Produktiv nutzen
- Rezept anlegen / bearbeiten
- Einkaufsliste aktualisieren
- Filter / Suche
- Favoriten / Tags
- Vorratsmanagement

### Phase 3 – Komfort
- Planer mit Tages-/Slot-Ansicht
- gute Lade- und Fehlerzustände
- bessere Responsiveness
- kleine UX-Verbesserungen

## UX-Prinzipien

### Einfach und klar
- kurze Wege
- wenig Klicktiefe
- verständliche Übersichten
- klar getrennte Bereiche für Rezept, Einkauf, Vorrat, Planer

### Mobile-first
- gut auf Smartphone nutzbar
- aber auch Desktop-optimiert
- große Touch-Ziele und einfache Navigation

### Ressourcenschonend
- nicht alles auf einmal laden
- nur wirklich benötigte Daten abrufen
- kleine, saubere Komponenten
- keine unnötigen Animationen oder großen Bibliotheken

## API-Integration
Das Frontend soll die vorhandenen Server-Endpunkte sauber nutzen, aber ohne übermäßigen Abstraktionsaufwand.

Empfohlene Struktur:
- `api/client.ts`
- `api/auth.ts`
- `api/recipes.ts`
- `api/shopping.ts`
- `api/pantry.ts`
- `api/household.ts`

Verwendung:
- `useQuery` für Lesedaten
- `useMutation` für Schreibvorgänge
- `invalidateQueries` nach erfolgreichen Änderungen

## Formulare und Validierung
- `react-hook-form` für form-basierte Flows
- `Zod` für Validierung
- klare Fehleranzeigen direkt am Feld
- keine komplexe, unlesbare Client-Logik

## Fehler- und Ladezustände
- Ladeskelette nur dort, wo es sinnvoll ist
- Error States mit verständlicher Meldung
- Retry-Mechanismen für API-Fehler
- keine leeren, uninformierten Screens

## Docker-Setup (sinngemäß, leicht)

### Ziel
Das Frontend in Docker leicht betreiben, ohne viel Overhead.

### Empfehlung
- Nginx als statischer Webserver für das fertige Frontend
- ein kleines Multi-Stage Build mit Node für den Build und nginx für die Runtime
- kein großes Node-Image als Laufzeit

### Grundidee
```dockerfile
# Builder
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Vorteile
- leichtes Image
- wenig RAM und CPU
- schnelle Starts
- gut für Container-Deployments
- einfache Inbetriebnahme hinter reverse proxy / traefik

## Security und Betrieb
- kein sensibler State im Frontend-Log
- Token nur im sicheren Kontext verwalten
- einfache Konfiguration über Umgebungsvariablen
- keine überflüssigen Runtime-Abhängigkeiten
- sauberes Deployment hinter Traefik oder Nginx Proxy

## Definition of Done
Das Frontend gilt als erfolgreich umgesetzt, wenn:

- es leicht startet und wenig Ressourcen verbraucht
- die wichtigsten Features funktionieren
- die API sauber genutzt wird
- es in Docker einfach deployed werden kann
- die Nutzeroberfläche klar und schnell bleibt
- der Code leicht verständlich und Wartbar ist

## Empfohlene Reihenfolge
1. Landing / App-Shell / Navigation
2. Auth / Session
3. Rezeptliste + Detail
4. Einkaufsliste
5. Vorrat
6. Planer
7. Sync-Status + Stabilisierung

## Abschluss
Die beste Umsetzung für Foody ist ein leichtes React-Frontend mit sauberer, kleiner Architektur, modernem Server-State-Management und einem schlanken Docker-Deployment. Es soll nicht maximal umfangreich sein, sondern genau so viel Funktionalität wie nötig – mit guter Struktur, guter UX und niedriger Betriebslast.

Das ist die sinnvollste Kombination aus:
- Produktivität
- Wartbarkeit
- Ressourcen schonen
- Docker-freundliche Bereitstellung
- State-of-the-Art-Praktiken ohne Overengineering

## Letzter Umsetzungsschritt: Foto-Scanning als Vorschlagsfeature

### Ziel
Nährwerte aus einem Produktfoto oder Rezeptfoto automatisch erkennen und als Vorschlag für den Nutzer bereitstellen, ohne die Daten blind zu übernehmen.

### Ablauf
1. Foto aufnehmen oder aus der Galerie wählen
2. Bild vorverarbeiten und auf relevante Bereiche zuschneiden
3. OCR mit ML Kit oder ähnlicher Text-Erkennung ausführen
4. relevante Werte extrahieren (kcal, Eiweiß, Kohlenhydrate, Fett, Portionen)
5. Werte als Vorschlag im UI anzeigen
6. Nutzer kann prüfen, korrigieren oder verwerfen
7. nur nach Freigabe wird der Datensatz übernommen

### Wichtig
- Keine automatische 100%-Übernahme ohne menschliche Prüfung
- Fokus zuerst auf Produkt-Nährwerttabellen und einfache Rezeptseiten
- Erst später erweitern auf stärkere Bildinterpretation oder KI-Unterstützung
- Das Foto-Feature bleibt ein Zusatzfeature, kein MVP-Critical-Path

### Technische Empfehlung
- Android: ML Kit Text Recognition + CameraX oder Galerie-Flow
- Verarbeitung: OCR-Text normalisieren, Muster erkennen, Werte extrahieren
- UX: Vorschlagsliste mit Editierfeldern und klarer Bestätigung

### Priorität
Später, nach Einkaufsliste, Essentracking und Wochenplanung
