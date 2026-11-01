# Fachliche Invarianten

1. Skalierte Menge = Rezeptmenge × geplante Portionen / Standardportionen.
2. Einheiten gehören zu genau einer Dimension (Masse, Volumen, Anzahl). Innerhalb einer Dimension wird frei umgerechnet.
3. Masse ↔ Volumen nur mit hinterlegter Dichte; Stück ↔ Masse nur mit Stückgewicht. Sonst bleiben Positionen getrennt.
4. Rechnen erfolgt mit `BigDecimal` in Basiseinheiten (g, ml, Stück), nie mit `Float`.
5. Eine fehlende Nährwertangabe ist unbekannt, nicht null. Die UI zeigt Vollständigkeit und markiert Untergrenzen.
6. Energie wird in kJ gespeichert; kcal ist eine abgeleitete Anzeige (÷ 4,184).
7. Zusammengeführt wird nur bei gleicher kanonischer Zutat und kompatibler Dimension – nie über Freitextähnlichkeit.
   Die Einheit, in der eine Zutat eingekauft wird, hängt nur von den vorkommenden Dimensionen ab, nicht von der
   Reihenfolge im Plan: zuerst Stück, dann Packung, Dose, Gewicht, Volumen – jeweils, wenn sicher umrechenbar.
8. *(entfallen)* Einen Vorrat gibt es seit der V3 nicht mehr (ADR 0011); die Einkaufsliste enthält den ganzen
   Bedarf. Die Nummern der übrigen Regeln bleiben.
9. Optionale Zutaten werden standardmäßig nicht eingekauft.
10. Eine erzeugte Einkaufsliste ist ein Snapshot und ändert sich nur nach expliziter Neuberechnung;
    diese zeigt hinzugefügt/geändert/entfällt. Abgehakte Einträge werden dabei nie gelöscht; sie kommen nur zurück
    auf die Liste, wenn jetzt mehr gebraucht wird als zuvor.
11. Jeder generierte Listeneintrag speichert seine Herkunft (Planposition, Rezeptzeile, Beitrag).
12. Kalendertage sind `LocalDate`; Sommer-/Winterzeit hat keinen Einfluss auf die Tageszuordnung.
13. Import: Eine Zutat ohne Zahl (z. B. „Salz und Pfeffer“) erhält die Menge 0 = „nach Bedarf“. Sie erscheint nicht
    auf der Einkaufsliste und beeinflusst die Nährwert-Vollständigkeit nicht.
14. Import: Der Zutatenname besteht aus den führenden Substantiven; Adjektive und Zusätze („rote“, „geschälte“,
    „à ca. 400 g“) werden Hinweis. So laufen z. B. rote, grüne und gelbe Paprika zu einer Einkaufsposition zusammen.
15. Zutatennamen werden nur über eine feste Synonymliste und einfache Pluralformen vereinheitlicht
    (`IngredientCatalog`), nie über Ähnlichkeit: „saure Sahne“ bleibt eine eigene Zutat (vgl. Regel 7).
16. Zutaten aus der Liste „nie einkaufen“ (Wasser, Leitungswasser) erscheinen nicht auf der Einkaufsliste.
17. Ein Rezept direkt auf die Einkaufsliste zu setzen folgt den Regeln 9, 13 und 16.
    Die Einträge gelten als manuell, damit eine Neuberechnung (Regel 10) sie nicht als „entfällt“ entfernt.
18. Import: Eine Quelle (URL), die schon als Rezept existiert – auch archiviert –, wird nicht erneut angelegt.
    Eine Kopie eines Rezepts übernimmt die Quelle nicht.
19. Kochmodus: Bei Zeitspannen („10 - 15 Minuten“, „10 bis 15 Minuten“) startet der Timer mit der unteren Grenze. Eine Menge im Schritt
    gehört zu einer Zutat nur, wenn sie direkt davor steht (höchstens zwei kleingeschriebene Wörter dazwischen).
20. Import: Mengenspannen („2 - 3 EL“) rechnen mit der unteren Grenze; die obere bleibt als Hinweis („bis 3“).
    Brüche und gemischte Zahlen („1/2“, „1 1/2“, „1½“) werden als Zahl gelesen, nie nur der Zähler.
21. Stück, Packungen und Dosen werden auf der Einkaufsliste auf ganze Einheiten aufgerundet;
    Gramm und Milliliter bleiben exakt. Rechenrauschen unter 0,005 löst kein zusätzliches Stück aus.
22. Einordnung: Ein Rezept passt zu Mahlzeiten (Frühstück, Mittagessen, Abendessen, Snack) und Gängen (Vorspeise,
    Hauptspeise, Nachspeise, Brotzeit). Gespeichert wird nur die eigene Festlegung je Dimension (`null` = vermuten,
    leer = bewusst keine); sonst wird aus Tags und Name vermutet und nie gespeichert. Stichwörter ab 5 Zeichen treffen
    auch als Teilwort, kürzere nur als ganzes Wort; ein längeres Stichwort im selben Wort verdrängt enthaltene kürzere
    („Pfannkuchen“ ist kein Kuchen). Ohne Mahlzeit passt ein Rezept zu jeder Mahlzeit.
