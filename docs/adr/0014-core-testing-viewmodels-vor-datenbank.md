# ADR 0014: `:core:testing` – ViewModels in Tests vor der Datenbank beenden

Status: angenommen (2026-10-28). PR #114.

**Kontext:** Die Feature-Tests bauen ViewModels mit einer Room-Datenbank im Speicher und schließen sie in `@After`.
Ein `viewModelScope` läuft danach weiter: Hat die letzte Änderung eines Tests einen Flow angestoßen, fragt er die schon
geschlossene Datenbank ab (`IllegalStateException` aus dem `SQLiteConnectionPool`). Das machte einen CI-Lauf von PR #110
rot und steckte in allen sieben Feature-Modulen.

**Entscheidung:** Ein kleines Modul `:core:testing` (Plugin `foody.android.library`) mit `ViewModelHalter`: Er hält die
ViewModels eines Tests in einem `ViewModelStore`; `schliessen()` beendet sie wie beim Verlassen eines Bildschirms. Das
Feature-Plugin bindet das Modul als `testImplementation` ein. Jeder Test gibt seine ViewModels hinein und ruft in
`@After` erst `schliessen()`, dann `db.close()`.

**Verworfen:**
- *Je Modul eine Kopie in den Testhilfen:* sieben gleiche Klassen.
- *Datenbank in Tests nicht schließen:* verdeckt den Fehler nur, Verbindungen blieben je Test offen.
- *`testFixtures` von `:core:data`:* braucht experimentelle Kotlin-Unterstützung im Android-Plugin.

**Folgen:** Ein `:core`-Modul mehr, das nie in die App gelangt (nur `testImplementation`).
