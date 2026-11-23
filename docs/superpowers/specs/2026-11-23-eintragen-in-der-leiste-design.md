# Eintragen in der Leiste – Entwurf

Status: Entwurf (2026-11-23)

## Absicht

Eintragen soll von jeder Seite aus mit einem Tipp erreichbar sein: aus Rezepte, Woche und Einkauf, nicht nur aus Heute
und dem Tagebuch. Dafür kommt ein fünfter Punkt „Eintragen“ in die Leiste, zwischen Heute und Rezepte (Wunsch des
Nutzers).

## Ansätze

- **Knopf in der Leiste (gewählt):** Ein Tipp öffnet die bekannte Eintragen-Seite (Vollbild, Erfassen) für heute. Nach
  dem Speichern oder mit Zurück ist man wieder dort, wo man war, zum Beispiel im Rezept. Der Punkt wird nie markiert.
  Das ist wenig Umbau und passt zu „schnell etwas eintragen und weiter“.
- **Echter Reiter (verworfen):** Die Leiste bliebe sichtbar und der Punkt markiert. Eintragen verlöre das Vollbild
  (ADR 0021), und offen wäre, wohin Speichern führt. Das bedeutet mehr Umbau für keinen erkennbaren Gewinn.

## Entwurf

- **Leiste und Rail:** In beiden stehen fünf Punkte: Heute · Eintragen · Rezepte · Woche · Einkauf. Der Punkt
  „Eintragen“ hat das Symbol `Icons.Outlined.AddCircleOutline`.
- **Tippen:** öffnet `Eintragen` mit dem heutigen Datum und ohne Mahlzeit. Die Mahlzeit richtet sich wie bisher nach
  der Tageszeit bzw. dem Menü. Das läuft über `eintragenOeffnen` mit dem Doppeltipp-Schutz aus #301.
  Der Stapel des aktuellen Reiters bleibt; Eintragen liegt obenauf.
- **Markierung:** Der aktive Punkt bleibt der Reiter, zu dem der Stapel gehört. „Eintragen“ ist nie aktiv; während der
  Seite ist die Leiste ohnehin weg.
- **Wörter je Stil:** Wo ein Stil eigene Wörter in der Leiste hat, bekommt auch Eintragen eins. Meine Wahl: Bayrisch
  „Eitragn“ (wie der Seitentitel), Sternenkreuzer „Erfassen“ (sein Tagebuch heißt schon
  „Bordbuch“). Sonst steht „Eintragen“ da; die Beschreibung für TalkBack ist immer
  „Eintragen“.
- **Platz:** Bei 360 dp und Schrift 90–130 % passt jedes Wort in einer Zeile, ohne Abschneiden. Ein Test sichert das
  ab; 200 % zählt nicht. Passt „Eintragen“ bei 130 % nicht, wird die Schrift der Leiste so angepasst, dass alle fünf
  Wörter passen. Abgekürzt wird nicht.
- **Unverändert:** die Wege aus Heute, Tagebuch und Widgets sowie die Seite selbst.

## Nicht-Ziele

- kein eigener Reiter mit sichtbarer Leiste auf der Eintragen-Seite;
- kein Tag aus dem Tagebuch als Datum (die Leiste trägt immer für heute ein).

## Dem Umsetzer überlassen

- wie die Leiste einen Punkt ohne eigene Route abbildet (z. B. ein Ziel mit Aktion statt Route);
- die Neuaufnahme der Referenzbilder, auf denen die Leiste zu sehen ist.
