# Rezept-Einordnung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rezepte bekommen Mahlzeiten und Gänge (vermutet oder manuell); der Planer filtert beim Hinzufügen und im Wochenvorschlag streng nach der gewählten Mahlzeit.

**Architecture:** Reine Logik (`Mahlzeit`, `Gang`, `RezeptEinordnung`, Filter in `MealSuggestions`) in `domain`, JVM-getestet. Room v7 speichert nur manuelle Festlegungen (`recipe.mahlzeiten`, `recipe.gaenge`, kommagetrennt, `null` = vermuten) und normalisiert `meal_slot.slotType` auf Enum-Namen. Sync/Backup transportieren die Felder optional; die UI berechnet die Einordnung bei jedem Lesen.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2 mit `MigrationTestHelper`, Hilt, kotlinx.serialization, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-10-10-rezept-einordnung-design.md`

## Global Constraints

- Neue Bezeichner auf Deutsch (`Mahlzeit`, `Gang`, `Einordnung`, `RezeptEinordnung`, Felder `mahlzeiten`/`gaenge`); bestehende englische Bezeichner nicht umbenennen.
- Enum-Werte exakt: `Mahlzeit { FRUEHSTUECK, MITTAGESSEN, ABENDESSEN, SNACK }`, `Gang { VORSPEISE, HAUPTSPEISE, NACHSPEISE, BROTZEIT }`.
- `null` = nicht festgelegt (vermuten), `""` = bewusst leer; je Dimension getrennt.
- Groß-/Kleinschreibung wird bei Erkennung und Zuordnung ignoriert; Kotlin `lowercase()`, nie SQLite `lower()`.
- Vermutungen werden nie gespeichert.
- Keine destruktiven Migrationen; jede Versionserhöhung mit Migrationstest (`FoodyDatabase.kt`-Kommentar).
- Build-Umgebung (Bash): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. Instrumentation-Tests auf eigener Instanz: `emulator -avd Foody_Test -port 5556 -no-window -gpu host -no-snapshot`, dann `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.foody.app.<Test>`.

## Review Focus

1. **Rezept bearbeiten ohne die Chips anzufassen** – `RecipeRepository.save` baut die Entity neu; Festlegungen (und die Bewertung `rating`, die dort heute schon verloren geht) müssen erhalten bleiben. Test in Task 4.
2. **Freitext mit Leerzeichen** („ Abendessen “) aus alten Daten → wird zugeordnet (`trim`). Test in Task 1.
3. **Mahlzeit im Dialog wechseln, nachdem ein Rezept gewählt war** – ist es nicht mehr sichtbar, wird die Auswahl aufgehoben, damit nichts Unsichtbares eingeplant wird. Test in Task 8 (`PlanAuswahl`).
4. **„Neu mischen“ im Wochenvorschlag** behält die gewählte Mahlzeit. Test in Task 8.
5. **Kurzes Stichwort in längerem Wort** („Gebratener Reis“, „Fleischpflanzerl“) → keine Nachspeise. Test in Task 2.

---

### Task 1: `Mahlzeit` und `Gang` mit Text-Helfern

**Files:**
- Create: `domain/src/main/kotlin/de/foody/domain/Einordnung.kt`
- Test: `domain/src/test/kotlin/de/foody/domain/MahlzeitTest.kt`

**Interfaces:**
- Produces:
  - `enum class Mahlzeit { FRUEHSTUECK, MITTAGESSEN, ABENDESSEN, SNACK }` mit `companion object { fun ausText(text: String): Mahlzeit?; fun mengeAus(text: String?): Set<Mahlzeit>?; fun reihenfolge(slotType: String): Int; fun vorschlagFuer(zeit: java.time.LocalTime): Mahlzeit }`
  - `enum class Gang { VORSPEISE, HAUPTSPEISE, NACHSPEISE, BROTZEIT }` mit `companion object { fun mengeAus(text: String?): Set<Gang>? }`
  - `fun <E : Enum<E>> alsText(menge: Set<E>?): String?` – `null` → `null`, sonst Namen in Enum-Reihenfolge, kommagetrennt (`emptySet()` → `""`).

- [ ] **Step 1: Write the failing test** `MahlzeitTest`

```kotlin
@Test fun ausTextErkenntNamenUndDeutscheBezeichnung() {
    assertEquals(Mahlzeit.FRUEHSTUECK, Mahlzeit.ausText("Frühstück"))
    assertEquals(Mahlzeit.FRUEHSTUECK, Mahlzeit.ausText("FRÜHSTÜCK"))
    assertEquals(Mahlzeit.FRUEHSTUECK, Mahlzeit.ausText("FRUEHSTUECK"))
    assertEquals(Mahlzeit.ABENDESSEN, Mahlzeit.ausText(" abendessen "))
    assertEquals(Mahlzeit.MITTAGESSEN, Mahlzeit.ausText("Mittagessen"))
    assertEquals(Mahlzeit.SNACK, Mahlzeit.ausText("snack"))
    assertNull(Mahlzeit.ausText("Brunch"))
}
@Test fun mengeAusUnterscheidetNullUndLeer() {
    assertNull(Mahlzeit.mengeAus(null))
    assertEquals(emptySet(), Mahlzeit.mengeAus(""))
    assertEquals(setOf(Mahlzeit.ABENDESSEN), Mahlzeit.mengeAus("XYZ,ABENDESSEN"))
    assertEquals(setOf(Gang.VORSPEISE, Gang.HAUPTSPEISE), Gang.mengeAus("HAUPTSPEISE, VORSPEISE"))
}
@Test fun alsTextIstStabil() {
    assertNull(alsText<Mahlzeit>(null))
    assertEquals("", alsText(emptySet<Mahlzeit>()))
    assertEquals("MITTAGESSEN,ABENDESSEN", alsText(setOf(Mahlzeit.ABENDESSEN, Mahlzeit.MITTAGESSEN)))
}
@Test fun reihenfolgeImTag() {
    val sortiert = listOf("ABENDESSEN", "Brunch", "SNACK", "FRUEHSTUECK", "MITTAGESSEN").sortedBy(Mahlzeit::reihenfolge)
    assertEquals(listOf("FRUEHSTUECK", "MITTAGESSEN", "SNACK", "ABENDESSEN", "Brunch"), sortiert)
}
@Test fun vorschlagNachUhrzeit() {
    assertEquals(Mahlzeit.FRUEHSTUECK, Mahlzeit.vorschlagFuer(LocalTime.of(9, 59)))
    assertEquals(Mahlzeit.MITTAGESSEN, Mahlzeit.vorschlagFuer(LocalTime.of(10, 0)))
    assertEquals(Mahlzeit.ABENDESSEN, Mahlzeit.vorschlagFuer(LocalTime.of(14, 0)))
}
```

- [ ] **Step 2: Run** `./gradlew :domain:test --tests de.foody.domain.MahlzeitTest` → FAIL (unresolved reference `Mahlzeit`).
- [ ] **Step 3: Implement** in `Einordnung.kt`. `ausText`: `text.trim().lowercase()` gegen `name.lowercase()` und deutsche Bezeichnung („frühstück“, „mittagessen“, „abendessen“, „snack“). `reihenfolge`: FRUEHSTUECK 0, MITTAGESSEN 1, SNACK 2, ABENDESSEN 3, sonst 4 (Freitexte untereinander stabil; die Tageskarte sortiert sekundär nach Text). `mengeAus` trimmt Einträge und überspringt Unbekanntes.
- [ ] **Step 4: Run** dasselbe → PASS.
- [ ] **Step 5: Commit** `git commit -m "Einordnung: Mahlzeit und Gang mit Text-Helfern"`

---

### Task 2: `RezeptEinordnung` (Vermutung, manuell schlägt Vermutung)

**Files:**
- Modify: `domain/src/main/kotlin/de/foody/domain/Einordnung.kt`
- Test: `domain/src/test/kotlin/de/foody/domain/RezeptEinordnungTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `data class Einordnung(val mahlzeiten: Set<Mahlzeit>, val gaenge: Set<Gang>, val mahlzeitenVermutet: Boolean, val gaengeVermutet: Boolean)` mit `fun passtZu(m: Mahlzeit): Boolean` (leer = passt), `fun passtZu(m: Mahlzeit, gewaehlt: Set<Gang>): Boolean` (zusätzlich: `gewaehlt.isEmpty() || gaenge.any { it in gewaehlt }`), `val eingeordnet: Boolean` (= `mahlzeiten.isNotEmpty()`).
  - `object RezeptEinordnung { fun einordnen(name: String, tags: List<String>, mahlzeiten: Set<Mahlzeit>?, gaenge: Set<Gang>?): Einordnung }`

