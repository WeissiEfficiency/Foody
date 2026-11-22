# ADR 0021: Eintragen als eigenes Ziel

Status: angenommen (2026-11-21), umgesetzt 2026-11-22 (#297–#300). Ändert einen Punkt von ADR 0020: Eintragen hat
keine eigene Form je Stil mehr (Blatt oder Seite, Reiter, Knopfwörter). Spec:
`docs/superpowers/specs/2026-11-21-eintragen-neu-design.md`, Plan: `docs/superpowers/plans/2026-11-21-eintragen-neu.md`,
Ergebnis: `ToDoS/stile-plan.md` („Teil B“).

**Kontext:** Eintragen ist das, was der Nutzer am häufigsten tut. Bis 3.9.0 bauten Heute und das Tagebuch es je selbst
ein (`EintragenBlatt`), und jeder Stil zeigte es anders: Milchglas als Blatt mit den Wegen „Rezept · Zutat · Plan ·
Frei“, die Stile Q–V mit Reitern „Suchen · Scannen · Foto · Geplant“, Sage, Anime und Edo als Blatt, Bayrisch, Grauen
und Kreuzer als Seite, dazu eigene Knopfwörter („Siegel“, „Deuten“, „Ins Bordbuch“). Die Mahlzeit stand als
Chip-Reihe ganz oben, für ein Foto brauchte es drei Tipps. Die Widgets „KI-Foto“ und „Scannen“ öffneten erst Heute mit
dem Blatt (`Schnellstart`), Kamera oder Scanner kamen erst danach; das Blatt blitzte also kurz auf. Der Nutzer wollte
erst erfassen (Foto, Scan, Suche) und dann erst Name und kcal sehen, in allen Stilen gleich.

**Entscheidung (Ansatz 1 der Spec):**
- **Ein Ziel in der Navigation:** `Eintragen(datum, mahlzeit?, sofort?, auftrag)` in `Ziele.kt`, wie `RezeptDetail` oder
  `Kochen`. Heute mit seinen Szenen, das Tagebuch und die Widgets navigieren dorthin, keiner baut Eintragen mehr selbst
  ein. Heute und Tagebuch liegen beide in `:feature:heute`, die Widgets in `:app`; es gibt kein neues Modul.
- **Eine Seite, gleich in allen Stilen:** Vollbild ohne Navigationsleiste, in allen sieben Stilen und beiden Varianten
  mit demselben Aufbau. Der Stil zeigt sich nur in Farben, Schrift, Formen und im Titel; die Knöpfe heißen überall
  „Foto“, „Scannen“ und „Eintragen“.
- **Zwei Schritte in einem ViewModel** (`EintragenViewModel`): Erfassen (Foto, Scannen, Suche, Geplantes, „Nicht dabei?
  Frei eintragen“), dann Bestätigen (je nach Quelle Portionen, Menge, Packung, Foto oder freie Felder, darunter die
  Mahlzeit über `FoodyMenue`, unten „Eintragen“). Zurück führt von Bestätigen nach Erfassen und von dort zur Herkunft.
- **Sofort-Start:** Mit `sofort` (`FOTO` oder `SCANNEN`) öffnet die Seite gleich Kamera bzw. Scanner und bleibt leer,
  bis ein Ergebnis da ist; ein Abbruch schließt sie. Die Knöpfe „Scannen“ und „Foto“ in den Heute-Szenen und die
  Widgets öffnen dafür nur das Ziel mit `sofort`.
- **Bewusst anders als die Skizze von Ansatz 1:** Die Spec skizziert, dass Foto und Scannen beim Auslöser starten
  (Knopf in Heute oder Tagebuch, bei Widgets die `MainActivity`); wo die Launcher liegen, hat sie dem Umsetzer
  überlassen. Gebaut ist es anders: Die Seite hält die Launcher für Kamera und Galerie (`rememberAufnahme` in
  `Aufnahme.kt`), der Strichcode-Scanner läuft über `ScanLauf` im ViewModel (`EintragenViewModel.oeffnen`). Grund: So
  gibt es eine Stelle, an der die Launcher angemeldet werden, statt je eine in Heute, im Tagebuch, in jeder Szene und
  in der `MainActivity`, und nur diese Stelle muss ein Prozessende überstehen (das Kameraergebnis kommt nach dem
  Neustart bei der Seite an, die Anfrage liegt im `SavedStateHandle`).

**Verworfen:**
- *Ansatz 2, gleicher Ort mit neuem Inhalt:* Eintragen bleibt in Heute und im Tagebuch eingebaut, wird überall eine
  Seite mit den zwei Schritten, und die Kamera startet beim Öffnen. Das wäre weniger Umbau gewesen, aber die Seite
  blitzt vor der Kamera kurz auf, und genau das störte bei den Widgets.
- *Ansatz 3, Erfassen als Blatt und Bestätigen als Seite:* zwei Formen in einem Ablauf, das widerspricht „gleich in
  allen Stilen“.

**Folgen:**
- Heute und Tagebuch haben kein eingebautes Blatt mehr; `EintragenBlatt`, `FotoErkennungKnopf` und `Schnellstart` sind
  gelöscht. Sage, Anime und Edo verlieren die Blattform, Q–V die Reiter, alle Stile ihre Knopfwörter beim Eintragen. Der
  Titel bleibt je Stil („Mahl eintragen“, „Eintragen!“, „Eitragn“, „Notieren“).
- Die Heute-Szenen mit einem kombinierten Knopf (Edo „Scannen · Foto“, Sage „+ SCANNEN · FOTO“, Bayrisch „Scannen oder
  Foto“) haben jetzt zwei Knöpfe „Scannen“ und „Foto“, die je direkt starten. „Foto“ erscheint nur, wenn die
  Fotoerkennung eingeschaltet ist und ein Modell hat.
- Die Widgets „KI-Foto“ und „Scannen“ öffnen Kamera bzw. Scanner direkt. Beim Kaltstart bleibt Heute unsichtbar, bis
  Eintragen obenauf liegt.
- Ein Ausnahmefall bleibt: Stirbt der Prozess, während der Google-Code-Scanner offen ist, kommt dessen Ergebnis nicht
  an, weil der Rückruf im alten Prozess hing. Nach dem Neustart steht der Nutzer in Erfassen und scannt neu. Ein
  eigener Scanner ist nicht gewollt (ADR 0008).
- Daten, Schema, Sync und Server bleiben, wie sie sind; gespeichert wird wie vorher. Kein Server-Rebuild.
