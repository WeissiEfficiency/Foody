# V3 Phase 6d Einkauf – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Einkaufsliste der V3: Kacheln für den Laden, Listen aus dem Plan mit Vorschau, Abgleich mit Diff, manuelle Einträge, Details mit Herkunft, Löschen mit „Rückgängig“ und „Rezept auf die Einkaufsliste“.

**Architecture:** Lesen und Schreiben im neuen `EinkaufRepository` (`:core:data`, Room + `:domain`-Regeln `EinkaufsBedarf`, `ShoppingDiff`, `EinkaufsAbgleich`, `RezeptAufListe`, `EinkaufEingabe`, `ShoppingCatalog`), lokal ignorierte Schlüssel in `IgnorierteSchluessel`; Oberfläche im neuen `:feature:einkauf` mit einem `EinkaufViewModel`; `Kachel` in `:core:designsystem` erweitert; `:feature:rezepte` und `:app3` angeschlossen.

**Tech Stack:** Kotlin 2.4.20, Compose (BOM 2026.09.00), Navigation Compose 2.10.2, Hilt 2.60.1, Room 2.8.5, Robolectric 4.16.1, Roborazzi 1.76.0.

**Spec:** `docs/superpowers/specs/2026-10-25-v3-phase6d-einkauf-design.md`

## Global Constraints

