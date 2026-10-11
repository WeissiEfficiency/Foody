# Web-Oberfläche, Teil 4: Vorrat, Zutatenkatalog und Tagebuch-Einträge

Stand: 2026-10-11 · Baut auf Teil 1–3 auf (`2026-10-11-web-oberflaeche-teil{1,2,3}-design.md`, Modul `:server-web`).
Gesamtvorhaben: 1 Grundgerüst + Lesen, 2 Rezeptpflege, 3 Planen + Einkaufen (alle fertig), **4 Vorrat, Zutatenkatalog,
Tagebuch-Einträge** (dieses Dokument) – danach kann der Browser alles, was die App kann, außer Fotos, Strichcodes und Timer.

## Ziel und Erfolgskriterien

Vorrat pflegen, Zutaten anlegen und bearbeiten sowie Tagebuch-Einträge erfassen, ändern und löschen – mit denselben
Regeln wie in der App, sodass die Handys alles beim nächsten Abgleich unverändert übernehmen.

- Browser und App rechnen identisch: Tagebuch-Werte beim Eintragen und Skalieren beim Ändern kommen aus `shared/domain`.
- Gleichzeitige Änderungen am Handy gehen nicht verloren; ein inzwischen gelöschter Eintrag ist kein Fehler.
- Kein Löschen, das auf dem Handy Datensätze ohne Bezug hinterlässt.
- Ressourcen wie bisher: Seiten < 30 KB (Vorrat mit 100 Einträgen, Katalog mit 300 Zutaten), kein Build-Werkzeug,
  kein zusätzlicher Container; ohne JavaScript bedienbar, htmx nur für Suchen.

Nicht in Teil 4: Packung scannen (Strichcode), Zutaten zusammenführen, Namen vereinheitlichen (beides bleibt in der App,
wo es in einer Datenbank-Transaktion läuft), Tagesziel-Einstellung.

## Gemeinsame Regeln nach `shared/domain`

- `Tagebuch.skaliert(...)`: Ändern sich Portionen (Rezept-Eintrag) bzw. Menge (Zutat-Eintrag), werden die festgehaltenen
  Werte im Verhältnis neu ÷ alt skaliert – nie aus dem heutigen Rezept neu berechnet (ADR 0007). Keine Skalierung, wenn
  sich der Wert nicht ändert oder kein brauchbarer alter Wert (> 0) vorliegt; freie Einträge werden nie skaliert.
  Bisher `TagebuchViewModel.bearbeiten`/`verhaeltnis`.
- Eintragsbildung: freier Eintrag (kcal → kJ mit `NutritionResult.KJ_JE_KCAL`, Werte vollständig), Rezept-Eintrag
  (`Tagebuch.naehrwerteRezept`), Zutat-Eintrag (`Tagebuch.naehrwerteZutat`; `null` = nicht berechenbar).
- Die App ruft diese Funktionen ab jetzt ebenfalls; ihr Verhalten bleibt unverändert.

## Lesen und Schreiben auf dem Server

- Lesen über `HaushaltsDaten`. Tagebuch-Einträge zusätzlich mit `rev`, Art, Rezept-/Zutat-ID, Portionen, Menge,
  Einheit, Planeintrags-ID; Zutaten zusätzlich mit `rev` und den Rohwerten für das Formular.
- Schreiben nur über `SyncService.push`, jeweils auf dem gerade gespeicherten Roh-JSON (unbekannte Felder wie
  `barcode`/`source` der Zutat bleiben erhalten), in drei Klassen:
  - `VorratSchreiber`: anlegen, ändern, löschen.
  - `ZutatSchreiber`: anlegen, ändern, löschen. Der Name wird wie in der App über `IngredientCatalog.canonicalName`
    vereinheitlicht; gibt es den vereinheitlichten Namen schon (andere Zutat) → kein Schreiben, Hinweis „Gibt es schon“
    mit Link. Neue Zutat ohne Abteilung bekommt `IngredientCatalog.guessCategory`.
  - `TagebuchSchreiber`: gegessen übernehmen, Rezept/Zutat/frei eintragen, ändern, löschen.
- **Gegessen höchstens einmal:** unter der `Haushaltssperre` aus Teil 3 prüfen, ob es schon einen lebenden
  Tagebuch-Eintrag mit dieser `planEintragId` gibt; dann nichts schreiben. Werte für 1 Portion (wie die App).
- **Zutat löschen:** abgelehnt, solange ein lebendes Rezept (Zeile), ein Vorratseintrag oder ein Einkaufseintrag sie
  nutzt (strenger als die App, die nur Rezepte prüft – der Server hat keine Fremdschlüssel, die Abhängiges mitlöschen).
  Die Meldung nennt die Verwendungen (Rezeptnamen, „Vorrat“, Listennamen; höchstens 5 Namen, dann „und N weitere“).

