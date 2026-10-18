# Foody V3 – Abnahmeliste

Jede hier genannte Funktion muss in V3 funktionieren ([ADR 0011](../docs/adr/0011-v3-umfang-ein-nutzer.md)). Jeder
PR hakt die Punkte ab, die er fertigstellt, und nennt sie in der Beschreibung. Release V3 erst, wenn alles abgehakt
ist. Grundlage: [`v2-abnahme.md`](v2-abnahme.md), gekürzt nach dem Review vom 2026-10-17. Gestrichenes steht am Ende.

## Rezepte – `:feature:rezepte`
- [ ] Anlegen, bearbeiten, duplizieren, archivieren
- [ ] Favoriten und „Oft gekocht“ (automatisch)
- [ ] Suche nach Name, Tags und Zutaten
- [ ] Ernährungsfilter (vegetarisch, vegan, eiweißreich, Low Carb, leicht), Sortierung, kcal auf den Karten
- [ ] Foto über System-Photo-Picker oder Kamera
- [ ] Portionen, Zeiten, Zutaten mit Menge/Einheit/Hinweis/optional, Arbeitsschritte, Notizen
- [ ] Einordnung nach Mahlzeit und Gang (manuell oder vermutet)
- [ ] Bildraster, Filter nach Tags und Favoriten
- [ ] Portionen skalieren in der Detailansicht
- [ ] Nährwerte (kJ/kcal, Eiweiß, Kohlenhydrate, Fett, Ballaststoffe, Zucker, Salz) pro Rezept und Portion,
      Vollständigkeitsanzeige – **kcal je Portion stimmen an allen Anzeigeorten überein** (Phase 1)
- [ ] Markdown-Import: mehrere Dateien oder ganzer Ordner, Gruppen, „nach Bedarf“, Quellen überspringen
- [ ] Rezept direkt auf die Einkaufsliste
- [ ] Tablet: Liste und Rezept nebeneinander

## Kochen – `:feature:kochen`
- [ ] Ein Schritt pro Seite, Display bleibt an
- [ ] „Du brauchst“ mit Teilmengen je Schritt
- [ ] Zutaten abhaken (Mise en place)
- [ ] Timer aus Zeitangaben; laufen bei ausgeschaltetem Bildschirm und im Hintergrund weiter; Benachrichtigung am Ende
- [ ] Einladung zum eigenen Foto nach dem letzten Schritt
- [ ] **Neu:** Beim Kochen geänderte Mengen oder Schritte ins Rezept übernehmen (mit Rückfrage)

## Woche – `:feature:woche`
- [ ] Eine fortlaufende Wochenansicht, weiterblättern in beide Richtungen
- [ ] Frei benennbare Mahlzeiten-Slots; verschieben
- [ ] Portionen skalieren im Planer
- [ ] „Gekocht“ markieren (zählt für „Oft gekocht“, kein Vorratsabzug)
- [ ] „Leere Tage füllen“ mit neuer Auswahl (Einordnung, Favoriten, Abwechslung, 14 Tage ohne Wiederholung),
      Vorschau, „Neu mischen“
- [ ] Nährwerte je Tag, optionales Tagesziel

## Einkauf – `:feature:einkauf`
- [ ] Liste aus dem Plan: Vorschau mit Abwählen, Snapshot, Neuberechnung mit Diff
- [ ] Eine Ansicht (Kacheln oder Liste – Entscheidung in Phase 6 nach Probe auf Handy und Tablet)
- [ ] „Zuletzt verwendet“, Katalog
- [ ] Manuelle Einträge, Artikeldetails bearbeiten
- [ ] Abhaken, Löschen mit Rückgängig
- [ ] Herkunftsanzeige („300 g Curry + 150 g Reispfanne“)
- [ ] Gruppiert nach Supermarkt-Abteilung
- [ ] Wasser wird nie eingekauft („nie einkaufen“)

