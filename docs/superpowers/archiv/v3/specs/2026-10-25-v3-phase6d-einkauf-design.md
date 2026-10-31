# V3 Phase 6d: Einkauf

Teil von Phase 6 („Bereiche neu bauen“, Reihenfolge 6a Rezepte → 6b Kochen → 6c Woche → **6d Einkauf** → 6e
Einstellungen). Grundlage: `ToDoS/v3-plan.md` (Phase 6: „Einkauf (eine Ansicht, ohne Vorratsabzug)“) und der Abschnitt
„Einkauf“ in `ToDoS/v3-abnahme.md`, dazu „Rezept direkt auf die Einkaufsliste“ aus dem Abschnitt „Rezepte“.

## Ziel

Die Einkaufsliste der V3 in einem neuen Modul `:feature:einkauf`: eine Kachelansicht für den Laden (offen oben,
„Zuletzt verwendet“ und Katalog darunter), Listen aus dem Wochenplan mit Vorschau und Abwählen, ein Abgleich mit Diff,
wenn sich der Plan danach ändert, manuelle Einträge, Details mit Herkunft und Löschen mit „Rückgängig“. Ersetzt die
Platzhalter des Tabs „Einkauf“ und der Route `EinkaufListe(listeId)` aus Phase 5.

**Fertig, wenn** der Abschnitt „Einkauf“ der Abnahmeliste und „Rezept direkt auf die Einkaufsliste“ abgehakt sind,
kein Screen über 300 Zeilen hat und die Sichtprüfung auf dem Emulator (Liste aus dem Plan mit Serverdaten, im Laden
abhaken, Plan ändern → Hinweis → abgleichen, Rezept auf die Liste) klappt.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Ansicht | Kacheln: offen oben (Antippen = gekauft), „Zuletzt verwendet“, Katalog nach Abteilungen |
| Listen | Die jüngste Liste (neuestes `createdAt`) ist die aktuelle; ältere nur über „Frühere Listen“ |
| Zeitraum „aus dem Plan“ | Vorbelegt heute bis heute+6, Anfang und Ende mit ‹ › verschiebbar, höchstens 28 Tage |
| Schnappschuss | Die Liste ändert sich nie von selbst; Hinweis „Plan geändert – abgleichen“ mit Diff, zusätzlich im Menü |
| Rezept → Liste | Im Rezeptdetail, Portionen per Dialog; landet in der aktuellen Liste |
| Vorrat | Kein Vorratsabzug (Vorrat ist abgewählt) |
| Teilen als Text | Entfällt |
| Logik | `EinkaufRepository` in `:core:data` auf den Regeln aus `:domain`; `:feature:einkauf` ist Oberfläche |
| v1 / Server | Unverändert; DB bleibt v10 |

## 1. Aufbau und Daten

**Vorhanden in `:domain`** (nur anschließen): `EinkaufsBedarf.berechnen` (schließt Nie-Kaufen wie Wasser aus),
`ShoppingDiff.diff`, `EinkaufsAbgleich.bleibtAbgehakt`/`entfaelltLoeschen`, `RezeptAufListe.bedarf`/`zusammenfuehren`,
`EinkaufEingabe.entscheiden`, `ShoppingCatalog` (Abteilungen in Laufreihenfolge, Emoji, `find`, `sectionFor`, `search`,
`sameItem`).

**Speicherung** wie v1, damit Sync und v1 die Daten verstehen:

- Plan-Artikel: `manual = false`, `ingredientId`, `name` = Name der Zutat, `amount`/`unit` = `toBuy` in der
  Basiseinheit seiner Dimension, `category` = Kategorie der Zutat, Herkunft je `NeedSource` als
  `shopping_item_source` (Rezeptname, Datum des Plan-Eintrags, Beitrag in Basiseinheit). Schlüssel eines Plan-Artikels:
  `"$ingredientId|${unit.dimension}"` (= `ShoppingNeed.key`).
- Manuelle Artikel (Katalog, Eingabe, Zuletzt, Rezept → Liste): `manual = true`, ohne Herkunft.
- Name einer Plan-Liste: „26.10.–1.11.“ mit `rangeStart`/`rangeEnd`; eine leer angelegte Liste heißt „Einkauf“ und hat
  keinen Zeitraum.
- Neue Artikel bekommen `sortOrder` = bisher größte + 1; Abhaken ändert `sortOrder` nicht (der Trigger setzt
  `checkedChangedAt`).

