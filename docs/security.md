# Sicherheit

Foody ist eine Offline-App; die Synchronisation mit einem selbst gehosteten Server ist optional. Dieses Dokument hält fest, wo fremde Daten hereinkommen, wie sie geprüft
werden und welche Restrisiken bewusst bleiben. Bei Änderungen an Import, Sicherung oder Manifest bitte mitpflegen.

## Angriffsfläche

| Bereich | Stand |
|---|---|
| Netzwerk | `INTERNET` nur für die optionale Synchronisation: ausschließlich HTTPS zum selbst gehosteten Sync-Server (Klartext per `network_security_config` verboten, nur System-CAs, keine Nutzer-Zertifikate; `ServerUrl` lehnt andere Schemata sowie Zugangsdaten und Query in der Adresse ab), kein Coil-Netzwerkmodul. Ohne eingerichtetes Konto baut die App keine Verbindung auf |
| Sync-Token | Gerätetoken: AES-256/GCM-Schlüssel im Android Keystore (Alias `foody_sync_token`, nicht auslesbar), nur das Chiffrat (`IV:Chiffrat`, Base64) liegt in den SharedPreferences `foody_sync`; nicht in Backups (Preferences sind ausgeschlossen), nie im Log. Nicht entschlüsselbar (z. B. Gerätewechsel) → Token gilt als nicht vorhanden, neue Anmeldung nötig |
| Debug-Ausnahme | Nur der Debug-Build (`app/src/debug`) erlaubt Klartext-`http://`, und nur für `10.0.2.2` und `localhost` (Emulator → Entwicklungsrechner); `ServerUrl` akzeptiert `http` nur mit diesem Schalter. Der Release-Build enthält die Ausnahme nicht |
| Berechtigungen | Nur für Kochtimer: `POST_NOTIFICATIONS` (ab Android 13 abgefragt, ablehnbar), `FOREGROUND_SERVICE(_SPECIAL_USE)`, `WAKE_LOCK`. Bilder über Photo Picker, Dateien über SAF, Fotos über die System-Kamera (`TakePicture`) – ohne Berechtigung |
| Dienst | `CookTimerService`: nicht exportiert, läuft nur solange ein Timer läuft; Wakelock mit Zeitlimit (nächstes Timer-Ende + 1 min) |
| Exportierte Komponenten | Nur `MainActivity` (Launcher). Keine Intent-Filter für fremde Daten, keine WebView. Einziger PendingIntent: Öffnen der App aus der Timer-Meldung (`FLAG_IMMUTABLE`, explizit) |
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

**Foto-Upload (Sync-Server, `PUT /api/v1/photos/{sha256}`):**
- Pfadsegment muss `^[0-9a-f]{64}$` sein, bevor irgendein Dateizugriff stattfindet; der Dateipfad entsteht nur aus
  diesem Hash und der Haushalts-ID des Geräts (kein Path-Traversal, keine Fremdzugriffe zwischen Haushalten).
- Größe ≤ 10 MB, beim Lesen gezählt (nicht aus dem Header übernommen); Content-Type wird nicht vertraut.
- Inhalt muss mit der JPEG-Signatur `FF D8 FF` beginnen und seinen SHA-256 dem Pfad entsprechen (Inhaltsadressierung).
- Schreiben über temporäre Datei im selben Ordner und atomares Umbenennen; unreferenzierte Fotos entfernt die
  Kompaktierung nach 30 Tagen.

## Sync

Server und Protokoll (`:server`, `:sync-protocol`); Fremdeingaben sind hier alle Anfragen eines Clients.

- **Authentifizierung:** Jede Sync- und Foto-Route verlangt `Authorization: Bearer <Gerätetoken>` (32 Zufallsbytes,
  serverseitig nur als SHA-256 gespeichert); widerrufene Geräte werden abgewiesen. Passwörter: Argon2id, Mindestlänge
  10 Zeichen; „unbekannter Benutzer“ und „falsches Passwort“ sehen gleich aus. Keine offene Registrierung
  (Einladungscode oder Admin).
- **Haushalts-Trennung:** Die Haushalts-ID kommt immer aus dem Gerät, nie aus der Anfrage; Datensätze und Fotos
  (`photos/<Haushalt>/<sha256>.jpg`) sind nur im eigenen Haushalt les- und schreibbar, auch nicht per geratener ID.
- **Drosselung:** 5 Fehlversuche je IP und Benutzername sperren Login/Registrierung 15 Minuten (`429 throttled`, Zustand
  nur im Speicher). Die IP stammt aus `X-Forwarded-For` des Reverse-Proxys (siehe `server/README.md`).
- **Eingabeprüfung:** Request-Bodies werden gezählt gelesen und begrenzt (`413`), Payloads wie bei der Sicherung
  validiert (Zahlenformat, Textlängen, Enums, UUIDs); Fotos wie oben.
- **Kein Token im Log:** Token und Passwörter stehen weder in Logs noch in `sync_state.lastError` (nur feste Kennungen),
  Ausnahmen, `SavedStateHandle` oder Backups.
- **Bewusst offen:** Server-Daten liegen unverschlüsselt im Volume des Betreibers; Transportschutz ist TLS am Proxy.

## Build und Lieferkette

- CI mit `permissions: contents: read`; Actions auf Commit-SHAs festgenagelt.
- Dependabot für Gradle und Actions (wöchentlich, gruppiert); Gradle-Wrapper wird von `setup-gradle` geprüft.
- Release-Build mit R8; keine Signaturschlüssel im Repository (`*.jks`, `*.keystore`, `local.properties` ignoriert).

## Bewusste Restrisiken

- **Datenbank unverschlüsselt:** liegt in der App-Sandbox, enthält keine sensiblen Daten (Rezepte, Vorrat).
- **Sicherungsdatei unverschlüsselt:** Die ZIP-Datei liegt dort, wo der Nutzer sie speichert (z. B. Cloud-Ordner).
- **Gradle-Abhängigkeiten ohne Prüfsummen-Verifikation** (`verification-metadata.xml`): Aufwand bei jedem Update
  hoch; Dependabot-Sicherheitswarnungen in den Repository-Einstellungen aktivieren.