## Heute und Tagebuch – `:feature:heute`
- [ ] Nächste Mahlzeit aus dem Plan, „Kochen starten“
- [ ] Offener Einkauf (Anzahl, Vorschau)
- [ ] Eine Rezept-Inspiration, nur wenn für heute nichts geplant ist
- [ ] Tagebuch-Einträge aus Rezept, Zutat, Planeintrag („Gegessen“) oder frei
- [ ] Nährwerte beim Eintragen festhalten (ADR 0007)
- [ ] Einträge ändern (Menge skaliert die Werte), löschen
- [ ] Tagesbilanz gegen das Tagesziel; „Alle Einträge“
- [ ] Packung scannen: Strichcode → Katalog → Open Food Facts (abschaltbar) → Foto der Nährwerttabelle

## Einstellungen – `:feature:einstellungen`
- [ ] Server verbinden: Adresse, Anmelden, Sync-Stand, „Jetzt synchronisieren“, Sync-Probleme, Trennen mit Rückfrage
- [ ] Zutatenkatalog: Dichte, Stückgewicht, Synonyme, Dubletten zusammenführen, Strichcode an der Zutat, Scan
- [ ] Startdaten mit `SeedData.VERSION`-Aktualisierung
- [ ] Sicherung als ZIP (Daten und Fotos), Wiederherstellung (auch v1-Dateien), vollständige lokale Löschung
- [ ] Darstellung hell / dunkel / System
- [ ] Tagesziel
- [ ] Online-Produktsuche beim Scan an/aus

## KI – `:core:ki`, `/ki/*`
- [ ] Rezept aus Foto oder Freitext erfassen
- [ ] Rezept anpassen („vegetarisch“, „weniger kcal“, …) als neue Variante
- [ ] Wochenplan-Vorschlag aus den eigenen Rezepten
- [ ] Fehlende Nährwerte einer Zutat schätzen, als „geschätzt“ markiert
- [ ] Ergebnisse immer als bearbeitbare Vorschau; ohne Server/Netz ausgeblendet; Datenschutzhinweis

## Sync – `:core:sync`, `:server-sync`
- [ ] Opt-in: ohne Verbindung keine Netzwerkzugriffe
- [x] Server: ein Konto, Anmeldung mit Argon2id, nur HTTPS (Traefik), keine Haushalte (Phase 2)
- [x] App: Anmelden am Konto, Geräte-Token im Keystore, nur HTTPS (Phase 3, `:core:sync`: `SyncKontoTest`,
      `TokenSpeicherTest`, `ServerAdresseTest`; Keystore auf dem Gerät und Oberfläche in Phase 6/8)
- [ ] Sync beim Öffnen/Fortsetzen und nach Änderungen (entprellt), nur mit Netz (Auslöser in `:core:sync` getestet,
      `SyncAusloeserTest`; Einbau in die App Phase 5)
- [x] Server: Last-Writer-Wins je Datensatz, Einkaufseinträge feldweise; Löschen gewinnt gegen Bearbeiten ohne
      Kenntnis (Phase 2, `/api/v3`)
- [x] App: dieselben Regeln im Sync-Client (Phase 3, `SyncLaufTest` gegen den echten `:server-sync`)
- [x] Server: Fotos inhaltsadressiert (`/api/v3/photos/{sha256}`, je Konto) (Phase 2)
- [x] App: Fotos hoch- und herunterladen, nicht übertragbare Fotos als Problem melden (Phase 3, `SyncLaufTest`)

## Betrieb
- [ ] Server mit Speicherlimit, nächtliche Sicherung nach OpenCloud
- [ ] Signierte Release-APK im OpenCloud-Ordner

## Gestrichen (nicht bauen)
Vorrat mit MHD · „Aus dem Vorrat“ · Vorratsabzug bei „Gekocht“ und beim Einkauf · Liste als Text teilen ·
Web-App · Haushalte, Einladungen, Geräteliste · Sterne-Bewertung · Planer-Modi 1/2/3/7 Tage · zweite Einkaufsansicht ·
drei „Rezepte des Tages“ · periodischer Hintergrund-Sync

## Später (nach V3)
Rezept-Import per Web-Link · Wochenplan wiederholen/als Vorlage · Startbildschirm-Widget
