# Foody installieren – kurz

Die einfache Fassung. Alle Einzelheiten, Varianten und Fehlerbilder stehen in der
[ausführlichen Anleitung](installation.md).

**Du brauchst:** Handy/Tablet mit Android 16, einen PC mit [Android Studio](https://developer.android.com/studio) und
Git. Für den Sync zusätzlich einen Linux-Server mit Docker und einen Host-Namen mit HTTPS.

## 1. App bauen (am PC, einmalig)

```powershell
git clone https://github.com/WeissiEfficiency/Foody.git
cd Foody
```

Den Ordner einmal in Android Studio öffnen (installiert das SDK, legt `local.properties` an). Dann einen eigenen
Schlüssel anlegen:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -keystore "$HOME\.android\foody-release.jks" -storetype PKCS12 -alias foody -keyalg RSA -keysize 4096 -validity 10000
```

`keystore.properties.example` nach `keystore.properties` kopieren, Pfad und Passwort eintragen. **Schlüssel und
Passwort gut aufheben**, ohne sie gibt es keine Updates mehr.

```powershell
.\gradlew :app:assembleRelease
```

Die App liegt dann in `android\app\build\outputs\apk\release\app-release.apk`.

## 2. Auf Handy und Tablet installieren

1. `app-release.apk` aufs Gerät bringen (Cloud-Ordner, USB oder Mail).
2. Antippen, **„Installation aus dieser Quelle zulassen“**, dann **Installieren**.
3. Foody öffnen, fertig. Ohne Server funktioniert alles, jedes Gerät hat dann eigene Daten.

Handy und Tablet bekommen dieselbe Datei.

## 3. Server aufsetzen (optional, für den Abgleich)

Voraussetzung: Docker läuft, und ein Traefik mit Let's-Encrypt-Zertifikat steht im Docker-Netzwerk `traefik`
(Beispiel in der [ausführlichen Anleitung, 6.4](installation.md#64-traefik-einrichten-falls-noch-keiner-läuft)).

```sh
git clone https://github.com/WeissiEfficiency/Foody.git foody
cd foody/server
cp .env.example .env
nano .env        # FOODY_HOST, FOODY_ADMIN_USER, FOODY_ADMIN_PASSWORD eintragen
docker compose up -d --build
```

Danach `FOODY_ADMIN_USER` und `FOODY_ADMIN_PASSWORD` in `.env` wieder leeren und `docker compose up -d`.
Test: `curl https://DEIN-HOST/health` antwortet `{"status":"ok"}`.

## 4. Geräte verbinden

In Foody: **Einstellungen → Konto → Server verbinden**, Adresse `https://DEIN-HOST`, Benutzer und Passwort.

- **Handy zuerst**, bei der Frage **„Zusammenführen“** wählen.
- **Tablet danach**, bei der Frage **„Ersetzen“** wählen (sonst gibt es Rezepte doppelt).

## 5. Updates

- **App:** `git pull`, `.\gradlew :app:assembleRelease`, neue APK auf dem Gerät öffnen, **Aktualisieren**.
- **Server:** `git pull && docker compose up -d --build` im Ordner `server/`.

## KI und Fotoerkennung (noch nicht fertig)

- **KI-Hilfe:** später nur einen Schlüssel in `server/.env` eintragen (`FOODY_KI_KEY=…`) und `docker compose up -d`.
  Auf Handy und Tablet ist nichts einzurichten.
- **Fotoerkennung:** nur auf dem Handy. Die Modelldatei kommt nicht in die APK, sondern wird später in der App unter
  *Einstellungen → Fotoerkennung* importiert. Auf dem Tablet nichts installieren.