- v1 unverändert: `android/app/**`, `:sync-protocol`; Server-Code unverändert; v1-Einkauf (`ui/shopping`, `data/repo/ShoppingRepository.kt`) nur als Vorlage lesen.
- Kein Schema-Wechsel: Datenbank bleibt Version 10, `android/core/data/schemas` unverändert; neue DAO-Abfragen sind erlaubt.
- `:feature:einkauf` = `android/feature/einkauf`, Paket `de.foody.feature.einkauf`, `build.gradle.kts` wie `:feature:woche` ohne Coil; hängt nur von `:core:*` und `:domain` ab.
- UI nur aus `:core:designsystem`; jede Datei in `android/feature/einkauf/src/main` ≤ 300 Zeilen; ein ViewModel für den Bereich (`hiltViewModel()`).
- Speicherung wie v1: Plan-Artikel `manual = false`, Menge = `toBuy` in `MeasureUnit.baseOf(dimension)`, `category` = Kategorie der Zutat, Herkunft je `NeedSource` (Beitrag in Basiseinheit, Datum des Plan-Eintrags, Rezeptname); manuelle Artikel `manual = true` ohne Herkunft. Schlüssel eines Plan-Artikels `"$ingredientId|${unit.dimension}"`.
- Abteilung eines Artikels = `ShoppingCatalog.sectionFor(category, name)`, Emoji = `ShoppingCatalog.emojiFor(name, category)`; Details schreiben die gewählte Abteilung nach `category`.
- Mengentext = `QuantityFormatter.format(Quantity.of(amount, unit))`; Herkunftstext „300 g Curry (Mo) + 150 g Reispfanne (Mi)“ (Wochentag zweibuchstabig Mo Di Mi Do Fr Sa So).
- Listenname aus dem Plan „26.10.–1.11.“ (Tag und Monat ohne führende Null, Halbgeviertstrich), leere Liste „Einkauf“. Zeitraum: Anfang ≤ Ende, höchstens 28 Tage, vorbelegt heute bis heute+6.
- Aktuelle Liste = `ORDER BY createdAt DESC, id DESC` – gilt auch für Heute.
- Zeit nur über `uhr: () -> Long` im Repository bzw. injizierte `Clock` im ViewModel (Provider in `DatenModul`). Jede Schreibstelle setzt `updatedAt = uhr()`; Outbox über die vorhandenen Trigger.
- Robolectric-UI-Tests: `@GraphicsMode(NATIVE)`, `junit4.v2.createComposeRule`, `application = android.app.Application::class`; Blätter/Dialoge mit Textfeld **ohne** Breiten-Qualifier; Room mit `setQueryExecutor { it.run() }`; ViewModels außerhalb der Komposition. Blätter: `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, Inhalt scrollbar. Snackbars mit Aktion: `SnackbarDuration.Long`.
- Texte: „Einkauf“, „· 12 offen“, „Neue Liste aus dem Plan“, „Mit Plan abgleichen“, „Frühere Listen“, „Liste löschen“, „Plan geändert – abgleichen“, „Was willst du einkaufen?“, „„<Text>“ hinzufügen“, „Zuletzt verwendet“, „Eigene Artikel“, „Noch nichts auf der Liste“, „Im Zeitraum ist nichts geplant“, „Liste anlegen (n)“, „Übernehmen (n)“, „+ neu“, „entfällt“, „wieder auf die Liste“, „Speichern“, „Löschen“, „Rückgängig“, „Diese Liste gibt es nicht mehr“, „Zur aktuellen Liste“, „Auf die Einkaufsliste“, „7 Artikel auf der Einkaufsliste“, „Ansehen“, „Nichts einzukaufen“, „auf der Liste“, „gekauft“, „nicht auf der Liste“, „Details“.
- Deutsch in Texten, Bezeichnern, Kommentaren; `allWarningsAsErrors` bleibt an. PR gegen `main` mit Auto-Merge; „Server aktualisieren? Nein“.

## Review Focus

1. **Eingabe in anderer Schreibweise** – „  milch “ bei offener „Milch“ hakt sie ab statt eine zweite anzulegen; „Grillkohle“ (unbekannt) wird „Eigene Artikel“ (Test in Task 3).
2. **Gleiche Zutat aus zwei Rezepten und in zwei Dimensionen** – 200 g Zwiebeln (Curry) + 100 g Zwiebeln (Pfanne) ergeben einen Artikel „300 g“ mit zwei Herkunftszeilen; 2 Stk. Zwiebeln dazu ergeben einen zweiten Artikel (eigener Schlüssel) (Test in Task 4).
3. **Zeitraumgrenzen** – ein Plan-Eintrag am letzten Tag des Zeitraums zählt mit, einer am Tag danach nicht (Test in Task 4).
4. **Aufgerundete Stückzahl beim Abgleich** – Bedarf ändert sich von 0,5 auf 0,7 Zwiebeln (beides „1 Stk.“) → keine Abgleichzeile, kein Hinweis (Test in Task 5).
5. **Angezeigte Liste verschwindet** – Liste über die Route offen, dann per Sync gelöscht → „Diese Liste gibt es nicht mehr“ mit „Zur aktuellen Liste“, kein Absturz (Test in Task 9).

---

### Task 1: Abfragen, aktuelle Liste und ignorierte Schlüssel

**Files:**
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/db/Daos.kt`
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/einkauf/IgnorierteSchluessel.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/heute/HeuteRepositoryTest.kt` (anpassen), `.../einkauf/IgnorierteSchluesselTest.kt`

**Interfaces:**
- Produces (DAO): `EinkaufslisteDao.alleFlow(): Flow<List<EinkaufslisteEntity>>` (`ORDER BY createdAt DESC, id DESC`); `EinkaufslisteDao.nachIdFlow(id): Flow<EinkaufslisteEntity?>`; `EinkaufsartikelDao.nachListeFlow(listeId): Flow<List<EinkaufsartikelEntity>>` und `suspend nachListe(listeId)` (`ORDER BY sortOrder, id`); `EinkaufsartikelDao.gekauftFlow(): Flow<List<EinkaufsartikelEntity>>` (`checked = 1 ORDER BY checkedChangedAt DESC, id`); `EinkaufsartikelDao.alleFlow()`; `EinkaufsartikelQuelleDao.nachListeFlow(listeId): Flow<List<EinkaufsartikelQuelleEntity>>` (Join über `shopping_item.listId`); `EinkaufsartikelQuelleDao.loeschenNachArtikel(shoppingItemId)`. `offeneDerJuengstenListe` sortiert die Listen nach `createdAt DESC, id DESC`.
- Produces: `class IgnorierteSchluessel(prefs: SharedPreferences)` mit `fun lesen(listeId: String): Set<String>`, `fun fluss(listeId: String): Flow<Set<String>>` (neu bei jedem Schreiben), `fun hinzufuegen(listeId: String, schluessel: Collection<String>)`, `fun entfernen(listeId: String, schluessel: String)`, `fun vergessen(listeId: String)`; Schlüssel `ignoriert_<listeId>`, Datei `einkauf`.

- [ ] **Step 1: Tests** `HeuteRepositoryTest`: Testhelfer `liste(id, angelegt)` setzt `createdAt`; `offenerEinkaufJuengsteListe` mit `liste("alt", 20)` (zuletzt geändert, aber älter angelegt: `updatedAt = 99`) und `liste("neu", 30)` → `listeId == "neu"`. `IgnorierteSchluesselTest` (Robolectric-Prefs): `hinzufuegenUndLesen`, `entfernen`, `vergessen`, `flussMeldetAenderung` (erst `emptySet()`, nach `hinzufuegen` `setOf("z|MASS")`), `listenGetrennt`.
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest --tests "*HeuteRepositoryTest*" --tests "*IgnorierteSchluesselTest*"` → FAIL (alt statt neu; Klasse fehlt).
- [ ] **Step 3:** Abfragen und `IgnorierteSchluessel` umsetzen (`fluss` über einen internen `MutableStateFlow<Int>`-Stand).
- [ ] **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün.
- [ ] **Step 5:** Commit `"V3 Phase 6d: Abfragen und ignorierte Schlüssel"`.

### Task 2: `EinkaufRepository` – Lesen

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/einkauf/{EinkaufModelle,EinkaufRepository,EinkaufTexte}.kt`
- Modify: `android/core/data/src/main/kotlin/de/foody/core/data/DatenModul.kt` (Provider `IgnorierteSchluessel(context.getSharedPreferences("einkauf", MODE_PRIVATE))` und `EinkaufRepository(db, System::currentTimeMillis, ignoriert)`, beide `@Singleton`)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/einkauf/{EinkaufTestDaten,EinkaufLesenTest}.kt`