- [ ] **Step 1: Write the failing test** `RezeptEinordnungTest`

```kotlin
private fun e(name: String, tags: List<String> = emptyList(), m: Set<Mahlzeit>? = null, g: Set<Gang>? = null) =
    RezeptEinordnung.einordnen(name, tags, m, g)

@Test fun curryIstHauptspeiseMittagAbend() {
    val r = e("Gemüsecurry mit Reis")
    assertEquals(setOf(Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN), r.mahlzeiten)
    assertEquals(setOf(Gang.HAUPTSPEISE), r.gaenge)
    assertTrue(r.mahlzeitenVermutet && r.gaengeVermutet)
}
@Test fun nachspeiseIstAuchSnack() {
    val r = e("Tiramisu")
    assertEquals(setOf(Mahlzeit.SNACK), r.mahlzeiten); assertEquals(setOf(Gang.NACHSPEISE), r.gaenge)
}
@Test fun tagZaehltUnabhaengigVonGrossschreibung() =
    assertEquals(setOf(Mahlzeit.FRUEHSTUECK), e("Omas Liebling", listOf("FRÜHSTÜCK")).mahlzeiten)
@Test fun salatVereinigtTreffer() = assertEquals(setOf(Gang.VORSPEISE, Gang.HAUPTSPEISE), e("Kartoffelsalat").gaenge)
@Test fun kurzeStichwoerterNurAlsGanzesWort() {
    assertFalse(Gang.NACHSPEISE in e("Gebratener Reis").gaenge)
    assertFalse(Gang.NACHSPEISE in e("Fleischpflanzerl").gaenge)
    assertTrue(Gang.NACHSPEISE in e("Eis mit Beeren").gaenge)
}
@Test fun ohneTrefferPasstUeberall() {
    val r = e("Käsespätzle")
    assertTrue(r.mahlzeiten.isEmpty() && !r.eingeordnet && Mahlzeit.entries.all(r::passtZu))
}
@Test fun festgelegtSchlaegtVermutungJeDimension() {
    val r = e("Tiramisu", m = setOf(Mahlzeit.ABENDESSEN))
    assertEquals(setOf(Mahlzeit.ABENDESSEN), r.mahlzeiten); assertFalse(r.mahlzeitenVermutet)
    assertEquals(setOf(Gang.NACHSPEISE), r.gaenge); assertTrue(r.gaengeVermutet)
}
@Test fun bewusstLeerVermutetNicht() {
    val r = e("Pfannkuchen", g = emptySet())
    assertTrue(r.gaenge.isEmpty()); assertFalse(r.gaengeVermutet)
}
@Test fun gangFilter() {
    val r = e("Gemüsecurry")
    assertTrue(r.passtZu(Mahlzeit.ABENDESSEN, emptySet()))
    assertTrue(r.passtZu(Mahlzeit.ABENDESSEN, setOf(Gang.HAUPTSPEISE, Gang.NACHSPEISE)))
    assertFalse(r.passtZu(Mahlzeit.ABENDESSEN, setOf(Gang.NACHSPEISE)))
    assertFalse(r.passtZu(Mahlzeit.FRUEHSTUECK, emptySet()))
}
```