23. Woche: „+ Mahlzeit“ bietet alle nicht archivierten Rezepte an. „Leere Tage füllen“ füllt die Tage ab heute, an
    denen die gewählte Mahlzeit noch fehlt, nur mit passenden Rezepten (Regel 28). Plan-Einträge speichern die Mahlzeit
    als festen Schlüssel.
24. Tagebuch: Ein Eintrag gilt pro Person (eine Portion = was eine Person isst). Nährwerte werden beim Eintragen
    festgehalten und ändern sich nicht, wenn Rezept oder Zutat später geändert oder gelöscht werden (ADR 0007).
    Ein Plan-Eintrag lässt sich höchstens einmal als „gegessen“ übernehmen. Fehlen Energiewerte, zeigen Zeile, Mahlzeit
    und Tag „≥“.
25. Planer „Ø kcal pro Tag“: zählt nur Tage mit Abendessen und mindestens einer weiteren Mahlzeit; „≥“, wenn einem
    gezählten Tag Nährwerte fehlen.
26. Packung scannen: Gescannte Werte (Strichcode/Open Food Facts oder Foto) sind nur ein Vorschlag – vorausgefüllt und
    markiert, übernommen erst beim Speichern. Unplausibles wird verworfen (Energie über 4000 kJ, Gramm über 100,
    Zucker über Kohlenhydraten). Ein Strichcode, den eine Zutat schon hat, führt zu dieser Zutat (ohne Online-Abfrage).
    Eine vorhandene Zutat wird im Tagebuch nur mit gesetztem Häkchen „Werte aktualisieren“ überschrieben.
27. Import: Nennt die Datei ihre Portionen („Portionen: 4 · …“ in der Kopfzeile), gilt diese Zahl; geschätzt wird nur
    ohne Angabe und bei Backwaren mit „Portionen: 1“ (ganzer Kuchen, ganzes Blech – gegessen wird ein Stück). Alle
    Anzeigen (Rezept, Liste, Planer, Tagebuch) zeigen dieselben kcal je Portion.
    Früher geschätzte Rezepte bekommen die Portionen der Datei nachgetragen, aber nur, solange ihre Notiz noch
    „aus den Mengen auf N geschätzt“ für genau die aktuellen Portionen nennt; die Mengen bleiben, wie sie sind.
28. „Leere Tage füllen“ (V3, ohne Vorrat): Zu Mittag- und Abendessen kommen nur Hauptspeisen in Frage, nie Brotzeit,
    Dips oder Nachspeisen; eingeordnete Rezepte vor solchen ohne bekannten Gang, uneingeordnete nur als Lückenfüller.
    Favoriten (×3) und „Oft gekocht“ (bis ×2) werden gewichtet gezogen, nicht vorangestellt. Gleiche Grundlage oder
    Eiweißquelle wie am Vortag, am selben Tag oder am Folgetag fällt weg, solange es Alternativen gibt; Würzzutaten
    („Fischsauce“, „Hühnerbrühe“) zählen dafür nicht. Nichts, was 14 Tage vor dem ersten leeren Tag oder später
    eingeplant ist; lieber eine Lücke als ein unpassendes Gericht.
29. Mahlzeit aus Foto (`FotoErkennung`): Das Ergebnis ist nur ein Vorschlag fürs Eintragen-Blatt, gespeichert wird erst
    mit dem Häkchen. Zugeordnet wird nur sicher, nie über Ähnlichkeit: ein Rezept, wenn die Wahl des Modells seinem
    Namen gleicht (ohne Groß-/Kleinschreibung und doppelte Leerzeichen; bei gleichem Namen gewinnt das Rezept aus dem
    heutigen Plan); ein Teller, wenn es mindestens zwei Zutaten sind und jede über ihren kanonischen Namen (Regel 15)
    eine Katalogzutat ist. Sonst ein freier Eintrag mit Name und Werten des Modells. Unplausibles bleibt leer statt
    falsch: Portionen außerhalb 0,25–6 → 1 (sonst auf Viertel gerundet), Gramm je Zeile außerhalb 1–1500, kcal
    außerhalb 1–3000, Makros außerhalb 0–300 g. Eine Antwort ohne lesbares JSON ergibt keinen Vorschlag.

## Tests je Regel

Jede Regel ist durch mindestens einen Test belegt (Domain: `shared/domain/src/test`, App: `android/core/data/src/androidTest` und die Tests der `:feature:*`).