## Seiten

- **Navigation:** Rezepte · Plan · Einkauf · Vorrat · Tagebuch. Der Zutatenkatalog ist von `/vorrat` und vom
  Rezept-Editor aus verlinkt.
- **Vorrat (`/vorrat`):** oben ein Formular zum Hinzufügen (Zutat mit Vorschlägen aus dem Katalog per `<datalist>`,
  Menge, Einheit mit Vorschlägen, haltbar bis optional). Darunter alle Vorräte alphabetisch: Name, Menge, MHD; abgelaufene
  (MHD vor heute) markiert „abgelaufen“. Eintrag anklicken → `/vorrat/{id}`: Menge, Einheit, haltbar bis ändern; Löschen.
  Eine unbekannte Zutat beim Hinzufügen wird wie im Rezept-Editor angelegt (Name, Katalog, Abteilung geschätzt).
- **Zutatenkatalog (`/zutaten`):** Suche (htmx), Liste mit Name, Abteilung und kcal je 100 g/ml bzw. „keine Nährwerte“;
  „Neue Zutat“. `/zutat/neu` und `/zutat/{id}`: Name, Abteilung, Dichte (g/ml), Stückgewicht (g), Basis (100 g / 100 ml),
  Energie in kJ (kcal daneben angezeigt), Eiweiß, Kohlenhydrate, Fett, Ballaststoffe, Zucker, Salz (je g); leere
  Felder = unbekannt. Ohne Basis werden Nährwerte nicht gespeichert (wie die App). Löschen mit Verwendungsprüfung.
- **Tagebuch (`/tagebuch`, Anzeige gibt es):**
  - Oben „Geplant, noch nicht eingetragen“ (`Tagebuch.offeneVorschlaege` für den Tag) mit Knopf „Gegessen“ je Planeintrag.
  - Je Mahlzeit „+ Eintragen“ → `/tagebuch/neu?tag=…&mahlzeit=…` mit drei Formularen: **Rezept** (Suche + Auswahl wie beim
    Einplanen, Portionen, Vorgabe 1), **Zutat** (Name mit Vorschlägen, Menge, Einheit; nicht berechenbar → Hinweis
    „Keine Nährwerte oder Einheit nicht umrechenbar – bitte frei eintragen“), **Frei** (Name, kcal, optional Eiweiß,
    Kohlenhydrate, Fett).
  - Eintrag anklicken → `/tagebuch/{id}`: Mahlzeit; bei Rezept Portionen, bei Zutat Menge (Werte werden skaliert); bei
    frei Name, kcal und Nährwerte; Löschen.

## Fehler und Grenzen

- Alle Routen über `webGet`/`webPost`: CSRF bei jedem POST (sonst 403), Formulargrenze 256 KB, Fehlerseite.
- Fremde oder unbekannte IDs → 404, nichts geschrieben.
- Zahlen über `ZahlGrenzen` (Komma oder Punkt, ≥ 0, keine Exponentenschreibweise); Einheit als Enum-Name oder Zeichen;
  Portionen im Tagebuch > 0 und ≤ 99 (Kommazahlen erlaubt); kcal 0–10 000; Namen 1–200 Zeichen (getrimmt); Datum ±1 Jahr
  um heute; Mahlzeit nur `Mahlzeit.entries`. Fehler → Formular mit Eingaben und Meldung.
- Inzwischen gelöscht → zurück mit Hinweis „Eintrag gibt es nicht mehr“. Abgelehnter Push → „Das ging nicht – bitte neu
  laden.“ (409).

## Tests

- `shared/domain`: Skalieren (Verhältnis; unverändert; kein alter Wert; frei nie), freier Eintrag kcal → kJ.
- App: bestehende Tagebuch-Tests bleiben grün; `TagebuchViewModel` ruft die gemeinsamen Funktionen.
- `server/web`: Vorrat anlegen/ändern/löschen (auch mit neuer Zutat); Zutat anlegen/bearbeiten (Strichcode bleibt),
  Dublette abgelehnt, Löschen bei Verwendung abgelehnt (Rezept, Vorrat, Einkauf) und sonst erlaubt; Tagebuch gegessen
  (einmal, auch bei gleichzeitigen Klicks), Rezept/Zutat/frei eintragen, Zutat ohne Nährwerte → Hinweis, ändern mit
  Skalierung, löschen; CSRF 403 auf allen neuen POSTs; fremde IDs 404; Größen (Vorrat 100 Einträge, Katalog 300 Zutaten
  < 30 KB).
- E2E (`SyncEndToEndTest`, im Prozess): Vorrat und Tagebuch-Eintrag aus dem Browser kommen aufs Handy, Werte gleich wie
  in der App gerechnet.
