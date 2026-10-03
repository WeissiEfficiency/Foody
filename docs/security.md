# Sicherheit

Foody ist eine reine Offline-App. Dieses Dokument hält fest, wo fremde Daten hereinkommen, wie sie geprüft
werden und welche Restrisiken bewusst bleiben. Bei Änderungen an Import, Sicherung oder Manifest bitte mitpflegen.

## Angriffsfläche

| Bereich | Stand |
|---|---|
| Netzwerk | Keine `INTERNET`-Berechtigung, kein Coil-Netzwerkmodul; Klartext zusätzlich per `network_security_config` verboten |
| Berechtigungen | Keine. Bilder über Photo Picker, Dateien über SAF, Fotos über die System-Kamera (`TakePicture`) |
| Exportierte Komponenten | Nur `MainActivity` (Launcher). Keine Intent-Filter für fremde Daten, keine PendingIntents, keine WebView |
| FileProvider | Nicht exportiert, gibt nur `files/recipe_images/` frei, Schreibrecht nur befristet an die Kamera-App |
| Datenbank | Room mit gebundenen Parametern; `LIKE`-Suche maskiert `%`/`_`; rohes SQL nur in Migrationen |
| Backup | Kein Cloud-Backup. Gerät-zu-Gerät-Umzug (Android 12+) nimmt Datenbank und eigene Fotos mit |

## Fremde Eingaben

**Rezept-Import (Markdown):** Höchstens 1 MB pro Datei, Ordnerimport ohne Unterordner. Lesen und Parsen
geschehen außerhalb der Datenbanktransaktion; kaputte Dateien zählen als „fehlgeschlagen“.

**Sicherung wiederherstellen (ZIP oder JSON):**
- Zip-Slip: Nur flache Namen `photos/<Ziffern>.jpg`; der Dateiname auf dem Gerät ist immer eine neue UUID.
  Ab Android 14 lehnt zusätzlich `ZipInputStream` `../`-Pfade ab.
- Zip-Bombe: Größen werden beim Lesen gezählt (JSON 64 MB, Foto 32 MB, gesamt 2 GB), nicht aus dem Header übernommen.
- Bildverweise: Nur eigene Fotos (`file:` im Fotoordner) und `content:`-Links. Ein `file:`-Pfad etwa zur eigenen
  Datenbank würde Foody sonst mit eigenen Rechten lesen und beim nächsten Export einpacken (Confused Deputy).
- Zahlen: Länge ≤ 40, Skala −6…20, Präzision ≤ 30 – kein `1E999999999`, das beim Rechnen hängen würde.
- Alles oder nichts: Fotos werden vorgemerkt, die Datenbank in einer Transaktion ersetzt; bei Fehlern bleiben
  Daten und Fotoordner unverändert.

## Build und Lieferkette

- CI mit `permissions: contents: read`; Actions auf Commit-SHAs festgenagelt.
- Dependabot für Gradle und Actions (wöchentlich, gruppiert); Gradle-Wrapper wird von `setup-gradle` geprüft.
- Release-Build mit R8; keine Signaturschlüssel im Repository (`*.jks`, `*.keystore`, `local.properties` ignoriert).

## Bewusste Restrisiken

- **Datenbank unverschlüsselt:** liegt in der App-Sandbox, enthält keine sensiblen Daten (Rezepte, Vorrat).
- **Sicherungsdatei unverschlüsselt:** Die ZIP-Datei liegt dort, wo der Nutzer sie speichert (z. B. Cloud-Ordner).
- **Gradle-Abhängigkeiten ohne Prüfsummen-Verifikation** (`verification-metadata.xml`): Aufwand bei jedem Update
  hoch; Dependabot-Sicherheitswarnungen in den Repository-Einstellungen aktivieren.
