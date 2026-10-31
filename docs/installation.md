# Foody installieren und aktualisieren

Foody kommt nicht über Google Play, sondern als APK-Datei über OpenCloud auf Handy und Tablet. Diese Anleitung
beschreibt die drei Varianten, wo die APKs liegen, die Erstinstallation und Updates.

## Die drei Varianten

| Variante | App-ID | Wofür | Signatur |
|---|---|---|---|
| **Foody** | `de.foody.app` | Alltag auf Handy und Tablet | eigener Release-Schlüssel |
| **Foody Vorschau** | `de.foody.app.vorschau` | Probeversion neben „Foody“ (R8 wie ein Release) | Debug-Schlüssel |
| **Foody Debug** | `de.foody.app.debug` | Entwicklung, Emulator | Debug-Schlüssel |

Jede Variante hat ihre eigenen Daten; sie laufen nebeneinander, ohne sich zu stören.

## Wo die APKs liegen

Zwei Gradle-Aufgaben bauen die APK und legen sie in den OpenCloud-Ordner, von dem aus sie auf die Geräte kommt:

```powershell
.\gradlew :app:apkNachOpenCloud        # „Foody“ → foody.apk
.\gradlew :app:vorschauNachOpenCloud   # „Foody Vorschau“ → foody-vorschau.apk
```

Das Ziel steht je Rechner in `local.properties` (nicht im Repository):

```properties
foody.apkZiel=E:/OpenCloud/Persönlich (2)/foody.apk
# optional, sonst foody-vorschau.apk neben foody.apkZiel
foody.vorschauZiel=E:/OpenCloud/Persönlich (2)/foody-vorschau.apk
```

Ohne Schlüssel (siehe unten) bricht `apkNachOpenCloud` mit einer Meldung ab; die CI baut deshalb nur eine
unsignierte APK, die sich nicht installieren lässt.

## Signaturschlüssel

Android installiert ein Update nur, wenn es mit **demselben Schlüssel** signiert ist wie die installierte App. Der
Release-Schlüssel von „Foody“ besteht seit Oktober 2026 (PKCS12, Alias `foody`) und liegt außerhalb des Projekts.

- `keystore.properties` (Vorlage `keystore.properties.example`) nennt Schlüsseldatei, Alias und Passwörter. Sie liegt
  neben dem Projekt oder dort, wohin `foody.keystoreProperties` in `local.properties` zeigt.
- **Schlüssel und Passwort sichern** (z. B. im Passwortmanager). Ohne sie lässt sich „Foody“ nicht mehr
  aktualisieren, nur deinstallieren – samt aller Daten auf dem Gerät.
- Neuer Rechner: `local.properties` mit `foody.keystoreProperties` und `foody.apkZiel` anlegen, sonst nichts.

## Erstinstallation

1. Auf dem Gerät in der OpenCloud-App `foody.apk` öffnen (bzw. `foody-vorschau.apk`).
2. Beim ersten Mal fragt Android, ob die OpenCloud-App Apps installieren darf: **„Installation aus dieser Quelle
   zulassen“** einschalten, zurück, dann „Installieren“.
3. Foody öffnen. Der Startkatalog mit Zutaten ist sofort da.
4. Optional unter **Einstellungen → Konto → Server verbinden** mit dem eigenen Sync-Server verbinden (Adresse,
   Benutzer, Passwort).
   Hat das Gerät schon Daten, fragt die App, ob sie mit dem Server zusammengeführt oder durch ihn ersetzt werden.
5. Optional **Einstellungen → Sicherung → Wiederherstellen**, um Daten aus einer Sicherungsdatei zu übernehmen
   (ersetzt alles auf dem Gerät).

## Update

Neue APK wie oben öffnen und „Aktualisieren“ wählen; Daten und Anmeldung bleiben. Voraussetzungen:

- gleicher Schlüssel (sonst meldet Android „App nicht installiert“ – dann **nicht** deinstallieren, sondern die
  richtig signierte APK bauen),
- `versionCode` in `android/app/build.gradle.kts` nicht kleiner als der installierte (vor jedem Release erhöhen).

Nach einem Update mit Datenbank-Änderung lässt sich nicht auf eine ältere Version zurück. Vor größeren Updates
deshalb eine Sicherung erstellen.

## Umstieg von der v1 (erledigt am 2026-10-28)

Handy, Tablet und Server laufen seit dem 28.10. auf der V3; die Schritte des Umstiegs stehen im Git-Verlauf dieser
Datei (Stand 9a98ac2). Einen Rückweg zur v1 gibt es seit dem 2026-10-31 nicht mehr (`ToDoS/aufraeumplan.md`, Stufe 3;
letzter Stand der v1 und des v1-Servers: Tag `v1-final`).

## Wenn etwas nicht klappt

| Meldung | Ursache und Abhilfe |
|---|---|
| „App nicht installiert“ / „Paket steht in Konflikt“ | Anderer Schlüssel als die installierte App. Nicht deinstallieren; mit dem richtigen Schlüssel bauen |
| „Installation blockiert“ | „Installation aus dieser Quelle zulassen“ für die OpenCloud-App einschalten |
| Sync: „Diese App ist zu alt für den Server.“ | Neue APK installieren |
| Sync: „Der Server ist zu alt für diese App.“ | Server neu bauen (`server/README.md`, Update) |
| Sync: „Anmeldung abgelaufen – bitte neu anmelden.“ | Unter Einstellungen → Konto → Server verbinden neu anmelden; die Daten bleiben |
