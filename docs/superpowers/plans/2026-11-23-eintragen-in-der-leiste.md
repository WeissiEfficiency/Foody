# Eintragen in der Leiste – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Leiste unten und die Leiste links bekommen einen fünften Punkt „Eintragen“ zwischen Heute und Rezepte. Er öffnet die Eintragen-Seite für heute.

**Architecture:** `Hauptgeruest` trennt die vier Reiter (Routen, aktiver Reiter) von den fünf angezeigten Punkten. Der Punkt an Stelle 1 ist eine Aktion: Er ruft `eintragenOeffnen` auf, statt einen Reiter zu wechseln. `NavZiel` bekommt dafür ein Merkmal `aktion`. Mit diesem Merkmal ist der Punkt für TalkBack ein Knopf statt ein Reiter und nie ausgewählt.

**Tech Stack:** Kotlin, Compose, Navigation Compose, Robolectric, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-11-23-eintragen-in-der-leiste-design.md`

## Global Constraints

- Reihenfolge: Heute · Eintragen · Rezepte · Woche · Einkauf, in Leiste und Rail.
- Symbol `Icons.Outlined.AddCircleOutline`.
- Wörter: `stilText("Eintragen", bayrisch = "Eitragn", kreuzer = "Erfassen")`; die Beschreibung für TalkBack ist immer „Eintragen“.
- Tippen öffnet `EintragenStart(LocalDate.now())`, also ohne Mahlzeit und ohne Sofort-Start, über `NavHostController.eintragenOeffnen`.
- Der Punkt „Eintragen“ ist nie markiert.
- 360 dp, Schrift 90–130 %: Jedes Wort steht in einer Zeile, ohne Abschneiden. 200 % zählt nicht.
- Konventionen aus `CLAUDE.md`: deutsche Bezeichner und KDoc, `allWarningsAsErrors`, ktlint.

## Review Focus

1. Eintragen aus einem Rezept öffnen, dann Zurück oder Speichern → wieder im Rezept, und der Reiter Rezepte ist markiert. Test: `eintragenAusRezeptFuehrtZurueck`.
2. Nach dem Verschieben der Stellen markieren Rezepte, Woche und Einkauf weiter den richtigen Punkt. Test: `handyZeigtLeiste` und `zurueckVomTabFuehrtZuHeute` bleiben grün; neu ist `reiterNachEintragenRichtigMarkiert`.
3. Doppeltipp auf „Eintragen“ während der Überblendung → genau eine Seite. Test: `eintragenAusLeisteNurEinmal`.
4. Tablet (Rail): Der Punkt ist da und öffnet Eintragen. Test: `tabletRailOeffnetEintragen`.
5. 130 % auf 360 dp: „Eintragen“ steht in einer Zeile, ohne Überlauf. Test: `fuenfPunkteBei130`.

---

### Task 1: Eintragen als Aktion in der Leiste

**Files:**
- Modify: `android/core/designsystem/src/main/kotlin/de/foody/core/designsystem/komponenten/FoodyNavigation.kt`. `NavZiel` bekommt `val aktion: Boolean = false`. `NavEintrag` nutzt bei `aktion` statt `selectable(Role.Tab)` ein `clickable(role = Role.Button)` mit derselben Interaktionsquelle und ohne Auswahlzustand.
- Modify: `android/app/src/main/kotlin/de/foody/app/navigation/Hauptgeruest.kt` (Punkte, Index-Abbildung, KDoc „vier Tabs“).
- Test: `android/app/src/test/kotlin/de/foody/app/navigation/NavigationTest.kt`
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/komponenten/GrundbausteineTest.kt`
- Doku: `docs/architecture.md` (wo die Navigation die vier Reiter nennt) und `README.md`, falls dort „vier Tabs“ steht.

**Interfaces:**
- Consumes: `NavHostController.eintragenOeffnen(start: EintragenStart)` (FoodyNavHost.kt), `EintragenStart(datum: LocalDate, mahlzeit: Mahlzeit? = null, sofort: SofortStart? = null)`.
- Produces: `data class NavZiel(val text: String, val symbol: ImageVector, val beschreibung: String? = null, val aktion: Boolean = false)`.

- [ ] **Schritt 1: Failing tests schreiben**

