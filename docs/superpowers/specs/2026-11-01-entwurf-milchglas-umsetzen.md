# Entwurf „Milchglas“ vollständig umsetzen – Spec

Stand 1. November 2026. Auslöser: Screenshots des Nutzers von 3.1.0 (Farbwelt Pistazie) gegen den Entwurf
„Foody V3 – Milchglas, Rund, Mango, hell“ im Canvas „Foody Design“.

## Befund

Mit #170 kamen nur die **Grundlagen** in die App: Farbwelten, Glaswerte, Formen „Rund“, die Farbwahl. Die
**Bildschirme selbst** (Aufbau, Kopf, Raster, Kacheln, Tagesleiste, Ring) blieben im Stand von V3. `abgleich.md`
und der Aufräum-PR #177 („Entwürfe seit der Umsetzung gelöscht“) haben das als umgesetzt dargestellt; das war
falsch. Die gerenderten Entwürfe liegen im Git-Verlauf (`git show 514825b:docs/design/milchglas/<seite>-hell.png`)
und im Canvas.

Ein Teil der Unterschiede hängt an den Daten: Die Referenzbilder von Heute zeigen Großkarte mit „Kochen“ und den
Einkaufsabschnitt, sobald etwas geplant bzw. offen ist. Auf dem Handy war beides leer (nichts geplant, 0 offen), deshalb
fehlten die Abschnitte. Der Entwurf zeigt den gefüllten Fall; leere Abschnitte bekommen künftig einen ruhigen
Leerzustand statt zu verschwinden.

## Abweichungen

### Überall (Designsystem)

| Stelle | Entwurf | App heute |
|---|---|---|
| Navigationsleiste | aktiver Eintrag: Pille (Behälterfarbe) hinter dem Symbol, Text fett in Primär; Druckeffekt nur in der Pille | keine Pille; Druckeffekt als Rechteck über den ganzen Eintrag, ragt über die runde Leiste hinaus |
| Seitenkopf | Überzeile in Versalien (Datum, „6 Rezepte“, „KW 44 · …“), darunter Titel; Aktionen als runde Glasknöpfe rechts | nur Titel, Drei-Punkte-Menü |
| Suchfeld / Eingabe | Glas-Pille ohne Rahmen, Lupe links | Rahmen, in Rezepte eckig |
| Chips | gewählt: Primär gefüllt, Text weiß; sonst feiner Rahmen | gewählt: fast schwarz; dicker Rahmen |
| Leerer Platz | gestrichelter Kasten, Text links, Textlink „Eintragen“ rechts | gestrichelter Kasten mit großem Rahmenknopf |
| Hauptaktion | Pillen-FAB „+ Eintragen“ unten rechts bzw. runder „+“-Knopf im Kopf | breiter Rahmenknopf am Listenende |
| Blätter (Bottom Sheets) | dichtes Glas in der Farbwelt, Felder als Pille, runde Haken | weißes Material-Blatt, eckige Felder, eckige Checkbox |
| Tagesleiste | sieben Pillen (Kürzel, Tag, Punkt bei Einträgen), heute in Primär | fehlt (Woche: Pfeile; Tagebuch: Pfeile + Kalender) |

### Je Bildschirm

