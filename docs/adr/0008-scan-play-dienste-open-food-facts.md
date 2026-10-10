# ADR 0008: Packung scannen über Play-Dienste und Open Food Facts

Status: angenommen (2026-10-10). Spec: `docs/superpowers/specs/2026-10-10-naehrwerte-scan-design.md`.

**Kontext:** Nährwerte von Packungen abzutippen ist mühsam. Zwei Wege bieten sich an: der Strichcode (Produktnummer,
Werte aus einer Datenbank) und das Foto der Nährwerttabelle (Texterkennung). Foody ist offline-first (ADR 0001) und
soll keine Daten unnötig weitergeben.

**Entscheidung:**
- **Strichcode zuerst:** Google Code Scanner (Play-Dienste, eigene Kameraansicht, keine Kamera-Berechtigung). Erst
  im eigenen Katalog suchen (`ingredient.barcode`, offline), dann **Open Food Facts** (frei, gemeinnützig) – nur die
  Produktnummer wird gesendet; abschaltbar („Online-Produktsuche“, Standard an).
- **Foto als Ausweichweg:** ML Kit Texterkennung über die **Play-Dienste** (Modell wird nachgeladen, App bleibt
  klein); die Erkennung läuft auf dem Gerät. Ein eigener Parser (`NaehrwertScan`) setzt Tabellenzeilen über die
  Höhe zusammen und nimmt je Stichwort die erste Zahl mit Einheit.
- Werte werden **nur vorausgefüllt und markiert**; Speichern ist die Bestätigung.

**Verworfen:**
- *ML Kit gebündelt:* funktioniert ohne Play-Dienste, macht die App aber rund 4 MB größer (Nutzerentscheidung).
- *Eigene Kameraansicht (CameraX):* mehr Code und eine Kamera-Berechtigung, ohne Mehrwert gegenüber Code Scanner und
  System-Kamera.
- *KI in der Cloud zum Lesen der Tabelle:* robuster, aber das Foto verließe das Gerät; widerspricht offline-first.

**Folgen:** Ohne Play-Dienste ist der Scan ausgegraut, alles andere funktioniert. Die erste Nutzung lädt die Module
nach („Scanner wird vorbereitet“). Open-Food-Facts-Werte sind gemeinschaftlich gepflegt und daher nur ein Vorschlag.
Typische Lesefehler der Texterkennung („Eiweis“ statt „Eiweiß“, „12 9“ statt „12 g“) fängt der Parser ab.
