# BLS nachschlagen im Tagebuch – Design

Stand: 2026-11-03. Entscheidung: ADR 0018.

## Ziel
Im Tagebuch unter „Zutat“ findet die Suche auch Lebensmittel, die noch nicht im Katalog stehen: „Breze“, „Semmel“,
„Leberkas“, „Fleischpflanzerl“, „Weißwurst“, „Obazda“ – mit Nährwerten aus dem Bundeslebensmittelschlüssel 4.0.
Antippen legt die Zutat im Katalog an und wählt sie; mit bekanntem Stückgewicht steht die Einheit auf „Stück“.

## Ablauf
1. Suche tippen → oben die eigenen Zutaten (Teilstring oder Katalogname: „Semmel“ findet „Brötchen“), darunter
   „Aus dem Bundeslebensmittelschlüssel“ mit Name, kcal je 100 g und Stückgewicht (höchstens 15 Treffer).
2. Treffer antippen → `ZutatenRepository.ausBls`: neue Zutat mit Werten je 100 g, Quelle „BLS 4.0 (MRI): <Name>“,
   Stückgewicht. Heißt schon eine Zutat so, wird diese gewählt (nichts doppelt).
3. Menge eingeben → speichern wie jede Katalog-Zutat (Nährwerte werden festgehalten, ADR 0007).

## Suche (`:domain`, `BlsSuche`)
- Wörter ab zwei Zeichen, ohne Groß/klein; jedes Wort muss im Namen vorkommen (Teilstring).
- Regionale Namen ergänzen ein Wort um ein Suchwort (Breze → „laugengebäck“); das getippte Wort sucht weiter mit.
- Rang: bevorzugter Eintrag des regionalen Namens, dann Name beginnt mit dem Wort, dann ein Wort im Namen beginnt
  damit; danach kürzere Namen.
- Ein Wort aus zwei bekannten Namen („Leberkassemmel“, „Fleischpflanzerlsemmel“) liefert die Treffer beider Teile.
- Kein Ähnlichkeitsvergleich: Der Nutzer wählt selbst (Invariante 7 bleibt).

## Nicht enthalten
- Zusammengesetztes mit einem Tipp eintragen (Semmel + Leberkäse als ein Eintrag) – dafür ein kleines Rezept oder
  später eine Texteingabe für die Fotoerkennung auf dem Handy („Per Text eintragen“).
- BLS-Suche im Zutateneditor und im Rezept-Editor.
- Getränke je 100 ml: Der BLS gibt alles je 100 g an.

## Tests
`BlsSucheTest` (`:domain`), `BlsNachschlagTest` (echter Auszug, `:core:data`), `EintragenViewModelTest`
(Übernehmen, vorhandener Name, nur im Weg „Zutat“, Synonyme im Katalog).
