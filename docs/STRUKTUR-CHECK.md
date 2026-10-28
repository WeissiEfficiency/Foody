# Struktur-Check: Foody – 2026-10-27

**Fazit:** 🟠 Der Code ist sauber aufgeteilt – Module hängen nur in eine Richtung voneinander ab, und die
Architektur-Doku wird Phase für Phase mitgeführt. Das größte Problem ist der Einstieg: README und eine fehlende
`CLAUDE.md` führen eine neue Session zur eingefrorenen v1 (`:app`) statt zur V3 (`:app3`), an der seit Phase 3 gebaut wird.

| Bereich | Bewertung | Kurz |
|---|---|---|
| Ordnerstruktur | 🟢 | klare Trennung `android/` · `shared/` · `server/`; lokal liegen alte Build-Reste und Worktrees herum |
| Logik & Aufteilung | 🟢 | sauberer Modulgraph; v1/V3-Doppelung ist gewollt (ADR 0011) und endet in Phase 8 |
| Dokumentation | 🔴 | README zeigt den v1-Stand, `CLAUDE.md` fehlt, `security.md` kennt die V3 nicht |

**Geprüft:** ganzes Repo auf `main` (`2761ada`), Stack Kotlin/Android (Compose, Room, Hilt) + Ktor-Server, Gradle mit
Convention-Plugins. Übersichts-Skript auf einem `git archive`-Export der 833 versionierten Dateien (im Arbeitsordner
blieb es an `.claude/worktrees` hängen). Modulgraph aus allen `build.gradle.kts`, README, `docs/` (Architektur,
Domänenregeln, Sicherheit, Roadmap, ADR 0001–0012), `ToDoS/`, Manifeste von `:app` und `:app3`.
**Nicht geprüft:** Bau- und Testbefehle aus der README (`./gradlew check`, `connectedCheck`) – gelesen, nicht
ausgeführt (lange Gradle-Läufe, teils Emulator nötig). Die rund 540 „Doku-Verweise ins Leere“ des Skripts sind
überwiegend relative Pfadangaben (`liste/`, `glas/`) und wurden nur stichprobenartig geprüft.

## 🔴 Kritisch

### ✅ K1 · README schickt neue Sessions in die eingefrorene v1 `[Doku]` `[Claude Code]`
**Was passiert:** Wer (Mensch oder Claude) nach einer Pause die README liest, findet nur `:app`. Er baut
`./gradlew :app:assembleDebug`, ändert Dateien unter `android/app/` – und arbeitet an der v1, die laut ADR 0011 „bis
zum Umstieg nicht mehr verändert“ wird. Die eigentliche App (`:app3`) kommt in der README nicht ein einziges Mal vor.
**Ursache:** `README.md:46–57` – die Modultabelle endet beim Stand von Phase 0: es fehlen `:app3`, `:core:data`,
`:core:sync`, `:core:designsystem`, `:katalog` und sechs der sieben Feature-Module. `README.md:53` behauptet,
`:feature:heute` sei „noch nicht in der App eingebunden“ – `android/app3/build.gradle.kts` bindet es ein.
`README.md:64–67` nennt nur Befehle für `:app`.
**Beleg:** `grep -c app3 README.md` → 0; Abhängigkeiten von `:app3` aus `android/app3/build.gradle.kts`.
**Vorschlag:** Modultabelle vervollständigen, mit Spalte „v1 (eingefroren) / V3“; oben ein Satz „Neue Arbeit nur in
`:app3`, `:core:*`, `:feature:*`; `:app` und `:sync-protocol` entfallen in Phase 8“; Bau-/Testbefehle für `:app3`
ergänzen. 1 Datei, ~20 Zeilen.

## 🟠 Wichtig

### ✅ W1 · Keine `CLAUDE.md` – die Projektregeln stehen verstreut in Plänen `[Claude Code]`
**Was passiert:** Jede neue Session muss sich die Regeln neu zusammensuchen oder rät: deutsche Bezeichner in der V3,
englische in der v1 (stehen nur in einzelnen `docs/superpowers/plans/*.md`), „v1 nicht anfassen“, „nach Änderungen an
`shared/api-protocol/` Server neu bauen“, JDK 21, Emulator-/Test-Server-Setup. Fehlt eine davon, entsteht Code im
falschen Modul oder im falschen Stil.
**Ursache:** Repo-Root – keine `CLAUDE.md` (Skript: „✗ CLAUDE.md: fehlt“).
**Vorschlag:** Kurze `CLAUDE.md` (< 80 Zeilen): Was ist Foody, aktueller Stand (V3 Phase 6 fertig, als Nächstes 7),
wo neue Arbeit hingehört, Bau-/Testbefehle, Benennungs- und Sprachregeln, Verweise auf `docs/architecture.md`,
`ToDoS/v3-plan.md`, ADRs. 1 neue Datei.