| Bildschirm | Entwurf | App heute |
|---|---|---|
| **Heute** | Kopf mit Datum, Sync-Pille, Konto-Knopf. Großkarte „nächste Mahlzeit“ (Bild, Glas-Einsatz, „Kochen“, Dauer). Tagebuchkarte: `1.240 / 2.000 kcal`, „760 übrig“, Makrobalken, Einträge mit Mahlzeit-Überzeile und kcal, leerer Platz als Textlink, geplante Mahlzeit mit „Gegessen?“. **Einkauf**: Kacheln mit Initiale, „6 offen“ | Inspiration-Karte; Tagebuch mit vier großen Leerkästen; **kein Einkauf** |
| **Rezepte** | Überzeile „6 Rezepte“, Titel, runder „+“. Suchfeld als Pille. Chips Alle/Favoriten/Schnell/… **Zwei Spalten**, quadratische Bilder mit Dauer-Abzeichen und Herz, darunter Name und „kcal · Portionen“ | eine Karte je Zeile, kcal-Abzeichen, Chips Sortierung/Favoriten/Oft gekocht |
| **Rezept** | Bild mit runden Knöpfen (Zurück, Bearbeiten, Herz), Glaskarte mit Gang, Titel, Chips (Dauer, kcal/Portion, kcal für N, zuletzt gekocht), Makros; Glaskarte Zutaten mit Portionen-Stepper und „Auf die Einkaufsliste“; Glaskarte Zubereitung mit nummerierten Kreisen; unten „In Woche planen“ + „Kochen“ | Aktionen als Symbolreihe, Zutaten und Schritte ohne Karte |
| **Kochen** | Kopf: Schließen rund, „Schritt 2 von 6“, Rezeptname, Pille „Bleibt an“. Segmentierter Fortschritt. Große Glaskarte: Schritt in Fraunces, Zutaten als Chips, **Timer-Kasten** (02:00, „Start“). Karte „Als nächstes“. Zurück / Weiter | durchgehender Balken, Timer als Chip, Zutaten als Tabelle |
| **Woche** | Überzeile KW, Titel, runde Pfeile; Tagesleiste; Tageskarten „Samstag, 31.“ + „Heute“, „kcal geplant“, Mahlzeiten mit Überzeile, „✓ gekocht“ / „2 P.“, gestrichelt „+ Snack planen“; Fuß „14 von 21 geplant“ + „Einkaufsliste erstellen“ | alle sieben Tage untereinander, Makrobalken je Tag, „+ Mahlzeit“, „Leere Tage füllen“ |
| **Tagebuch** | runder Zurück-Knopf, Titel; Tagesleiste; **Ring** (kcal von Ziel) neben drei Makrobalken; je Mahlzeit eine Glaskarte (Titel, kcal-Summe, Einträge mit Menge, gestrichelter Platz, „Geplant … Gegessen?“); FAB „+ Eintragen“ | langer Datumstitel mit Pfeilen und Kalender; flache Liste mit Stift/Mülleimer; Rahmenknopf am Ende |
| **Einkauf** | Überzeile, Titel; „7 von 13 erledigt“ mit Balken; Eingabe-Pille + runder „+“; Gruppen mit Kacheln (Initiale, Name, Menge); erledigt: Haken in Oliv, durchgestrichen | Katalog mit Emoji-Kacheln und Klappgruppen (Bring-Stil), gewählte Artikel als volle Primär-Kacheln |
| **Einstellungen** | Abschnitte als Glaskarten; Konto mit Initiale; Farbmuster; Tagesziel mit −/+; Schalter | weitgehend da; Farbwahl-Raster lückenhaft (FlowRow mit SpaceBetween), Modus-Chips wie oben |
| **Zutaten / Server verbinden** | Kopf mit rundem Zurück, Suchfeld-Pille, Chips, Liste mit Initiale und kcal/100 g; Felder als Pille | Felder eckig, Chips wie oben |

## Entscheidungen (selbst getroffen, Nutzer: „nicht auf Freigabe warten, dokumentieren“)

1. **Funktionen bleiben, das Aussehen folgt dem Entwurf.** Was die App mehr kann als der Entwurf (Einkaufskatalog,
   Inspiration ohne Plan, Sortierung, Tablet-Ansichten), bleibt erreichbar und bekommt die Formen des Entwurfs.
2. **Keine erfundenen Daten.** Makroziele (`62 / 90 g`) gibt es nicht; die Balken zeigen Gramm und den Anteil an
   der Energie. Kommen Makroziele, ist das eine eigene Funktion.
3. **Einkauf:** Die Liste steht oben als Kacheln wie im Entwurf; der Katalog zum Hinzufügen erscheint beim Tippen
   in die Eingabe-Pille und unter der Liste, in denselben Kacheln (Initiale statt voller Primärfläche).
4. **Heute:** Ist eine Mahlzeit geplant, zeigt die Großkarte sie mit „Kochen“; sonst die Inspiration in derselben
   Karte. Der Einkaufsabschnitt zeigt bis zu sechs offene Artikel. Dafür braucht `:feature:heute` die offenen
   Einkaufsartikel über ein `:core`-Repository (Features kennen sich nicht).
5. **Tablet** folgt denselben Bausteinen; Rail bekommt dieselbe Pille wie die Leiste.

## Umsetzung (je ein kleiner PR gegen `main`)

