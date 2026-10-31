# Foody v2 – Abnahmeliste

> **Abgelöst** durch [`v3-abnahme.md`](v3-abnahme.md) (Review vom 2026-10-17, ADR 0011).

Jede Funktion der v1 muss in v2 wieder funktionieren ([ADR 0009](../docs/adr/0009-neubau-v2-module-navigation.md)).
Jeder PR hakt die Punkte ab, die er fertigstellt, und nennt sie in der Beschreibung. Release 2.0 erst, wenn alles
abgehakt ist. Quelle: `README.md` und Web-Oberfläche Teil 1–4 (Stand 2026-10-13).

## Rezepte – `:feature:rezepte`
- [ ] Anlegen, bearbeiten, duplizieren, archivieren
- [ ] Favoriten
- [ ] Eigene Bewertung (Sterne), „Oft gekocht“
- [ ] Suche nach Name, Tags und Zutaten
- [ ] Ernährungsfilter (vegetarisch, vegan, eiweißreich, Low Carb, leicht), Sortierung, kcal auf den Karten
- [ ] Foto über System-Photo-Picker oder Kamera
- [ ] Portionen, Zeiten, Zutaten mit Menge/Einheit/Hinweis/optional, Arbeitsschritte, Notizen
- [ ] Einordnung nach Mahlzeit und Gang (manuell oder vermutet)
- [ ] Startseite der Rezepte: drei „Rezepte des Tages“, Bildraster, Filter nach Tags und Favoriten
- [ ] „Aus dem Vorrat“ (höchstens eine fehlende Zutat)
- [ ] Portionen skalieren in der Detailansicht
- [ ] Nährwerte (kJ/kcal, Eiweiß, Kohlenhydrate, Fett, Ballaststoffe, Zucker, Salz) pro Rezept und Portion,
      Vollständigkeitsanzeige
- [ ] Markdown-Import: mehrere Dateien oder ganzer Ordner, Gruppen, „nach Bedarf“, Quellen überspringen
- [ ] Rezept direkt auf die Einkaufsliste
- [ ] Tablet: Liste und Rezept nebeneinander

## Kochen – `:feature:kochen`
- [ ] Ein Schritt pro Seite, Display bleibt an
- [ ] „Du brauchst“ mit Teilmengen je Schritt („0,25 l von 0,5 l Bier“)
- [ ] Zutaten abhaken (Mise en place)
- [ ] Timer aus Zeitangaben; laufen bei ausgeschaltetem Bildschirm und im Hintergrund weiter; Benachrichtigung am Ende
- [ ] Einladung zum eigenen Foto nach dem letzten Schritt

## Woche – `:feature:woche`
- [ ] 1, 2, 3, 7 oder beliebig viele Tage
- [ ] Frei benennbare Mahlzeiten-Slots; verschieben
- [ ] Portionen skalieren im Planer
- [ ] „Gekocht“ bucht den Verbrauch vom Vorrat ab
- [ ] „Leere Tage füllen“: Vorrat zuerst, dann Favoriten, keine Wiederholung der letzten zwei Wochen, Vorschau,
      „Neu mischen“
- [ ] Nährwerte je Tag, optionales Tagesziel

## Einkauf – `:feature:einkauf`
- [ ] Vorschau mit Abwählen vorhandener Artikel, Vorratsabzug, Snapshot
- [ ] Kacheln und Listenansicht, „Zuletzt verwendet“, Katalog
- [ ] Manuelle Einträge, Artikeldetails bearbeiten
- [ ] Abhaken, Löschen mit Rückgängig
- [ ] Herkunftsanzeige („300 g Curry + 150 g Reispfanne“)
- [ ] Neuberechnung mit Diff
- [ ] Gruppiert nach Supermarkt-Abteilung
- [ ] Wasser wird nie eingekauft („nie einkaufen“)
- [ ] Teilen als Text über das Android-Sharesheet

## Küche – `:feature:kueche`
- [ ] Vorrat mit Menge, Einheit und MHD; bald Ablaufendes zuerst und farbig markiert
- [ ] Zutatenstamm mit Dichte und Stückgewicht
- [ ] Synonyme vereinheitlichen, Dubletten zusammenführen
- [ ] Startdaten mit `SeedData.VERSION`-Aktualisierung
- [ ] Packung scannen: Strichcode → Katalog → Open Food Facts (abschaltbar) → Foto der Nährwerttabelle
- [ ] Strichcode an der Zutat speichern

## Heute und Tagebuch – `:feature:heute`
- [ ] Nächste Mahlzeit aus dem Plan, „Kochen starten“
- [ ] Offener Einkauf (Anzahl, Vorschau)
- [ ] Tagebuch-Einträge aus Rezept, Zutat, Planeintrag („Gegessen“) oder frei
- [ ] Nährwerte beim Eintragen festhalten (ADR 0007)
- [ ] Einträge ändern (Menge skaliert die Werte), löschen
- [ ] Tagesbilanz gegen das Tagesziel; „Alle Einträge“
- [ ] Scan im Reiter „Frei“

## Einstellungen – `:feature:einstellungen`
- [ ] Server verbinden (Anmelden, mit Einladungscode registrieren, Haushalt wählen/anlegen, Zusammenführen/Ersetzen)
- [ ] Einladen (Code `FOODY-XXXX-XXXX`), Geräte anzeigen/abmelden, Sync-Probleme, Trennen mit Rückfrage
- [ ] „Jetzt synchronisieren“, Sync-Stand
- [ ] Sicherung als ZIP (Daten und Fotos), Wiederherstellung (auch v1-Dateien), vollständige lokale Löschung
- [ ] Darstellung hell / dunkel / System
- [ ] Tagesziel
- [ ] Online-Produktsuche beim Scan an/aus

## Sync – `:core:sync`, `:server-api`, `:server-dienst`
- [ ] Opt-in: ohne Verbindung keine Netzwerkzugriffe
- [ ] Hintergrund-Sync: beim Öffnen, nach Änderungen (entprellt), periodisch, nur mit Netz
- [ ] Fotos inhaltsadressiert hoch- und herunterladen, nicht übertragbare Fotos als Problem melden
- [ ] Feldweises Zusammenführen (ADR 0010)
- [ ] Konten (Argon2id), Einladungen, Geräte-Tokens im Keystore, nur HTTPS

## Web-App – `:server-web`
- [ ] Anmeldung, Sitzung im Cookie (`HttpOnly; Secure; SameSite=Strict`), CSRF-Schutz, Login nur von derselben Website
- [ ] Rezepte: Liste mit Suche, Filter, kcal; Detail mit Portionen ±
- [ ] Rezept-Editor (neu, bearbeiten), Markdown-Import
- [ ] Wochenplan mit kcal je Tag und Ø, bearbeiten, „Leere Tage füllen“
- [ ] Einkauf: abhaken, Einträge, Rezept auf die Liste
- [ ] Vorrat, Zutatenkatalog (löschen nur ungenutzt, Dubletten abgelehnt)
- [ ] Tagebuch: Einträge, „Gegessen“ aus dem Plan
- [ ] Zeitzone über `FOODY_ZEITZONE`
- [ ] Fehlerseite im Layout
