# Sicherheit

Foody ist eine Offline-App; die Synchronisation mit einem selbst gehosteten Server ist optional. Dieses Dokument hält fest, wo fremde Daten hereinkommen, wie sie geprüft
werden und welche Restrisiken bewusst bleiben. Bei Änderungen an Import, Sicherung oder Manifest bitte mitpflegen.

## Angriffsfläche

| Bereich | Stand |
|---|---|
| Netzwerk | `INTERNET` für die optionale Synchronisation und die abschaltbare Online-Produktsuche (siehe unten). Sync: ausschließlich HTTPS zum selbst gehosteten Sync-Server (Klartext per `network_security_config` verboten, nur System-CAs und keine Nutzer-Zertifikate – einzige Ausnahme: die mitgelieferte lokale CA [`foody_local_ca.crt`](../android/app/src/main/res/raw/foody_local_ca.crt) gilt ausschließlich für `foody.homeserver.lan` ohne Subdomains; `ServerUrl` lehnt andere Schemata sowie Zugangsdaten und Query in der Adresse ab), kein Coil-Netzwerkmodul. Ohne eingerichtetes Konto baut die App keine Verbindung auf |
| Sync-Token | Gerätetoken: AES-256/GCM-Schlüssel im Android Keystore (Alias `foody_sync_token`, nicht auslesbar), nur das Chiffrat (`IV:Chiffrat`, Base64) liegt in den SharedPreferences `foody_sync`; nicht in Backups (Preferences sind ausgeschlossen), nie im Log. Nicht entschlüsselbar (z. B. Gerätewechsel) → Token gilt als nicht vorhanden, neue Anmeldung nötig |
| Debug-Ausnahme | Nur der Debug-Build (`android/app/src/debug`) erlaubt Klartext-`http://`, und nur für `10.0.2.2` und `localhost` (Emulator → Entwicklungsrechner); `ServerUrl` akzeptiert `http` nur mit diesem Schalter. Der Release-Build enthält die Ausnahme nicht |
| Berechtigungen | Nur für Kochtimer: `POST_NOTIFICATIONS` (ab Android 13 abgefragt, ablehnbar), `FOREGROUND_SERVICE(_SPECIAL_USE)`, `WAKE_LOCK`. Bilder über Photo Picker, Dateien über SAF, Fotos über die System-Kamera (`TakePicture`) – ohne Berechtigung |
| Dienst | `CookTimerService`: nicht exportiert, läuft nur solange ein Timer läuft; Wakelock mit Zeitlimit (nächstes Timer-Ende + 1 min) |
| Exportierte Komponenten | Nur `MainActivity` (Launcher). Keine Intent-Filter für fremde Daten, keine WebView. Einziger PendingIntent: Öffnen der App aus der Timer-Meldung (`FLAG_IMMUTABLE`, explizit) |
| FileProvider | Nicht exportiert, gibt nur `files/recipe_images/` und `cache/scan/` frei, Schreibrecht nur befristet an die Kamera-App |
| Packung scannen | Strichcode über den Google Code Scanner, Texterkennung über ML Kit – beides Play-Dienste, auf dem Gerät, ohne Kamera-Berechtigung. Packungsfotos liegen nur kurz in `cache/scan/` und werden nach der Erkennung gelöscht. Online-Produktsuche: HTTPS-GET an `world.openfoodfacts.org` mit **nur der Produktnummer** (kein Konto, keine Gerätekennung, `User-Agent: Foody/<Version>`), eigener HTTP-Client ohne die lokale CA des Sync-Servers; abschaltbar unter „Mehr“ (ADR 0008) |
| Datenbank | Room mit gebundenen Parametern; `LIKE`-Suche maskiert `%`/`_`; rohes SQL nur in Migrationen |
| Backup | Kein Cloud-Backup. Gerät-zu-Gerät-Umzug (Android 12+) nimmt Datenbank und eigene Fotos mit |

## V3 (`:app3`)

Die Tabelle oben beschreibt die v1 (`android/app`). Die V3 läuft bis zum Umstieg (Phase 8) daneben und unterscheidet
sich so:

- **Deep Links:** `MainActivity` nimmt `foody://rezept/{id}`, `foody://einkauf/{listeId}`,
  `foody://kochen/{rezeptId}?portionen={n}` und `foody://tagebuch/{datum}` an (`VIEW` mit `BROWSABLE`). Damit kann
  jede Webseite oder App sie öffnen. Sie **navigieren nur** und ändern keine Daten, bis man selbst etwas antippt.
  Unbekannte IDs zeigen „nicht gefunden“, ein ungültiges Datum zeigt heute, und die Portionen sind auf 1–99 begrenzt.
  Links ohne passendes Ziel werden verworfen (`deepLinkOeffnen`). Bei laufender App navigiert ein Link im
  bestehenden Stapel (`singleTask`, `onNewIntent`), statt den Task neu aufzubauen. Der Kochmodus merkt sich nur seine
  Sitzung lokal (Portionen, Seite).
- **Network-Security-Config** (`android/app3/src/main/res/xml`): nur HTTPS. Die lokale CA gilt ausschließlich für
  `foody.homeserver.lan`. Klartext für `10.0.2.2` und `localhost` gibt es nur im Debug-Build (`src/debug`). Nur dort
  akzeptiert `ServerAdresse.pruefen` auch `http` (`lokalesHttpErlaubt = BuildConfig.DEBUG`).