### W2 · `security.md` beschreibt nur die v1 `[Doku]`
**Was passiert:** Die Sicherheitsdoku sagt „Nur `MainActivity` (Launcher). Keine Intent-Filter für fremde Daten“
und nennt Pfade aus `android/app/src/debug`. Die V3 öffnet aber Deep Links `foody://…` mit `BROWSABLE` – also auch aus
dem Browser – und hat eigene Network-Security-Configs. Wer die V3 auf Angriffsfläche prüft, verlässt sich auf eine
falsche Liste; nach Phase 8 beschreibt die Doku nichts mehr, was existiert.
**Ursache:** `docs/security.md:9–17`; `android/app3/src/main/AndroidManifest.xml:22–28`.
**Vorschlag:** Abschnitt „V3 (`:app3`)“ ergänzen: Deep Links (welche Argumente, wie geprüft), Network-Security-Config
Debug/Release, KI nur über den Server (ADR 0011). 1 Datei.

### ✅ W3 · README beschreibt Funktionen und Datenschutz des alten Stands `[Doku]`
**Was passiert:** Die Funktionsliste wirbt mit Vorrat, „Aus dem Vorrat“, Teilen als Text und Planer-Modi 1/2/3/7 Tage –
alles laut ADR 0011 gestrichen. Der Abschnitt Datenschutz (`README.md:121`) sagt „Kein Konto, kein Server, kein
Internetzugriff“, obwohl beide Apps `INTERNET` anfordern, synchronisieren, Open Food Facts abfragen und die V3 KI über
den Server nutzen wird. Das widerspricht `docs/roadmap.md` und `docs/security.md`.
**Ursache:** `README.md:8–40`, `README.md:119–122`.
**Vorschlag:** Funktionsliste auf den V3-Umfang (`ToDoS/v3-abnahme.md`) umstellen oder klar als „v1, bis Phase 8“
kennzeichnen; Datenschutz an `security.md` angleichen. Lässt sich mit K1 in einem Schritt erledigen.

### ✅ W4 · Veraltete Pläne und Regeln ohne Kennzeichnung `[Doku]` `[Tot/Doppelt]`
**Was passiert:** Planungsdokumente liegen an drei Orten (`ToDoS/`, `docs/superpowers/plans|specs/`, `docs/roadmap.md`).
Einige sind überholt, ohne es zu sagen – eine Session, die „die Projektstruktur-Regeln“ befolgt, sucht ein
`server/web/`, das es nicht mehr gibt.
**Ursache:** `ToDoS/projektstruktur-regeln.md:7` (nennt `server/web/`, Stand 2026-10-11);
`ToDoS/web-frontend-plan.md` (Web-App gestrichen, kein Hinweis „abgelöst“ – anders als `v2-plan.md`);
`ToDoS/optimierungsplan.md` (Stand 2026-10-10, Zahlen der v1, Status unklar).
**Beleg:** Skript-Abschnitt „Doku-Verweise“; Kopfzeilen der Dateien gelesen.
**Vorschlag:** In den drei Dateien einen Kopfhinweis „Abgelöst/Stand …“ wie in `v2-plan.md`; Zeile 7 der
Strukturregeln auf `android/` · `shared/` · `server/{sync,start}` korrigieren. Umzug von `ToDoS/` nach `docs/` nur,
wenn gewünscht (viele Links in ADRs und Roadmap). 3 Dateien.

## 🟢 Nice-to-have
- **N1** `[Doku]` `docs/architecture.md:3–6` – Einleitung nennt Module und Abhängigkeitsrichtung ohne `:core:*`,
  `:app3`, `:katalog`; der Rest des Dokuments ist aktuell.
