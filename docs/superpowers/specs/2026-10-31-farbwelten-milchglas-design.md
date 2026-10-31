# Farbwelten und Milchglas – Design

Stand 2026-10-31. Entscheidung: ADR 0015. Entwürfe: `docs/design/milchglas/` (zehn Bildschirme hell und dunkel,
Farbtabellen in der `README.md`). Grundlage war der Design-Canvas „Foody Design“.

## Ziel

Die App bekommt die Oberfläche „Milchglas“ mit runden Formen; die Farbe ist frei wählbar (acht Farbwelten, Standard
Mango). Aufbau, Schrift und Komponenten bleiben; es ändert sich nur das Aussehen.

## Umfang

1. **Farbwelten** (`:core:designsystem`, `token/Farbwelt.kt`): Enum `Farbwelt` mit Anzeigename; je Modus
   `FarbweltWerte` = `FoodyFarben`, `Verlauf`, Glastönung, drei Farben für den Platzhalter-Verlauf.
   `Farbwelt.ausName()` liest den gespeicherten Namen, unbekannt oder fehlend = `Farbwelt.Standard` (Mango).
2. **Theme:** `FoodyTheme(dunkel, farbwelt, unschaerfe)`; `FoodyTheme.farbwelt` und `FoodyTheme.werte` für
   Komponenten. `FoodyHintergrund` zeichnet den Verlauf der Farbwelt, `GlasFlaeche` deren Tönung.
3. **Milchglas:** `glasWerte(stufe, dunkel, farbwelt)` – Unschärfe 40 dp, Korn 0,08/0,05 (Haze `noiseFactor`).
4. **Formen:** `FoodyFormen` 14/23/32 dp, neu `leiste` 39 dp für Navigationsleiste und -rail.
5. **Rezeptkarte:** Herz in der hellen Primärfarbe der Farbwelt (`herzFarbe`), Platzhalter im Verlauf der Farbwelt.
6. **Speicher** (`:core:data`): `DarstellungSpeicher.farbwelt()`, `farbweltAktuell()`, `farbweltSetzen(name)` unter dem
   neuen Schlüssel `farbwelt`. Das Datenmodul kennt nur den Namen, nicht das Enum.
7. **Einstellungen:** Abschnitt „Darstellung“ mit „Farbe“ (acht Muster, Optionsfeld-Gruppe, gewählt mit Ring und
   Haken) und darunter „Modus“ (System/Hell/Dunkel wie bisher).
8. **App:** `MainActivity` liest die Farbwelt wie die Darstellung (Startwert sofort aus den Prefs) und gibt sie an das
   Theme.

## Farben

Die Werte stehen in `Farbwelt.kt` und in `docs/design/milchglas/README.md`. Gegenüber dem Canvas ist der Nebentext im
hellen Modus dunkler: Der Canvas hat Text nur auf Glas geprüft, die App prüft auch Text direkt auf dem Verlauf.

## Kontrast

`kontrastPaare(dunkel, farbwelt)`; `KontrastTest` prüft alle Farbwelten hell und dunkel mit denselben Paaren wie bisher
(Text, Nebentext, Primär ohne Glas und auf jeder Glasstufe, Oliv und Safran als Grafik, über Bildern inklusive
Platzhalter der Farbwelt, Herz auf dem Abzeichen).

## Tests

- `KontrastTest`: alle Farbwelten; `Farbwelt.ausName` mit Standard.
- `DarstellungSpeicherTest`: Farbwelt fehlt = `null`, Setzen schreibt und meldet.
- `EinstellungenViewModelTest`: Standard Mango, Setzen speichert und kommt im Zustand an.
- `EinstellungenScreenTest`: Muster „Pflaume“ antippen speichert die Farbwelt und markiert sie als gewählt.
- Roborazzi: alle Referenzbilder neu aufgenommen.

## Nicht in diesem Schritt

Sync der Farbwahl zwischen Geräten; andere Oberflächen und Formen aus dem Canvas; Farbwahl im `:katalog`.