- [ ] **Step 2: Run** `./gradlew :domain:test --tests de.foody.domain.RezeptEinordnungTest` → FAIL.
- [ ] **Step 3: Implement.** Regeln als private Liste `Regel(stichwoerter: List<String>, mahlzeiten: Set<Mahlzeit>, gaenge: Set<Gang>)` mit exakt der Tabelle aus Spec 2.2. Wörter = Tags (ganz, `trim().lowercase()`) plus Namenswörter (`lowercase().split(Regex("[^\\p{L}]+"))`). Treffer: Stichwort mit ≥ 5 Zeichen → `wort.contains(stichwort)`; kürzer → `wort == stichwort`. Treffer vereinigen.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `git commit -m "Einordnung: Mahlzeit und Gang aus Tags und Namen vermuten"`

---

### Task 3: `MealSuggestions` filtert nach Mahlzeit

**Files:**
- Modify: `domain/src/main/kotlin/de/foody/domain/MealSuggestions.kt`
- Test: `domain/src/test/kotlin/de/foody/domain/MealSuggestionsTest.kt`

**Interfaces:**
- Produces: `Candidate(..., val mahlzeiten: Set<Mahlzeit> = emptySet())` (leer = passt überall); `suggest(candidates, days, seed, mahlzeit: Mahlzeit? = null)` – `null` = kein Filter. Bestehende Aufrufe bleiben gültig.

