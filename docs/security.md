# Sicherheit

Foody ist eine Offline-App; die Synchronisation mit einem selbst gehosteten Server ist optional. Dieses Dokument hält fest, wo fremde Daten hereinkommen, wie sie geprüft
werden und welche Restrisiken bewusst bleiben. Bei Änderungen an Import, Sicherung oder Manifest bitte mitpflegen.

## Angriffsfläche

| Bereich | Stand |
|---|---|
| Netzwerk | `INTERNET` für die optionale Synchronisation und die abschaltbare Online-Produktsuche (siehe unten). Sync: ausschließlich HTTPS zum selbst gehosteten Sync-Server (`network_security_config` in `android/app/src/main/res/xml`: Klartext verboten, nur System-CAs, keine Nutzer-Zertifikate – einzige Ausnahme: die mitgelieferte lokale CA [`foody_local_ca.crt`](../android/app/src/main/res/raw/foody_local_ca.crt) gilt ausschließlich für `foody.homeserver.lan`). `ServerAdresse.pruefen` lehnt andere Schemata sowie Zugangsdaten und Query in der Adresse ab; kein Coil-Netzwerkmodul. Ohne eingerichtetes Konto baut die App keine Verbindung zum Server auf |
| Sync-Token | Gerätetoken (`TokenSpeicher`, `:core:sync`): AES-256/GCM-Schlüssel im Android Keystore (nicht auslesbar), nur das Chiffrat (`IV:Chiffrat`, Base64) liegt in den SharedPreferences; nie im Log. Nicht entschlüsselbar (z. B. Gerätewechsel) → gilt als abgemeldet, neue Anmeldung nötig |
| Debug-Ausnahme | Nur der Debug-Build (`android/app/src/debug`) erlaubt Klartext-`http://`, und nur für `10.0.2.2` und `localhost` (Emulator → Entwicklungsrechner); nur dort akzeptiert `ServerAdresse.pruefen` auch `http` (`lokalesHttpErlaubt = BuildConfig.DEBUG`). Release und „Foody Vorschau“ enthalten die Ausnahme nicht |
| Berechtigungen | Nur für Kochtimer (`:feature:kochen`): `POST_NOTIFICATIONS` (abgefragt, ablehnbar), `FOREGROUND_SERVICE(_SPECIAL_USE)`, `WAKE_LOCK`, `VIBRATE`. Bilder über Photo Picker, Dateien über SAF, Fotos über die System-Kamera (`TakePicture`) – ohne Berechtigung |
| Dienst | Kochtimer-Dienst: nicht exportiert, läuft nur, solange ein Timer läuft; Wakelock bis zum Timer-Ende |
| Exportierte Komponenten | Nur `MainActivity` (Launcher und Deep Links, siehe unten). Keine Intent-Filter für fremde Daten, keine WebView. Einziger PendingIntent: der Kochmodus aus der Timer-Meldung (`FLAG_IMMUTABLE`, an das eigene Paket gebunden) |
| Deep Links | `foody://rezept/{id}`, `foody://einkauf/{listeId}`, `foody://kochen/{rezeptId}?portionen={n}`, `foody://tagebuch/{datum}` (`VIEW` mit `BROWSABLE`): jede Webseite oder App kann sie öffnen. Sie **navigieren nur** und ändern keine Daten, bis man selbst etwas antippt. Unbekannte IDs zeigen „nicht gefunden“, ein ungültiges Datum zeigt heute, Portionen sind auf 1–99 begrenzt; Links ohne passendes Ziel werden verworfen (`deepLinkOeffnen`). Bei laufender App navigiert ein Link im bestehenden Stapel (`singleTask`, `onNewIntent`) |
| FileProvider | Einer: `KameraFotoProvider` in `:core:data` (`${applicationId}.kamera`), nicht exportiert, Schreibrecht nur befristet an die Kamera-App. Gibt nur `cache/kamera/` (Rezeptfotos, verkleinert übernommen) und `cache/scan/` (Nährwerttabelle, nach der Erkennung gelöscht) frei |
| Packung scannen | Strichcode über den Google Code Scanner, Texterkennung über ML Kit – beides Play-Dienste, auf dem Gerät, ohne Kamera-Berechtigung. Online-Produktsuche (`OffProduktSuche`, `:core:scan`): HTTPS-GET an Open Food Facts mit **nur der Produktnummer** (vorher auf Ziffern gefiltert, `User-Agent: Foody/<Version>`), eigener OkHttp-Client ohne die lokale CA, Zeitlimit 8 s, Antwort gezählt gelesen und auf 256 kB begrenzt; abschaltbar unter „Einstellungen → Zutaten → Online-Produktsuche“ (ADR 0013). Die Play-Dienste laden die beiden ML-Kit-Module nach, sobald ein Scan-Knopf zu sehen ist (`deferredInstall`, ohne Dialog) |
| Datenbank | Room mit gebundenen Parametern; `LIKE`-Suche maskiert `%`/`_`; rohes SQL nur in Migrationen |
| Backup | `allowBackup="false"`, kein Cloud-Backup. Daten verlassen das Gerät nur über „Sicherung erstellen“ (Prüfungen beim Wiederherstellen unten) oder den eigenen Sync-Server |
| Signatur | Release „Foody“ mit eigenem Schlüssel (nicht im Repository, `keystore.properties`). „Foody Vorschau“ (`.vorschau`) ist mit dem Debug-Schlüssel signiert: nur zum Testen, nicht zur Weitergabe |

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
  Konten legt nur `foody-admin konto-anlegen` an. Tokens laufen nicht von selbst ab: `foody-admin geraete` zeigt die
  letzte Anfrage je Gerät, `abmelden-ungenutzt <tage>` widerruft lange ungenutzte.
