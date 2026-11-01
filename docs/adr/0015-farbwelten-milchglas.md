# ADR 0015: Wählbare Farbwelten und Milchglas

Status: angenommen (2026-10-31). Ersetzt in ADR 0012 die Farben „Ofenwarm“ und die Glaswerte; Haze, Glasstufen,
Kontrast-Regel, Schrift und das Modul `:core:designsystem` gelten weiter. Entwürfe als Bilder:
`docs/design/milchglas/` (Übersicht mit allen Farbwerten in der `README.md`). Spec:
`docs/superpowers/specs/2026-10-31-farbwelten-milchglas-design.md`.

**Kontext:** Nach dem Umstieg wurde das Aussehen im Design-Canvas „Foody Design“ noch einmal ausprobiert: andere
Farben (u. a. „Beere“), Oberflächen (Glas, Matt, Papier, Tonal, Milchglas …) und Formen (Rund, Kantig, Pille,
Squircle …). Der Nutzer wollte eine Oberfläche, die er selbst einfärben kann.

**Entscheidung:**
- **Farbwelten:** acht wählbare Paletten (Beere, Kirsche, Mango, Pflaume, Heidelbeere, Lagune, Pistazie, Abendrot),
  je hell und dunkel; **Standard Mango**. Der Hintergrundverlauf ist Ton in Ton aus der Hauptfarbe (zweiter Ton um 28°
  verschoben, bei Grün Richtung Gelb), das Glas ist mit der Hauptfarbe getönt, der Platzhalter für Rezepte ohne Foto
  folgt der Farbwelt. Oliv bleibt die Eiweiß-Farbe, Safran die Kohlenhydrat-Farbe.
- **Wahl** unter Einstellungen › Darstellung (Muster mit Namen, neben Hell/Dunkel/System); gespeichert in den Prefs
  `foody` unter `farbwelt` (Name der Farbwelt), fehlend = Standard. Vorerst nur auf dem Gerät, kein Sync.
- **Milchglas:** Unschärfe 40 dp statt 24 dp, feines Korn (Haze `noiseFactor` 0,08 hell, 0,05 dunkel), Tönung je
  Farbwelt; Deckkraft der Stufen unverändert.
- **Formen „Rund“:** Ecken 14/23/32 dp (vorher 12/20/28), Navigationsleiste 39 dp; Knöpfe und Chips bleiben Pillen.
- **Kontrast-Regel unverändert:** Der Test prüft jetzt jede Farbwelt in beiden Modi – auch Text direkt auf dem
  Verlauf. Dafür musste der Nebentext im hellen Modus gegenüber dem Canvas etwas dunkler werden.

**Verworfen:** Beere als einzige feste Farbe (Zwischenstand); andere Oberflächen (Matt, Papier, Tonal, Emaille,
Körnig, Relief, Kräftig, Leinen) und Formen (Kantig, Pille, Squircle, Schnitt, Blatt, Etikett, Angedockt) – sie
bleiben als Vergleich im Canvas; vier Farbflecken im Hintergrund wie im Canvas (überlagerte Flecken drücken den
Kontrast unter AA); Sync der Farbwahl in diesem Schritt (bräuchte eine Änderung am Protokoll und Server-Rebuild).

**Folgen:**
- Alle Roborazzi-Referenzbilder wurden neu aufgenommen (Standard Mango statt Ofenwarm).
- `FoodyFarben.Hell`/`Dunkel` liefern die Farben der Standard-Farbwelt; Komponenten lesen Verlauf, Glas und
  Platzhalter über `FoodyTheme.werte`.
- Möglicher nächster Schritt: Farbwahl zwischen Handy und Tablet abgleichen.
