# Kosmisches Grauen: zwei immersive Gesichter – Entwurf

Status: Entwurf (2026-11-25), Richtung vom Nutzer bestätigt. Entscheidung: ADR 0022. Bilder liegen in
`2026-11-25-grauen-zwei-gesichter/` (Necronomicon: Rezept, Heute, Woche; Ominös: Rezept).

## Absicht

Das Kosmische Grauen soll mehr nach Lovecraft aussehen, als altes Buch: Seiten, Tinte, Siegel. Der Nutzer will dabei
das ominöse Grün behalten. Statt „Immersiv“ und „Ruhig“ bekommt das Grauen deshalb **zwei immersive Gesichter**:

- **Ominös** auf dem Platz von „Ruhig“: das grüne Grauen, wie es ursprünglich gedacht war (Sternenhimmel, Glut-Grün,
  Auge, Mondbogen), mit allem, was in Runde 2 dazukam (Buchblätter, Almanach, Regal, Buchschmuck).
- **Necronomicon** auf dem Platz von „Immersiv“: die ganze App als verwittertes Buch.

Eine Variante „schlicht“ gibt es beim Grauen nicht mehr. Die anderen Stile bleiben, wie sie sind.

## Was beide Gesichter teilen

- **Aufbau wie in allen Stilen:** die schwebende Leiste unten, dieselben Seiten, dieselben Abschnitte und Reihenfolgen.
  Kein Daumenregister und kein Ledereinband als Rahmen (Runden 1–3 verworfen: zu viel Platz, nicht einheitlich).
- **Bedienelemente ohne eckige Kästen:**
  - Zurück als „↶ Bibliothek“ (Pfeil mit Schwung und Ziel), das Menü als „∴“.
  - Portionen als „Für 4 Personen“ mit − und + als Zeichen.
  - Reiter als Kapitelwörter mit einem Schwung unter dem aktiven.
  - Die Hauptaktion je Seite als **Wachssiegel** mit Zeichen (Kerze für Kochen, Auge für Füllen) und kursivem Text
    daneben. Weitere Aktionen sind kleine gezeichnete Symbole (Schriftrolle = Liste, Lesebändchen = Merken,
    Feder = notieren).
  - „›“-Pfeile werden zur Zeigehand ☞, Aufklappen zu ▾/▸.
  - Jede Tippfläche bleibt mindestens 48 dp groß, auch wenn man ihren Rand nicht sieht.
- **Schmuck:**
  - Trennlinien mit einem Auge in der Mitte statt Karten.
  - Initialen in Fraktur (`UnifrakturFamilie`) bei Rezepttitel und nächstem Gericht.
  - Randnotizen mit Feder.
  - Das „Älteste Zeichen“ (verzweigter Stern im Kreis) blass als Wasserzeichen, an einer Tentakel-Ranke am Rand.
  - Seitenzahlen in römischen Ziffern.
- **Schriften wie bisher:** IM Fell (Text), IM Fell Kapitälchen (Titel, Reiter), Special Elite (Zahlen, Daten); neu
  Fraktur für große Titel im Necronomicon.
- **Keine Akzentfarbe zur Wahl.** Die vier Akzente (Glut, Rosé, Blau, Lila) entfallen beim Grauen; Ominös ist immer
  Glut-Grün, Necronomicon hat feste Tintenfarben.

## Ominös (auf dem Platz von „Ruhig“)

- **Grund:** Nachtgrün mit Sternen (`Hintergrundmuster.STERNE`), wie das heutige Immersiv.
- **Farben:** Text `#DFE4D6`, Nebentext `#8A9C8F`, Glut `#86C49C` für Akzente, Rosé `#C99AA8` für Mengen und Notizen,
  Linien `#2C4439`. Siegel in dunklem Grün.
- **Szenen:** alles, was heute „Grauen Immersiv“ ist (Auge, Mondphasen, Almanach, Regal, Logbuch, Buchblätter), mit
  den neuen Bedienelementen.
- **Leiste:** dunkelgrün, der aktive Punkt mit Glut-Strich darüber.

## Necronomicon (auf dem Platz von „Immersiv“)

- **Grund:** verwittertes Pergament über die ganze Seite. Fasern, Flecken, Schimmel und ein Becherrand liegen als
  Textur darunter, die Ränder sind dunkel bis verkohlt. Text liegt nie auf starkem Muster: Die Textur ist leise genug,
  dass jede Schrift 4,5:1 hält (gemessen gegen den dunkelsten Fleck unter Text).
- **Farben, bewusst nicht weihnachtlich** (Runde 3 war zu rot-grün-beige):
  - Tinte `#17110B`, Nebentext `#4A3D2A`.
  - Getrocknetes Blut `#5C2318` für Mengen, Initialen und Rubriken.
  - Grünspan `#3D5446` nur sparsam.
  - Kein helles Rot, kein sattes Grün, keine hellen Flächen.
- **Siegel:** schwarzes Wachs mit mattem Messing (`#B9A77C`).
- **Leiste:** dunkles Leder mit mattem Messing; der aktive Punkt mit Messing-Strich darüber.
- **Woche:** Monde als Federzeichnung mit schraffiertem Schatten, Neumond gestrichelt, heute mit Blutring.
- **Heute:** Ring als Tintenkreis; Titel „Heute“ in Fraktur, darunter „Liber I · Vom Tage“.
- **Statusleiste:** dunkle Symbole auf hellem Grund (der Grund ist hell genug).

## Einstellungen

- Unter „Variante“ heißen die beiden Segmente beim Grauen **„Ominös“** und **„Necronomicon“**, bei allen anderen
  Stilen weiter „Immersiv“ und „Ruhig“. Gespeichert wird weiter `RUHIG` bzw. `IMMERSIV`: Wer heute „Ruhig“ hat, sieht
  danach Ominös, wer „Immersiv“ hat, sieht das Necronomicon.
- Die Zeile „Farbe“ entfällt beim Grauen. Ein gespeicherter Akzent bleibt in den Prefs, wird aber nicht gelesen.

## Widgets

Ominös übernimmt die heutigen Grauen-Widgets (grün). Das Necronomicon bekommt Pergament als Bitmap-Grund, Tinte und
Messing; Fraktur gibt es dort nicht, weil `RemoteViews` keine Schriften der App laden.

## Nicht Teil davon

Andere Stile, Funktionen, Daten, Server. Kein Server-Rebuild.

## Prüfung

- Kontrast: `KontrastTest` für beide Gesichter (Text auf Grund, auf Siegel, auf der Leiste).
- Bilder: je Feature `stil_grauen_*` für beide Gesichter, Handy und Tablet, bis 130 % Schrift.
- Auf dem Handy: jede Seite in beiden Gesichtern einmal ansehen (Debug-App).
