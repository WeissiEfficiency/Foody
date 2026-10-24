package de.foody.feature.heute.tagebuch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.heute.RezeptKurz
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.feature.heute.mahlzeitNachUhrzeit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class EintragenWeg { REZEPT, ZUTAT, PLAN, FREI }

data class ZutatKurz(val id: String, val name: String)

/** Eingaben des Blatts; Zahlen als Text, wie getippt. */
data class Eingaben(
    val datum: LocalDate = LocalDate.EPOCH,
    val mahlzeit: Mahlzeit = Mahlzeit.ABENDESSEN,
    val weg: EintragenWeg = EintragenWeg.REZEPT,
    val suche: String = "",
    val rezeptId: String? = null,
    val portionen: String = "1",
    val zutatId: String? = null,
    val menge: String = "",
    val einheit: MeasureUnit = MeasureUnit.GRAM,
    val planEintragId: String? = null,
    val freiName: String = "",
    val freiKcal: String = "",
    val freiEiweiss: String = "",
    val freiKh: String = "",
    val freiFett: String = "",
)

/** Eingaben plus Trefferlisten: Suche als Teilstring ohne Groß/klein, höchstens [MAX_TREFFER]. */
data class EintragenZustand(
    val eingaben: Eingaben,
    val rezepte: List<RezeptKurz>,
    val zutaten: List<ZutatKurz>,
    val plan: List<GeplanteMahlzeit>,
) {
    val mahlzeit: Mahlzeit get() = eingaben.mahlzeit
}

const val MAX_TREFFER = 30

/** Einheiten zur Auswahl beim Eintragen einer Zutat. */
val EINTRAGEN_EINHEITEN = listOf(MeasureUnit.GRAM, MeasureUnit.MILLILITER, MeasureUnit.PIECE, MeasureUnit.TABLESPOON, MeasureUnit.TEASPOON)