**Interfaces:**
- Produces: `data class EinkaufsArtikel(val id: String, val name: String, val emoji: String, val abteilung: String, val menge: String?, val notiz: String?, val gekauft: Boolean, val plan: Boolean, val herkunft: String?, val ingredientId: String?)`; `data class Abteilung(val name: String, val emoji: String, val artikel: List<EinkaufsArtikel>)`; `data class EinkaufsAnsicht(val id: String, val name: String, val von: LocalDate?, val bis: LocalDate?, val offen: List<Abteilung>, val alle: List<EinkaufsArtikel>)` mit `val anzahlOffen: Int`; `data class ZuletztArtikel(val name: String, val emoji: String, val abteilung: String, val ingredientId: String?)`; `data class ListenKopf(val id: String, val name: String, val angelegt: LocalDate, val artikel: Int)`.
- Produces: `class EinkaufRepository(db: FoodyDatenbank, uhr: () -> Long, ignoriert: IgnorierteSchluessel)` mit `fun aktuelleListeId(): Flow<String?>`, `fun liste(id: String): Flow<EinkaufsAnsicht?>`, `fun zuletzt(): Flow<List<ZuletztArtikel>>`, `fun fruehere(): Flow<List<ListenKopf>>`. In `EinkaufTexte.kt`: `internal fun mengeText(amount: BigDecimal?, unit: MeasureUnit?): String?`, `internal fun herkunftText(quellen: List<EinkaufsartikelQuelleEntity>): String?`, `internal fun listenName(von: LocalDate, bis: LocalDate): String`, `internal fun schluessel(e: EinkaufsartikelEntity): String?` (`null` bei manuellen oder ohne Einheit).
- `offen`: nur `checked = false`, Abteilungen in `ShoppingCatalog.sectionOrder`, darin nach `sortOrder`, `id`; Emoji der Abteilung aus `ShoppingCatalog.sections` („🛒“ für „Eigene Artikel“). `zuletzt`: höchstens 18, einer je `ShoppingCatalog.sameItem`. `angelegt` aus `createdAt` in `ZoneId.systemDefault()`.

- [ ] **Step 1: Tests** `EinkaufTestDaten.kt` (erfunden: Zutaten Reis/Zwiebeln/Milch, Rezepte „Curry“ und „Reispfanne“, Helfer `liste(id, angelegt, von?, bis?)`, `artikel(...)`, `quelle(...)`). `EinkaufLesenTest`: `aktuelleNachAngelegt`, `ohneListeNull`, `offenNachAbteilungInLaufreihenfolge` (Milch, Zwiebeln, Reis, „Grillkohle“ → Abteilungsnamen in der Reihenfolge von `ShoppingCatalog.sectionOrder`, „Eigene Artikel“ zuletzt; innerhalb nach `sortOrder`), `gekaufteNichtOffen`, `mengeUndHerkunftText` (`"300 g"`, `"200 g Curry (Mo) + 100 g Reispfanne (Mi)"`), `zuletztUeberAlleListen` (neueste zuerst, „Milch“ in zwei Listen nur einmal), `zuletztHoechstens18`, `fruehereOhneAktuelle` (mit `artikel`-Anzahl), `listenNameOhneNullen` (`listenName(26.10.2026, 1.11.2026) == "26.10.–1.11."`).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:data:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6d: Einkauf lesen"`.

### Task 3: `EinkaufRepository` – Kacheln, Eingabe, Details, Löschen

**Files:**
- Modify: `einkauf/EinkaufRepository.kt`, `einkauf/EinkaufModelle.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/einkauf/EinkaufSchreibenTest.kt`

**Interfaces:**
- Produces: `suspend fun abhaken(id: String, an: Boolean)`; `suspend fun antippen(listeId: String?, name: String, abteilung: String, ingredientId: String? = null): String` (Regel wie `EinkaufEingabe`: offen → gekauft, gekauft → offen, sonst neu manuell; Rückgabe = ID der Liste, ohne Liste neu „Einkauf“); `suspend fun zuletztAntippen(listeId: String?, z: ZuletztArtikel): String` (gekauft in der Liste → offen; offen → nichts; sonst neu manuell mit Name, Abteilung, Zutat, ohne Menge); `suspend fun eingeben(listeId: String?, text: String): String?` (`EinkaufEingabe.entscheiden`; leer → `null`, nichts angelegt); `suspend fun details(id: String, name: String, notiz: String, abteilung: String)` (Name getrimmt 1–60, sonst `IllegalArgumentException`; leere Notiz → `null`); `data class GeloeschterArtikel(val artikel: EinkaufsartikelEntity, val herkunft: List<EinkaufsartikelQuelleEntity>)`; `suspend fun loeschen(id: String): GeloeschterArtikel?` (Plan-Artikel → Schlüssel ignoriert); `suspend fun wiederherstellen(g: GeloeschterArtikel)` (dieselbe ID, Herkunft zurück, Schlüssel nicht mehr ignoriert; Liste fehlt → still); `suspend fun listeLoeschen(id: String)` (+ `ignoriert.vergessen`).

