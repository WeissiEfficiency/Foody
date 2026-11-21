# Spec: Einheitlicher Rahmen über alle Stile (Teil A)

Stand 2026-11-20, vom Nutzer freigegeben. Grundlage: Alltagstest von 3.9.0 auf dem Handy (OnePlus CPH2645, 361 dp
breit, Schrift 90 %). Teil B (Eintragen neu) bekommt einen eigenen Spec.

## Warum

Die sieben Stile sollen einzigartig bleiben, aber Dinge, die man überall sucht, sollen überall gleich sitzen und gleich
groß sein. Heute baut jede Szene ihren Kopf selbst; dadurch wandert das Zahnrad (Anime links in der zweiten Zeile,
Edo ganz unten), seine Größe schwankt, und Milchglas zeigt eine Person. Dazu kommen Kleinigkeiten, die beim Umschalten
oder Bedienen stören.

## Ziele

1. **Einstellungen-Knopf einheitlich.** Ein gemeinsamer Baustein `EinstellungenKnopf` in `:core:designsystem`
   (`komponenten/`), in allen sieben Stilen und beiden Varianten immer **oben rechts** im Kopf von Heute.
   - Größe überall gleich: 48 dp Zielfläche, darin eine runde Fläche von 40 dp mit dem Zahnrad, genau wie die
     übrigen runden Knöpfe (`RunderKnopf`, Symbol 20 dp; Abweichung vom Gespräch „24 dp“, damit Zahnrad und Zurück
     gleich groß wirken).
   - Der Stil bestimmt nur Farben (Fläche, Rand, Symbol) über `FoodyTheme.farben`; Form immer Kreis, Rand immer
     gleich dick. Bedienhilfe „Einstellungen“.
   - Milchglas: Zahnrad statt Person (`RunderKnopf(PersonOutline)` in `HeuteKopf`).
   - Edo: Zahnrad aus der Knopfreihe am Ende („＋ Eintragen · Scannen · Foto · ⚙“) nach oben in den Kopf.
   - Anime: Der Kopf bricht nicht mehr um. Titel, Datum und Zahnrad stehen in einer Zeile; reicht die Breite nicht,
     wird der Titel kleiner (oder das Datum rückt unter den Titel), das Zahnrad bleibt rechts.
   - Sage, Bayrisch, Grauen, Kreuzer: gleicher Baustein an derselben Stelle; Sage Immersiv weiter über dem Feuerbild.
2. **Stilkacheln in den Einstellungen mit fester Größe.** Die Kacheln unter „Stil“ ändern beim Umschalten weder Größe
   noch Lage:
   - die gewählte Kachel bekommt einen Rand bzw. Haken, aber keinen anderen Innenabstand;
   - der Name darunter hat immer Platz für zwei Zeilen (feste Höhe), damit „Kosmisches Grauen“ die Reihe nicht je
     nach Schrift verschiebt.
3. **Anime: Navigationsleiste weiß.** Die schwebende Leiste (Heute · Rezepte · Woche · Einkauf) ist bei Anime deckend
   weiß statt halb durchsichtiges Glas über dem rosa Punktmuster (Immersiv und Ruhig). Die anderen Stile bleiben.
4. **Einkauf: kein doppelter Weg zu den Listen.** In den Stilen Q–V führen heute das ⋮-Menü und der Knopf „Listen“
   zu denselben drei Dingen.
   - Q–V: Das ⋮-Menü fällt weg; „Listen“ (je Stil „Zettl“, „Archiv“, „Ladelisten“ …) öffnet die Listen-Seite mit
     den Reitern wie bisher. „Liste löschen“ zieht auf die Listen-Seite um (als Aktion zur aktuellen Liste).
   - Milchglas behält das ⋮-Menü (dort gibt es keinen Knopf „Listen“).
5. **Menüs und Abteilungen im Stil.**
   - Ein gemeinsames Menü `FoodyMenue` (+ Eintrag) in `:core:designsystem`: Fläche, Form, Rand und Schrift aus dem
     Stil (`FoodyTheme.stilWerte`, `FoodyTheme.formen`). Alle `DropdownMenu` der App nutzen es (Einkauf ⋮,
     Kamera/Galerie bei Foto und Packung, Rezept- und Zutatenmenüs, Sortierung).
   - Die aufklappbaren Abteilungen im Einkauf („Obst & Gemüse ⌄“, `Klappkopf` in `EinkaufKacheln.kt`) bekommen
     Schrift und Farben des Stils (Überschrift wie die anderen Abschnittsköpfe der Szene).
6. **Tests mit der Breite des Handys.** Die Bildvergleiche der Heute-Köpfe laufen zusätzlich mit `w360dp`, damit ein
   Umbruch wie bei Anime in der CI auffällt.

## Nicht-Ziele

- Keine neuen Funktionen, keine Änderungen an Daten, Sync oder Server (Server-Rebuild: nein).
- Eintragen bleibt wie es ist (Teil B).
- Der Sync-Hinweis darf je Stil an seiner Stelle bleiben (Nutzer: „ist okay“).
- Die Szenen behalten ihre Zeichnungen und Wörter; nur Lage und Größe der genannten Bausteine werden gleich.

## Anti-Ziele

- Ein Stil verliert seinen Charakter (z. B. Anime ohne Konturen, Sage ohne Gold), nur damit alles gleich aussieht.
- Milchglas-Bilder ändern sich außer am Zahnrad und an den Menüs.
- Kontrast unter WCAG 2.2 AA oder Zielflächen unter 48 dp.

## Ansätze

1. **Gemeinsame Bausteine im Designsystem (gewählt).** `EinstellungenKnopf` und `FoodyMenue` einmal bauen, die
   Szenen rufen sie auf. Lage und Größe sind an einer Stelle festgelegt; Farben kommen aus dem Theme.
2. Je Szene nachbessern: jede der sieben Heute-Szenen einzeln anpassen. Schneller im ersten Schritt, aber die nächste
   Änderung läuft wieder auseinander.
3. Ein fester Kopf für alle Szenen (Titel, Sync, Zahnrad), die Szenen nur darunter. Am einheitlichsten, nimmt aber
   den Stilen ihre Titelgestaltung (Rollbild, Maibaum, „HEUTE!“) und widerspricht den Entwürfen.

Gewählt ist 1, weil es Lage und Größe festlegt, ohne die Köpfe der Stile anzufassen.

## Dem Umsetzer überlassen

- Wie Anime bei wenig Breite ausweicht (kleinerer Titel oder Datum darunter), solange das Zahnrad rechts oben bleibt.
- Wo „Liste löschen“ auf der Listen-Seite genau sitzt.
- Aufteilung in PRs (Vorschlag: Zahnrad + 360-dp-Tests; Kacheln + Anime-Leiste; Einkauf; Menüs + Abteilungen).

## Prüfung

- Roborazzi: neue Bilder je Stil für Heute bei 360 dp; geänderte Bilder nur dort, wo dieser Spec etwas ändert.
- `EinstellungenScreenTest`: Kacheln behalten beim Wechsel des Stils ihre Größe (Grenzen vorher/nachher gleich).
- Einkauf: in Q–V kein „Weitere Aktionen“ mehr, „Liste löschen“ auf der Listen-Seite; Milchglas unverändert.
- `KontrastTest` grün; `./gradlew check` und `ktlintPruefen` grün.
- Am Handy (361 dp) in allen Stilen ansehen.