- **FileProvider:** zwei, beide nicht exportiert, Schreibrecht nur befristet an die Kamera-App:
  - `ScanFotoProvider` (`${applicationId}.scan`, `cache/scan/`, Fotos der Nährwerttabelle; nach der Erkennung gelöscht),
  - `${applicationId}.rezeptfotos` (`cache/kamera/`, Rezeptfotos; verkleinert übernommen).
- **Open Food Facts** (`:core:scan`, `OffProduktSuche`):
  - Der Strichcode wird vorher auf Ziffern gefiltert (EAN/UPC).
  - Es wird nur die Produktnummer gesendet, mit `User-Agent: Foody/<Version>`.
  - Ein eigener OkHttp-Client ohne die lokale CA, mit Zeitlimit von 8 s.
  - Die Antwort wird gezählt gelesen und ist auf 256 kB begrenzt; mehr gilt als kaputt, dann wird ein Foto angeboten.
  - Abschaltbar unter „Einstellungen → Zutaten → Online-Produktsuche“.
- **Backup:** `allowBackup="false"`. Daten kommen nur über „Sicherung erstellen/wiederherstellen“ vom Gerät, mit
  denselben Prüfungen wie unten.
- **Signatur:** Einen Release-Build gibt es erst in Phase 8. Die Probeversion „Foody Vorschau“ (`assembleVorschau`,
  `de.foody.app.vorschau`) ist mit dem Debug-Schlüssel signiert. Das ist nur zum Testen gedacht und eignet sich
  nicht für die Weitergabe.

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

**Foto-Upload (Sync-Server, `PUT /api/v3/photos/{sha256}`):**
- Pfadsegment muss `^[0-9a-f]{64}$` sein, bevor irgendein Dateizugriff stattfindet; der Dateipfad entsteht nur aus
  diesem Hash und der Konto-ID des Geräts (kein Path-Traversal, keine Fremdzugriffe zwischen Konten).
- Größe ≤ 10 MB, beim Lesen gezählt (nicht aus dem Header übernommen); Content-Type wird nicht vertraut.
- Inhalt muss mit der JPEG-Signatur `FF D8 FF` beginnen und seinen SHA-256 dem Pfad entsprechen (Inhaltsadressierung).
- Schreiben über temporäre Datei im selben Ordner und atomares Umbenennen; unreferenzierte Fotos entfernt die
  Kompaktierung nach 30 Tagen.

## Sync

Server und Protokoll (`:server`, `:api-protocol`); Fremdeingaben sind hier alle Anfragen eines Clients.

- **Authentifizierung:** Jede Sync- und Foto-Route verlangt `Authorization: Bearer <Gerätetoken>` (32 Zufallsbytes,
  serverseitig nur als SHA-256 gespeichert); widerrufene Geräte werden abgewiesen. Passwörter: Argon2id, Mindestlänge
  10 Zeichen; „unbekannter Benutzer“ und „falsches Passwort“ sehen gleich aus. Keine Registrierung über die API;
  Konten legt nur `foody-admin konto-anlegen` an.
- **Konto-Trennung:** Die Konto-ID kommt immer aus dem Gerät, nie aus der Anfrage; Datensätze und Fotos
  (`photos/<Konto>/<sha256>.jpg`) sind nur im eigenen Konto les- und schreibbar, auch nicht per geratener ID.
- **Drosselung:** 5 Fehlversuche je IP und Benutzername sperren den Login 15 Minuten (`429 throttled`, Zustand
  nur im Speicher). Die IP stammt aus `X-Forwarded-For` des Reverse-Proxys (siehe `server/README.md`).
- **Eingabeprüfung:** Request-Bodies werden gezählt gelesen und begrenzt (`413`), Payloads wie bei der Sicherung
  validiert (Zahlenformat, Textlängen, Enums, UUIDs); Fotos wie oben.
- **Löschmarkierungen:** Gelöschte Datensätze bleiben als Löschmarkierung erhalten und werden nach 90 Tagen kompaktiert (Geräte mit älterem Cursor machen dann einen Voll-Abgleich).
- **Kein Token im Log:** Token und Passwörter stehen weder in Logs noch in `sync_state.lastError` (nur feste Kennungen),
  Ausnahmen, `SavedStateHandle` oder Backups.
- **Bewusst offen:** Server-Daten liegen unverschlüsselt im Volume des Betreibers; Transportschutz ist TLS am Proxy.

## Build und Lieferkette

- CI mit `permissions: contents: read`; Actions auf Commit-SHAs festgenagelt.
- Dependabot für Gradle und Actions (wöchentlich, gruppiert); Gradle-Wrapper wird von `setup-gradle` geprüft.
- Release-Build mit R8; keine Signaturschlüssel im Repository (`*.jks`, `*.keystore`, `local.properties` ignoriert).

## Bewusste Restrisiken

- **Produktnummer an Open Food Facts:** Verrät, welches Produkt gescannt wurde (und die IP-Adresse). Abschaltbar;
  dann wird nur im eigenen Katalog gesucht und sonst die Nährwerttabelle fotografiert.
- **Datenbank unverschlüsselt:** liegt in der App-Sandbox, enthält keine sensiblen Daten (Rezepte, Vorrat).
- **Sicherungsdatei unverschlüsselt:** Die ZIP-Datei liegt dort, wo der Nutzer sie speichert (z. B. Cloud-Ordner).
- **Gradle-Abhängigkeiten ohne Prüfsummen-Verifikation** (`verification-metadata.xml`): Aufwand bei jedem Update
  hoch; Dependabot-Sicherheitswarnungen in den Repository-Einstellungen aktivieren.