- [ ] **Step 1: Tests** `EinkaufSchreibenTest`: `abhakenUndZurueck`, `antippenNeuOffenGekauft` (drei Tipps auf „Milch“ → neu, gekauft, wieder offen), `antippenOhneListeLegtEinkaufAn` (Name „Einkauf“, `rangeStart == null`), `zuletztAntippen` (gekauft in Liste → offen; aus alter Liste → neu manuell ohne Menge; schon offen → keine Änderung), `eingabeAndereSchreibweise` (Review Focus 1: offene „Milch“, `eingeben(l, "  milch ")` → Milch gekauft, Artikelzahl gleich), `eingabeUnbekanntEigenerArtikel` („Grillkohle“ → `category == "Eigene Artikel"`), `eingabeLeerNichts`, `detailsAendern` (Name, Notiz „Bio“, Abteilung), `detailsNameLeerAbgelehnt` (auch 61 Zeichen), `loeschenUndWiederherstellenMitHerkunft` (dieselbe ID, zwei Quellen zurück), `loeschenPlanArtikelIgnoriert` (Schlüssel in `ignoriert.lesen`, nach `wiederherstellen` nicht mehr), `wiederherstellenOhneListeStill` (Liste gelöscht → kein Fehler, nichts angelegt), `abhakenGeloeschtStill`, `listeLoeschenVergisstSchluessel`, `aenderungenInDerOutbox` (Sync aktiv: `shopping_item`/ID nach `details`), `updatedAtGesetzt`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen (jede Methode eine Transaktion; Existenzprüfung darin). **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6d: Einkauf schreiben"`.

### Task 4: Liste aus dem Plan

**Files:**
- Modify: `einkauf/EinkaufRepository.kt`, `einkauf/EinkaufModelle.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/einkauf/EinkaufPlanTest.kt`

**Interfaces:**
- Consumes: `EinkaufsBedarf.berechnen(DateRange(von, bis), slots, recipes, ingredients, emptyList())`; Plan-Einträge über `planEintragDao().zwischen(von, bis).first()` als `MealSlot(id, date, slotType, recipeId, servings)`; Rezepte/Zutaten über `alsRecipe`/`alsIngredient`.
- Produces: `data class VorschauZeile(val schluessel: String, val name: String, val emoji: String, val abteilung: String, val menge: String, val herkunft: String)`; `suspend fun planVorschau(von: LocalDate, bis: LocalDate): List<VorschauZeile>` (nur `toBuy > 0`, sortiert nach Abteilung in Laufreihenfolge, dann Name); `suspend fun ausPlanAnlegen(von: LocalDate, bis: LocalDate, abgewaehlt: Set<String>): String?` (eine Transaktion: Bedarf neu rechnen, abgewählte weglassen; nichts übrig → `null`, keine Liste; sonst Liste `listenName(von, bis)` mit Zeitraum, Plan-Artikel samt Herkunft, abgewählte Schlüssel ignoriert, **offene manuelle** Artikel der bisherigen aktuellen Liste bekommen die neue `listId`). Intern: `private suspend fun bedarf(von, bis): List<ShoppingNeed>` (für Task 5 wiederverwendet).

- [ ] **Step 1: Tests** `EinkaufPlanTest`: `vorschauOhneWasser` (Rezept mit Wasser → keine Wasserzeile), `vorschauOhneVorrat`, `zweiRezepteEinArtikel` (Review Focus 2: 200 g + 100 g Zwiebeln → eine Zeile „300 g“, Herkunft mit beiden Rezepten), `zweiDimensionenZweiArtikel` (Review Focus 2: dazu 2 Stk. Zwiebeln → zwei Schlüssel), `zeitraumGrenzen` (Review Focus 3: Eintrag am `bis`-Tag zählt, Eintrag am Tag danach nicht), `anlegenMitHerkunft` (`manual == false`, Menge in Basiseinheit, Quellen mit Rezeptname und Datum), `anlegenAbgewaehltIgnoriert` (Zeile fehlt, Schlüssel in `ignoriert.lesen(neu)`), `manuelleWandernMit` (offene manuelle der alten Liste in der neuen, gekaufte bleiben in der alten), `allesAbgewaehltNull` (keine neue Liste), `nichtsGeplantLeer`, `neueListeIstAktuell`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6d: Liste aus dem Plan"`.

### Task 5: Abgleich

**Files:**
- Modify: `einkauf/EinkaufRepository.kt`, `einkauf/EinkaufModelle.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/einkauf/EinkaufAbgleichTest.kt`

