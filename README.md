# Foody

Native, offline-first Android-App für Rezepte, Nährwertberechnung, Essensplanung und automatisch
konsolidierte Einkaufslisten.

`Rezept → Portionierung → Essensplan → Zutatenaggregation → Vorratsabzug → Einkaufsliste`

## Funktionen (MVP)

- **Rezepte:** anlegen, bearbeiten, duplizieren, archivieren, suchen (Name/Tags); Bild über den System-Photo-Picker,
  Portionen, Zeiten, Zutaten mit Menge/Einheit/Hinweis/optional, Arbeitsschritte, Notizen.
- **Portionen skalieren** in der Detailansicht und im Planer.
- **Nährwerte:** Energie (kJ/kcal), Eiweiß, Kohlenhydrate, Fett, Ballaststoffe, Zucker, Salz – pro Rezept und pro
  Portion, mit Vollständigkeitsanzeige („zu 82 % vollständig“) statt stiller Nullen.
- **Zutatenstamm** mit Dichte und Stückgewicht für sichere Umrechnungen; kleiner Startdatensatz.
- **Planer:** 1, 2, 3, 7 oder beliebig viele Tage; frei benennbare Mahlzeiten-Slots; verschieben; „gekocht“ bucht
  den Verbrauch vom Vorrat ab.
- **Einkaufsliste:** Vorschau mit Abwählen vorhandener Artikel, Vorratsabzug, Snapshot, manuelle Einträge,
  Abhaken, Löschen mit Rückgängig, Herkunftsanzeige („300 g Curry + 150 g Reispfanne“), Neuberechnung mit Diff.
- **Teilen** der Liste als Text über das Android-Sharesheet (z. B. an Bring!).
- **Vorrat** mit Menge, Einheit und MHD.
- **JSON-Export/-Import** und vollständige lokale Löschung.

## Projektstruktur

| Modul | Inhalt |
|---|---|
| `:domain` | Reines Kotlin (keine Android-Abhängigkeiten): Einheiten, Skalierung, Nährwerte, Einkaufsaggregation, Diff, Export-Schnittstelle |
| `:app` | Compose-UI, ViewModels, Room, Hilt, Repositories, Backup |

Details: [`docs/architecture.md`](docs/architecture.md), fachliche Invarianten: [`docs/domain-rules.md`](docs/domain-rules.md).

## Bauen & Testen

Voraussetzungen: JDK 17+, Android SDK (compileSdk 36).

```bash
./gradlew :domain:test            # schnelle JVM-Tests des Rechenkerns
./gradlew check                   # alle Unit-Tests + Lint
./gradlew :app:assembleDebug
./gradlew :app:connectedCheck     # Room-/End-to-End-Tests auf Gerät/Emulator
```

## Datenschutz

Kein Konto, kein Server, keine Berechtigungen. Alle Daten liegen lokal in Room; kein automatisches Cloud-Backup.
Cleartext-Traffic ist per Network Security Config verboten.
