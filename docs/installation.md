# Foody installieren – Handy, Tablet, Server, KI

Diese Anleitung beschreibt eine komplette Einrichtung von Foody von null an, so dass sie jeder nachbauen kann: App bauen,
auf Handy und Tablet installieren, den eigenen Sync-Server aufsetzen, beide Geräte verbinden, sichern, aktualisieren –
und was für die geplante KI-Hilfe und die Fotoerkennung dazukommt.

Foody gibt es nicht im Play Store. Die App wird aus diesem Repository gebaut und als APK-Datei installiert.

**Inhalt**

1. [Überblick: was wohin gehört](#1-überblick-was-wohin-gehört)
2. [Was man braucht](#2-was-man-braucht)
3. [Die App bauen (am PC)](#3-die-app-bauen-am-pc)
4. [Auf dem Handy installieren](#4-auf-dem-handy-installieren)
5. [Auf dem Tablet installieren](#5-auf-dem-tablet-installieren)
6. [Den Sync-Server aufsetzen](#6-den-sync-server-aufsetzen)
7. [Handy und Tablet mit dem Server verbinden](#7-handy-und-tablet-mit-dem-server-verbinden)
8. [KI-Hilfe über den Server (in Vorbereitung)](#8-ki-hilfe-über-den-server-in-vorbereitung)
9. [Fotoerkennung auf dem Handy: wohin das Modell kommt (geplant)](#9-fotoerkennung-auf-dem-handy-wohin-das-modell-kommt-geplant)
10. [Sichern](#10-sichern)
11. [Aktualisieren](#11-aktualisieren)
12. [Wenn etwas nicht klappt](#12-wenn-etwas-nicht-klappt)
13. [Für die Entwicklung: Varianten, Emulator, Test-Server](#13-für-die-entwicklung-varianten-emulator-test-server)

---

## 1. Überblick: was wohin gehört

```
 PC (einmalig und bei Updates)          Handy                      Tablet
 ┌──────────────────────────┐          ┌──────────────┐           ┌──────────────┐
 │ Repository + Android SDK │  APK ──▶ │ Foody        │           │ Foody        │
 │ Release-Schlüssel (.jks) │  APK ──────────────────────────────▶│ (dieselbe    │
 └──────────────────────────┘          │ + Modell für │           │  APK, ohne   │
                                       │ Fotoerkenn.  │           │  Modell)     │
                                       │ (geplant)    │           └──────┬───────┘
                                       └──────┬───────┘                  │
                                              │ HTTPS (optional)         │
                                              ▼                          ▼
                                   ┌─────────────────────────────────────────┐
                                   │ Server (Linux, Docker)                  │
                                   │ Traefik (HTTPS) → foody-server (Ktor)   │
                                   │ SQLite + Fotos in Docker-Volumes        │
                                   │ KI-Schlüssel in server/.env (geplant)   │
                                   └─────────────────────────────────────────┘
```

| Teil | Pflicht? | Wo es läuft | Was es tut |
|---|---|---|---|
| **App „Foody“** | ja | Handy und Tablet (dieselbe APK) | Rezepte, Nährwerte, Wochenplan, Einkauf, Tagebuch – komplett offline |
| **Sync-Server** | nein | eigener Rechner mit Docker (Homeserver, NAS, VPS) | gleicht Handy und Tablet ab, hält die Fotos der Rezepte |
| **KI-Hilfe** | nein, in Vorbereitung | auf dem Server (Schlüssel eines Sprachmodell-Dienstes) | Rezept aus Foto/Text erfassen, anpassen, Plan vorschlagen, Nährwerte schätzen |
| **Fotoerkennung** | nein, geplant | nur auf dem Handy (Modelldatei im App-Speicher) | Tagebucheintrag aus einem Foto des Essens, ohne Server und ohne Cloud |

Ohne Server funktioniert Foody vollständig; dann hat jedes Gerät seine eigenen Daten, und Daten wandern nur über eine
Sicherungsdatei von einem Gerät aufs andere.

---

## 2. Was man braucht

### Geräte

- **Android 16 oder neuer** auf Handy und Tablet (die App verlangt `minSdk` 36, ADR 0009). Ältere Geräte lassen die
  Installation nicht zu („App nicht kompatibel“).
- Für die geplante Fotoerkennung zusätzlich ein Handy mit 64-Bit-ARM (`arm64-v8a`) und viel Arbeitsspeicher
  (Richtwert 8–12 GB) und 0,5–2 GB freiem Speicher für das Modell.
- Für „Packung scannen“ die Google-Play-Dienste (sie laden Strichcode- und Texterkennung nach).

### PC zum Bauen (Windows, Linux oder macOS)

| Was | Wozu | Woher |
|---|---|---|
| **Git** | Repository holen | git-scm.com |
| **Android Studio** (aktuell) | bringt JDK (JBR, Java 21+) und SDK-Manager mit | developer.android.com/studio |
| **Android SDK Platform 37** | `compileSdk` 37 | in Android Studio: *Settings → Languages & Frameworks → Android SDK* |
| ca. 15 GB frei | SDK, Gradle-Cache, Build | – |

Ohne Android Studio geht es auch mit einem JDK 21 und den Android-Kommandozeilenwerkzeugen; die Befehle unten sind
dieselben. Gradle selbst muss man nicht installieren, das Repository bringt den Wrapper (`gradlew`) mit.

### Server (nur für den Sync)

| Was | Hinweis |
|---|---|
| Linux-Rechner, der dauerhaft läuft | Homeserver, NAS mit Docker oder kleiner VPS; 1 GB RAM reicht (der Container ist auf 512 MB begrenzt) |
| **Docker Engine** mit **Compose v2** (≥ 2.24) und BuildKit | BuildKit ist in aktuellen Docker-Versionen Standard |
| **Reverse-Proxy mit HTTPS** | vorgesehen ist **Traefik**; Caddy oder nginx gehen auch (Abschnitt 6.8) |
| **Host-Name**, den Handy und Tablet auflösen | z. B. `foody.example.de` (öffentlich) oder `foody.heim.lan` (nur im eigenen Netz/VPN) |
| **TLS-Zertifikat**, dem Android vertraut | Let's Encrypt (empfohlen) oder eine eigene CA, die in die App eingebaut wird (Abschnitt 6.3) |
| Internet beim Bauen des Images | Gradle lädt die Abhängigkeiten |

Die App spricht **nur HTTPS** und vertraut nur den System-Zertifikaten von Android (keine vom Nutzer installierten
Zertifikate). Ein selbst signiertes Zertifikat funktioniert deshalb nicht ohne Weiteres – siehe 6.3.

---

## 3. Die App bauen (am PC)

Alle Befehle für Windows in PowerShell. Unter Linux/macOS statt `.\gradlew` einfach `./gradlew` und `/`-Pfade.

### 3.1 Repository holen

```powershell
git clone https://github.com/WeissiEfficiency/Foody.git
cd Foody
```

### 3.2 Android Studio und SDK

1. Android Studio installieren und einmal starten (Einrichtungsassistent mit Standardwerten durchlaufen).
2. Im SDK-Manager **Android SDK Platform 37** und die **Build-Tools** installieren.
3. Den Ordner `Foody` in Android Studio öffnen. Android Studio legt dabei `local.properties` mit dem SDK-Pfad an.

Wer nur auf der Kommandozeile baut, legt `local.properties` im Projektordner selbst an und setzt `JAVA_HOME`:

```properties
# local.properties (liegt nicht im Repository)
sdk.dir=C\:\\Users\\DEIN_NAME\\AppData\\Local\\Android\\Sdk
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew --version          # zeigt Gradle 9 und JVM 21+
```

Kurz prüfen, ob alles baut (dauert beim ersten Mal einige Minuten):

```powershell
.\gradlew :domain:test
.\gradlew :app:assembleDebug
```

### 3.3 Den eigenen Release-Schlüssel anlegen (einmalig, wichtig)

Android spielt ein Update nur ein, wenn es mit **demselben Schlüssel** signiert ist wie die installierte App. Der
Schlüssel ist also so wertvoll wie die Daten auf den Geräten: Ohne ihn lässt sich Foody nicht mehr aktualisieren,
nur deinstallieren – samt aller Daten.

```powershell
New-Item -ItemType Directory -Force "$HOME\.android" | Out-Null
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "$HOME\.android\foody-release.jks" -storetype PKCS12 `
  -alias foody -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` fragt nach einem Passwort und nach Namen/Ort (beliebig ausfüllen). Danach:

1. `keystore.properties.example` nach `keystore.properties` kopieren und ausfüllen (die Datei ist in `.gitignore`):

   ```properties
   storeFile=C:/Users/DEIN_NAME/.android/foody-release.jks
   storePassword=DEIN_PASSWORT
   keyAlias=foody
   keyPassword=DEIN_PASSWORT
   ```

   Bei PKCS12 sind beide Passwörter gleich. Liegt `keystore.properties` woanders, in `local.properties` darauf zeigen:
   `foody.keystoreProperties=D:/Geheim/keystore.properties`.
2. **`.jks`-Datei und Passwort sichern** (Passwortmanager, zweiter Datenträger). Nie ins Repository.

### 3.4 Die APK bauen

```powershell
.\gradlew :app:assembleRelease
```

Ergebnis: `android\app\build\outputs\apk\release\app-release.apk` – das ist „Foody“ (`de.foody.app`) für Handy **und**
Tablet. Fehlt der Schlüssel, entsteht nur `app-release-unsigned.apk`, die sich nicht installieren lässt.

**Bequemer: direkt in einen Cloud-Ordner.** Die Aufgabe `apkNachOpenCloud` baut und kopiert die APK in einen Ordner,
den ein Cloud-Client (OpenCloud, Nextcloud, Syncthing …) auf die Geräte bringt. Ziel in `local.properties`:

```properties
foody.apkZiel=D:/Cloud/Apps/foody.apk
# optional für die Probeversion, sonst foody-vorschau.apk neben foody.apkZiel
foody.vorschauZiel=D:/Cloud/Apps/foody-vorschau.apk
```

```powershell
.\gradlew :app:apkNachOpenCloud        # „Foody“ → foody.apk
.\gradlew :app:vorschauNachOpenCloud   # „Foody Vorschau“ → foody-vorschau.apk (zum Ausprobieren)
```

---

## 4. Auf dem Handy installieren

### Weg A: über einen Cloud-Ordner oder USB (ohne PC-Werkzeuge)

1. Die APK aufs Handy bringen: Cloud-App (z. B. OpenCloud), USB-Kabel (Ordner *Download*) oder E-Mail an sich selbst.
2. Die Datei auf dem Handy antippen.
3. Beim ersten Mal fragt Android, ob die App (Cloud-App, Dateien-App, Browser) Apps installieren darf:
   **„Installation aus dieser Quelle zulassen“** einschalten, zurück, dann **„Installieren“**.
4. Meldet Play Protect „Unbekannte App“, **„Trotzdem installieren“** wählen (die App ist selbst gebaut und nicht bei
   Google registriert).

### Weg B: per USB mit `adb` (vom PC aus)

1. Auf dem Handy die Entwickleroptionen freischalten (*Einstellungen → Über das Telefon → 7× auf „Build-Nummer“*) und
   dort **USB-Debugging** einschalten.
2. Handy per USB verbinden, Dialog „USB-Debugging zulassen?“ bestätigen.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices                                                   # Gerät muss als „device“ erscheinen
& $adb install -r android\app\build\outputs\apk\release\app-release.apk
```

`-r` erlaubt später auch Updates über denselben Befehl (Daten bleiben).

### Danach in der App

1. Foody öffnen. Der Startkatalog mit Zutaten und Nährwerten ist sofort da.
2. Optional **Benachrichtigungen erlauben** (nur für Kochtimer im Hintergrund; ablehnbar).
3. Optional eigene Rezepte übernehmen: im Bereich *Rezepte* über **„Markdown importieren“** einzelne `.md`-Dateien
   oder einen ganzen Ordner wählen (auch aus einem Cloud-Anbieter). Schon importierte Quellen werden übersprungen.
4. Optional mit dem Server verbinden (Abschnitt 7) oder eine Sicherung einspielen (Abschnitt 10).

---

## 5. Auf dem Tablet installieren

Das Tablet bekommt **dieselbe APK** wie das Handy (mit demselben Schlüssel signiert). Die Installation läuft genau wie
in Abschnitt 4. Unterschiede:

- Foody zeigt auf breiten Bildschirmen Listen und Details zweispaltig; dafür ist nichts einzustellen.
- Die geplante **Fotoerkennung** wird auf dem Tablet nicht eingerichtet (kein Modell installieren); der Knopf bleibt
  dort einfach ausgeblendet.
- Beim **Verbinden mit dem Server** auf dem zweiten Gerät die richtige Wahl treffen (Abschnitt 7.2), sonst entstehen
  Rezepte doppelt.
- Ohne Server: Daten vom Handy aufs Tablet mit **Sicherung erstellen** (Handy) und **Wiederherstellen** (Tablet)
  übertragen (Abschnitt 10). Danach laufen beide Geräte getrennt weiter.

---

## 6. Den Sync-Server aufsetzen

Der Server ist ein einzelner Docker-Container (Ktor + SQLite), der **keine eigenen Ports veröffentlicht**: Er hängt in
einem Docker-Netzwerk hinter einem Reverse-Proxy, der HTTPS macht. Die Befehle unten gelten für Debian/Ubuntu; auf
anderen Systemen ist nur die Docker-Installation anders.

### 6.1 Docker installieren

Nach der offiziellen Anleitung (docs.docker.com/engine/install/), kurz für Debian/Ubuntu:

```sh
sudo apt update && sudo apt install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
. /etc/os-release
sudo curl -fsSL "https://download.docker.com/linux/$ID/gpg" -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/$ID $VERSION_CODENAME stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"     # danach neu anmelden
docker compose version              # muss 2.24 oder neuer sein
```

### 6.2 Erreichbarkeit planen

Zwei übliche Wege:

| Weg | Wie | Zertifikat |
|---|---|---|
| **Nur im eigenen Netz / per VPN** (empfohlen) | Host-Name zeigt im Heimnetz-DNS (Router, Pi-hole, AdGuard) auf den Server; unterwegs per WireGuard/Tailscale | Let's Encrypt per **DNS-Challenge** (eigene Domain nötig) oder eigene CA (6.3) |
| **Öffentlich im Internet** | DNS-Eintrag der eigenen Domain auf die öffentliche IP, Ports 80/443 im Router auf den Server weiterleiten | Let's Encrypt per HTTP-Challenge |

Der Server hat Anmeldung, Drosselung bei Fehlversuchen und keine Registrierung über die API; trotzdem ist ein Zugang
nur über das eigene Netz/VPN die kleinere Angriffsfläche.

### 6.3 Zertifikat: worauf Android besteht

Die App vertraut nur den Zertifikaten, die Android mitbringt (`android/app/src/main/res/xml/network_security_config.xml`).

- **Let's Encrypt (oder ein anderes öffentliches Zertifikat):** nichts weiter zu tun.
- **Eigene CA im Heimnetz:** Die App muss die CA kennen. Dafür vor dem Bauen (Abschnitt 3.4):
  1. das CA-Zertifikat (PEM, `.crt`) nach `android/app/src/main/res/raw/foody_local_ca.crt` legen (ersetzt die
     mitgelieferte Datei, die nur für den Host des ursprünglichen Autors gilt),
  2. in `network_security_config.xml` im `<domain-config>` den eigenen Host-Namen eintragen:

     ```xml
     <domain includeSubdomains="false">foody.heim.lan</domain>
     ```

  3. App neu bauen. Die CA gilt dann nur für diesen einen Host.
- **Selbst signiertes Zertifikat ohne CA** oder ein **auf dem Gerät installiertes Zertifikat**: funktioniert nicht.

### 6.4 Traefik einrichten (falls noch keiner läuft)

Ein gemeinsames Docker-Netzwerk für Traefik und Foody anlegen:

```sh
docker network create traefik
mkdir -p ~/docker/traefik && cd ~/docker/traefik
touch acme.json && chmod 600 acme.json
```

`~/docker/traefik/docker-compose.yml` (Variante öffentlich mit HTTP-Challenge; E-Mail anpassen):

```yaml
services:
  traefik:
    image: traefik:v3.5
    restart: unless-stopped
    command:
      - --providers.docker=true
      - --providers.docker.exposedbydefault=false
      - --entrypoints.web.address=:80
      - --entrypoints.web.http.redirections.entrypoint.to=websecure
      - --entrypoints.websecure.address=:443
      - --certificatesresolvers.letsencrypt.acme.email=du@example.de
      - --certificatesresolvers.letsencrypt.acme.storage=/acme.json
      - --certificatesresolvers.letsencrypt.acme.httpchallenge.entrypoint=web
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./acme.json:/acme.json
    networks:
      - traefik

networks:
  traefik:
    external: true
```

Für den Weg „nur im Heimnetz“ statt der `httpchallenge`-Zeile die DNS-Challenge des eigenen DNS-Anbieters verwenden
(`--certificatesresolvers.letsencrypt.acme.dnschallenge.provider=<anbieter>` plus dessen API-Zugang als Umgebungsvariablen,
siehe Traefik-Doku „ACME DNS providers“).

```sh
docker compose up -d
```

**Wichtig:** Am Entrypoint weder `forwardedHeaders.insecure` noch `forwardedHeaders.trustedIPs` mit Client-Netzen
setzen. Die Login-Drosselung richtet sich nach der Client-IP, die Traefik in `X-Forwarded-For` einträgt; dürften
Clients den Header selbst setzen, ließe sie sich umgehen.

### 6.5 Foody-Server installieren

```sh
cd ~/docker
git clone https://github.com/WeissiEfficiency/Foody.git foody
cd foody/server
cp .env.example .env
nano .env
```

Die `.env` (wird nie eingecheckt):

| Variable | Beispiel | Bedeutung |
|---|---|---|
| `FOODY_HOST` | `foody.example.de` | Host-Name, unter dem Traefik den Server ausliefert |
| `TRAEFIK_NETWORK` | `traefik` | Name des externen Docker-Netzwerks aus 6.4 |
| `TRAEFIK_ENTRYPOINT` | `websecure` | HTTPS-Entrypoint in Traefik |
| `TRAEFIK_CERTRESOLVER` | `letsencrypt` | Name des Zertifikats-Resolvers; bei eigener CA/Datei-Zertifikat leer lassen und Traefik entsprechend konfigurieren |
| `FOODY_ADMIN_USER` | `anna` | **nur beim ersten Start:** legt das erste Konto an (3–32 Zeichen, `A-Z a-z 0-9 . _ -`) |
| `FOODY_ADMIN_PASSWORD` | (lang, zufällig) | **nur beim ersten Start:** 10–200 Zeichen |
| `FOODY_MEM_LIMIT` | `512m` | Speichergrenze des Containers; die JVM nimmt 75 % davon |
| `FOODY_KI_KEY` | leer | Schlüssel für die KI-Hilfe, leer = aus (Abschnitt 8) |
| `FOODY_KI_PRO_STUNDE` | `30` | KI-Anfragen je Konto und Stunde |

Ein gutes Passwort erzeugen: `openssl rand -base64 24`.

Bauen und starten (der erste Bau dauert einige Minuten):

```sh
docker compose up -d --build
docker compose logs -f foody-server        # Strg+C beendet nur die Anzeige
```

Danach `FOODY_ADMIN_USER` und `FOODY_ADMIN_PASSWORD` in `.env` wieder **leeren** und neu anwenden:

```sh
docker compose up -d
```

Prüfen, ob der Server über HTTPS erreichbar ist (von einem Rechner im selben Netz oder VPN):

```sh
curl -fsS https://foody.example.de/health      # erwartet: {"status":"ok"}
```

Die Daten liegen in zwei Docker-Volumes: `foody_foody-data` (Datenbank `/data/foody.db`) und `foody_foody-photos`
(Fotos `/data/photos/<Konto-ID>/<sha256>.jpg`). Der Container läuft ohne Root, mit schreibgeschütztem Dateisystem
und ohne Capabilities.

### 6.6 Konten verwalten

Jede Person bekommt ein Konto; Handy und Tablet derselben Person melden sich mit **demselben** Konto an. Konten legt
nur der Admin-Befehl an (keine Registrierung in der App):

```sh
docker compose exec foody-server foody-admin                        # Übersicht aller Befehle
docker compose exec foody-server foody-admin konto-anlegen bernd     # gibt ein Zufallspasswort einmal aus
docker compose exec foody-server foody-admin reset-password bernd
docker compose exec foody-server foody-admin geraete                 # Geräte mit letzter Anfrage
docker compose exec foody-server foody-admin abmelden-ungenutzt 180  # Geräte widerrufen, die 180 Tage still waren
```

### 6.7 Nächtliche Sicherung des Servers

`backup.sh` (im Ordner `server/`) sichert Datenbank und Fotos in einen Ordner auf dem Host, gut geeignet für einen
Ordner, den ein Cloud-Client abgleicht. Es behält die letzten 14 Tage (`FOODY_BACKUP_KEEP`).

```sh
mkdir -p /srv/sicherung/foody
FOODY_BACKUP_DIR=/srv/sicherung/foody ./backup.sh     # einmal von Hand testen
crontab -e
```

```
15 3 * * * cd /home/DEIN_NAME/docker/foody/server && FOODY_BACKUP_DIR=/srv/sicherung/foody ./backup.sh >> /var/log/foody-backup.log 2>&1
```

Einspielen einer Sicherung: [`server/README.md`](../server/README.md), Abschnitt „Sicherung einspielen“.

### 6.8 Ohne Traefik (Caddy, nginx, anderer Proxy)

Eine Datei `server/docker-compose.override.yml` anlegen; Compose liest sie automatisch mit. Sie entfernt die
Traefik-Labels und das Netzwerk und gibt den Port nur lokal frei:

```yaml
services:
  foody-server:
    labels: !reset []
    networks: !reset []
    ports:
      - "127.0.0.1:8080:8080"
```

In `.env` müssen die `TRAEFIK_*`-Variablen trotzdem einen Wert haben (z. B. `TRAEFIK_NETWORK=bridge`), weil
`docker-compose.yml` sie liest. Dann z. B. mit **Caddy** (holt das Zertifikat selbst):

```
foody.example.de {
    reverse_proxy 127.0.0.1:8080
}
```

Auch hier gilt: Der Proxy muss `X-Forwarded-For` selbst setzen und darf einen vom Client mitgeschickten Wert nicht
durchreichen (Caddy und nginx mit `proxy_set_header X-Forwarded-For $remote_addr;` tun das richtig).

---

## 7. Handy und Tablet mit dem Server verbinden

### 7.1 Anmelden

In Foody: **Einstellungen → Konto → Server verbinden**.

| Feld | Eingabe |
|---|---|
| Server-Adresse | `https://foody.example.de` (nur `https://`; ein Unterpfad wie `https://example.de/foody` geht auch) |
| Benutzername | Konto aus 6.5/6.6 |
| Passwort | Passwort des Kontos |

Danach steht dort „Verbunden mit …“ und „Zuletzt synchronisiert: …“. Die App gleicht Rezepte, Plan, Einkauf,
Tagebuch und Fotos ab; das Passwort wird nicht gespeichert, nur ein Gerätetoken (verschlüsselt im Android Keystore).

### 7.2 Erstes und zweites Gerät

Hat das Gerät beim Verbinden schon Daten, fragt Foody:

- **Zusammenführen** – die Daten dieses Geräts kommen zu denen auf dem Server dazu.
- **Ersetzen** – die Daten dieses Geräts werden durch die des Servers ersetzt (Zutaten bleiben).

Empfohlene Reihenfolge:

1. **Handy zuerst** verbinden und **Zusammenführen** wählen – der Server ist noch leer und übernimmt alles.
2. **Tablet danach** verbinden. Hat es keine eigenen Daten, fragt es nicht. Hat es dieselben Rezepte schon (z. B. aus
   einer Sicherung), **Ersetzen** wählen, damit nichts doppelt entsteht. Nur wenn auf dem Tablet etwas eigenes liegt,
   das der Server noch nicht hat, Zusammenführen.

---

## 8. KI-Hilfe über den Server (in Vorbereitung)

> **Stand:** Das Gerüst ist da, die Funktionen noch nicht. Was gebaut wird, steht in
> [`ToDoS/ki-plan.md`](../ToDoS/ki-plan.md). Der Abschnitt beschreibt, was man heute schon einrichten kann und wo
> später was hingehört.

**Grundsätze (ADR 0011):** Die KI läuft **nur über den eigenen Server** – die App spricht nie selbst mit einem
KI-Dienst, und der Schlüssel liegt **nie auf Handy oder Tablet**. Sie arbeitet nur auf Knopfdruck, jedes Ergebnis ist
eine bearbeitbare Vorschau, und ohne Server oder Netz sind die Knöpfe ausgeblendet.

**Was man braucht:** einen API-Schlüssel des Sprachmodell-Dienstes, den das KI-Projekt festlegt (Modellwahl und Kosten
werden dort noch per ADR entschieden). Auf dem Server selbst ist nichts zu installieren; das Modell läuft beim Dienst.

**Einrichten** in `server/.env`:

```sh
FOODY_KI_KEY=dein-api-schluessel      # leer = KI aus
FOODY_KI_PRO_STUNDE=30                # Obergrenze je Konto und Stunde (Kostenbremse)
```

```sh
docker compose up -d                  # übernimmt die geänderte .env
docker compose exec foody-server sh -c 'test -n "$FOODY_KI_KEY" && echo "KI-Schlüssel gesetzt" || echo "KI aus"'
```

Der Server meldet den Geräten unter `GET /api/v3/ki/status`, ob ein Schlüssel eingerichtet ist
(`{"eingerichtet": true}`); den Schlüssel selbst gibt er nie aus und schreibt ihn nicht ins Log. Sobald die
KI-Funktionen fertig sind, braucht es nur ein Server-Update und eine neue APK (Abschnitt 11) – an der `.env` ändert
sich nichts.

---

## 9. Fotoerkennung auf dem Handy: wohin das Modell kommt (geplant)

> **Stand:** geplant, noch nicht gebaut ([`ToDoS/foto-erkennung-plan.md`](../ToDoS/foto-erkennung-plan.md)). Welches
> Modell es wird, entscheidet erst eine Messung (Phase 0, ADR 0017). Die Angaben hier zeigen den geplanten Ablauf.

Die Fotoerkennung („Mahlzeit aus Foto eintragen“) ist **unabhängig von der KI-Hilfe**: Sie läuft komplett auf dem
Handy, ohne Server und ohne Cloud. Das Foto verlässt das Gerät nie.

**Was installiert wird:**

| Teil | Wo | Wie |
|---|---|---|
| Laufzeit (llama.cpp) | in der APK | kommt mit dem App-Update, nichts zu tun |
| **Modelldatei(en)**, GGUF, 4 Bit, ca. 0,5–2 GB | **App-interner Speicher** des Handys | **in der App importieren**, nicht von Hand in einen Ordner kopieren |
| Server | – | nichts; kein neuer Endpunkt, kein Rebuild |

Das Modell steckt bewusst **nicht in der APK** (sie würde sonst um bis zu 2 GB wachsen) und wird einmalig
eingerichtet. Bild-Sprach-Modelle für llama.cpp bestehen in der Regel aus **zwei Dateien**: dem Sprachmodell
(`…-Q4_K_M.gguf`) und dem Bild-Teil (`mmproj-….gguf`); beide gehören zusammen.

**Geplanter Ablauf:**

1. Die gewählten GGUF-Dateien am PC besorgen (Bezugsquelle und Prüfsumme nennt ADR 0017, voraussichtlich Hugging Face)
   und in einen Ordner legen, den das Handy erreicht – z. B. den Cloud-Ordner, in dem auch die APK liegt.
2. Auf dem Handy: **Einstellungen → Fotoerkennung → Modell installieren**, die Datei(en) über die Dateiauswahl
   wählen (z. B. aus der Cloud-App). Foody prüft die Prüfsumme und kopiert das Modell in seinen eigenen Speicher
   (`filesDir`, für andere Apps und den Dateimanager unsichtbar). Alternativ ein einmaliger Download per Knopfdruck.
3. Danach erscheint im Tagebuch beim Eintragen der Knopf **„Foto“**. Ohne Modell bleibt er ausgeblendet.
4. Entfernen unter demselben Menüpunkt; Deinstallieren der App entfernt das Modell ebenfalls.

Die Einstellung gilt **je Gerät** und wird nicht gesynct. Auf dem Tablet einfach kein Modell installieren.

---

## 10. Sichern

| Was | Wie | Wo es landet |
|---|---|---|
| **Daten auf einem Gerät** | Foody: **Einstellungen → Sicherung → Sicherung erstellen** | ZIP-Datei (Datenbank + Fotos) an einem selbst gewählten Ort, z. B. im Cloud-Ordner |
| Gerät wiederherstellen / auf ein anderes Gerät übertragen | **Einstellungen → Sicherung → Wiederherstellen** | ersetzt **alle** Daten auf dem Gerät |
| **Server** | `backup.sh` per Cron (6.7) | Ordner auf dem Server-Host |
| **Release-Schlüssel** | `.jks` + Passwort einmalig sichern (3.3) | Passwortmanager, zweiter Datenträger |
| Server-Konfiguration | `server/.env` | mit sichern; sie liegt nicht im Repository |

Foody macht kein automatisches Cloud-Backup (`allowBackup=false`). Die Sicherungsdatei ist unverschlüsselt und
enthält das Ernährungstagebuch – sie gehört an einen Ort, auf den nur man selbst Zugriff hat.

---

## 11. Aktualisieren

### App (Handy und Tablet)

1. Am PC: `git pull`, dann `.\gradlew :app:assembleRelease` (oder `:app:apkNachOpenCloud`).
2. Auf jedem Gerät die neue APK öffnen und **„Aktualisieren“** wählen (oder `adb install -r …`). Daten und Anmeldung
   bleiben.

Voraussetzungen: derselbe Schlüssel und ein `versionCode` in `android/app/build.gradle.kts`, der nicht kleiner ist als
der installierte (wer selbst Änderungen macht, erhöht ihn vor jedem Release). Nach einem Update mit
Datenbank-Änderung gibt es kein Zurück auf eine ältere Version – vor großen Updates eine Sicherung erstellen.

### Server

```sh
cd ~/docker/foody/server
FOODY_BACKUP_DIR=/srv/sicherung/foody ./backup.sh     # vorher sichern
git pull
docker compose up -d --build
docker compose logs --tail=50 foody-server
```

Steht eine Datenbank-Migration an, sichert der Server die Datenbank vorher selbst (`vor-migration-….db` im Volume).
Scheitert sie, startet er nicht und nennt den Grund im Log; Details in [`server/README.md`](../server/README.md),
„Datenbank und Migrationen“.

**Reihenfolge:** Verlangt eine neue App-Version ein neues Protokoll, zuerst den Server aktualisieren, dann die Apps.
Die App meldet sonst „Der Server ist zu alt für diese App.“

---

## 12. Wenn etwas nicht klappt

| Meldung / Beobachtung | Ursache und Abhilfe |
|---|---|
| „App nicht installiert“ / „Paket steht in Konflikt“ | anderer Schlüssel als die installierte App. **Nicht deinstallieren** (Daten weg), sondern mit dem richtigen Schlüssel bauen |
| „Installation blockiert“ | „Installation aus dieser Quelle zulassen“ für die App einschalten, aus der die APK geöffnet wird |
| „App nicht kompatibel“ | Gerät hat kein Android 16 |
| Gradle: „Kein Release-Schlüssel …“ / nur `-unsigned.apk` | `keystore.properties` fehlt oder Pfad falsch (3.3) |
| Gradle: „foody.apkZiel fehlt in local.properties“ | Ziel für `apkNachOpenCloud` eintragen (3.4) |
| Gradle: „SDK location not found“ | `sdk.dir` in `local.properties` setzen (3.2) |
| Server startet nicht, Log: „FOODY_ADMIN_USER muss 3–32 Zeichen …“ | Name oder Passwort für das erste Konto ungültig |
| Log: „Keine Konten vorhanden und FOODY_ADMIN_USER/… nicht gesetzt“ | erstes Konto fehlt: Variablen setzen und neu starten, oder `foody-admin konto-anlegen` |
| `curl …/health` geht nicht, Traefik zeigt 404 | `FOODY_HOST` passt nicht zum aufgerufenen Namen oder Container nicht im Netzwerk `TRAEFIK_NETWORK` |
| „Keine Verbindung zum Server.“ | Host-Name im Netz/VPN nicht auflösbar, Proxy aus, oder das Zertifikat stammt nicht von einer System-CA → Let's Encrypt nehmen oder eigene CA einbauen (6.3) |
| „Die Adresse ist ungültig (https nötig).“ | nur `https://…`, ohne Benutzer, `?` oder `#` in der Adresse |
| „Benutzername oder Passwort falsch.“ | Zugangsdaten prüfen, ggf. `foody-admin reset-password <name>` |
| „Zu viele Versuche – bitte kurz warten.“ | 5 Fehlversuche sperren 15 Minuten; abwarten |
| „Für einen anderen Server oder ein anderes Konto bitte zuerst trennen.“ | Einstellungen → Konto → **Trennen**, dann neu verbinden |
| Sync: „Diese App ist zu alt für den Server.“ | neue APK installieren |
| Sync: „Der Server ist zu alt für diese App.“ | Server aktualisieren (11) |
| Sync: „Anmeldung abgelaufen – bitte neu anmelden.“ | Einstellungen → Konto → Server verbinden, neu anmelden; Daten bleiben |
| Rezepte doppelt nach dem Verbinden | auf dem zweiten Gerät „Zusammenführen“ statt „Ersetzen“ gewählt; doppelte Rezepte löschen (7.2) |

Server-Logs: `docker compose logs --tail=200 foody-server` im Ordner `server/`.

---

## 13. Für die Entwicklung: Varianten, Emulator, Test-Server

| Variante | App-ID | Wofür | Signatur |
|---|---|---|---|
| **Foody** | `de.foody.app` | Alltag auf Handy und Tablet | eigener Release-Schlüssel |
| **Foody Vorschau** | `de.foody.app.vorschau` | Probeversion neben „Foody“ (R8 wie ein Release) | Debug-Schlüssel |
| **Foody Debug** | `de.foody.app.debug` | Entwicklung, Emulator | Debug-Schlüssel |

Jede Variante hat eigene Daten; sie laufen nebeneinander. Nur „Foody Debug“ darf per `http://10.0.2.2` bzw.
`http://localhost` mit einem Server ohne HTTPS sprechen (Emulator → Entwicklungsrechner).

```powershell
.\gradlew :app:installDebug          # „Foody Debug“ auf Emulator oder Gerät
.\gradlew :app:assembleVorschau      # „Foody Vorschau“
.\gradlew check                      # alle Tests + Lint, wie die CI
```

- Einen zweiten Server mit eigenen Daten für die Entwicklung (ohne Traefik, nur `127.0.0.1:8081`):
  [`server/README.md`](../server/README.md), Abschnitt „Test-Server“.
- Server lokal ohne Docker (auch unter Windows): ebenda, „Lokale Entwicklung unter Windows“.
- Weitere Befehle: [`README.md`](../README.md), „Bauen & Testen“; Aufbau: [`docs/architecture.md`](architecture.md);
  Sicherheit: [`docs/security.md`](security.md).