**Interfaces:**
- Consumes: `ShoppingDiff.diff(planArtikel, ::schluessel, menge, bedarf)`, `EinkaufsAbgleich.bleibtAbgehakt`, `EinkaufsAbgleich.entfaelltLoeschen`, `ignoriert.fluss(listeId)`.
- Produces: `enum class AbgleichArt { NEU, MEHR, WENIGER, WIEDER_OFFEN, ENTFAELLT }`; `data class AbgleichZeile(val schluessel: String, val art: AbgleichArt, val name: String, val emoji: String, val alt: String?, val neu: String?)`; `fun abgleich(listeId: String): Flow<List<AbgleichZeile>>` (neu bei Änderung an Liste, Artikeln, Plan im Zeitraum, Rezepten, Zeilen, Zutaten, ignorierten Schlüsseln; Liste ohne Zeitraum oder fehlend → leer); `suspend fun abgleichen(listeId: String, abgewaehlt: Set<String>): Int` (eine Transaktion, rechnet die Zeilen neu, wendet die nicht abgewählten an, ignoriert die abgewählten; Rückgabe = angewendete Zeilen).
- Regeln (Spec-Tabelle): ADDED → `NEU`; CHANGED offen → `MEHR`/`WENIGER` (Menge und Herkunft ersetzen); CHANGED gekauft mit `bleibtAbgehakt == false` → `WIEDER_OFFEN` (Menge ersetzen, `checked = false`); CHANGED gekauft sonst → keine Zeile; REMOVED offen → `ENTFAELLT` (löschen); REMOVED gekauft → keine Zeile; ignorierter Schlüssel → keine Zeile; CHANGED, dessen Mengentext gleich bleibt → keine Zeile.

- [ ] **Step 1: Tests** `EinkaufAbgleichTest`: `unveraendertLeer`, `neuerEintragNeu`, `mehrUndWeniger`, `gekauftWenigerKeineZeile`, `gekauftMehrWiederOffen`, `entfaelltOffen`, `entfaelltGekauftKeineZeile`, `ignoriertKeineZeile` (in Vorschau abgewählt und gelöschter Plan-Artikel), `aufgerundeteStueckzahlKeineZeile` (Review Focus 4: 0,5 → 0,7 Zwiebeln je Rezept → leer), `mahlzeitGeloeschtEntfaellt`, `rezeptmengeGeaendert` (Zeile im Rezept 400 → 600 g → `MEHR` mit `alt == "400 g"`, `neu == "600 g"`), `ohneZeitraumLeer`, `abgleichenWendetAn` (danach `abgleich` leer, Herkunft ersetzt), `abgleichenAbgewaehltIgnoriert` (Zeile bleibt unangewendet und erscheint nicht wieder), `flussMeldetPlanaenderung` (neuer Plan-Eintrag → neue Zeile im Flow).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6d: Abgleich mit dem Plan"`.

### Task 6: Rezept auf die Einkaufsliste

**Files:**
- Modify: `einkauf/EinkaufRepository.kt`; `android/feature/rezepte/src/main/kotlin/de/foody/feature/rezepte/detail/{RezeptDetailViewModel,RezeptDetailScreen,DetailBereiche}.kt`; neu `detail/EinkaufsDialog.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/einkauf/RezeptAufListeRepoTest.kt`, `android/feature/rezepte/src/test/kotlin/de/foody/feature/rezepte/detail/RezeptDetailScreenTest.kt` (erweitert)

**Interfaces:**
- Produces: `suspend fun rezeptAufListe(rezeptId: String, portionen: Int): Int` (über `RezeptAufListe.bedarf` mit `LocalDate.now()` aus `uhr` und `zusammenfuehren`; Erhöhen addiert in Basiseinheit; neu = manuell, Kategorie der Zutat; ohne Liste „Einkauf“; Rezept fehlt → 0). `RezeptDetailViewModel` bekommt `EinkaufRepository` injiziert und `suspend fun aufEinkaufsliste(portionen: Int): Int`. `RezeptDetailRoute` bekommt `onEinkauf: () -> Unit`.
- Oberfläche: Das Rezeptdetail hat kein Menü, nur die Aktionszeile – „Auf die Einkaufsliste“ wird dort ein `IconButton` (`Icons.Outlined.AddShoppingCart`, Beschreibung „Auf die Einkaufsliste“). Dialog „Auf die Einkaufsliste“ mit Portionen ± (1–99, vorbelegt mit den Portionen des Steppers), „Hinzufügen“/„Abbrechen“. Danach Snackbar „n Artikel auf der Einkaufsliste“ mit „Ansehen“ (→ `onEinkauf`), bei 0 „Nichts einzukaufen“ ohne Aktion.

- [ ] **Step 1: Tests** `RezeptAufListeRepoTest`: `ohneListeAngelegt` (Rückgabe 1, Liste „Einkauf“), `addiertAufManuelle` (offener manueller Reis 100 g + Rezept 400 g → 500 g, kein zweiter Artikel), `planUndGekaufteUnberuehrt` (Plan-Artikel Reis und gekaufter manueller Reis bleiben; neuer manueller Artikel), `ohneWasser`, `rezeptFehltNull`, `skaliertPortionen` (4 Portionen → 800 g). `RezeptDetailScreenTest`: `aufEinkaufslisteMitAnsehen` (Knopf → Dialog → „Hinzufügen“ → „1 Artikel auf der Einkaufsliste“ → „Ansehen“ ruft `onEinkauf`), `nichtsEinzukaufen`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen (Zeilenlimit: Dialog in eigene Datei). **Step 4:** `./gradlew :core:data:testDebugUnitTest :feature:rezepte:testDebugUnitTest` grün (Roborazzi von `:feature:rezepte` neu aufnehmen, falls die Aktionszeile sich im Bild ändert – Bild ansehen). **Step 5:** Commit `"V3 Phase 6d: Rezept auf die Einkaufsliste"`.

