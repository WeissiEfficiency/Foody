# Foody

Native, offline-first Android-App für Rezepte, Nährwertberechnung, Essensplanung und automatisch
konsolidierte Einkaufslisten.

`Rezept → Portionierung → Essensplan → Zutatenaggregation → Vorratsabzug → Einkaufsliste`

## Funktionen

- **Rezepte:** anlegen, bearbeiten, duplizieren, archivieren, **Favoriten**; suchen nach Name, Tags und
  **Zutaten** („Zucchini“); Bild über den System-Photo-Picker, Portionen, Zeiten, Zutaten mit
  Menge/Einheit/Hinweis/optional, Arbeitsschritte, Notizen.
- **Startseite:** drei „Rezepte des Tages“, Bildraster, Filter nach Tags und Favoriten.
- **Kochmodus:** ein Schritt pro Seite, Display bleibt an; „Du brauchst“ zeigt die im Schritt genannte Teilmenge
  („0,25 l von 0,5 l Bier“); erkannte Zeitangaben („20 Minuten“) als **Timer** mit Signalton.
- **Portionen skalieren** in der Detailansicht und im Planer.
- **Nährwerte:** Energie (kJ/kcal), Eiweiß, Kohlenhydrate, Fett, Ballaststoffe, Zucker, Salz – pro Rezept und pro
  Portion, mit Vollständigkeitsanzeige („zu 82 % vollständig“) statt stiller Nullen.
- **Zutatenstamm** mit Dichte und Stückgewicht für sichere Umrechnungen; Startdatensatz mit häufigen Zutaten.
  **Synonyme** vereinheitlichen Namen („Mehl“ → „Weizenmehl“), Dubletten lassen sich zusammenführen.
- **Planer:** 1, 2, 3, 7 oder beliebig viele Tage; frei benennbare Mahlzeiten-Slots; verschieben; „gekocht“ bucht
  den Verbrauch vom Vorrat ab.
- **Einkaufsliste:** Vorschau mit Abwählen vorhandener Artikel, Vorratsabzug, Snapshot, manuelle Einträge,
  Abhaken, Löschen mit Rückgängig, Herkunftsanzeige („300 g Curry + 150 g Reispfanne“), Neuberechnung mit Diff.
  Gruppiert nach Supermarkt-Abteilung; Rezepte lassen sich auch **direkt** auf die Liste setzen. Wasser wird nie
  eingekauft.
- **Teilen** der Liste als Text über das Android-Sharesheet (z. B. an Bring!).
- **Vorrat** mit Menge, Einheit und MHD; bald Ablaufendes zuerst und farbig markiert.
- **Rezept-Import aus Markdown** (z. B. Web-Clipper-Export von Chefkoch): Titel, Quelle, Zutaten inkl. Gruppen,
  Mengen/Einheiten, Zubereitung; mehrere Dateien oder ein **ganzer Ordner** auf einmal. Mengen ohne Zahl werden
  „nach Bedarf“ (n. B.). Bereits importierte Quellen werden übersprungen.
- **JSON-Export/-Import** und vollständige lokale Löschung.

## Projektstruktur

| Modul | Inhalt |
|---|---|
| `:domain` | Reines Kotlin (keine Android-Abhängigkeiten): Einheiten, Skalierung, Nährwerte, Einkaufsaggregation, Diff, Export-Schnittstelle |
| `:app` | Compose-UI, ViewModels, Room, Hilt, Repositories, Backup |

Details: [`docs/architecture.md`](docs/architecture.md), fachliche Invarianten: [`docs/domain-rules.md`](docs/domain-rules.md), Sicherheit: [`docs/security.md`](docs/security.md).

## Bauen & Testen

Voraussetzungen: JDK 17+ (z. B. das JBR von Android Studio), Android SDK (compileSdk 36). AGP 9, Gradle 9.

```bash
./gradlew :domain:test            # schnelle JVM-Tests des Rechenkerns
./gradlew check                   # alle Unit-Tests + Lint
./gradlew :app:assembleDebug
./gradlew :app:connectedCheck     # Room-, Migrations- und End-to-End-Tests auf Gerät/Emulator
```

## Datenschutz

Kein Konto, kein Server, keine Berechtigungen. Alle Daten liegen lokal in Room; kein automatisches Cloud-Backup.
Cleartext-Traffic ist per Network Security Config verboten.