- [ ] **Step 1: Write the failing test** (in `MealSuggestionsTest`)

```kotlin
@Test fun nurPassendeKandidatenFuerDieMahlzeit() {
    val list = (1..10).map { Candidate("abend$it", false, null, null, mahlzeiten = setOf(Mahlzeit.ABENDESSEN)) } +
        Candidate("frueh", false, null, null, mahlzeiten = setOf(Mahlzeit.FRUEHSTUECK)) +
        Candidate("ueberall", false, null, null)
    repeat(20) { seed ->
        val plan = MealSuggestions.suggest(list, week, seed.toLong(), Mahlzeit.FRUEHSTUECK)
        assertEquals(setOf("frueh", "ueberall"), plan.values.toSet())
    }
}
```

- [ ] **Step 2: Run** `./gradlew :domain:test --tests de.foody.domain.MealSuggestionsTest` → FAIL.
- [ ] **Step 3: Implement** – Filter vor dem Ranking: `mahlzeit == null || c.mahlzeiten.isEmpty() || mahlzeit in c.mahlzeiten`. KDoc um den Filter ergänzen.
- [ ] **Step 4: Run** `./gradlew :domain:test` → alle PASS.
- [ ] **Step 5: Commit** `git commit -m "Wochenvorschlag: Kandidaten nach Mahlzeit filtern"`

---

### Task 4: Datenbank v7, Rezept speichern

**Files:**
- Modify: `app/src/main/java/de/foody/app/data/db/Entities.kt` (`RecipeEntity`)
- Modify: `app/src/main/java/de/foody/app/data/db/FoodyDatabase.kt` (`version = 7`, `MIGRATION_6_7`, `ALL_MIGRATIONS`)
- Create: `app/schemas/de.foody.app.data.db.FoodyDatabase/7.json` (vom Build erzeugt)
- Modify: `app/src/main/java/de/foody/app/data/repo/Repositories.kt` (`RecipeDraft`, `RecipeRepository.save`, `draftOf`)
- Modify: `app/src/main/java/de/foody/app/data/repo/Mappers.kt`
- Test: `app/src/androidTest/java/de/foody/app/MigrationTest.kt`, `app/src/androidTest/java/de/foody/app/FavoritesAndDedupTest.kt`

**Interfaces:**
- Consumes: Task 1, 2.
- Produces:
  - `RecipeEntity.mahlzeiten: String? = null`, `RecipeEntity.gaenge: String? = null` (Kommentar „Seit DB v7.“)
  - `RecipeDraft.mahlzeiten: Set<Mahlzeit>? = null`, `RecipeDraft.gaenge: Set<Gang>? = null`, `RecipeDraft.einordnungUebernehmen: Boolean = false` – nur bei `true` schreibt `save` die Draft-Werte; sonst bleiben die gespeicherten (Import, ältere Aufrufer).
  - `fun RecipeEntity.einordnung(): Einordnung` in `Mappers.kt` (ruft `RezeptEinordnung.einordnen(name, tags-Liste, Mahlzeit.mengeAus(mahlzeiten), Gang.mengeAus(gaenge))`).
  - `val MIGRATION_6_7`.

- [ ] **Step 1: Write the failing tests**