### Task 7: `Kachel` mit Liste-Zustand und Langdruck

**Files:**
- Modify: `android/core/designsystem/src/main/kotlin/de/foody/core/designsystem/komponenten/Kachel.kt`, `android/katalog/src/main/kotlin/de/foody/katalog/seiten/KatalogSeitenInhalt.kt` (Kacheln „auf der Liste“, „gekauft“, „neutral“ mit Emoji)
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/komponenten/KachelTest.kt`

**Interfaces:**
- Produces: `fun Kachel(name, menge, onKlick, modifier, untertitel, abgehakt, symbol, aufDerListe: Boolean = false, onLangdruck: (() -> Unit)? = null, zustandsText: String? = null)` – `aufDerListe` tönt die Fläche warm (Akzentfarbe aus den Tokens, Text 4,5:1); `combinedClickable` mit `onLongClickLabel = "Details"`, wenn `onLangdruck` gesetzt; `zustandsText` überschreibt die `stateDescription` („auf der Liste“, „gekauft“, „nicht auf der Liste“), sonst bleibt „abgehakt“ wie bisher.

- [ ] **Step 1: Tests** `KachelTest`: `langdruckRuftCallback`, `langdruckBeschriftetDetails`, `zustandsTextGemeldet` („auf der Liste“), `ohneNeueParameterUnveraendert` (bestehende Tests bleiben grün).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :core:designsystem:testDebugUnitTest :feature:heute:verifyRoborazziDebug` grün (Heute unverändert); `:katalog:recordRoborazziDebug`, neue Bilder ansehen. **Step 5:** Commit `"V3 Phase 6d: Kachel für den Einkauf"`.

### Task 8: `:feature:einkauf` und `EinkaufViewModel`

**Files:**
- Create: `android/feature/einkauf/build.gradle.kts`, Eintrag in `settings.gradle.kts`, `src/main/kotlin/de/foody/feature/einkauf/{EinkaufViewModel,EinkaufZustand}.kt`, `src/test/.../{TestHilfen,EinkaufTestDaten}.kt` (Hilfen aus `:feature:woche` kopiert)
- Test: `src/test/kotlin/de/foody/feature/einkauf/{EinkaufViewModelTest,EinkaufNebenlaeufigTest}.kt`

**Interfaces:**
- Consumes: Tasks 2–5.
- Produces: `sealed interface Blatt : Parcelable { Vorschau; Abgleich; data class Details(val id: String); Frueher }`; `data class VorschauZustand(val von: LocalDate, val bis: LocalDate, val zeilen: List<VorschauZeile>?, val abgewaehlt: Set<String>)` (`zeilen == null` = rechnet); `data class EinkaufZustand(val ansicht: EinkaufsAnsicht?, val geladen: Boolean, val listeFehlt: Boolean, val zuletzt: List<ZuletztArtikel>, val abgleich: List<AbgleichZeile>, val suche: String)`; `@HiltViewModel class EinkaufViewModel @Inject constructor(saved: SavedStateHandle, repo: EinkaufRepository, clock: Clock)` mit `fun anzeigen(listeId: String?)`, `val zustand: StateFlow<EinkaufZustand>`, `val blatt: StateFlow<Blatt?>`, `val vorschau: StateFlow<VorschauZustand?>`, `val abgleichAbgewaehlt: StateFlow<Set<String>>`, `fun suche(text)`, `fun eingeben()`, `fun abhaken(id, an)`, `fun antippen(name, abteilung, ingredientId)`, `fun zuletztAntippen(z)`, `fun details(id, name, notiz, abteilung)`, `suspend fun loeschen(id): GeloeschterArtikel?`, `fun wiederherstellen(g)`, `fun listeLoeschen()`, `fun blattOeffnen(b: Blatt)`, `fun blattSchliessen()`, `fun zeitraum(von, bis)` (Grenzen: Anfang ≤ Ende, höchstens 28 Tage), `fun vorschauUmschalten(schluessel)`, `suspend fun anlegen(): String?` (Sperre; `null` beim zweiten Tipp), `fun abgleichUmschalten(schluessel)`, `suspend fun abgleichen(): Int?` (Sperre). `listeId`, Blatt, Zeitraum (Epoch-Days), Suchtext im `SavedStateHandle`; `listeId == null` folgt `aktuelleListeId()`; nach `anlegen` zeigt der Bereich die neue Liste. Ein Rechen-Auftrag für die Vorschau (`Job`), Ergebnis nur für den Zeitraum, für den gerechnet wurde.

