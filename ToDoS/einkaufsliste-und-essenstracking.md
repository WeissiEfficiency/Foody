# Einkaufsliste & Essenstracking – Feature-Umsetzung

## Überblick
Dieses Feature ergänzt die vorhandene Einkaufs- und Planungslogik um zwei konkrete Nutzerbedürfnisse:

1. Die Einkaufsliste muss besser bedienbar sein, insbesondere beim Zuklappen und beim manuellen Hinzufügen eigener Einträge.
2. Das Essentracking soll als eigenes Feature ergänzt werden, damit Mahlzeiten, Nährwerte und Tagesziele klar erfasst werden können.

## 1) Einkaufsliste: Zuklappen und eigenes Hinzufügen

### Problem
Der aktuelle Flow scheint an zwei Stellen unvollständig zu sein:
- Das Zuklappen von Listenabschnitten oder Gruppen funktioniert nicht zuverlässig oder fehlt vollständig.
- Der Nutzer kann keine eigenen Einträge direkt in die Liste hinzufügen, obwohl das für spontane Käufe oder Notfallartikel relevant ist.

### Ziel
Die Einkaufsliste soll wie ein praxisnahes Haushaltswerkzeug funktionieren:
- Gruppen oder Kategorien können kompakt/eingeklappt werden
- Einträge können schnell hinzugefügt werden
- abgehakte Einträge bleiben verständlich und gut sicht- bzw. filterbar

### Anforderungen
- Kategorien/Gruppen in der Einkaufsliste lassen sich ein- und ausklappen
- Zustand bleibt nach Nutzerinteraktion stabil
- Eintrag „Eigene hinzufügen“ ist sichtbar und leicht zugänglich
- eigener Eintrag kann manuell mit Name und optionaler Kategorie angelegt werden
- gespeicherte Einträge sind eindeutig vom automatisch generierten Bedarf unterscheidbar

### UX-Verbesserungen
- direkter Button „Eigene hinzufügen“ statt versteckter Aktion
- Zuklapp-Button mit klarer Sichtbarkeit
- kompakte Darstellung mit gutem Tastatur- und Touch-Handling
- schnelle Einfügung ohne unnötigen Dialog, wenn möglich

### Priorität
Sehr hoch.

---

## 2) Essenstracking als eigenes Feature

### Zweck
Das Essenstracking soll die Nahrungsaufnahme über den Tag hinweg sichtbar machen und hilft dabei, die Essensplanung mit Nährwert- und Tageszielen zu verknüpfen.

### Kernidee
Der Nutzer kann pro Tag Essen erfassen und kategorisieren:
- Frühstück
- Mittagessen
- Abendessen
- Snack

Die Erfassung ist nicht nur als Planer, sondern als Tages-Tracking-Log.

### Anforderungen
- Einträge können für einen Tag angelegt werden
- Eintrag kann einer Mahlzeit zugeordnet werden: Frühstück, Mittagessen, Abendessen, Snack
- Zuweisung zu Rezepten oder eigenen Essensbezeichnungen
- Kalorien-/Nährwert-Zusammenfassung pro Tag
- Übersichten über den Verlauf von mehreren Tagen

### Übersicht
- Tagesansicht mit vier Hauptkategorien
- Summen je Kategorie und Tagesgesamtsumme

### Priorität
Mittel bis hoch, sobald die Planner- und Rezeptfunktionen stabil sind.

---

## 3) Einlesen der Nährwerte per Foto

### Problem
Die App soll bereits Rezept-Nährwerte verwalten, aber die Erfassung kann aufwendig sein.

### Ziel
Nährwerte möglichst automatisch aus einem Foto eines Produktes, einer Rezeptseite oder einer Packung einlesen.

### Vorgehensweise
Die Implementierung sollte als späteres Feature erfolgen und nicht als erste Voraussetzung für den MVP.

### Mögliche Varianten
#### Variante OCR für Produkt- oder Rezeptinformationen
- Produktfoto analysieren
- Nährwerte wie kcal, Eiweiß, Kohlenhydrate, Fett extrahieren
- nur als Vorschlag nutzen, nicht automatisch übernehmen

### Anforderungen an die Umsetzung
- nur als Vorschlag, keine automatische 100%-Annahme
- Nutzer muss „übernehmen / anpassen / verwerfen“ können
- keine unkontrollierte Datenverfälschung
- für den ersten Rollout eher nur eine Hilfsfunktion, keine zentrale Datenquelle