| Regel | Tests |
|---|---|
| 1 | `DomainTest.scalesFourServingRecipe`, `DomainTest.scalesByPlannedServings` |
| 2 | `DomainTest.addsGramsAndKilograms`, `DomainTest.spoonsAreVolumes` |
| 3 | `DomainTest.volumeToMassNeedsDensity`, `DomainTest.pieceToGramWithoutPieceWeightStaysUnconverted`, `DomainTest.sameIngredientDifferentDimensionsWithoutDensityStaySeparate`, `AmountParsingTest.zeroDensityConvertsInNeitherDirection` |
| 4 | Typ-Regel (alle Mengen sind `BigDecimal`); `DomainTest.keepsFractionalContributions` |
| 5 | `DomainTest.missingNutrientsAreIncompleteNotZero`, `DayNutritionTest.missingValuesMakeItALowerBound` |
| 6 | `DayNutritionTest.kcalWirdEinmalGerundet`, `TagebuchTest.summeAusGerundetenZeilen`, `NaehrwertScanTest.nurKcalWirdUmgerechnet` |
| 7 | `DomainTest.milkAndFlourNeverMerged`, `DomainTest.aggregatesSameIngredientAcrossRecipesWithSources`, `DomainTest.unitDoesNotDependOnPlanOrder`, `EinkaufPlanTest.zweiDimensionenZweiArtikel` |
| 8 | entfallen (kein Vorrat) |
| 9 | `DomainTest.optionalIngredientsExcludedByDefault`, `RezeptAufListeRepoTest.alle` |
| 10 | `DomainTest.diffDetectsAddedChangedRemoved`, `EinkaufAbgleichTest.gekauftWenigerKeineZeile`, `EinkaufAbgleichTest.gekauftMehrWiederOffen`, `EinkaufAbgleichTest.entfaelltGekauftKeineZeile` |
| 11 | `DomainTest.aggregatesSameIngredientAcrossRecipesWithSources`, `EinkaufPlanTest.anlegenMitHerkunft` |
| 12 | `DomainTest.dstDoesNotShiftLocalDay` |
| 13 | `MarkdownImportTest.ingredientsWithoutAmount`, `DomainTest.asNeededLinesNeverReachShoppingList` |
| 14 | `MarkdownImportTest.adjectivesGoToNoteAndOptionalIsDetected` |
| 15 | `IngredientCatalogTest.mapsFrequentImportNamesToCanonicalIngredients`, `IngredientCatalogTest.matchingIgnoresCaseSpacesAndPluralsButKeepsUnknownNames` |
| 16 | `IngredientCatalogTest.waterIsNeverBought` |
| 17 | `RezeptAufListeRepoTest.addiertAufManuelle`, `RezeptAufListeRepoTest.planUndGekaufteUnberuehrt`, `RezeptAufListeRepoTest.ohneWasser` |
| 18 | `RezeptImportDienstTest.gleicheQuelleUebersprungen`, `RezeptSchreibenTest.duplizierenOhneQuelleUndFavorit` |
| 19 | `StepTimerParserTest.rangesUseTheLowerBoundSoNothingBurns`, `AmountParsingTest.timerRangeWithBisUsesLowerBound`, `StepMentionTest.findsPartialAmountRightBeforeTheIngredient` |
| 20 | `AmountParsingTest.rangeKeepsUnitAndUsesTheLowerBound`, `AmountParsingTest.fractionIsNotReadAsItsNumerator`, `AmountParsingTest.mixedNumbersAddTheFraction` |
| 21 | `ShoppingRoundingTest.countableNeedsAreRoundedUp`, `ShoppingRoundingTest.roundingNoiseDoesNotBuyAnExtraPiece` |
| 22 | `RezeptEinordnungTest` (u. a. `festgelegtSchlaegtVermutungJeDimension`, `ohneTrefferPasstUeberall`) |
| 23 | `WocheLesenTest.auswahlOhneArchivierte`, `WocheVorschlagTest.leereTageNurAbHeute`, `WocheVorschlagTest.belegterTagFehlt` |
| 24 | `TagebuchTest.rezeptPortionenSkalieren`, `TagebuchRepositoryTest.ausRezeptHaeltWerteFest`, `TagebuchRepositoryTest.ausPlanEinePortionUndNurEinmal`, `TagebuchRepositoryTest.geloeschtesRezeptBehaeltEintrag` |
| 25 | `PlanDurchschnittTest` |
| 26 | `PackungScanTest.katalogTrefferOhneOnlineAbfrage`, `ZutatEditorScanTest.uebernimmtUndMarkiert`, `TagebuchRepositoryTest.packungOhneHaekchenFrei`, `TagebuchRepositoryTest.packungAktualisiertVorhandene` |
| 27 | `MarkdownImportTest.servingsFromHeaderLine`, `RezeptImportTest.portionenAusDerDateiStattSchaetzung`, `RezeptImportTest.eineGanzeBackwareWirdGeschaetzt`, `KcalJePortionTest`, `PortionenNachtragenTest` |
| 28 | `LeereTageFuellenTest`, `AbwechslungTest` |
| 29 | `FotoErkennungTest` (u. a. `gleicherNameDerAusDemPlanGewinnt`, `eineUnbekannteZutatMachtFrei`, `unplausibleWerteBleibenLeer`), `ModellJsonTest` |