**`:core:data`** (`einkauf/`)

- `EinkaufRepository(db, uhr: () -> Long, ignoriert: IgnorierteSchluessel)`:
  - Lesen (Flows): `aktuelleListeId(): Flow<String?>` (neuestes `createdAt`, bei Gleichstand ID);
    `liste(id): Flow<EinkaufsAnsicht?>` (Kopf: Name, Zeitraum, Zahl offen; offene Artikel gruppiert nach Abteilung in
    der Reihenfolge `ShoppingCatalog.sectionOrder`, innerhalb nach `sortOrder`; je Artikel Emoji, Mengentext, Notiz,
    Herkunft); `zuletzt(): Flow<List<ZuletztArtikel>>` (abgehakte Artikel aller Listen, neueste `checkedChangedAt`
    zuerst, einer je `ShoppingCatalog.sameItem`, höchstens 18); `fruehere(): Flow<List<ListenKopf>>` (alle außer der
    aktuellen, neueste zuerst, mit Datum und Zahl der Artikel); `abgleich(listeId): Flow<List<AbgleichZeile>>`.
  - Kacheln: `abhaken(id, an)`; `antippen(listeId?, name, abteilung, ingredientId?)` nach `EinkaufEingabe`-Regel
    (offen → gekauft, gekauft → wieder offen, sonst neu manuell); `eingeben(listeId?, text)` über
    `EinkaufEingabe.entscheiden`. Ohne Liste (`listeId == null`) legt der erste Tipp eine leere Liste „Einkauf“ an und
    gibt ihre ID zurück.
  - Details: `details(id, name, notiz, abteilung)` (Name 1–60 Zeichen getrimmt, sonst `IllegalArgumentException`;
    Notiz leer → `null`); `loeschen(id): GeloeschterArtikel?` (Artikel samt Herkunft, für „Rückgängig“; löscht ein
    Plan-Artikel, kommt sein Schlüssel zu den ignorierten); `wiederherstellen(g)` (dieselbe ID; nimmt den Schlüssel
    wieder aus den ignorierten).
  - Listen: `listeLoeschen(id)` (samt Artikeln über CASCADE; ignorierte Schlüssel der Liste werden entfernt).
  - Aus dem Plan: `planVorschau(von, bis): List<VorschauZeile>` (`EinkaufsBedarf.berechnen` ohne Vorrat, nur
    `toBuy > 0`; je Zeile Schlüssel, Name, Emoji, Abteilung, Mengentext, Herkunftstext);
    `ausPlanAnlegen(von, bis, abgewaehlt: Set<String>): String?` (neue Liste mit allen nicht abgewählten Zeilen und
    ihrer Herkunft; die abgewählten Schlüssel werden ignoriert; die **offenen manuellen** Artikel der bisherigen
    aktuellen Liste wandern in die neue (`listId` ändert sich); `null`, wenn nichts übrig bleibt).
  - Abgleich: `abgleichen(listeId, abgewaehlt: Set<String>)` (siehe 2.).
  - Rezept: `rezeptAufListe(rezeptId, portionen): Int` über `RezeptAufListe` (Erhöhen addiert auf einen offenen
    manuellen Artikel gleicher Zutat und Dimension, sonst neuer manueller Artikel); ohne Liste wird „Einkauf“
    angelegt; Rückgabe = Zahl der betroffenen Artikel; Rezept fehlt → 0.
- `IgnorierteSchluessel` – SharedPreferences-Datei `einkauf`, je Liste ein StringSet `ignoriert_<listeId>`;
  `lesen(listeId)`, `hinzufuegen(listeId, schluessel)`, `entfernen(listeId, schluessel)`, `vergessen(listeId)`.
  Nur lokal; ein anderes Gerät kennt sie nicht.
- `HeuteRepository.offenerEinkauf` / `EinkaufsartikelDao.offeneDerJuengstenListe`: jüngste Liste künftig nach
  `createdAt DESC, id` statt `updatedAt` – Heute und Einkauf meinen dieselbe Liste.

Jede Änderung läuft in einer Transaktion; Artikel und Herkunft erreichen die Outbox über die vorhandenen Trigger
(`shopping_item_source` ist Kindtabelle von `shopping_item`). Fehlt Liste oder Artikel (per Sync gelöscht), bleiben
Schreiben und „Rückgängig“ still; die Prüfung steht in der Transaktion.