- **N2** `[Repo-Hygiene]` lokal – im Root liegen `app/`, `domain/`, `sync-protocol/` (nur `build/`, vom 12.10., allein
  `app/` 117 MB): Reste vom Umzug nach `android/`/`shared/`. Nicht im Repo, aber verwirrend und wird von OpenCloud
  mitsynchronisiert. Löschen.
- **N3** `[Repo-Hygiene]` lokal – drei alte Worktrees in `.claude/worktrees/` (detached HEAD, u. a. auf `10dad3c`) mitten
  im OpenCloud-Ordner: volle Kopien samt Builds, bremsen Sync und Werkzeuge, die das Repo durchsuchen. Nach Prüfung mit
  `git worktree remove` entfernen.
- **N4** `[Repo-Hygiene]` `gradle/gradle-daemon-jvm.properties` ist unversioniert – einchecken (legt die Daemon-JVM für
  alle fest) oder in `.gitignore`.
- **N5** `[Doku]` kein Changelog – bei den vielen Phasen-PRs wäre eine kurze Liste „was ist in V3 schon drin“ nützlich;
  übernimmt heute teils `ToDoS/v3-plan.md` mit Häkchen.

## Was gut ist
- **Modulgraph ohne Querverbindungen:** Feature-Module hängen nie voneinander ab, `:core:*` nie von Features,
  `:core:sync` nutzt `:server-sync` nur im Test; Server und App teilen nur `shared/`.
- **Entscheidungen sind nachvollziehbar:** ADR 0001–0012 lückenlos, abgelöste ADRs und Pläne verweisen auf ihre
  Nachfolger; `docs/architecture.md` hat für jede V3-Phase einen gepflegten Abschnitt.
- **Secrets sauber getrennt:** `keystore.properties`, `server/.env`, Schlüsseldateien und echte DB-Kopien sind
  ignoriert, Vorlagen (`*.example`) liegen bei.

## Nebenbei aufgefallen
- `:app3` und `:app` nutzen dieselbe `applicationId` `de.foody.app` und denselben Debug-Suffix. Läuft im Alltag noch die
  v1 als „Foody Debug“, ersetzt ein `:app3:installDebug` auf diesem Handy die Alltags-App samt Migration 9 → 10
  (Vorrat weg, v1-Sync danach nicht mehr möglich). Vermutlich als Umstiegsweg gewollt – vor dem nächsten Install aufs
  echte Handy bewusst prüfen; gehört ins Code-Review/Phase 8.

## Vorschlag
Jetzt: K1 + W3 (README in einem Schritt), W1 (`CLAUDE.md`), W4 (drei Kopfhinweise, eine Zeile). Danach W2 und N1.
N2–N3 sind lokales Aufräumen ohne Commit. Soll ich K1, W1, W3 und W4 umsetzen?

## Nachtrag Delta – 2026-10-27 (`2761ada..572cee2`)

**Umfang:** #110 „V3 Phase 6e3: Packung scannen“ (`8ce3b79`, 102 Dateien, neues Modul `:core:scan`, ADR 0013, Spec,
Plan) und #111 „V3-Theme in Ofenwarm“ (`572cee2`, nur `FoodyTheme.kt` + Test + Bilder). Geprüft: Modulgraph,
Plugins, Manifest von `:core:scan`, Doku-Änderungen (README, `architecture.md`, ADR 0011/0013, `v3-plan.md`,
`v3-abnahme.md`, `security.md`). Bau und Tests nicht ausgeführt.

**Fazit Delta:** 🟢 Das Delta selbst ist strukturell sauber. Die Doku-Funde von oben werden aber größer, weil README
und `security.md` wieder nicht mitgezogen wurden.

**Was gut ist:**
- `:core:scan` hängt nur nach unten (`:core:data`, `:domain`, `:core:designsystem`); genutzt wird es von `:feature:heute`,
  `:feature:zutaten`, `:feature:einstellungen` und `:app3`. Features kennen sich weiterhin nicht. Der Werte-Typ
  `Packungswerte` liegt bewusst in `:core:data`, damit kein Zyklus entsteht – im ADR begründet.
- Doku im selben PR: ADR 0013 neu, ADR 0011 mit Verweis auf die Ablösung, `architecture.md` mit eigenem Abschnitt
  und einem Hinweis am alten v1-Abschnitt, `v3-plan.md` (Modulliste, Ergebnis 6e3) und Abnahme nachgeführt.