In `NavigationTest` (Platzhalter-Bildschirme wie die anderen Tests; `knopf(text)` = Knoten mit dem Text und `Role.Button`):
- `eintragenInDerLeiste` (w411dp): Die Leiste zeigt die fünf Wörter in der Reihenfolge Heute, Eintragen, Rezepte, Woche, Einkauf (x-Positionen steigend). Klick auf „Eintragen“ → `Eintragen`-Ziel obenauf mit dem Datum von heute und `mahlzeit == null`, die Leiste ist weg.
- `eintragenAusRezeptFuehrtZurueck` (w411dp): Reiter Rezepte, dann `RezeptDetail("r1")`, Klick auf „Eintragen“, dann Zurück → „Rezept r1“ ist zu sehen und `tab("Rezepte")` ist ausgewählt.
- `reiterNachEintragenRichtigMarkiert` (w411dp): Woche wählen → `tab("Woche")` ausgewählt. Der Punkt „Eintragen“ ist nie `isSelectable`; ein `tab("Eintragen")` findet keinen Knoten.
- `eintragenAusLeisteNurEinmal` (w411dp): „Eintragen“ zweimal per `SemanticsActions.OnClick` während der Überblendung auslösen, nach dem Muster des Doppeltipp-Tests aus #301 → genau ein `Eintragen` im Stapel.
- `tabletRailOeffnetEintragen` (w1280dp-h800dp): In der Rail steht „Eintragen“; ein Klick öffnet das Ziel.
- `fuenfPunkteBei130` (w360dp-h780dp, Schrift 1,3 über `RuntimeEnvironment.setFontScale(1.3f)`): Für jedes der fünf Wörter liefert `GetTextLayoutResult` `lineCount == 1` und `!hasVisualOverflow`.

In `GrundbausteineTest`: `aktionIstKnopf`. Eine Leiste mit `NavZiel("Eintragen", …, aktion = true)` an Stelle 1 und `aktiv = 1`: Der Knoten hat `Role.Button`, keinen `Selected`-Zustand, und ein Klick ruft `onWahl(1)` auf.

- [ ] **Schritt 2: Rot sehen**

Run: `JAVA_HOME=/c/Users/Stefan/.jdks/jbr-21.0.11 ./gradlew :app:testDebugUnitTest --tests "de.foody.app.navigation.NavigationTest" :core:designsystem:testDebugUnitTest --tests "*GrundbausteineTest"`
Expected: Die neuen Tests schlagen fehl (kein Knoten „Eintragen“ bzw. kein Parameter `aktion`).

- [ ] **Schritt 3: Umsetzen**

- `NavZiel.aktion` und die Rolle in `NavEintrag` wie oben.
- `Hauptgeruest`: Die Reiterliste `tabs` bleibt bei vier Routen. Die angezeigte Liste hat fünf `NavZiel`; Stelle 1 ist `NavZiel("Eintragen", Icons.Outlined.AddCircleOutline, aktion = true)` mit dem Stilwort.
- Abbildung zwischen Stelle und Reiter: Stelle 0 ↔ Reiter 0, Stelle 2..4 ↔ Reiter 1..3. `aktiv` wird zur Stelle des aktiven Reiters. `waehlen(1)` ruft `navController.eintragenOeffnen(EintragenStart(LocalDate.now()))` auf, die anderen Stellen das bisherige `waehlen` mit dem Reiter.
- Die Bayrisch- und Kreuzer-Wörter kommen in die vorhandene `woerter`-Liste.

- [ ] **Schritt 4: Grün sehen**

Run: derselbe Befehl wie in Schritt 2. Expected: alles PASS, auch die bestehenden Navigationstests.

- [ ] **Schritt 5: Bilder und Gesamtlauf**

Run: `./gradlew check ktlintPruefen`, danach `verifyRoborazziDebug` für `:app` und `:core:designsystem`. Wo Bilder mit der Hauptleiste abweichen, mit `recordRoborazziDebug` neu aufnehmen und ansehen. Die `GrundbausteineTest`-Leiste mit eigener Viererliste bleibt unverändert. Expected: EXIT 0.

- [ ] **Schritt 6: Doku und Commit**

Im Navigationsabschnitt von `docs/architecture.md` steht dann: fünf Punkte, „Eintragen“ ist eine Aktion, kein Reiter. KDoc von `Hauptgeruest` anpassen. Commit: „Leiste: Eintragen zwischen Heute und Rezepte“.