- [ ] **Step 1: Tests** `EinkaufViewModelTest` (Room + echtes Repository, feste `Clock` Mittwoch 28.10.2026): `folgtAktuellerListe`, `bestimmteListe`, `listeFehltGemeldet`, `vorschauVorbelegtSiebenTage` (28.10.–3.11.), `zeitraumGrenzen` (Ende vor Anfang → Ende = Anfang; 29 Tage → 28), `anlegenEinmal` (zwei parallele `anlegen()` → einmal ID, einmal `null`, eine Liste), `anlegenZeigtNeueListe`, `abgleichenEinmal`, `blattUndZeitraumUeberstehenNeuaufbau`, `loeschenUndRueckgaengig`, `eingebenLeertSuche`. `EinkaufNebenlaeufigTest` (Abfragen auf eigenem Thread hinter einer Schranke, wie `FuellenNebenlaeufigTest`): `neuerZeitraumGewinnt` (während der ersten Rechnung `zeitraum` ändern → Zeilen des zweiten Zeitraums), `schliessenWaehrendRechnungBleibtZu`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :feature:einkauf:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6d: Einkauf-ViewModel"`.

### Task 9: Einkauf-Bildschirm

**Files:**
- Create: `src/main/kotlin/de/foody/feature/einkauf/{EinkaufScreen,EinkaufKopf,EinkaufKacheln,EingabeFeld}.kt`
- Test: `src/test/kotlin/de/foody/feature/einkauf/EinkaufScreenTest.kt`

**Interfaces:**
- Produces: `@Composable fun EinkaufRoute(listeId: String?, onListe: (String) -> Unit, onAktuelle: () -> Unit, vm: EinkaufViewModel = hiltViewModel())` (`onListe` öffnet eine frühere Liste, `onAktuelle` führt zur aktuellen). Raster `GridCells.Adaptive(104.dp)`; ab 840 dp zwei Bereiche nebeneinander (links offen + Zuletzt, rechts Katalog, je eigenes Raster); Abschnitte „Zuletzt verwendet“ aufgeklappt, Katalog-Abteilungen zugeklappt (Zustand per `rememberSaveable`). Kacheln: offen `aufDerListe = true`, „auf der Liste“; Zuletzt/Katalog nach Stand der aktuellen Liste.

- [ ] **Step 1: Tests** `EinkaufScreenTest` (`h1600dp`): `kachelnJeAbteilung` (Abschnittsköpfe, „300 g“), `kopfMitZeitraumUndAnzahl` („26.10.–1.11. · 2 offen“), `abhakenWandertZuZuletzt`, `eingabeNeuerArtikel` („Grillkohle“ → „„Grillkohle“ hinzufügen“ → Kachel unter „Eigene Artikel“), `katalogAufklappenUndAntippen`, `leerzustandMitPlanKnopf`, `hinweisNurBeiAbgleich` (ohne Änderung kein „Plan geändert – abgleichen“; nach neuem Plan-Eintrag sichtbar), `listeFehlt` (Review Focus 5: Route mit gelöschter Liste → „Diese Liste gibt es nicht mehr“ → „Zur aktuellen Liste“ ruft `onAktuelle`), `tabletZweiBereiche` (`w1280dp-h1200dp`: Katalog-Abschnitt rechts neben dem ersten offenen Artikel, per Bounds), `schrift200OhneAbschneiden`, `ueberstehtDrehen` (Suchtext bleibt).
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6d: Einkauf-Bildschirm"`.

### Task 10: Blätter

**Files:**
- Create: `src/main/kotlin/de/foody/feature/einkauf/{VorschauBlatt,AbgleichBlatt,DetailBlatt,FruehereBlatt}.kt`
- Modify: `EinkaufScreen.kt` (Menü ⋮, Blätter, Snackbar „Rückgängig“, Rückfrage „Liste löschen“)
- Test: `EinkaufScreenTest` (erweitert)

**Interfaces:**
- Vorschau: Zeitraum „‹ Mi 28.10. ›“ bis „‹ Di 3.11. ›“ (Beschreibungen „Anfang früher“, „Anfang später“, „Ende früher“, „Ende später“), Zeilen mit Häkchen je Abteilung, Knopf „Liste anlegen (n)“ fest unten (aus bei n = 0). Abgleich: Zeilen „+ 🍅 Tomaten · 400 g · neu“, „↑ 🧅 Zwiebeln · 3 → 4 Stk.“, „↓ …“, „↑ … · wieder auf die Liste“, „− 🥬 Spinat · entfällt“ mit Häkchen, „Übernehmen (n)“. Details: Felder „Name“ und „Notiz“, Abteilungs-Chips (`ShoppingCatalog.sectionOrder`), Menge nur bei Plan-Artikeln als Text, Herkunft, „Speichern“ (aus bei leerem Namen oder > 60 Zeichen), „Löschen“. Frühere: Zeilen „19.10.–25.10. · angelegt 19.10. · 14 Artikel“, Antippen → `onListe(id)`, Papierkorb mit Rückfrage.

