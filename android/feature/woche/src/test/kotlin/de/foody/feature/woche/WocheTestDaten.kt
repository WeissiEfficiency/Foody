package de.foody.feature.woche

import androidx.lifecycle.SavedStateHandle
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.data.woche.WocheRepository
import de.foody.core.testing.ViewModelHalter
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.EmptyCoroutineContext

/** Verstellbare Uhr für Tests („heute“ wechselt). */
class TestUhr(var jetzt: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = jetzt

    fun auf(tag: LocalDate) {
        jetzt = tag.atTime(12, 0).toInstant(ZoneOffset.UTC)
    }
}

/** Mittwoch, 28.10.2026 – in KW 44 (Mo 26.10.–So 1.11.). */
val MITTWOCH: LocalDate = LocalDate.of(2026, 10, 28)
val MONTAG: LocalDate = LocalDate.of(2026, 10, 26)

/** Fünf erfundene Hauptspeisen fürs Abendessen, h1 bis h5. */
suspend fun FoodyDatenbank.hauptspeisen() = listOf(
    "h1" to "Gemüsecurry", "h2" to "Linsenpasta", "h3" to "Pilzrisotto", "h4" to "Bohneneintopf", "h5" to "Kartoffelauflauf",
).forEach { (id, name) ->
    rezeptDao().upsert(
        RezeptEntity(
            id = id, name = name, defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null, notes = null,
            tags = "", archivedAt = null, createdAt = 1, updatedAt = 1, mahlzeiten = "ABENDESSEN", gaenge = "HAUPTSPEISE",
        ),
    )
}

/** Repository und ViewModel wie in der App, mit fester bzw. verstellbarer Uhr; außerhalb der Komposition anlegen. */
class WocheTestUmgebung(val db: FoodyDatenbank, val uhr: TestUhr = TestUhr(Instant.EPOCH).apply { auf(MITTWOCH) }) {
    private val app = RuntimeEnvironment.getApplication()
    val repo = WocheRepository(db, RezeptRepository(db, FotoAblage(app)) { 1_000L }, { uhr.millis() }, EmptyCoroutineContext)
    val ziel = TageszielSpeicher(app)

    /** Hält die ViewModels; `viewModels.schliessen()` gehört in `@After` vor `db.close()` ([ViewModelHalter]). */
    val viewModels = ViewModelHalter()

    fun vm(saved: SavedStateHandle = SavedStateHandle()) = viewModels.behalten(WocheViewModel(saved, repo, ziel, uhr))
}