1. **Designsystem:** Navigation (Pille, begrenzter Druckeffekt), `Seitenkopf` (Überzeile, Titel, runde Aktionen),
   `RunderKnopf`, `Suchfeld`/`FoodyTextfeld` als Glas-Pille, `FoodyChip` gewählt in Primär, `LeererPlatz` mit
   Textlink, `FoodyFab` (Pille), `Tagesleiste`, `Initiale`, Glas-Blatt für Bottom Sheets.
2. **Rezepte:** Liste als zweispaltiges Raster, Karte nach Entwurf; Detail mit Karten und Fußleiste.
3. **Tagebuch** (Tagesleiste, Ring, Mahlzeitkarten, FAB) und **Heute** (Großkarte, Tagebuchkarte, Einkauf).
4. **Woche:** Tagesleiste, Tageskarten, Planplätze, Fuß.
5. **Einkauf:** Fortschritt, Eingabe-Pille, Kacheln, Katalog in Entwurfsform.
6. **Kochen:** Kopf, Segmente, Schrittkarte mit Timer-Kasten, „Als nächstes“.
7. **Einstellungen, Zutaten, Server verbinden, Blätter:** Feinschliff.

Jeder PR nimmt die Roborazzi-Bilder seines Bereichs neu auf und vergleicht sie mit dem Entwurfsbild. Danach
`abgleich.md` und `docs/architecture.md` nachziehen; die Aussage „umgesetzt“ in README/Aufräumplan korrigieren.

## Abstimmung

Mit der Session „Offene Aufgaben“ (Fotoerkennung, `ToDoS/foto-erkennung-plan.md`) abgesprochen: Sie fasst
`:core:designsystem` und `:feature:heute` bis zum Ende dieses Umbaus nicht an; ihre sichtbaren Teile (Foto-Knopf im
Eintragen-Blatt, Einstellungen) kommen danach. Die Reihenfolge oben bleibt deshalb ohne Rücksicht auf die KI-Hilfe.

## Ergebnis

Umgesetzt in sieben PRs, alle mit neu aufgenommenen Referenzbildern:

| PR | Bereich | Inhalt |
|---|---|---|
| #180 | Designsystem | Leiste mit Pille und auf die Pille begrenztem Druckeffekt, Chips gewählt in Primär, `Seitenkopf`, `RunderKnopf`, `FoodyFab`, `Suchfeld`, `Tagesleiste`, `Initiale` |
| #181 | Rezepte | zweispaltiges Raster (`Adaptive(140 dp)`), Seitenkopf mit „+“-Menü, Glas-Suche; Detail mit runden Knöpfen im Bild, Kopfkarte und Karten für Zutaten, Zubereitung, Notizen |
| #182 | Woche | Seitenkopf mit Pfeilen, Tagesleiste springt zum Tag, Tageskarten mit Planplätzen je fehlender Mahlzeit, Fuß „x von 28 geplant“ |
| #183 | Einkauf | Fortschritt, Eingabe-Pille, Kacheln mit Rand und Haken, Katalog mit Initialen |
| #184 | Kochen | Kopf mit „Schritt n von m“, Segmente, Schrittkarte mit Timer-Kasten, „Als nächstes“ |
| #185 | Tagebuch, Heute | Tagesleiste, Ring mit Makrobalken, Mahlzeitkarten, FAB; auf Heute die geplante Mahlzeit hervorgehoben mit „Gegessen?“ |
| #186 | Einstellungen, Zutaten, Server, Rezept-Editor | Seitenkopf überall, Farbraster ohne Loch in der letzten Reihe, Glas-Suche und FAB im Zutatenkatalog |

Abweichungen vom Entwurf, bewusst:

- **Heute:** Der Einkaufsabschnitt erscheint nur mit offenen Artikeln, die Großkarte nur mit Plan oder Inspiration.
  Auf einem Gerät ohne Daten fehlen sie also, das ist kein Fehler.
- **Tagebuch:** Die Makrobalken zeigen den Anteil an der Energie des Tages, weil es keine Makroziele gibt.
- **Rezeptdetail:** „Kochen“ sitzt in der Kopfkarte statt in einer eigenen Fußleiste. Die Navigationsleiste liegt
  ohnehin unten.

Nachtrag (#188): Alle Bottom Sheets laufen über `GlasBlatt` (Grund, Verlauf und DICHT-Tönung selbst gezeichnet, weil das
Blatt in einem eigenen Fenster liegt und keine Unschärfe-Quelle hat; oben 32 dp rund, Lichtkante, eigener Griff mit den
TalkBack-Aktionen des Material-Griffs).
