# ADR 0019: KI-Hilfe über den Server gestrichen

Status: angenommen (2026-11-04). Ändert einen Punkt von ADR 0011.

**Kontext:** ADR 0011 sah KI nur über den eigenen Server vor: Die App ruft `POST /ki/…` auf, nur der Server kennt den
Schlüssel eines Sprachmodell-Dienstes. Gebaut war nur das Gerüst (`GET /ki/status`, `KiKontingent`, `FOODY_KI_KEY`,
`FOODY_KI_PRO_STUNDE`); Modellwahl und Funktionen standen in `ToDoS/ki-plan.md` aus. Seit 3.3.0 läuft eine
Fotoerkennung auf dem Handy (ADR 0017), ohne Server, ohne Netz und ohne laufende Kosten; der Nutzer braucht die
Server-KI damit nicht mehr.

**Entscheidung:** Die KI-Hilfe über den Server entfällt. Das Gerüst kommt aus Server und Protokoll heraus (`KiRoutes`,
`KiKontingent`, `KiStatus`, die beiden Umgebungsvariablen). KI gibt es in Foody nur noch auf dem Gerät. Der Plan liegt
archiviert in `ToDoS/archiv/ki-plan.md`.

**Verworfen:**
- *Gerüst für später stehen lassen:* toter Code mit eigener Konfiguration und Tests, ohne absehbaren Nutzen.

**Folgen:** Der Server muss einmal neu gebaut werden; `FOODY_KI_*` in einer alten `server/.env` wird ignoriert. Die App
ist nicht betroffen, sie hat `GET /ki/status` nie aufgerufen. Soll später doch eine Server-KI kommen, braucht sie ein
neues ADR.