**`:feature:einkauf`** (neu; hängt nur von `:core:*` und `:domain` ab)

- `EinkaufViewModel` – `listeId` aus der Route (`null` = aktuelle Liste), Zustand per `combine` aus `liste`, `zuletzt`,
  `abgleich` und Suchtext; offenes Blatt (`Blatt?`: Vorschau, Abgleich, Details(id), Frühere), Zeitraum der Vorschau
  und Suchtext im `SavedStateHandle`. Ein Rechen-Auftrag für die Vorschau: ein neuer Zeitraum bricht den laufenden ab,
  das Ergebnis landet nur im Zustand, für den gerechnet wurde. „Liste anlegen“ und „Übernehmen“ mit Sperre
  (`try/finally`) gegen Doppeltipp. „Rückgängig“ für Löschen.
- `EinkaufScreen`, `EinkaufKacheln`, `VorschauBlatt`, `AbgleichBlatt`, `DetailBlatt`, `FruehereBlatt`.

**`:core:designsystem`**: `Kachel` bekommt `aufDerListe: Boolean = false` (warme Akzenttönung) und
`onLangdruck: (() -> Unit)? = null`; das Katalog-Emoji kommt über den vorhandenen `symbol`-Platz. Vorgabewerte lassen
Heute unverändert.

**`:feature:rezepte`**: `RezeptDetailViewModel.aufEinkaufsliste(portionen)` ruft `EinkaufRepository.rezeptAufListe`
und meldet die Anzahl als Ereignis; Menüpunkt „Auf die Einkaufsliste“ mit Portionen-Dialog.

**`:app3`**: Tab „Einkauf“ → `EinkaufRoute(listeId = null)`; `EinkaufListe(id)` → `EinkaufRoute(id)` (Heute, Deep Link
`foody://einkauf/{listeId}`, „Frühere Listen“); „Ansehen“ nach Rezept → Liste wechselt zum Tab „Einkauf“.

## 2. Abläufe und Sonderfälle

- **Im Laden:** Offene Kachel antippen → gekauft, sie erscheint unter „Zuletzt verwendet“. Zuletzt-Kachel antippen →
  wieder auf der aktuellen Liste: gekauft in der aktuellen Liste → wieder offen; dort schon offen → nichts; sonst neu
  manuell mit Name, Abteilung und Zutat, ohne Menge. Katalog-Kachel: nicht auf der Liste → neu; offen → gekauft; gekauft → wieder offen.
- **Eingabe „Was willst du einkaufen?“:** Tippen filtert auf Katalogtreffer (`ShoppingCatalog.search`) plus
  „„<Text>“ hinzufügen“, solange kein Treffer genau passt; Enter → `EinkaufEingabe.entscheiden`; danach ist das Feld
  leer.
- **Neue Liste aus dem Plan:** Blatt mit Zeitraum ‹ Anfang › bis ‹ Ende ›, Vorschau je Abteilung mit Häkchen,
  „Liste anlegen (n)“ (n = angehakte Zeilen). Danach zeigt der Tab die neue Liste.
- **Abgleich:** `abgleich(listeId)` vergleicht die Plan-Artikel der Liste per `ShoppingDiff` mit dem aktuellen Bedarf
  im Zeitraum der Liste und liefert nur Zeilen, die „Übernehmen“ wirklich ändern würde:

| Diff | Artikel | Zeile | Übernehmen |
|---|---|---|---|
| neu | – | „+ neu“ | neuer Plan-Artikel mit Herkunft |
| mehr / weniger | offen | „↑/↓ alt → neu“ | Menge und Herkunft ersetzen |
| mehr | gekauft, `bleibtAbgehakt` = false | „↑ wieder auf die Liste“ | Menge ersetzen, wieder offen |
| weniger / gleich | gekauft | – (keine Zeile) | – |
| entfällt | offen | „− entfällt“ | löschen |
| entfällt | gekauft | – (keine Zeile) | bleibt |
| beliebig | Schlüssel ignoriert | – (keine Zeile) | – |

  Der Hinweis „Plan geändert – abgleichen ›“ erscheint, solange die Zeilenliste nicht leer ist. Im Blatt ist jede Zeile
  abwählbar; abgewählte Schlüssel werden ignoriert. „Übernehmen (n)“ schreibt alles in einer Transaktion.

