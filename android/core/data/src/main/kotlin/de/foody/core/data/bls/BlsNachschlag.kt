package de.foody.core.data.bls

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.core.data.Rechnen
import de.foody.core.data.zutaten.Packungswerte
import de.foody.domain.BlsLebensmittel
import de.foody.domain.BlsSuche
import de.foody.domain.BlsTreffer
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Nachschlagen im Bundeslebensmittelschlüssel: ein nur lesbarer Auszug als Asset (`bls/bls_4_0.tsv`, erzeugt mit
 * `werkzeuge/bls_auszug.py`), getrennt vom Zutatenkatalog und ohne Sync. Geladen wird beim ersten Suchen, danach bleibt
 * er im Speicher (gut 7.000 Zeilen). Tests geben die Zeilen über [oeffnen] direkt vor.
 */
@Singleton
class BlsNachschlag(
    private val oeffnen: () -> Sequence<String>,
    private val rechnen: CoroutineContext,
) {
    @Inject constructor(@ApplicationContext kontext: Context, @Rechnen rechnen: CoroutineContext) : this(
        { kontext.assets.open(ASSET).bufferedReader(Charsets.UTF_8).readLines().asSequence() },
        rechnen,
    )

    private val sperre = Mutex()
    private var geladen: List<BlsLebensmittel>? = null

    /** Treffer für [eingabe] ([BlsSuche.suchen]); leer bei weniger als zwei Zeichen. */
    suspend fun suchen(eingabe: String, max: Int = MAX): List<BlsTreffer> =
        if (eingabe.isBlank()) emptyList() else withContext(rechnen) { BlsSuche.suchen(alle(), eingabe, max) }

    private suspend fun alle(): List<BlsLebensmittel> = sperre.withLock { geladen ?: blsAuszugLesen(oeffnen()).also { geladen = it } }

    companion object {
        const val ASSET = "bls/bls_4_0.tsv"
        const val MAX = 15
    }
}

/** Herkunft wie bei den Startdaten: „BLS 4.0 (MRI): <Name im BLS>“. */
fun BlsLebensmittel.quelle(): String = "BLS 4.0 (MRI): $name"

/** Werte des Treffers als Packung (je 100 g), damit der Katalog sie wie einen Scan übernimmt. */
fun BlsTreffer.alsPackung(): Packungswerte =
    Packungswerte(name = vorschlagName, basis = NutrientBasis.PER_100_G, werte = lebensmittel.werte, strichcode = null, quelle = lebensmittel.quelle())

/** Spalten des Auszugs nach Code und Name, in dieser Reihenfolge. */
private val NAEHRSTOFFE = listOf(
    Nutrient.ENERGY_KJ, Nutrient.PROTEIN_G, Nutrient.CARBS_G, Nutrient.SUGAR_G, Nutrient.FAT_G, Nutrient.FIBER_G, Nutrient.SALT_G,
)

/** Liest den Auszug: Kommentarzeilen (`#`) und die Kopfzeile fallen weg, leere Werte heißen „unbekannt“. */
internal fun blsAuszugLesen(zeilen: Sequence<String>): List<BlsLebensmittel> = zeilen
    .filterNot { it.isBlank() || it.startsWith("#") || it.startsWith("code\t") }
    .map { zeile ->
        val felder = zeile.split('\t')
        val werte = buildMap {
            NAEHRSTOFFE.forEachIndexed { i, n -> felder.getOrNull(i + 2)?.takeIf { it.isNotBlank() }?.let { put(n, BigDecimal(it)) } }
        }
        BlsLebensmittel(code = felder[0], name = felder[1], werte = werte)
    }
    .toList()