`MigrationTest`:
```kotlin
@Test fun migrate6To7AddsEinordnungAndNormalizesSlots() {
    helper.createDatabase(dbName, 6).use { db ->
        db.execSQL("INSERT INTO recipe (id, name, defaultServings, tags, createdAt, updatedAt, version, favorite) VALUES ('r', 'Curry', 2, '', 1, 1, 1, 0)")
        listOf("a" to "Abendessen", "b" to "frühstück", "c" to "FRÜHSTÜCK", "d" to "Brunch").forEach { (id, t) ->
            db.execSQL("INSERT INTO meal_slot (id, date, slotType, recipeId, servings, createdAt, updatedAt) VALUES ('$id', '2026-10-10', '$t', 'r', 2, 1, 1)")
        }
    }
    helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7).use { db ->
        db.query("SELECT mahlzeiten, gaenge FROM recipe").use { c -> c.moveToFirst(); assertTrue(c.isNull(0) && c.isNull(1)) }
        db.query("SELECT id, slotType FROM meal_slot ORDER BY id").use { c ->
            val m = buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
            assertEquals(mapOf("a" to "ABENDESSEN", "b" to "FRUEHSTUECK", "c" to "FRUEHSTUECK", "d" to "Brunch"), m)
        }
    }
}
```

`FavoritesAndDedupTest` (Review Focus 1):
```kotlin
@Test fun bearbeitenOhneEinordnungBehaeltFestlegungUndBewertung() = runBlocking {
    val id = recipes.save(RecipeDraft(null, "Curry", 2, ingredients = emptyList(),
        mahlzeiten = setOf(Mahlzeit.ABENDESSEN), gaenge = emptySet(), einordnungUebernehmen = true))
    recipes.setRating(id, 4)
    recipes.save(recipes.draftOf(id)!!.copy(name = "Curry scharf"))
    val r = recipes.get(id)!!
    assertEquals("ABENDESSEN", r.mahlzeiten); assertEquals("", r.gaenge); assertEquals(4, r.rating)
}
```

- [ ] **Step 2: Run** beide Tests per `connectedDebugAndroidTest` (Global Constraints) → FAIL (Kompilierfehler).
- [ ] **Step 3: Implement.**
  - `MIGRATION_6_7`: zwei `ALTER TABLE recipe ADD COLUMN … TEXT`; dann `SELECT id, slotType FROM meal_slot` lesen, je Zeile `Mahlzeit.ausText(slotType)?.name` und bei Treffer ungleich bisherigem Wert `UPDATE meal_slot SET slotType = ? WHERE id = ?` (gebundene Argumente). Trigger nicht anfassen.
  - `save`: `mahlzeiten = if (d.einordnungUebernehmen) alsText(d.mahlzeiten) else existing?.mahlzeiten`, analog `gaenge`; zusätzlich `rating = existing?.rating` (behebt den bestehenden Verlust beim Bearbeiten).
  - `draftOf`: füllt `mahlzeiten`/`gaenge` aus der Entity (`mengeAus`), `einordnungUebernehmen = false`.
- [ ] **Step 4: Build** `./gradlew :app:assembleDebug` erzeugt `7.json`; Tests erneut → PASS.
- [ ] **Step 5: Commit** inkl. `7.json`: `git commit -m "DB v7: Einordnung am Rezept, Mahlzeiten im Plan als feste Schlüssel"`

---

### Task 5: Sync und Backup

**Files:**
- Modify: `sync-protocol/src/main/kotlin/de/foody/sync/protocol/Payloads.kt` (`RecipePayload`)
- Modify: `app/src/main/java/de/foody/app/sync/SyncMapper.kt` (`recipe(...)` beide Richtungen, `mealSlot(id, p, …)`)
- Modify: `app/src/main/java/de/foody/app/data/repo/BackupRepository.kt` (`BackupDto.Recipe`, Export, Import inkl. `mealSlots`)
- Modify: `docs/architecture.md` (Sync-Abschnitt: DB v7, neue Felder, bekannte Grenze aus Spec 3.2)
- Test: `app/src/androidTest/java/de/foody/app/SyncApplierTest.kt`, `app/src/androidTest/java/de/foody/app/BackupZipTest.kt`

**Interfaces:**
- Consumes: Task 1 (`Mahlzeit.ausText`), Task 4 (Entity-Felder).
- Produces: `RecipePayload.mahlzeiten: String? = null`, `RecipePayload.gaenge: String? = null`; `BackupDto.Recipe` dieselben Felder (am Ende der Parameterliste, Default `null`).

