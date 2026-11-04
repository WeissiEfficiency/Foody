# ADR 0018: BLS-Auszug zum Nachschlagen

Status: angenommen (2026-11-03). Spec: `docs/superpowers/specs/2026-11-03-bls-nachschlagen-design.md`.

**Kontext:** Wer eine Breze, eine Semmel oder ein Fleischpflanzerl ins Tagebuch einträgt, findet sie nur, wenn sie
schon im Zutatenkatalog stehen; sonst muss er die Nährwerte selbst eintippen oder eine Packung scannen. Der
Bundeslebensmittelschlüssel 4.0 des Max Rubner-Instituts (gut 7.000 Lebensmittel, auch fertige Gerichte) ist seit
Dezember 2025 frei unter CC BY 4.0. Die Startdaten nutzen ihn schon als Quelle für einzelne Zeilen.

**Entscheidung:** Ein Auszug des BLS (Code, Name, kJ, Eiweiß, Kohlenhydrate, Zucker, Fett, Ballaststoffe, Salz je
100 g) liegt als Asset `bls/bls_4_0.tsv` in `:core:data` (≈ 540 KB, im APK komprimiert ≈ 160 KB), erzeugt mit
`werkzeuge/bls_auszug.py` aus der amtlichen Excel-Datei. `BlsNachschlag` lädt ihn beim ersten Suchen in den Speicher;
die Suche (`BlsSuche` in `:domain`) kennt bairische Namen und zerlegt zusammengesetzte wie „Leberkassemmel“. Erst wenn
der Nutzer einen Treffer antippt, entsteht eine normale Katalog-Zutat mit der Quelle „BLS 4.0 (MRI): <Name>“; nur sie
wird synchronisiert. Der Herausgeber steht in den Einstellungen unter „Zutaten“.

**Verworfen:**
- *Eigene Room-Tabelle:* bräuchte Migration, Sync-Ausnahmen und Sicherungsregeln für Daten, die sich nie ändern.
- *Alle 7.000 als Startdaten in den Katalog:* Der Katalog würde unübersichtlich, jede Zeile ginge in den Sync.
- *Online-Suche (Open Food Facts nach Namen):* braucht Netz und liefert Markenprodukte statt Bäckerware; bleibt eine
  mögliche Ergänzung.

**Folgen:** Eine neue BLS-Version heißt: Skript erneut laufen lassen, Asset und Test (`BlsNachschlagTest`) anpassen.
Regionale Namen, bevorzugte Einträge und Stückgewichte (Näherungen, im Katalog änderbar) stehen in `BlsSuche`.