| Lage | Verhalten |
|---|---|
| Noch keine Liste | Leerzustand „Noch nichts auf der Liste“ mit „Neue Liste aus dem Plan“; Katalog und Eingabe gehen trotzdem (legen „Einkauf“ an) |
| Im Zeitraum nichts geplant | Vorschau „Im Zeitraum ist nichts geplant“, „Liste anlegen“ aus |
| Alle Zeilen abgewählt | „Liste anlegen (0)“ aus |
| Liste ohne Zeitraum | Kein Abgleich, kein Hinweis, Menüpunkt fehlt |
| Mahlzeit im Zeitraum gelöscht / Rezept geändert | Abgleich zeigt „entfällt“ bzw. „↑/↓“ |
| Rezept im Zeitraum per Sync gelöscht | Sein Bedarf entfällt („− entfällt“) |
| Plan-Artikel gelöscht | Schlüssel ignoriert, kommt beim Abgleich nicht wieder |
| Liste stammt von einem anderen Gerät | Keine ignorierten Schlüssel; dort Gelöschtes kann als „+ neu“ erscheinen |
| Liste/Artikel per Sync gelöscht | Schreiben und „Rückgängig“ still; angezeigte Liste weg → „Diese Liste gibt es nicht mehr“ mit „Zur aktuellen Liste“ |
| „Liste anlegen“/„Übernehmen“ doppelt | Genau einmal |
| Zeitraum | Anfang ≤ Ende, höchstens 28 Tage; ‹ › halten die Grenzen |
| Name in Details leer oder über 60 Zeichen | „Speichern“ aus |
| Rezept → Liste ohne einzukaufende Zutat | Snackbar „Nichts einzukaufen“ |
| Wasser | Nie aus Plan oder Rezept; von Hand eingeben geht |
| „Liste löschen“ | Rückfrage; danach die aktuelle Liste bzw. der Leerzustand |

## 3. Darstellung

- **Kopf:** „Einkauf“, darunter „26.10.–1.11. · 12 offen“ (bzw. „Einkauf · 3 offen“); Menü ⋮ mit „Neue Liste aus dem
  Plan“, „Mit Plan abgleichen“ (nur mit Zeitraum), „Frühere Listen“, „Liste löschen“.
- **Hinweiskarte** „Plan geändert – abgleichen ›“ unter dem Kopf.
- **Eingabefeld** „Was willst du einkaufen?“ (Lupe, Löschen-Knopf).
- **Offen:** Abschnittskopf je Abteilung (Emoji + Name), Kacheln mit `aufDerListe = true`, Name, Menge oder Notiz.
  Antippen = gekauft, Langdruck = Details.
- **„Zuletzt verwendet“** (aufgeklappt) und **Katalog-Abteilungen** samt „Eigene Artikel“ (zugeklappt; Aufklappen
  übersteht Drehen). Kacheln, die auf der aktuellen Liste offen stehen, mit `aufDerListe = true`; gekaufte
  durchgestrichen und gedimmt.
- **Raster:** `GridCells.Adaptive(104.dp)`; Platz unten für die Navigationsleiste.
- **Tablet ab 840 dp:** links offen + Zuletzt, rechts Katalog; jede Seite scrollt für sich.
- **Blätter** (`rememberModalBottomSheetState(skipPartiallyExpanded = true)`, Inhalt scrollbar, Knopf fest unten):
  Vorschau (Zeitraum, Zeilen „☑ 🧅 Zwiebeln · 3 Stk.“ je Abteilung, „Liste anlegen (n)“); Abgleich (Zeilen mit
  „+ / ↑ / ↓ / −“, Häkchen, „Übernehmen (n)“); Details (Name, Notiz, Abteilung als Chips, Menge nur lesend bei
  Plan-Artikeln, Herkunft „300 g Curry (Mo) + 150 g Reispfanne (Mi)“, „Speichern“, „Löschen“); Frühere Listen (Name,
  „angelegt 19.10.“, „14 Artikel“; Antippen öffnet, Löschen mit Rückfrage).
- **Snackbars** mit „Rückgängig“ bzw. „Ansehen“ immer `SnackbarDuration.Long`.
- **TalkBack:** Kachel meldet „auf der Liste“, „gekauft“ oder „nicht auf der Liste“; Langdruck heißt „Details“.
- Hell/dunkel, Schrift bis 200 % ohne abgeschnittenen Text.

## 4. Tests