- [ ] **Step 1: Write the failing tests**
  - `SyncApplierTest.einordnungUndSlotTypUeberstehenSync`: Rezept-Payload mit `mahlzeiten = "ABENDESSEN"`, `gaenge = ""` anwenden → Entity hat `"ABENDESSEN"` / `""`; `SyncMapper.recipe(entity, …)` ergibt wieder dieselben Werte. `MealSlotPayload(slotType = "Abendessen")` anwenden → Entity `slotType == "ABENDESSEN"`; `"Brunch"` bleibt `"Brunch"`.
  - `BackupZipTest.einordnungImBackup`: Rezept mit `mahlzeiten = "FRUEHSTUECK"`, Slot mit `"ABENDESSEN"` exportieren, DB leeren, importieren → Werte gleich. Zweiter Fall: Backup-JSON mit `"slotType": "Frühstück"` und ohne `mahlzeiten` importieren → `slotType == "FRUEHSTUECK"`, `mahlzeiten == null`.
- [ ] **Step 2: Run** beide → FAIL.
- [ ] **Step 3: Implement** – Felder durchreichen; beim Eingang (Sync und Backup-Import) `slotType = Mahlzeit.ausText(p.slotType)?.name ?: p.slotType`. Ausgang unverändert.
- [ ] **Step 4: Run** beide plus `./gradlew :sync-protocol:test :server:test` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync/Backup: Einordnung übertragen, Mahlzeit beim Eingang zuordnen"`

---

### Task 6: Rezept-Editor und Rezeptdetail

**Files:**
- Create: `app/src/main/java/de/foody/app/ui/common/EinordnungUi.kt`
- Modify: `app/src/main/java/de/foody/app/ui/recipes/RecipeEditorViewModel.kt`, `RecipeEditorScreen.kt`, `RecipeDetailScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/de/foody/app/RecipeEditorEinordnungTest.kt`

**Interfaces:**
- Consumes: Task 2, Task 4 (`RecipeDraft`-Felder, `RecipeEntity.einordnung()`).
- Produces:
  - `@StringRes fun Mahlzeit.label(): Int` (vorhandene `slot_breakfast`/`slot_lunch`/`slot_dinner`/`slot_snack`), `@StringRes fun Gang.label(): Int`, `@Composable fun slotLabel(slotType: String): String` (Enum → Label, sonst Freitext).
  - `@Composable fun EinordnungChips(titel: String, werte: List<E>, gewaehlt: Set<E>, vermutet: Boolean, label: (E) -> Int, onToggle: (E) -> Unit, onZuruecksetzen: (() -> Unit)?)` für beide Dimensionen.
  - `RecipeEditorState.mahlzeiten: Set<Mahlzeit>?`, `.gaenge: Set<Gang>?` (null = nicht festgelegt); `RecipeEditorViewModel.toggleMahlzeit(m)`, `toggleGang(g)`, `mahlzeitenZuruecksetzen()`, `gaengeZuruecksetzen()`, `val einordnung: Einordnung` (aus Name, Tags, State berechnet).
- Neue Strings: `gang_vorspeise` „Vorspeise“, `gang_hauptspeise` „Hauptspeise“, `gang_nachspeise` „Nachspeise“, `gang_brotzeit` „Brotzeit“, `einordnung_titel` „Einordnung“, `einordnung_mahlzeit` „Passt zu“, `einordnung_gang` „Gang“, `einordnung_vermutet` „vermutet“, `einordnung_zuruecksetzen` „Zurücksetzen“.

- [ ] **Step 1: Write the failing test** `RecipeEditorEinordnungTest` (ViewModel mit In-Memory-DB wie `PlannerSuggestTest`):
  - Neues Rezept „Tiramisu“: `vm.einordnung.gaenge == {NACHSPEISE}`, `gaengeVermutet`.
  - `toggleMahlzeit(ABENDESSEN)` bei vermutet `{SNACK}` → State `{SNACK, ABENDESSEN}` (Vermutung wird übernommen plus Änderung), nach Speichern `entity.mahlzeiten == "ABENDESSEN,SNACK"`.
  - `gaengeZuruecksetzen()` → State `null`, gespeichert `gaenge == null`.
  - Alle Mahlzeit-Chips abwählen → gespeichert `""`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** ViewModel (State, Toggles, `save` mit `einordnungUebernehmen = true`, Laden aus `draftOf`); Editor-Block unter dem Tags-Feld; Detail: Pills aus `entity.einordnung()`, vermutete mit `alpha(0.6f)`.