### Empfehlung
Am sinnvollsten ist ein späteres Feature mit klarer menschlicher Freigabe.

### Priorität
Niedrig bis mittel, als Erweiterungsfeature.

---

## 4) Kcal-Tracking für den Tag

### Zweck
Die tägliche Nährwert-Erfassung soll als leicht nutzbares Tracking funktionieren.

### Kategorien
- Frühstück
- Mittagessen
- Abendessen
- Snack

Optionale Erweiterung:
- Vorspeise
- Nachspeise
- Brotzeit

### Anforderungen
- Zahl pro Kategorie erfassen
- Tagesgesamtsumme berechnen
- optional Zielwert definieren
- Verlauf über mehrere Tage anzeigen
- gut nutzbar im mobilen Alltag

### UX-Vorschlag
- Tagesansicht mit 4 Feldern pro Mahlzeit
- einfache Auswahl „Diät / Ziel / frei“
- pro Eintrag: Name + kcal + optionaler Nährwert-Block
- Gesamtwert am unteren Ende der Tagesansicht

### Priorität
Hoch, wenn das Essentracking als produktiver Alltagsteil dienen soll.

---

## 5) Smarte Auswahl für Essen bei der Wochenplanung

### Ziel
Bei der Wochenplanung soll der Nutzer schnell passende Essen auswählen können, ohne lange durch allzu viele Rezepte zu stöbern.

### Anforderungen
Die Auswahl soll intelligent und kategorisiert sein:
- Nachspeise
- Hauptspeisen
- Vorspeisen
- Frühstück, Mittagessen, Abendessen, optional kombiniert

### Filterlogik
- Essen kann nur Frühstück sein
- Essen kann nur Mittagessen sein
- Essen kann nur Abendessen sein
- Essen kann auch mehrere Mahlzeiten abdecken
- Kategorien können pro Rezept oder Mahlzeit angepasst werden

### Beispiel
- `Pfannkuchen` = Frühstück
- `Curry` = Mittagessen und Abendessen
- `Salat` = Vorspeise oder Hauptspeise
- `Joghurt` = Frühstück oder Snack

### UX-Forderungen
- intelligente Empfehlung basierend auf Kategorie und Tageszeit
- einfache Auswahlchips oder Filter-Kategorien
- Darstellung mit Text „passt zu Frühstück / Mittagessen / Abendessen“
- keine überladene Filteroberfläche

### Priorität
Hoch, wenn Wochenplanung ein zentraler Teil der App ist.

---

## 6) Sortierung und Gewichtsung der Mahlzeiten

### Ziel
Beim Planen soll der Nutzer nicht nur nach Kategorie auswählen, sondern auch nach gewünschter Gewichtung.

### Vorschlag
Sortierung nach:
- Hauptspeise
- Vorspeise
- Nachspeise
- Brotzeit
- Frühstück
- Mittagessen
- Abendessen

Zusätzlich:
- `nur Frühstück`
- `nur Mittagessen`
- `nur Abendessen`
- `Mittagessen & Abendessen`
- `Frühstück & Abendessen`

### Nutzung
Diese Strukturen helfen bei:
- smarter Auswahl
- einfacher Wochenplanung
- sinnvollen Rezeptvorschlägen
- besserem Alltagsempfinden

### Priorität
Mittel bis hoch.

---

## Umsetzungsempfehlung

### MVP-Reihenfolge
1. Einkaufsliste: Zuklappen + eigenes Hinzufügen
2. Essentracking: Frühstück / Mittagessen / Abendessen / Snack
3. Wochenplanung: Auswahl- und Filterlogik
4. Nährwert-Tracking und Tagesübersicht
5. Foto-Input als spätere Erweiterung

### Wichtig
Die Foto-Erkennung sollte kein Critical-Path für die erste Version sein. Sie sollte als Zusatz-Feature mit manueller Freigabe kommen.

## Abschluss
Die hier beschriebenen Features bauen logisch auf der bestehenden App auf und erweitern sie zu einem echten alltäglichen Ernährungs- und Haushalts-Tool.

Die sinnvollste Reihenfolge ist:
- erste die Produktivität im Alltag verbessern
- dann Nährwert-Tracking ergänzen
- dann intelligentere Wochenplanung
- zuletzt Bild-/OCR-Funktionen als Erweiterung
