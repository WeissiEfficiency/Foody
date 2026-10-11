# Projektstruktur-Regeln für Foody

## Ziel
Die Projektstruktur soll sauber, verständlich und langlebig bleiben. Sie darf nicht zu groß, zu tief verschachtelt oder zu „kreativ“ werden. Die Hauptregel ist: Ordnung durch Verantwortlichkeiten, nicht durch reine Ordnerzahl.

**Aktuelle Struktur (2026-10-11):** `android/` (Handy-App), `shared/` (von App und Server genutzt), `server/` mit
`sync/`, `web/` und `start/` – Begründung und Abhängigkeitsrichtung in
`docs/superpowers/specs/2026-10-11-projektstruktur-design.md`.

## Grundprinzip
Ein Ordner oder ein Modul sollte immer eine klare Aufgabe haben.

Gute Kriterien:
- Es gibt ein zentrales Thema
- Inhalte gehören logisch zusammen
- man findet einen Bereich schnell wieder
- man kann das Modul isoliert verstehen

## Die wichtigsten Regeln

### 1. Nach Verantwortung strukturieren
Nicht nach Dateityp, sondern nach Funktionalität.

Beispiele:
- `data` für Datenzugriff und Repository-Logik
- `ui` für Screens, Compose-Views und UI-Zustände
- `sync` für Synchronisationslogik
- `domain` für fachliche Kernlogik
- `server` für Backend-API und Server-Services

### 2. Keine „Sammler-Ordner“
Ordner wie `misc`, `stuff`, `common`, `helpers` sollten nur sehr sparsam verwendet werden.

Wenn ein Bereich mehrere Verantwortungsschwerpunkte hat, sollte er aufgeteilt werden.

### 3. Tiefe Verschachtelung vermeiden
Zu tiefe Ordnerstrukturen machen das Projekt schwer lesbar.

Gut:
```text
app/
  src/
    main/
      java/
        de/foody/app/
          data/
          ui/
          sync/
```

Schlecht:
```text
app/
  src/
    main/
      java/
        de/
          foody/
            app/
              featureA/
                subfeature/
                  internal/
                    more/
```

### 4. Layer trennen, aber nicht künstlich verzerren
In einer App sind Layer normal:
- UI
- ViewModel
- Repository
- Datenbank
- Sync
- Domain

Das ist gut. Mehrere Layer in einem Feature zu mischen ist aber ein Signal für unklare Verantwortung.

### 5. Feature-Ordner nur dann, wenn sie wirklich hochwertig sind
Wenn ein Bereich groß genug ist, kann man ein Feature-Ordner benutzen. Beispiel:

```text
ui/
  recipe/
  shopping/
  pantry/
  planner/
```

Wenn es nur wenige Dateien sind, ist ein gemeinsamer Bereich oft besser als künstlich viele Feature-Ordner.

## Richtlinien für Foody

### Android-App
```text
app/
  src/
    main/
      java/
        de/foody/app/
          data/
          ui/
          sync/
          util/
```

Wichtige Trennung:
- `data` = DB, Repository, Mappers, Storage
- `ui` = Screens, ViewModels, Composables
- `sync` = Client-Sync, API-Calls, Synchronisationslogik

### Server
```text
server/
  src/
    main/
      kotlin/
        de/foody/server/
          auth/
          household/
          sync/
          db/
          admin/
```

Wichtige Trennung:
- `auth` = Login, Token, Berechtigung
- `sync` = Push/Pull und Synchronisations-Logik
- `household` = Haushalts-/Mitgliederfunktionen
- `db` = Datenbank-Handling

### Domain
```text
domain/
  src/
    main/
      kotlin/
        de/foody/domain/
```

Domain sollte fachlich sauber und möglichst unabhängig von Android oder Server sein.

## Was nicht gut ist

### 1. Alles in einen großen Ordner
Wenn `android/app/src/main/java/de/foody/app` zu viele Themen enthält, wird es schnell unübersichtlich.

### 2. Zu viele Sub-Sub-Ordner
Wenn jeder kleine Bereich ein eigener Unterordner ist, entsteht hoher Navigationsaufwand.

### 3. Funktionale Logik im falschen Layer
Beispiel:
- UI-Code soll keine Server-Validierung „heimlich“ machen
- Sync-Code sollte nicht in UI-Komponenten liegen
- Datenbank-Mappings sollten nicht im UI-Layer landen

### 4. Namen nach Technik statt nach Bedeutung
`Utils`, `Helpers`, `Common` sind nur dann okay, wenn sie wirklich breitgenutzt und untypisch sind. In vielen Fällen ist eine fachliche Unterteilung besser.

## Gute Praxis für Foody

### Regel 1: Ein Feature = ein klarer Bereich
Beispiele:
- Rezept-Feature
- Einkaufsliste-Feature
- Vorrat-Feature
- Planner-Feature
- Sync-Feature

### Regel 2: Ein Modul hat eine Hauptverantwortung
Kein Modul soll gleichzeitig:
- UI rendern
- Datenbank anfragen
- API-Calls machen
- Business-Logik ausführen

### Regel 3: Nur dort verschachteln, wo es wirklich Struktur bringt
Wenn ein Bereich mehrere Unterthemen hat, dann sinnvoll unterteilen.
Wenn nicht, dann nicht.

## Praktische Empfehlung für dieses Projekt
Die bisherige Struktur ist schon sinnvoll:
- `app` für Android
- `server` für Backend
- `domain` für Geschäftslogik
- `sync-protocol` für gemeinsame Schnittstelle
- `docs` für Architektur- und Design-Docs

Das heißt: Es ist keine Notwendigkeit, alles noch weiter in 20 kleine Top-Level-Ordner zu splitten. Das würde eher hinderlich sein.

## Abschluss
Die beste Projektstruktur für Foody ist die, die:
- logisch nach Verantwortung trennt
- nicht zu tief verschachtelt ist
- leicht lesbar bleibt
- bei wachsendem Umfang trotzdem stabil bleibt

Das Wichtigste ist also nicht „so wenige Ordner wie möglich“, sondern „so wenig Verwirrung wie nötig“.