- [ ] **Step 4: Run** → PASS; Sichtprüfung im Emulator (Editor-Block, Detail-Pills, Dunkelmodus).
- [ ] **Step 5: Commit** `git commit -m "Rezept: Einordnung im Editor festlegen und im Detail zeigen"`

---

### Task 7: Rezeptliste – Gang-Filter und „Nicht eingeordnet“

**Files:**
- Modify: `app/src/main/java/de/foody/app/ui/recipes/RecipeListViewModel.kt`, `RecipeListScreen.kt`, `strings.xml`
- Test: `app/src/androidTest/java/de/foody/app/RecipeListViewModelTest.kt`

**Interfaces:**
- Consumes: Task 4 (`RecipeEntity.einordnung()`), Task 6 (`Gang.label()`).
- Produces: `RecipeListUiState.gaenge: Set<Gang>`, `.nurNichtEingeordnet: Boolean`; `onToggleGang(g: Gang)`, `onToggleNichtEingeordnet()` (in `SavedStateHandle` wie `diets`); `resetDiscover()` setzt beide zurück. String `filter_nicht_eingeordnet` „Nicht eingeordnet“.

- [ ] **Step 1: Write the failing test**: Rezepte „Tiramisu“, „Gemüsecurry“, „Käsespätzle“. `onToggleGang(NACHSPEISE)` → nur Tiramisu; zusätzlich `HAUPTSPEISE` → Tiramisu + Curry (ODER innerhalb Gang); zurück, `onToggleNichtEingeordnet()` → nur Käsespätzle.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** – Filter im vorhandenen `matching`-Block; Chips in der vorhandenen Filterleiste.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `git commit -m "Rezeptliste: nach Gang und „Nicht eingeordnet“ filtern"`

---

### Task 8: Planer – Hinzufügen, Wochenvorschlag, Tageskarte

**Files:**
- Create: `domain/src/main/kotlin/de/foody/domain/PlanAuswahl.kt`
- Test: `domain/src/test/kotlin/de/foody/domain/PlanAuswahlTest.kt`
- Modify: `app/src/main/java/de/foody/app/ui/planner/PlannerViewModel.kt`, `PlannerScreen.kt`, `strings.xml`
- Modify: `docs/domain-rules.md` (Abschnitt Einordnung), `ToDoS/einkaufsliste-und-essenstracking.md` (Abschnitte 5 und 6 erledigt)
- Test: `app/src/androidTest/java/de/foody/app/PlannerSuggestTest.kt`

**Interfaces:**
- Consumes: Task 2 (`Einordnung.passtZu(m, gewaehlt)`), Task 3, Task 6 (`slotLabel`, Labels).
- Produces:
  - `object PlanAuswahl { fun <T> filtern(rezepte: List<T>, einordnung: (T) -> Einordnung, mahlzeit: Mahlzeit, gaenge: Set<Gang>, alle: Boolean): List<T>; fun <T> auswahlBehalten(gewaehltId: String?, sichtbar: List<T>, id: (T) -> String): String? }` – `auswahlBehalten` gibt `null` zurück, wenn die gewählte ID nicht sichtbar ist.
  - `PlannerViewModel.Proposal(seed, mahlzeit: Mahlzeit, entries)`; `suggest(mahlzeit: Mahlzeit = Mahlzeit.ABENDESSEN, seed: Long = …)`; `reshuffle()` behält `mahlzeit`; `acceptProposal()` ohne Parameter, nutzt `proposal.mahlzeit.name`.
  - `PlannerViewModel.add(date, mahlzeit: Mahlzeit, recipeId, servings)` speichert `mahlzeit.name`.
  - Strings: `planner_alle_rezepte` „Alle Rezepte zeigen“, `planner_keine_passenden` „Keine passenden Rezepte“, `planner_alle_zeigen` „Alle zeigen“.

- [ ] **Step 1: Write the failing tests**