- [ ] **Step 1: Tests** `EinkaufScreenTest`: `vorschauAbwaehlenUndAnlegen` („Liste anlegen (3)“ → Häkchen weg → „Liste anlegen (2)“ → zwei Plan-Artikel), `vorschauNichtsGeplant` („Im Zeitraum ist nichts geplant“, Knopf aus), `abgleichUebernehmen` (Hinweis → Blatt → „Übernehmen (1)“ → Hinweis weg), `detailsSpeichern` (Langdruck → Notiz „Bio“ → „Speichern“ → Kachel zeigt „Bio“), `detailsHerkunft` („200 g Curry (Mo) + 100 g Reispfanne (Mi)“), `loeschenMitRueckgaengig`, `rueckgaengigVerschwindetNachEinerWeile`, `fruehereOeffnen` (`onListe("alt")`), `listeLoeschenMitRueckfrage`.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** grün. **Step 5:** Commit `"V3 Phase 6d: Vorschau-, Abgleich-, Detail-Blatt"`.

### Task 11: Navigation

**Files:**
- Modify: `android/app3/build.gradle.kts` (`:feature:einkauf`), `navigation/FoodyNavHost.kt` (`Bildschirme.einkauf: @Composable (String?, NavHostController) -> Unit`; echt `EinkaufRoute(listeId, onListe = { nav.navigate(EinkaufListe(it)) }, onAktuelle = { nav.zumEinkauf() })`; Platzhalter `Platzhalter("Einkauf", listeId?.let { "Liste $it" } ?: "Einkaufsliste")`; `composable<Einkauf>` und `composable<EinkaufListe>` nutzen ihn; `rezeptDetail` reicht `onEinkauf = { nav.zumEinkauf() }`), `fun NavHostController.zumEinkauf()` (wie der Tabwechsel in `Hauptgeruest`: `popUpTo(start) { saveState = true }`, `launchSingleTop`, `restoreState`); KDoc „Einkauf ist bis 6d Platzhalter“ entfernen.
- Test: `android/app3/src/test/kotlin/de/foody/app3/navigation/NavigationTest.kt`

- [ ] **Step 1: Tests** `NavigationTest`: `einkaufTabZeigtListe` (Tab „Einkauf“ → „Einkaufsliste“), `deepLinkOeffnetListe` (`foody://einkauf/l1` → „Liste l1“), `zumEinkaufWechseltTab` (von `RezeptDetail` aus `zumEinkauf()` → Tab „Einkauf“ ausgewählt, „Zurück“ führt zu Heute); bestehende bleiben grün.
- [ ] **Step 2:** Rot. **Step 3:** Umsetzen. **Step 4:** `./gradlew :app3:testDebugUnitTest` grün. **Step 5:** Commit `"V3 Phase 6d: Navigation"`.

### Task 12: Screenshots, Sichtprüfung, Abschluss

**Files:**
- Create: `android/feature/einkauf/src/test/kotlin/de/foody/feature/einkauf/EinkaufScreenshotTest.kt`, Referenzbilder in `src/test/screenshots/`
- Modify: `.github/workflows/ci.yml` (`:feature:einkauf:verifyRoborazziDebug`), `docs/architecture.md` (Abschnitt „Einkauf (Phase 6d)“), `ToDoS/v3-plan.md` („Einkauf“ abhaken mit Ergebnis), `ToDoS/v3-abnahme.md` (Abschnitt „Einkauf“ und „Rezept direkt auf die Einkaufsliste“)

- [ ] **Step 1:** `EinkaufScreenshotTest`: Liste, leer, Vorschau-, Abgleich-, Detail-Blatt – hell/dunkel × Handy `w411dp-h1600dp` / Tablet `w1280dp-h1200dp`; Blätter mit `captureScreenRoboImage` (je Bild ein Test, `@OptIn(ExperimentalRoborazziApi::class)`), erfundene Daten, Toleranz 0,1 %.
- [ ] **Step 2:** `recordRoborazziDebug`, Bilder ansehen, Befunde beheben; `verifyRoborazziDebug` grün, Gegenprobe rot.
- [ ] **Step 3:** Sichtprüfung auf `Foody_Test` (Port 5556, AVD-Name prüfen) gegen den Test-Server: Liste aus dem Plan mit Serverdaten (Vorschau plausibel, Wasser fehlt), Artikel abhaken und zurück, eigenen Artikel eingeben, Plan in der Woche ändern → Hinweis → abgleichen, Rezept auf die Liste → „Ansehen“; Handy und Tablet-Größe; Screenshots an den Nutzer; danach angelegte Listen und Plan-Einträge entfernen, App deinstallieren, Emulator und Tunnel beenden.
- [ ] **Step 4:** Zeilencheck; `./gradlew check :app3:assembleDebug` und alle `verifyRoborazziDebug` (katalog, heute, rezepte, kochen, woche, einkauf) grün.
- [ ] **Step 5:** Doku; Commit `"V3 Phase 6d: Doku und Abschluss"`; Gesamtreview; PR gegen `main` mit Auto-Merge, „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 (2 braucht 1; 3–6 bauen auf 2; 8 braucht 2–5; 9/10 brauchen 7 und 8; 11 braucht 6 und 9). Ein Branch (`claude/v3-phase6d-einkauf`), ein PR am Ende.