- **Konto-Trennung:** Die Konto-ID kommt immer aus dem Gerät, nie aus der Anfrage; Datensätze und Fotos
  (`photos/<Konto>/<sha256>.jpg`) sind nur im eigenen Konto les- und schreibbar, auch nicht per geratener ID.
- **Drosselung:** 5 Fehlversuche je IP und Benutzername sperren den Login 15 Minuten (`429 throttled`, Zustand
  nur im Speicher), ebenso 20 Fehlversuche einer IP über alle Namen. Fehlversuche und neue Sperren stehen im Log
  (Benutzername und IP, nie das Passwort). Die IP stammt aus `X-Forwarded-For` des Reverse-Proxys (siehe
  `server/README.md`). Argon2 rechnet außerhalb des Request-Threads.
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
- **Google Play-Dienste beim Scannen:** Code Scanner und Texterkennung laufen auf dem Gerät, die Play-Dienste melden
  dabei aber Nutzungsdaten (welche API, Dauer, Fehler) an Google; laut Google keine Bilder oder Strichcodes. Nicht
  abschaltbar, ohne auf den Scan zu verzichten; ohne Scan entsteht nichts davon.
- **Datenbank unverschlüsselt:** liegt in der App-Sandbox. Sie enthält neben Rezepten und Plan das
  Ernährungstagebuch – das sind **Gesundheitsdaten**. Die Sandbox schützt sie vor anderen Apps; wer das entsperrte
  Gerät hat, sieht sie ohnehin in der App.
- **Sicherungsdatei unverschlüsselt:** Die ZIP-Datei enthält dieselben Daten samt Tagebuch und liegt dort, wo der
  Nutzer sie speichert (z. B. Cloud-Ordner). Schutz ist hier der Zugang zu diesem Ordner; ein Passwort für die
  Sicherung ist bewusst nicht eingebaut (ein vergessenes Passwort wäre Datenverlust). Ebenso liegen die Daten auf dem
  Server unverschlüsselt im Volume (siehe „Sync“).
- **Gradle-Abhängigkeiten ohne Prüfsummen-Verifikation** (`verification-metadata.xml`): Aufwand bei jedem Update
  hoch; Dependabot-Sicherheitswarnungen in den Repository-Einstellungen aktivieren.