`PlanAuswahlTest`:
```kotlin
private val curry = "curry" to RezeptEinordnung.einordnen("Gemüsecurry", emptyList(), null, null)
private val tiramisu = "tiramisu" to RezeptEinordnung.einordnen("Tiramisu", emptyList(), null, null)
private val spaetzle = "spaetzle" to RezeptEinordnung.einordnen("Käsespätzle", emptyList(), null, null)
private val alle = listOf(curry, tiramisu, spaetzle)
private fun f(m: Mahlzeit, g: Set<Gang> = emptySet(), a: Boolean = false) = PlanAuswahl.filtern(alle, { it.second }, m, g, a).map { it.first }

@Test fun strengNachMahlzeit() = assertEquals(listOf("curry", "spaetzle"), f(Mahlzeit.ABENDESSEN))
@Test fun gangFilterSchliesstUneingeordneteAus() = assertEquals(listOf("curry"), f(Mahlzeit.ABENDESSEN, setOf(Gang.HAUPTSPEISE)))
@Test fun alleHebtFilterAuf() = assertEquals(listOf("curry", "tiramisu", "spaetzle"), f(Mahlzeit.FRUEHSTUECK, setOf(Gang.NACHSPEISE), a = true))
@Test fun unsichtbareAuswahlWirdAufgehoben() {
    assertNull(PlanAuswahl.auswahlBehalten("tiramisu", listOf(curry), { it.first }))
    assertEquals("curry", PlanAuswahl.auswahlBehalten("curry", listOf(curry), { it.first }))
}
```

`PlannerSuggestTest` (vorhandenes Setup):
  - `vorschlagFuerFruehstueckNurPassendUndJeMahlzeitLeer`: Rezepte „Porridge“ (Frühstück), „Gemüsecurry“; heute ist schon ein `ABENDESSEN` (Curry) geplant. `vm.suggest(Mahlzeit.FRUEHSTUECK, seed = 1)` → Vorschlag enthält genau einen Eintrag: Porridge für heute (heute fehlt das Frühstück; Curry passt nicht).
  - `neuMischenBehaeltMahlzeit`: nach `suggest(FRUEHSTUECK)` und `reshuffle()` ist `proposal.mahlzeit == FRUEHSTUECK`; `acceptProposal()` legt Slots mit `slotType == "FRUEHSTUECK"` an.
- [ ] **Step 2: Run** `./gradlew :domain:test --tests de.foody.domain.PlanAuswahlTest` und `PlannerSuggestTest` → FAIL.
- [ ] **Step 3: Implement.**
  - `PlanAuswahl` in `domain`.
  - ViewModel: leere Tage = Tage ab heute ohne Slot mit `Mahlzeit.ausText(slotType) == mahlzeit`; `Candidate.mahlzeiten` aus `entity.einordnung().mahlzeiten`; `suggest(..., mahlzeit)`.
  - `AddSlotDialog`: Mahlzeit-Chips (Einfachauswahl, Vorauswahl `Mahlzeit.vorschlagFuer(LocalTime.now())`), Gang-Chips (Mehrfach), Schalter „Alle Rezepte zeigen“; `RecipePicker` bekommt die gefilterte Liste; nach jeder Filteränderung `recipeId = PlanAuswahl.auswahlBehalten(...)`; leeres Ergebnis → Hinweis plus Knopf, der `alle = true` setzt. Freies Textfeld entfernen; `field_slot`-String bleibt als Überschrift der Chips.
  - `ProposalDialog`: Mahlzeit-Chips (Standard Abendessen), Wechsel ruft `suggest(neueMahlzeit, p.seed)`.
  - Tageskarte: Slots sortiert nach `Mahlzeit.reihenfolge(slotType)`, dann `slotType`; Etikett `slotLabel(slot.slotType).uppercase()`.
  - Doku-Updates aus der Files-Liste.
- [ ] **Step 4: Run** `./gradlew :domain:test` und `PlannerSuggestTest` → PASS; vollständige Suite `connectedDebugAndroidTest` → PASS; Sichtprüfung Dialog, Vorschlag, Tageskarte im Emulator.
- [ ] **Step 5: Commit** `git commit -m "Planer: Rezepte nach Mahlzeit und Gang auswählen, Vorschlag je Mahlzeit"`
