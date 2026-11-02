# Spec: Fotoerkennung Phase 3 und 4 (Oberfläche)

Stand 2026-11-02. Plan: `ToDoS/foto-erkennung-plan.md`, Phase 3 und 4; Entscheidung: ADR 0017. Baut auf `:core:erkennung`
(Phase 2) und dem Vorbau `ErkennungLauf`, `ModellVerwaltung`, `ErkennungEinstellung` auf. Kein Server, kein Schema.

Ziel (Nutzer, 2026-11-02): Foto → was ist es, wie viele kcal → freier Tagebucheintrag. Das Design (Milchglas, PR
#180–#188) ist fertig; die Oberfläche folgt den vorhandenen Bausteinen von „Packung scannen“.

## Phase 3: Eintragen-Blatt › „Frei“

- Neben „Packung scannen“ ein Umriss-Knopf **„Foto erkennen“** (Symbol Kamera, gleiche Form wie der Scan-Knopf). Tippen
  öffnet ein Menü **„Foto aufnehmen“ / „Aus der Galerie“** (`TakePicture` in `cache/mahlzeit/`, `PickVisualMedia`).
- Sichtbar nur, wenn `ErkennungEinstellung.sichtbar` (Schalter an **und** Modell installiert). Auf dem Tablet ohne
  Modell gibt es den Knopf also nicht; das Blatt sieht dort aus wie bisher.
- Während der Erkennung: Fortschritt statt Symbol, Knopf gesperrt, darunter „Erkennt das Foto … (bis zu einer Minute)“
  und der Textknopf **„Abbrechen“**.
- Ergebnis: Name, kcal und, falls geliefert, Eiweiß, KH, Fett füllen die freien Felder (vorher Eingetipptes wird
  ersetzt). Markiert wie beim Scan (`vorausgefuelltFarben`), solange ein Feld dem Vorschlag gleicht; darüber
  „Geschätzt aus dem Foto – bitte prüfen“. Gespeichert wird erst mit „Eintragen“ (Regel 29).
- Ohne Vorschlag: die Meldung aus `ErkennungTexte` unter dem Knopf; die Felder bleiben, wie sie waren.
- Die Werte des Vorschlags liegen in `Eingaben` (SavedStateHandle), damit die Markierung ein Prozessende übersteht.
  Ein neues Blatt (anderer Auftrag) bricht eine laufende Erkennung ab.
- Kamerafotos: eigener Cache-Ordner `mahlzeit/` im FileProvider (`kamera_pfade.xml`, `KameraDatei.MAHLZEIT`); Reste
  räumt der Knopf beim Anzeigen weg, wie der Scan-Knopf.

## Phase 4: Einstellungen › „Fotoerkennung“

Eigener Abschnitt nach „Zutaten“:

- Schalter **„Foto erkennen“**, Untertitel „Läuft nur auf diesem Gerät; das Foto verlässt es nicht.“ Gilt je Gerät.
- Stand: „Modell installiert · 1,4 GB“ oder „Kein Modell installiert“.
- **„Modell installieren“** (ohne Modell gefüllter Knopf, mit Modell „Modell ersetzen“ als Umriss): Dateidialog
  `OpenMultipleDocuments` für die beiden GGUF-Dateien; Kopieren läuft im `viewModelScope` („Läuft …“), Ergebnis als
  Meldung:
  - bekannte Dateien: „Modell installiert“;
  - andere GGUF-Dateien: „Modell installiert (unbekannte Dateien, ohne Gewähr)“;
  - kein GGUF: „Das sind keine Modelldateien.“;
  - nicht genau Bild- und Sprachteil: „Bitte beide Dateien wählen: Sprachteil und Bildteil (mmproj).“;
  - Kopierfehler: „Installieren fehlgeschlagen (genug Speicher frei?)“.
- **„Modell entfernen“** (Umriss, nur mit Modell) fragt nach („Modell entfernen?“ / „Die Fotoerkennung braucht es
  wieder, um zu laufen.“) und meldet „Modell entfernt“.
- Während einer Modell-Aktion sind Sicherung und Löschen gesperrt (gleiches `Aktion`-Schema wie bisher).

## Abhängigkeiten

`:feature:heute` und `:feature:einstellungen` → `:core:erkennung` (wie `:core:scan`); `:app` nimmt das Modul für Hilt
mit auf. Features kennen sich weiter nicht.

## Tests

- `EintragenViewModel`: Vorschlag füllt die Felder, Markierung bis zur Änderung, Prozessende behält Werte und
  Markierung, neues Blatt bricht ab (Fake-`MahlzeitErkenner`).
- Blatt: Knopf nur mit Modell und Schalter, Fortschritt mit „Abbrechen“, Meldung, Vorschlag sichtbar und speicherbar.
- Einstellungen: Schalter, Installieren (Fake-Dateiwahl), Entfernen mit Rückfrage, Meldungen.
- Referenzbilder: „Frei“ mit Vorschlag (hell/dunkel); Einstellungen neu aufgenommen (neuer Abschnitt).
- Release-Kurztest: Release-Build (R8) baut; auf dem Emulator ohne Modell erscheint kein Knopf und nichts stürzt ab.