- Benennung passt zur V3-Regel (deutsche Bezeichner, `de.foody.core.scan`, Schnittstellen hinter `ScanModul`).
  Dass der Scan-Ablauf in v1 und `:core:scan` doppelt existiert, ist gewollt und endet in Phase 8.

**Neue Funde:**
- **N6** `[Doku]` `docs/architecture.md:530–531` sagt `foody.android.library` für `:core:*`, aber
  `:core:designsystem` und das neue `:core:scan` nutzen `foody.android.feature` (sie brauchen Compose). Folge: Wer ein
  neues `:core`-Modul nach Doku anlegt, wundert sich über fehlendes Compose. Vorschlag: einen Halbsatz ergänzen
  („`:core:*` mit Compose nutzen `.feature`“).
- **N7** `[Ordner]` `settings.gradle.kts:48, 63–65` – `include(":core:scan")` steht zwischen `include(":core:designsystem")` (Zeile 48)
  und dessen `projectDir`-Zeile. Funktioniert, aber die Datei wird mit jeder Phase schwerer zu lesen. Vorschlag beim
  nächsten Anfassen: je Modul `include` und `projectDir` direkt untereinander, nach `core`/`feature` gruppiert.

**Alte Funde nach dem Delta:**

| Fund | Stand | Warum |
|---|---|---|
| K1 README führt zur v1 | 🔴 verschärft | `README.md` unverändert; jetzt fehlen zwölf Module, darunter `:core:scan`. `grep -c "app3\|core:scan" README.md` → 0 |
| W1 keine `CLAUDE.md` | 🟠 unverändert | weiterhin keine; die Regel „neue gemeinsame Teile nach `:core:*`, Features kennen sich nicht“ steht jetzt zusätzlich nur in ADR 0013/`v3-plan.md` |
| W2 `security.md` nur v1 | 🟠 verschärft | `security.md` nicht angefasst. Die V3 hat jetzt einen zweiten FileProvider (`ScanFotoProvider`, `${applicationId}.scan`, `cache/scan/`) neben dem aus `:feature:rezepte`, eine eigene Open-Food-Facts-Anbindung (`OffProduktSuche`), und der Schalter liegt in den Einstellungen statt unter „Mehr“ (`security.md:17`) |
| W3 README Funktionen/Datenschutz | 🟠 unverändert | README unverändert |
| W4 veraltete Pläne | 🟠 unverändert | `projektstruktur-regeln.md`, `web-frontend-plan.md`, `optimierungsplan.md` unverändert; die neuen Plan- und Spec-Dateien liegen am gewohnten Ort |
| N1 Einleitung `architecture.md` | 🟢 unverändert | Kopf nennt weiter keine `:core:*`; der neue Absatz zu `:core:scan` steht unten im Abschnitt „App V3: Daten und Sync“ |

**Vorschlag:** an der Reihenfolge ändert sich nichts – K1 + W3 (README), W1 (`CLAUDE.md`), W4; danach W2 (jetzt mit den
Scan-Punkten) sowie N1 und N6 in einem Schritt in `architecture.md`.

## Umgesetzt – 2026-10-28 (Branch `claude/struktur-doku`)

- ✅ **K1** `README.md`: Stand-Absatz (V3 vs. eingefrorene v1, wo neue Arbeit hingehört), vollständige Modultabelle
  inkl. `:app3`, `:core:*`, sieben `:feature:*`, `:katalog`, Abhängigkeitsrichtung, Befehle für `:app3`, Hinweis auf
  die gemeinsame App-ID.
- ✅ **W1** `CLAUDE.md` neu (54 Zeilen): Phase, Modulregeln, Befehle inkl. Roborazzi, Sprach-/Room-/Server-Regeln,
  welche Doku je Phase mitzupflegen ist.
- ✅ **W3** `README.md`: Funktionsliste auf V3-Umfang, gestrichene v1-Funktionen genannt, Datenschutz an Sync,
  Open Food Facts und geplante KI angeglichen; Release-Abschnitt als v1-only gekennzeichnet.
- ✅ **W4** `ToDoS/projektstruktur-regeln.md` Strukturzeile korrigiert; `web-frontend-plan.md` und
  `optimierungsplan.md` mit Hinweis „Abgelöst“.
- Offen: W2 (`security.md` für die V3), N1–N7.
