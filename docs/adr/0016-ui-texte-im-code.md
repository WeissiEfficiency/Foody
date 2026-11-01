# ADR 0016: UI-Texte im Code statt in `strings.xml`

Status: angenommen (2026-11-01). Aus der Gesamtprüfung, Q6 im Aufräumplan. Hält eine bestehende Praxis fest.

**Kontext:** Android empfiehlt, sichtbare Texte als Ressourcen (`strings.xml`, `stringResource`) abzulegen. Dann
lassen sie sich übersetzen und an Gerätesprache und Pluralregeln anpassen. Foody ist eine deutschsprachige App für einen
Haushalt (ADR 0011). Eine zweite Sprache ist nicht geplant. Die V3 schreibt ihre Texte direkt in den Kotlin-Code: rund
70 `Text("…")` sowie Texte als Parameter. Gebündelt liegen sie in kleinen `…Texte`-Sammlungen
(`ScanTexte`, `EinstellungenTexte`, `FehlerTexte`, `ZutatenTexte`, `EinkaufTexte`, `Texte.kt` in `:core:data`). In
`strings.xml` steht nur `app_name`.

**Entscheidung:** Texte bleiben im Code. Ein Text, den mehrere Stellen oder Tests brauchen, kommt als Konstante oder
Funktion in die `…Texte`-Sammlung seines Moduls. Ein einmal benutzter Text steht dort, wo er angezeigt wird. Zahlen,
Datumsangaben und Mehrzahl formatieren eigene Funktionen (`anzahl`, `datumKurz`, `kcalText`), nicht `plurals`.
`strings.xml` enthält nur, was Android selbst liest: den App-Namen und die Namen der Varianten.

**Verworfen:**
- *Alles nach `strings.xml` umziehen:* Das wären mehrere hundert Stellen und `stringResource` in jeder Composable.
  ViewModels und Repositories bräuchten einen `Context` für ihre Meldungen. Einen Nutzen hätte das erst mit einer
  zweiten Sprache.
- *Nur neue Texte als Ressourcen:* Zwei Wege für dasselbe wären schlechter als ein einheitlicher.

**Folgen:** Tests prüfen Texte direkt über dieselben Konstanten (`ScanTexte.KNOPF`) oder als Klartext. Für eine
Übersetzung müssten alle Texte umziehen; in der Sammlung liegen sie dafür schon gebündelt. Lint meldet keine fest
verdrahteten Texte, weil Compose-Texte nicht unter `HardcodedText` fallen. Ein künftiger Linter (E7) darf diese Regel
deshalb nicht anmahnen.