private val SCHRITT = BigDecimal("0.5")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EintragenViewModel @Inject constructor(
    heute: HeuteRepository,
    private val tagebuch: TagebuchRepository,
    db: FoodyDatenbank,
) : ViewModel() {
    private val eingaben = MutableStateFlow(Eingaben())

    /** Heutige, noch nicht eingetragene Plan-Einträge des gewählten Tags. */
    private val offenerPlan = eingaben.map { it.datum }.distinctUntilChanged().flatMapLatest { tag ->
        combine(heute.planHeuteUndMorgen(tag), tagebuch.amTag(tag)) { plan, eintraege ->
            val uebernommen = eintraege.mapNotNull { it.planEintragId }.toSet()
            plan.filter { it.datum == tag && it.planEintragId !in uebernommen }
        }
    }

    private val zutatenKatalog = db.zutatDao().alleFlow().map { z -> z.map { ZutatKurz(it.id, it.canonicalName) }.sortedBy { it.name.lowercase() } }

    val zustand: StateFlow<EintragenZustand> =
        combine(eingaben, heute.rezeptVorschlaege(), zutatenKatalog, offenerPlan) { e, rezepte, zutaten, plan ->
            val s = e.suche.trim()
            EintragenZustand(
                eingaben = e,
                rezepte = rezepte.filter { it.titel.contains(s, ignoreCase = true) }.take(MAX_TREFFER),
                zutaten = zutaten.filter { it.name.contains(s, ignoreCase = true) }.take(MAX_TREFFER),
                plan = plan,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, EintragenZustand(Eingaben(), emptyList(), emptyList(), emptyList()))

    val speichernMoeglich: StateFlow<Boolean> =
        eingaben.map(::gueltig).stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Neues Blatt: alles zurück; Mahlzeit aus der angetippten Zeile, sonst nach Uhrzeit. */
    fun oeffnen(datum: LocalDate, mahlzeit: Mahlzeit?, uhrzeit: LocalTime = LocalTime.now()) {
        eingaben.value = Eingaben(datum = datum, mahlzeit = mahlzeit ?: mahlzeitNachUhrzeit(uhrzeit))
    }

    fun weg(w: EintragenWeg) = eingaben.update { it.copy(weg = w, suche = "") }
    fun mahlzeit(m: Mahlzeit) = eingaben.update { it.copy(mahlzeit = m) }
    fun suche(text: String) = eingaben.update { it.copy(suche = text) }
    fun rezeptWaehlen(id: String) = eingaben.update { it.copy(rezeptId = id) }
    fun portionen(text: String) = eingaben.update { it.copy(portionen = text) }

    /** − / + in Schritten von 0,5; nie unter 0,5. */
    fun portionenSchritt(mehr: Boolean) = eingaben.update {
        val alt = zahl(it.portionen) ?: BigDecimal.ONE
        it.copy(portionen = zahlText((if (mehr) alt + SCHRITT else alt - SCHRITT).max(SCHRITT)))
    }

    fun zutatWaehlen(id: String) = eingaben.update { it.copy(zutatId = id) }
    fun menge(text: String) = eingaben.update { it.copy(menge = text) }
    fun einheit(u: MeasureUnit) = eingaben.update { it.copy(einheit = u) }
    fun planWaehlen(id: String) = eingaben.update { it.copy(planEintragId = id) }
    fun freiName(text: String) = eingaben.update { it.copy(freiName = text) }
    fun freiKcal(text: String) = eingaben.update { it.copy(freiKcal = text) }
    fun freiEiweiss(text: String) = eingaben.update { it.copy(freiEiweiss = text) }
    fun freiKh(text: String) = eingaben.update { it.copy(freiKh = text) }
    fun freiFett(text: String) = eingaben.update { it.copy(freiFett = text) }

    /** Speichert nach dem gewählten Weg; `null`, wenn die Eingaben ungültig sind oder Rezept/Zutat/Plan fehlen. */
    suspend fun speichern(): String? {
        val e = eingaben.value
        if (!gueltig(e)) return null
        return when (e.weg) {
            EintragenWeg.REZEPT -> tagebuch.ausRezept(e.datum, e.mahlzeit, e.rezeptId!!, zahl(e.portionen)!!)
            EintragenWeg.ZUTAT -> tagebuch.ausZutat(e.datum, e.mahlzeit, e.zutatId!!, zahl(e.menge)!!, e.einheit)
            EintragenWeg.PLAN -> tagebuch.ausPlan(e.planEintragId!!)
            EintragenWeg.FREI -> tagebuch.frei(
                e.datum, e.mahlzeit, e.freiName.trim(), e.freiKcal.trim().toInt(),
                optional(e.freiEiweiss), optional(e.freiKh), optional(e.freiFett),
            )
        }
    }
}

private fun BigDecimal?.positiv() = this != null && signum() > 0

/** Leeres optionales Feld → `null`; sonst die Zahl (ungültig ist schon in [gueltig] ausgeschlossen). */
private fun optional(text: String): BigDecimal? = zahl(text)

private fun optionalGueltig(text: String) = text.isBlank() || zahl(text)?.let { it.signum() >= 0 } == true

/** Portionen > 0, Menge > 0, „Frei“ mit Name und kcal ≥ 0 (ganzzahlig), Makros leer oder ≥ 0. */
internal fun gueltig(e: Eingaben): Boolean = when (e.weg) {
    EintragenWeg.REZEPT -> e.rezeptId != null && zahl(e.portionen).positiv()
    EintragenWeg.ZUTAT -> e.zutatId != null && zahl(e.menge).positiv()
    EintragenWeg.PLAN -> e.planEintragId != null
    EintragenWeg.FREI -> e.freiName.isNotBlank() && (e.freiKcal.trim().toIntOrNull() ?: -1) >= 0 &&
        optionalGueltig(e.freiEiweiss) && optionalGueltig(e.freiKh) && optionalGueltig(e.freiFett)
}
