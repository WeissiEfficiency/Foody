# Foody V3 – Abnahmeliste

> **Archiviert (2026-10-31).** Die V3 ist fertig und seit dem 28.10. im Einsatz. Offene Punkte stehen in
> [`aufraeumplan.md`](../aufraeumplan.md) (R6); den Ist-Stand beschreibt [`docs/architecture.md`](../../docs/architecture.md).

Jede hier genannte Funktion muss in V3 funktionieren ([ADR 0011](../../docs/adr/0011-v3-umfang-ein-nutzer.md)). Jeder
PR hakt die Punkte ab, die er fertigstellt, und nennt sie in der Beschreibung. Release V3 erst, wenn alles abgehakt
ist. Grundlage: [`v2-abnahme.md`](v2-abnahme.md), gekürzt nach dem Review vom 2026-10-17. Gestrichenes steht am Ende.

## Rezepte – `:feature:rezepte`
- [x] Anlegen, bearbeiten, duplizieren, archivieren
- [x] Favoriten und „Oft gekocht“ (automatisch)
- [x] Suche nach Name, Tags und Zutaten
- [x] Ernährungsfilter (vegetarisch, vegan, eiweißreich, Low Carb, leicht), Sortierung, kcal auf den Karten
- [x] Foto über System-Photo-Picker oder Kamera
- [x] Portionen, Zeiten, Zutaten mit Menge/Einheit/Hinweis/optional, Arbeitsschritte, Notizen
- [x] Einordnung nach Mahlzeit und Gang (manuell oder vermutet)
- [x] Bildraster, Filter nach Tags und Favoriten
- [x] Portionen skalieren in der Detailansicht
- [x] Nährwerte (kJ/kcal, Eiweiß, Kohlenhydrate, Fett, Ballaststoffe, Zucker, Salz) pro Rezept und Portion,
      Vollständigkeitsanzeige – **kcal je Portion stimmen an allen Anzeigeorten überein** (Phase 1)
- [x] Markdown-Import: mehrere Dateien oder ganzer Ordner, Gruppen, „nach Bedarf“, Quellen überspringen
- [x] Rezept direkt auf die Einkaufsliste (Phase 6d, Symbol im Rezeptdetail mit Portionen-Dialog)
- [x] Tablet: Liste und Rezept nebeneinander

## Kochen – `:feature:kochen`
- [x] Ein Schritt pro Seite, Display bleibt an
- [x] „Du brauchst“ mit Teilmengen je Schritt
- [x] Zutaten abhaken (Mise en place)
- [x] Timer aus Zeitangaben; laufen bei ausgeschaltetem Bildschirm und im Hintergrund weiter; Benachrichtigung am Ende
- [x] Einladung zum eigenen Foto nach dem letzten Schritt
- [x] **Neu:** Beim Kochen geänderte Mengen oder Schritte ins Rezept übernehmen (mit Rückfrage)

## Woche – `:feature:woche`
- [x] Eine fortlaufende Wochenansicht, weiterblättern in beide Richtungen
- [x] Frei benennbare Mahlzeiten-Slots; verschieben
- [x] Portionen skalieren im Planer
- [x] „Gekocht“ markieren (zählt für „Oft gekocht“, kein Vorratsabzug)
- [x] „Leere Tage füllen“ mit neuer Auswahl (Einordnung, Favoriten, Abwechslung, 14 Tage ohne Wiederholung),
      Vorschau, „Neu mischen“
- [x] Nährwerte je Tag, optionales Tagesziel

## Einkauf – `:feature:einkauf`
- [x] Liste aus dem Plan: Vorschau mit Abwählen, Snapshot, Neuberechnung mit Diff (Phase 6d; Hinweis „Plan
      geändert – abgleichen“, abgewählte Zeilen kommen nicht wieder, `EinkaufAbgleichTest`)
