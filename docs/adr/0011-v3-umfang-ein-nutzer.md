# ADR 0011: V3 – Funktionsumfang nach Review, ein Nutzer, schlanker Sync, KI über den Server

Status: angenommen (2026-10-17). Löst ADR 0009 teilweise und ADR 0010 weitgehend ab. Plan: `ToDoS/v3-plan.md`,
Abnahme: `ToDoS/v3-abnahme.md`.

**Kontext:** Der v2-Plan (ADR 0009/0010) wollte App, Web-App und Sync mit gleichem Funktionsumfang neu bauen. Ein
Review aller Funktionen (Fragenkatalog „Foody V3 Review“, 2026-10-17) hat ergeben:
- Foody nutzt **eine Person** auf **zwei Geräten** (Handy und Tablet). Haushalte, Einladungen und Geräteverwaltung
  werden nicht gebraucht.
- Die **Web-App** wird nicht benutzt; Rezepte, Plan und Einkauf laufen nur in der App.
- Der **Vorrat** wird nicht gepflegt. Alles, was auf ihm aufbaut, rechnet mit falschen Zahlen.
- Im Alltag laufen Einkauf, Kochen und Markdown-Import gut. Beim Planen sind die **kcal je Portion unzuverlässig**
  und die Auswahl von „Leere Tage füllen“ passt schlecht.
- Gewünscht ist **KI-Hilfe**: Rezepte erfassen und anpassen, Wochenplan vorschlagen, Nährwerte schätzen.

**Entscheidung:**
- **V3 statt v2:** Der Neubau aus ADR 0009 baut nur die im Review bestätigten Funktionen (`ToDoS/v3-abnahme.md`).
  Qualität vor Tempo. Die v1 wird bis zum Umstieg nicht mehr verändert.
- **Gestrichen:** Vorrat mit MHD, „Aus dem Vorrat“, Abbuchen bei „Gekocht“, Vorratsabzug beim Einkauf, Liste als Text
  teilen, Web-App (`:server-web`), Haushalte/Einladungen/Geräteliste, Sterne-Bewertung, die Planer-Modi 1/2/3/7 Tage,
  die zweite Einkaufsansicht.
- **Sync schlank:** Das bestehende Push/Pull-Protokoll bleibt (Outbox → `POST /sync/push`, Cursor →
  `GET /sync/pull`, Fotos inhaltsadressiert). Neu: ein Konto statt Haushalten; **Last-Writer-Wins je Datensatz** wie
  heute (Einkaufseinträge weiter feldweise); kein Haushaltsdienst, keine Hybrid Logical Clock, keine Stempel je Feld in
  Room. Sync beim Öffnen/Fortsetzen der App und nach lokalen Änderungen, kein periodischer Hintergrund-Sync.
- **KI nur über den eigenen Server:** Die App ruft `POST /ki/…` am Foody-Server auf; nur der Server kennt den
  Schlüssel des Sprachmodell-Dienstes. Jedes Ergebnis erscheint als bearbeitbare Vorschau und wird erst nach
  Bestätigung gespeichert. Ohne Server oder Netz sind die KI-Knöpfe ausgeblendet, alles andere funktioniert weiter.
- **Navigation:** vier Tabs – Heute · Rezepte · Woche · Einkauf. „Küche“ entfällt mit dem Vorrat; der Zutatenkatalog
  wandert in die Einstellungen, der Scan bleibt im Tagebuch und im Katalog.
- **Design nur in Compose:** Ohne Web-App entfällt die gemeinsame Token-Datei mit Generator; `:core:designsystem` ist
  die einzige Quelle. Glas-Optik und Kacheln bleiben (ADR 0009).
- **Module zusammengefasst:** `:core:scan` und `:core:timer` gehen in ihre Features; Server ohne `:server-web` und
  `:server-dienst`.
- **Verteilung:** Signierte Release-APK wird in den OpenCloud-Ordner gelegt und von dort installiert.

**Verworfen:**
- *v2 wie geplant, Ausbau danach:* hätte gestrichene Funktionen noch einmal neu gebaut.
- *ADR 0010 (Haushaltsdienst, feldweises Zusammenführen):* war vor allem für die Web-App und mehrere Personen nötig.
  Bei einer Person auf zwei Geräten sind gleichzeitige Änderungen am selben Rezept selten; Last-Writer-Wins reicht.
- *Sync ohne eigenen Server über OpenCloud-Dateien:* kein Server zu betreiben, aber Konflikte über Dateien sind
  schwerer zu lösen und die KI bräuchte einen Schlüssel auf dem Gerät.
- *Vorrat auf „Grundvorrat“ reduzieren:* die Vorschau beim Erzeugen der Einkaufsliste (Vorhandenes abwählen) deckt
  denselben Zweck ab.

**Folgen:**
- Die Migration Room 9 → 10 entfernt die Vorratstabelle. Vorratsdaten gehen verloren (Entscheidung im Review); die
  ZIP-Sicherung vor dem Umstieg enthält sie noch.
- Die Server-Datenbank wird einmalig umgewandelt: der Haushalt wird zum Konto, Vorrats-Datensätze werden gelöscht,
  Einladungen und Geräteeinträge entfallen. App v1 kann danach nicht mehr synchronisieren.
- Domänenregeln zum Vorrat (Regeln 8, 17, 21 in `docs/domain-rules.md`) werden angepasst oder gestrichen.
- Rezeptdaten gehen zur KI-Verarbeitung an einen externen Dienst – nur auf ausdrücklichen Knopfdruck, nur das
  betroffene Rezept bzw. Foto. Der Datenschutzhinweis in der App nennt das.
- ADR 0006 bleibt für Opt-in, Konto, HTTPS und Fotos gültig; Haushalte entfallen.

Der Punkt zu `:core:scan` ist durch ADR 0013 ersetzt.