- `EinkaufRepository` (Room im Speicher, `uhr` fest):
  - Lesen: aktuelle Liste nach `createdAt`, Gruppierung in Laufreihenfolge, Zuletzt über alle Listen (eindeutig,
    höchstens 18, neueste zuerst), Frühere ohne die aktuelle.
  - Schreiben: Abhaken an/aus, Antippen (neu / gekauft / wieder offen), Eingabe bekannt und unbekannt, ohne Liste wird
    „Einkauf“ angelegt, Details (Grenzen), Löschen + Wiederherstellen mit Herkunft und derselben ID, Löschen eines
    Plan-Artikels ignoriert seinen Schlüssel, still nach Sync-Löschung, jede Änderung in der Outbox.
  - Plan: Vorschau ohne Wasser und ohne Vorrat, Anlegen mit Herkunft, abgewählte werden ignoriert, offene manuelle
    Artikel wandern mit (gekaufte nicht), alles abgewählt → `null`.
  - Abgleich: neu, mehr, weniger, entfällt; gekauft bleibt bzw. kommt bei mehr zurück; ignoriert; Mahlzeit gelöscht;
    Rezeptmenge geändert; ohne Zeitraum leer; Übernehmen in einer Transaktion, danach leer.
  - Rezept → Liste: ohne Liste angelegt, addiert auf offene manuelle, Plan-Artikel und gekaufte unberührt, Rezept fehlt
    → 0.
  - `HeuteRepositoryTest`: jüngste Liste nach `createdAt`.
- `EinkaufViewModel`: Blätter und Zeitraum überstehen Neuaufbau, Doppeltipp-Sperre, Rückgängig.
- Nebenläufigkeit (Abfragen auf eigenem Thread hinter einer Schranke, wie `FuellenNebenlaeufigTest`): Zeitraum ändern
  während die Vorschau rechnet → die neue gewinnt; Schließen während der Rechnung bleibt zu; „Liste anlegen“ doppelt →
  eine Liste.
- Bildschirm (Robolectric, echtes ViewModel): Kacheln je Abteilung, Abhaken → Zuletzt, Eingabe mit neuem Artikel,
  Langdruck → Details → Löschen + „Rückgängig“ (und die Snackbar verschwindet nach einer Weile), Hinweis → Abgleich →
  Übernehmen, Vorschau → Anlegen, Schrift 200 %, Drehen, Tablet zweispaltig (Katalog rechts neben der Liste, per
  Bounds).
- `:feature:rezepte`: „Auf die Einkaufsliste“ → Dialog → Snackbar mit Anzahl.
- `:core:designsystem`: Kachel-Zustände (auf der Liste, gekauft, neutral) als Bild.
- `:app3`: Tab „Einkauf“ echt, Deep Link öffnet die Liste, „Ansehen“ nach Rezept → Liste wechselt zum Tab.
- Roborazzi: Einkauf (Liste, leer), Vorschau-, Abgleich-, Detail-Blatt × hell/dunkel × Handy/Tablet; CI vergleicht
  `:feature:einkauf:verifyRoborazziDebug`.
- Sichtprüfung auf `Foody_Test` gegen den Test-Server: Liste aus dem Plan mit Serverdaten, abhaken, Plan in der Woche
  ändern → Hinweis → abgleichen, Rezept auf die Liste; Testdaten danach wieder entfernen.

## Nicht in 6d

- Vorrat und Vorratsabzug, Teilen als Text, Ladenreihenfolge, Echtzeit-Einkauf, zweite Ansicht.
- Strichcode/Scan (Heute bzw. 6e).
- Mengen von Plan-Artikeln bearbeiten (dafür die Notiz).

## Risiken

- **Ignorierte Schlüssel sind nur lokal:** Auf einem zweiten Gerät kann eine dort gelöschte Zeile beim Abgleich als
  „+ neu“ auftauchen. Folge: ein überflüssiger Hinweis, nie falsche Mengen.
- **Abgleich rechnet bei jeder Plan- oder Rezeptänderung:** `EinkaufsBedarf` über den Zeitraum der aktuellen Liste;
  bei einer Woche und gut 100 Rezepten unkritisch, nicht gemessen.
- **v1 auf demselben Konto:** v1 sortiert Listen selbst und kennt keine ignorierten Schlüssel; beide lesen und
  schreiben dieselben Tabellen, die Daten bleiben gültig.
