# ADR 0013: `:core:scan` für Tagebuch und Zutatenkatalog

Status: angenommen (2026-10-26). Spec: `docs/superpowers/specs/2026-10-26-v3-phase6e3-scan-design.md`. Ändert
einen Punkt von ADR 0011.

**Kontext:** ADR 0011 hat `:core:scan` aufgelöst: Der Scan sollte in sein Feature wandern, laut v3-Plan in
`:feature:heute`, und der Katalog sollte ihn über eine schmale Schnittstelle in `:core:data` nutzen. Seit 6e2 liegt der
Zutatenkatalog aber in einem eigenen Modul, `:feature:zutaten`. Beide Features brauchen denselben Scan: Ablauf, Open
Food Facts, ML Kit, die Einstellung und den Knopf mit Kamera und Galerie. Features dürfen sich nicht kennen.

**Entscheidung:** Der Scan bekommt ein eigenes Modul `:core:scan`. Darin liegen die Schnittstellen und der Ablauf
(`PackungScan`), der Open-Food-Facts-Client, die Play-Dienste-Anbindung, die Einstellung „Online-Produktsuche“ und der
Compose-Knopf `PackungScanKnopf`. `:feature:heute`, `:feature:zutaten` und `:feature:einstellungen` hängen davon ab;
`:core:scan` selbst hängt nur von `:core:data`, `:core:designsystem` und `:domain` ab. Der Werte-Typ
`Packungswerte` liegt in `:core:data`, weil die Repositories dort ihn zum Speichern brauchen. `:core:scan` liefert
ihn nur; so entsteht kein Zyklus.

**Verworfen:**
- *Scan in `:feature:heute`, nur eine Schnittstelle in `:core:data`:* Der Zutateneditor hätte keinen eigenen Knopf
  bekommen, oder der Knopf hätte doppelt entstehen müssen.
- *Scan in `:core:data`:* Kamera, Play-Dienste und Compose gehören nicht in die Datenschicht.

**Folgen:** Es gibt ein `:core`-Modul mehr; die Play-Dienste-Bibliotheken hängen nur an `:core:scan`. Der Punkt
„`:core:scan` geht in sein Feature“ aus ADR 0011 gilt nicht mehr; für `:core:timer` bleibt er bestehen.
