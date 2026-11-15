# ADR 0020: Wählbare Stile

Status: angenommen (2026-11-13). Erweitert ADR 0015: Milchglas mit den Farbwelten bleibt der Standard und wird ein
Stil unter sieben. Plan und Ergebnisse: `ToDoS/stile-plan.md`. Entwürfe: Design-Canvas „Foody Design“ (privat), Q bis V
mit je 14 Screens und Widgets.

**Kontext:** Im Design-Canvas sind sechs Stile entstanden, jeder mit eigener Welt: Q Düstere Sage, R Anime, S Edo,
T Bayrisch, U Kosmisches Grauen, V Sternenkreuzer. Der Nutzer wollte sie alle in der App wählen können, nicht nur
einen als Pilot, und zwar so, wie die Entwürfe aussehen: mit Szenen und Wörtern des Stils, auch in Editor,
Einstellungen und auf den Widgets.

**Entscheidung:**
- **Sieben Stile:** `FoodyStil { MILCHGLAS, SAGE, ANIME, EDO, BAYRISCH, GRAUEN, KREUZER }`. Jeder Stil außer Milchglas
  hat zwei **Varianten**: Immersiv (Szenen, Wörter und Zeichnungen des Entwurfs) und Ruhig (Farben, Schrift und Formen
  des Stils im schlichten Layout). Dazu kommt je Stil eine von vier **Akzentfarben** aus dem Entwurf.
- **Nur das Aussehen ändert sich.** Funktionen, Daten und Abläufe bleiben gleich. Jede Szene bekommt denselben
  Zustand und dieselben Aktionen wie der normale Screen, die Logik und ihre Tests gelten für alle Stile.
- **Wörter nur in Immersiv** und nur für Überschriften, Knöpfe, Navigation und Leertexte (`stilText(standard, sage =
  …, …)`). Namen, Mengen, kcal und Fehlermeldungen bleiben wörtlich. TalkBack liest immer die normalen Begriffe.
- **Fester Grund je Stil:** Die Entwürfe kennen keinen Wechsel zwischen Hell und Dunkel. Den „Modus“ gibt es nur bei
  Milchglas, die Farbwelten auch. Die Systemleisten folgen dem Grund des Stils.
- **Ein Modul:** Alles Gemeinsame liegt in `:core:designsystem/stil/` (`StilWerte`, `StilTexte`, Bausteine je Stil
  unter `stil/<stil>/`). Die Szenen liegen im jeweiligen Feature unter `stil/`. Es gibt kein neues Modul, die
  Abhängigkeiten bleiben wie gehabt.
- **Speicher wie die Farbwelt:** Prefs `foody`, Schlüssel `stil`, `stilVariante`, `stilAkzent` (`StilWahl`). Fehlt ein
  Wert oder ist er unbekannt, gilt Milchglas. Kein Sync zwischen den Geräten, kein Server-Rebuild.
- **Kontrast hat Vorrang vor dem Entwurf:** Farben der Entwürfe, die WCAG 2.2 AA verfehlen, werden beim Ableiten
  verschoben, bis sie halten (`angleichen`). `KontrastTest` prüft alle Stile, Varianten und Akzente.
- **Schriften:** Jeder Stil bringt die Schriften seines Entwurfs eingebettet mit (Google Fonts, OFL; Special Elite
  Apache 2.0), beschnitten auf Latein und die Zeichen der App mit `werkzeuge/schriften_beschneiden.py`. Schriften mit
  „Reserved Font Name“ liegen unverändert bei. Budget: höchstens 2 MB mehr APK, nichts wird nachgeladen. Symbole, die
  einer Schrift fehlen (✓, ▶, Emojis), zeichnet Android in der Systemschrift. Die Widgets bleiben bei Systemschriften
  (`RemoteViews` können keine Schriften der App laden).
- **Tablet:** Die Entwürfe gibt es nur für das Handy. Auf dem Tablet bleibt das zweispaltige Layout mit Farben,
  Schrift und Formen des Stils.
- **Widgets:** Die `RemoteViews` übernehmen Farbe, Form, Schrift und die Wörter des Stils. Zeichnungen kommen als
  Bitmaps, Szenen gibt es dort nicht.
- **Keine fremden Marken:** Q und V bleiben allgemeines Genre (dunkle Fantasy, Raumschiff), ohne Namen, Logos oder
  Motive aus Spielen oder Filmen.

**Verworfen:**
- *Nur einen Stil als Pilot ausliefern:* Der Nutzer wollte alle sechs gleich weit haben.
- *Nur Ruhig:* Das wäre viel weniger Pflege gewesen, aber es hätte die Entwürfe nicht getroffen.
- *Eigene Feature-Module je Stil:* Die Szenen brauchen Zustand und Aktionen des Screens. Getrennte Module hätten die
  Logik verdoppelt oder Features voneinander abhängig gemacht.
- *Hell und Dunkel je Stil:* Die Entwürfe sehen das nicht vor, und jeder Stil hätte eine zweite Palette gebraucht.
- *Schriften nachladen (Downloadable Fonts):* Die App muss offline bleiben (ADR 0001).
- *Sync der Wahl:* Das hätte eine Änderung am Protokoll gebraucht, wie schon bei der Farbwelt (ADR 0015).

**Folgen:**
- Jede künftige Änderung an einem Screen muss bis zu sieben Stile mitziehen. Bei Ruhig passiert das fast von selbst,
  bei Immersiv nicht. Die Screen-Tests prüfen die Szenen. Roborazzi hat je Stil ein Bild jedes Screens in Immersiv,
  „Heute“ auch in Ruhig.
- Die Referenzbilder von Milchglas sind gleich geblieben. Nur bei den Einstellungen kam die Stilwahl dazu.
- Alle Schriften der Stile zusammen 1,78 MB in `res/font` (vorher 0,98 MB für Cinzel und EB Garamond unbeschnitten).
- Offen: das Gegenlesen der Mundart (T).
