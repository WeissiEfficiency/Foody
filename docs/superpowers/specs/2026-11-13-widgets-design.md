# Spec: Widgets aus den Entwürfen (Phase 7 der Stile)

Stand 2026-11-13. Grundlage sind die Widget-Entwürfe Q–V im Canvas (`Q-Widgets` … `V-Widgets`). Die Knopf-Widgets
„Scannen“ und „KI-Foto“ tragen den Stil seit `claude/stile-widgets`. Dieser Spec betrifft die neuen Widgets, die
Daten zeigen.

## Ziel

Fünf neue Homescreen-Widgets, die in allen Stilen aussehen wie die Entwürfe (Milchglas in den Farben des
Hintergrundbilds wie die Knöpfe):

| Widget | Größe | Inhalt | Tipp |
|---|---|---|---|
| Heute | 4×2 | nächste geplante Mahlzeit (Mahlzeit, Titel, Zeit, kcal je Portion), kcal heute / Tagesziel mit Balken; Knöpfe „Kochen“ und „Eintragen“ | Fläche → „Heute“, „Kochen“ → Kochmodus des Rezepts, „Eintragen“ → Eintragen-Blatt |
| Kalorien | 2×2 | Ring „übrig“ (Ziel − heute), ohne Ziel die Summe | Tagebuch von heute |
| Einkauf | 2×2 | offene Artikel der aktuellen Liste: Anzahl und die ersten vier Namen, „+ n weitere“ | die Liste |
| Schnell eintragen | 2×2 | großes „+“, „Suchen · Scannen · Foto“ | Eintragen-Blatt |
| Woche | 4×1 | sieben Tage ab Montag, je Tag das erste Gericht (gekürzt) und ein Punkt; heute hervorgehoben | „Woche“ |

Ohne Daten: Heute zeigt „Nichts geplant“ und nur „Eintragen“; Einkauf „Alles erledigt“; Woche leere Punkte.

## Aussehen je Stil

Wie die Knöpfe (`KnopfAussehen`): Fläche, Schrift, Nebentext, Akzent, Rand, Rundung, Versatzschatten (Anime).
Wörter nur in Immersiv:

- Sage: „ABENDMAHL“, „Vitalität“, „DER HÄNDLER“.
- Anime: „QUEST!“ (Einkauf), Pillen für Knöpfe und Artikel.
- Edo: Tage 月火水木金土日, Stempel 籠 am Einkauf.
- Bayrisch: „Heid auf d’Nacht“, „Eikaufn“, „no n offn“, „Eitragn“.
- Grauen: „Das Ritual des Abends“, „Expeditionsliste“, „Notieren“.
- Kreuzer: „NÄCHSTE MISSION · 19:00“, „Fracht“, „Proviant erfassen“.

Gezeichnete Teile (Ring, Balken, Tagespunkte) entstehen als Bitmap auf einem Canvas (`setImageViewBitmap`); alles
andere sind normale `RemoteViews`. Schriften: Systemschriften wie in der App (Serife, Grotesk, Monospace über
`android:fontFamily`).

## Technik

- **RemoteViews, kein Glance.** Keine neue Abhängigkeit; die Knöpfe zeigen, dass Fläche, Rand, Rundung und Schatten
  so gehen. Listen haben höchstens vier Einträge und brauchen keine Collection.
- **Daten:** `WidgetDatenQuelle` (in `:app`) liest einmal einen Schnappschuss `WidgetDaten` aus den vorhandenen
  Repositories (`HeuteRepository.planHeuteUndMorgen`, `TagebuchRepository.amTag`, `TageszielSpeicher`,
  `HeuteRepository.offenerEinkauf`, Plan der Woche). „Nächste Mahlzeit“ nutzt `naechsteMahlzeit` aus `:feature:heute`.
- **Aktualisieren:**
  1. Solange der Prozess lebt, beobachtet `WidgetBeobachter` (gestartet in `FoodyApp`) die Daten-Flows und
     zeichnet die Daten-Widgets nach einer Änderung neu (gebündelt, höchstens einmal je Sekunde). Das deckt
     Eingaben in der App und den Sync ab.
  2. `updatePeriodMillis` 30 Minuten für Tageswechsel und Uhrzeit (nächste Mahlzeit).
  3. Ein Stilwechsel zeichnet alle Widgets neu (`alleAktualisieren`).
- **Laden im Widget:** `onUpdate` mit `goAsync()` und Coroutine auf IO; Datenbankzugriff über Hilt
  (`@AndroidEntryPoint` auf dem Provider).
- **Links:** vorhanden sind `foody://kochen/{id}?portionen=`, `foody://einkauf/{listeId}`, `foody://tagebuch/{datum}`.
  Neu: `foody://eintragen` (Schnellstart „Eintragen“: Heute mit offenem Blatt) und `foody://woche`.

## Barrierefreiheit

Jedes Widget hat eine Beschreibung mit dem Inhalt („Einkauf: 9 offen, Paprika, Feta …“, „Heute: 860 von 2.000 kcal,
heute Abend Ofengemüse mit Feta“). Knöpfe im Heute-Widget sind eigene Tippflächen mit eigener Beschreibung.
Gezeichnete Teile sind für TalkBack ausgeblendet.

## Nicht im Umfang

- Konfiguration je Widget (welche Liste, welcher Tag).
- Widgets auf dem Sperrbildschirm.
- Eigene Schriften (folgen mit den Schriftdateien, wenn der Nutzer den Download freigibt).