- [x] Eine Ansicht (Phase 6d: Kacheln; Tablet mit Katalog rechts)
- [x] „Zuletzt verwendet“, Katalog (Phase 6d; über alle Listen, höchstens 18)
- [x] Manuelle Einträge, Artikeldetails bearbeiten (Phase 6d; Name, Notiz, Abteilung; Plan-Mengen nur lesend)
- [x] Abhaken, Löschen mit Rückgängig (Phase 6d)
- [x] Herkunftsanzeige („300 g Curry + 150 g Reispfanne“) (Phase 6d, „200 g Curry (Mo) + 100 g Reispfanne (Mi)“)
- [x] Gruppiert nach Supermarkt-Abteilung (Phase 6d, Laufreihenfolge des Katalogs)
- [x] Wasser wird nie eingekauft („nie einkaufen“) (Phase 6d, `EinkaufPlanTest`, `RezeptAufListeRepoTest`)

## Heute und Tagebuch – `:feature:heute`
- [x] Nächste Mahlzeit aus dem Plan, „Kochen starten“ (Phase 5; der Kochmodus selbst folgt in Phase 6)
- [x] Offener Einkauf (Anzahl, Vorschau) (Phase 5)
- [x] Eine Rezept-Inspiration, nur wenn für heute nichts geplant ist (Phase 5)
- [x] Tagebuch-Einträge aus Rezept, Zutat, Planeintrag („Gegessen“) oder frei (Phase 5)
- [x] Nährwerte beim Eintragen festhalten (ADR 0007) (Phase 5, `TagebuchRepositoryTest`)
- [x] Einträge ändern (Menge skaliert die Werte), löschen (Phase 5, mit „Rückgängig“)
- [x] Tagesbilanz gegen das Tagesziel; „Alle Einträge“ (Phase 5; Tagesziel einstellen in Phase 6)
- [x] Packung scannen: Strichcode → Katalog → Open Food Facts (abschaltbar) → Foto der Nährwerttabelle
      (Phase 6e3, `:core:scan`; im Tagebuch „Frei“, `PackungScanTest`, `EintragenPackungTest`)

## Einstellungen – `:feature:einstellungen`
- [x] Server verbinden: Adresse, Anmelden, Sync-Stand, „Jetzt synchronisieren“, Sync-Probleme, Trennen mit Rückfrage
      (Phase 5, `VerbindenScreenTest`; Sync-Probleme als letzter Fehler im Klartext)
- [x] Zutatenkatalog: Dichte, Stückgewicht, Synonyme, Dubletten zusammenführen, Strichcode an der Zutat, Scan
      (Phase 6e2; Scan im Editor und in „Neue Zutat“ seit Phase 6e3, `ZutatEditorScanTest`, `ZutatenListeScanTest`)
- [x] Startdaten mit `SeedData.VERSION`-Aktualisierung (Phase 5, `StartdatenLaderTest`)
- [x] Sicherung als ZIP (Daten und Fotos), Wiederherstellung (auch v1-Dateien), vollständige lokale Löschung (Phase 6e1;
      Löschen spielt den Startkatalog neu ein; beides gesperrt, solange der Sync verbunden ist)
- [x] Darstellung hell / dunkel / System (Phase 6e1, v1-Schlüssel `themeMode`)
- [x] Tagesziel (Phase 6e1, 500–10.000 kcal)
- [x] Online-Produktsuche beim Scan an/aus (Phase 6e3, Abschnitt „Zutaten“, v1-Schlüssel, `ScanEinstellungTest`)

## KI – ausgelagert
Eigenes Projekt nach dem Umstieg; die Abnahmepunkte stehen in [`ki-plan.md`](../ki-plan.md).

## Sync – `:core:sync`, `:server-sync`
- [ ] Opt-in: ohne Verbindung keine Netzwerkzugriffe
- [x] Server: ein Konto, Anmeldung mit Argon2id, nur HTTPS (Traefik), keine Haushalte (Phase 2)
- [x] App: Anmelden am Konto, Geräte-Token im Keystore, nur HTTPS (Phase 3, `:core:sync`: `SyncKontoTest`,
      `TokenSpeicherTest`, `ServerAdresseTest`; Keystore auf dem Gerät und Oberfläche in Phase 6/8)
- [x] Sync beim Öffnen/Fortsetzen und nach Änderungen (entprellt), nur mit Netz (Auslöser in `:core:sync` getestet,
      `SyncAusloeserTest`; in `:app3` eingebaut, Phase 5, auf dem Emulator gegen den Test-Server geprüft)
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
