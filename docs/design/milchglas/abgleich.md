# Abgleich mit dem Code Review – 1. November 2026

Der Design-Branch (Farbwelten und Milchglas, ADR 0015) ist mit `main` bis #164 zusammengeführt. Die Änderungen aus dem
Code Review und dem Aufräumplan sind die Grundlage; Farbwelt, Glas und Formen kamen obendrauf. Mit der Aufräum-Session
abgestimmt (Nachrichten zwischen den Chats). Der Nutzer hat gesagt: selbst durcharbeiten, nicht auf Freigabe warten,
dokumentieren.

## Zusammenführung im Code

| Stelle | Aus dem Code Review | Gelöst |
|---|---|---|
| `FoodyNavigation` | #153: Abstände über `FoodyAbstaende`, Leiste `RoundedCornerShape(34.dp)` | Abstände aus #153, Form `FoodyFormen.leiste` (39 dp, Rund) |
| `Rezeptkarte` | #153: Abstände über `FoodyAbstaende` | Abstände aus #153; Herz `herzFarbe(farbwelt)`, Platzhalter aus der Farbwelt |
| `Einstellungsschluessel` | toter Schlüssel `EINKAUF_ANSICHT` entfernt | entfernt lassen, `FARBWELT` neu |
| `EinstellungenAbschnitte` | #153: `FoodyAbstaende`; #145: nicht Übertragenes unter Konto | beides übernommen; Darstellung mit Farbe und Modus |
| `EinstellungenScreen` | #162: Tagesziel speichert erst bei „Fertig“ | übernommen; Darstellungsabschnitt mit Farbwelt |
| `EinstellungenViewModel` | #147: `SolangeAppOffen` statt `Eagerly` | `SolangeAppOffen`, Farbwelt im selben Fluss |
| `FarbweltWahl` (neu) | Regel aus #153: Abstände nur über `FoodyAbstaende` | `s1`/`s3` statt `4.dp`/`12.dp` |
| `docs/architecture.md` | Specs archiviert, `FoodyAbstaende`-Regel, `FoodyBreiten` | beides in einem Absatz |
| Roborazzi | #148 neue Rezeptdetail-Bilder | alle Bilder nach dem Merge neu aufgenommen |

## Entwürfe angeglichen

- **Rezeptdetail (#148):** Im Kopf steht neben „kcal / Portion“ auch „… kcal für N Portionen“; bei einer Portion
  entfällt die Angabe.
- **Farben:** Der Canvas verwendet jetzt die Werte aus `Farbwelt.kt`. Der Nebentext im hellen Modus ist dunkler,
  Heidelbeere dunkel hat Primär `#8D91F2`, Safran und Spur entsprechen der App.
- **Verlauf:** nur noch die zwei Flecken der App, oben links und unten rechts. Die beiden zusätzlichen sind entfallen.
- **Einstellungen:** Der Abschnitt heißt wie in der App „Darstellung“. Der Hinweis „Wird mit dem Tablet abgeglichen“
  ist entfallen, die Farbwahl wird nicht synchronisiert (ADR 0015).
- Alle 20 Bilder wurden neu gerendert, der Canvas „Foody Design“ ist neu veröffentlicht.

## Bewusst nur in der App (nicht im Entwurf)

- Konto: Liste „nicht übertragen“ in Fehlerfarbe (#145). Sie erscheint nur, wenn ein Abgleich gescheitert ist.
- Rezeptkopf mindestens 260 dp und mit dem Namen wachsend, Pfeil und Name untereinander (#150). Der Entwurf zeigt den
  Normalfall.
- TalkBack-Überschriften, Auswahl und Links (#162) sind unsichtbar.

## Abgesprochen mit der Aufräum-Session

- Q1, Q2 und Q4 sind mit #153 erledigt; an Farben, Formen und Glaswerten ändert die Aufräum-Session nichts.
- C8 (v1-Sicherungen), E7 (Linter-Baseline) und Q7 (`:baselineprofile`) laufen unabhängig. Sie berühren
  `DarstellungAbschnitt` und den Einstellungszustand nicht.
- **Q8** (Fraunces auf Latein beschneiden) und **E5** (Referenzbilder kleiner aufnehmen) kommen erst nach dem Merge
  dieses Branches, damit alle Bilder nur einmal neu aufgenommen werden.
