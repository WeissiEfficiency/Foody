package de.foody.feature.heute.tagebuch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.heute.RezeptKurz
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.data.zutaten.nameBereinigt
import de.foody.core.data.zutaten.zahlAus
import de.foody.core.data.zutaten.zahlText
import de.foody.core.data.zutaten.zutatMitNamen
import de.foody.core.designsystem.SolangeAppOffen
import de.foody.core.scan.PackungScan
import de.foody.core.scan.ScanAusgang
import de.foody.core.scan.ScanLauf
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.feature.heute.mahlzeitNachUhrzeit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class EintragenWeg { REZEPT, ZUTAT, PLAN, FREI }

data class ZutatKurz(val id: String, val name: String)

/** Eingaben des Blatts; Zahlen als Text, wie getippt. Serializable: Sie liegen im SavedStateHandle (Prozessende). */
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
    /** Gescannte Packung in „Frei“ (Phase 6e3); die p-Felder sind ihr Formular, Werte je 100 g/ml. */
    val packung: Packungswerte? = null,
    val pName: String = "",
    val pMenge: String = "",
    val pBasis: NutrientBasis = NutrientBasis.PER_100_G,
    val pKcal: String = "",
    val pEiweiss: String = "",
    val pKh: String = "",
    val pFett: String = "",
    val alsZutat: Boolean = false,
    /** Hat der Nutzer das Häkchen selbst gesetzt, bestimmt der Name die Vorgabe nicht mehr. */
    val alsZutatVomNutzer: Boolean = false,
) : java.io.Serializable

/** Eingaben plus Trefferlisten: Suche als Teilstring ohne Groß/klein, höchstens [MAX_TREFFER]. */
data class EintragenZustand(
    val eingaben: Eingaben,
    val rezepte: List<RezeptKurz>,
    val zutaten: List<ZutatKurz>,
    val plan: List<GeplanteMahlzeit>,
    /** Name der Katalog-Zutat zum Packungsnamen ([zutatMitNamen]); `null`: Das Häkchen legt eine neue an. */
    val vorhandeneZutat: String? = null,
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
    /** Eingaben, Auftrag und Scan-Knopf überstehen damit ein Prozessende (etwa während das Kamerafoto entsteht). */
    private val gespeichert: SavedStateHandle,
    heute: HeuteRepository,
    private val tagebuch: TagebuchRepository,
    db: FoodyDatenbank,
    scan: PackungScan,
) : ViewModel() {
    private val eingaben = gespeichert.getMutableStateFlow(EINGABEN, Eingaben())

    /** Der Scan-Knopf in „Frei“; Erkennung läuft im viewModelScope weiter, auch wenn gedreht wird. */
    val scanLauf = ScanLauf(scan, viewModelScope, gespeichert, ::packungUebernehmen, ::imKatalog)

    /** Während des Speicherns und nach Erfolg gesperrt (bis zum nächsten [oeffnen]): ein Doppeltipp legt nichts doppelt an. */
    private val gesperrt = MutableStateFlow(false)

    /** Einmal gerechnet für Plan und Rezeptsuche; nur solange das Blatt offen ist. */
    private val rezepte = heute.rezepteKurz().shareIn(viewModelScope, SolangeAppOffen, replay = 1)

    /** Heutige, noch nicht eingetragene Plan-Einträge des gewählten Tags. */
    private val offenerPlan = eingaben.map { it.datum }.distinctUntilChanged().flatMapLatest { tag ->
        combine(heute.planHeuteUndMorgen(tag, rezepte), tagebuch.amTag(tag)) { plan, eintraege ->
            val uebernommen = eintraege.mapNotNull { it.planEintragId }.toSet()
            plan.filter { it.datum == tag && it.planEintragId !in uebernommen }
        }
    }

    /** Der ganze Katalog, auch für die Häkchen-Vorgabe beim Packungsnamen. */
    private val katalog = db.zutatDao().alleFlow().stateIn(viewModelScope, SolangeAppOffen, emptyList())

    private val zutatenKatalog = katalog.map { z -> z.map { ZutatKurz(it.id, it.canonicalName) }.sortedBy { it.name.lowercase() } }

    val zustand: StateFlow<EintragenZustand> =
        combine(eingaben, heute.rezeptVorschlaege(rezepte), zutatenKatalog, offenerPlan) { e, rezepte, zutaten, plan ->
            val s = e.suche.trim()
            EintragenZustand(
                eingaben = e,
                rezepte = rezepte.filter { it.titel.contains(s, ignoreCase = true) }.take(MAX_TREFFER),
                zutaten = zutaten.filter { it.name.contains(s, ignoreCase = true) }.take(MAX_TREFFER),
                plan = plan,
                vorhandeneZutat = e.packung?.let { vorhandene(e.pName) },
            )
        }.stateIn(viewModelScope, SolangeAppOffen, EintragenZustand(Eingaben(), emptyList(), emptyList(), emptyList()))

    val speichernMoeglich: StateFlow<Boolean> =
        combine(eingaben, gesperrt) { e, sperre -> !sperre && gueltig(e) }.stateIn(viewModelScope, SolangeAppOffen, false)

    private var letzterAuftrag: String?
        get() = gespeichert[AUFTRAG]
        set(wert) {
            gespeichert[AUFTRAG] = wert
        }

    private val fokus = Channel<Unit>(Channel.CONFLATED)

    /** Ein Wert: Das Mengenfeld von „Zutat“ soll den Fokus bekommen (nach „im Katalog“). */
    val mengeFokus: Flow<Unit> = fokus.receiveAsFlow()

    /**
     * Neues Blatt: alles zurück; Mahlzeit aus der angetippten Zeile, sonst nach Uhrzeit. Derselbe [auftrag] erneut
     * (Neuaufbau nach Drehen) lässt die Eingaben stehen.
     */
    fun oeffnen(datum: LocalDate, mahlzeit: Mahlzeit?, uhrzeit: LocalTime = LocalTime.now(), auftrag: String? = null) {
        if (auftrag != null && auftrag == letzterAuftrag) return
        letzterAuftrag = auftrag
        eingaben.value = Eingaben(datum = datum, mahlzeit = mahlzeit ?: mahlzeitNachUhrzeit(uhrzeit))
        gesperrt.value = false
    }

    fun weg(w: EintragenWeg) = eingaben.update { it.copy(weg = w, suche = "") }
    fun mahlzeit(m: Mahlzeit) = eingaben.update { it.copy(mahlzeit = m) }
    fun suche(text: String) = eingaben.update { it.copy(suche = text) }
    fun rezeptWaehlen(id: String) = eingaben.update { it.copy(rezeptId = id) }
    fun portionen(text: String) = eingaben.update { it.copy(portionen = text) }

    /** − / + in Schritten von 0,5; nie unter 0,5. */
    fun portionenSchritt(mehr: Boolean) = eingaben.update {
        val alt = zahlAus(it.portionen) ?: BigDecimal.ONE
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

    /** Scan-Ergebnis in „Frei“: Name bereinigt (höchstens 200 Zeichen), Werte ungerundet, Häkchen nach dem Namen. */
    fun packungUebernehmen(p: Packungswerte) {
        val name = nameBereinigt(p.name.orEmpty()).take(MAX_NAME)
        eingaben.update {
            it.copy(
                packung = p, pName = name, pMenge = "", pBasis = p.basis ?: NutrientBasis.PER_100_G,
                pKcal = p.kcalText, pEiweiss = p.feldText(Nutrient.PROTEIN_G), pKh = p.feldText(Nutrient.CARBS_G),
                pFett = p.feldText(Nutrient.FAT_G), alsZutat = vorhandene(name) == null, alsZutatVomNutzer = false,
            )
        }
    }

    /** „Zurück zu Frei“. */
    fun packungVerwerfen() = eingaben.update { it.copy(packung = null) }

    /** Den Strichcode trägt schon eine Zutat: Weg „Zutat“ mit ihr, Suche auf ihrem Namen, Fokus auf die Menge. */
    fun imKatalog(t: ScanAusgang.ImKatalog) {
        eingaben.update { it.copy(weg = EintragenWeg.ZUTAT, zutatId = t.id, suche = t.name) }
        fokus.trySend(Unit)
    }

    /** Neuer Name: Die Häkchen-Vorgabe folgt ihm, bis der Nutzer selbst gewählt hat. */
    fun pName(text: String) = eingaben.update {
        it.copy(pName = text, alsZutat = if (it.alsZutatVomNutzer) it.alsZutat else vorhandene(text) == null)
    }

    fun pMenge(text: String) = eingaben.update { it.copy(pMenge = text) }
    fun pBasis(b: NutrientBasis) = eingaben.update { it.copy(pBasis = b) }
    fun pKcal(text: String) = eingaben.update { it.copy(pKcal = text) }
    fun pEiweiss(text: String) = eingaben.update { it.copy(pEiweiss = text) }
    fun pKh(text: String) = eingaben.update { it.copy(pKh = text) }
    fun pFett(text: String) = eingaben.update { it.copy(pFett = text) }
    fun alsZutat(an: Boolean) = eingaben.update { it.copy(alsZutat = an, alsZutatVomNutzer = true) }

    private fun vorhandene(name: String): String? = zutatMitNamen(katalog.value, name)?.canonicalName

    /** Speichert nach dem gewählten Weg; `null`, wenn die Eingaben ungültig sind oder Rezept/Zutat/Plan fehlen. */
    suspend fun speichern(): String? {
        val e = eingaben.value
        if (!gueltig(e) || !gesperrt.compareAndSet(expect = false, update = true)) return null
        val id = when (e.weg) {
            EintragenWeg.REZEPT -> tagebuch.ausRezept(e.datum, e.mahlzeit, e.rezeptId!!, zahlAus(e.portionen)!!)
            EintragenWeg.ZUTAT -> tagebuch.ausZutat(e.datum, e.mahlzeit, e.zutatId!!, zahlAus(e.menge)!!, e.einheit)
            EintragenWeg.PLAN -> tagebuch.ausPlan(e.planEintragId!!)
            EintragenWeg.FREI -> if (e.packung != null) {
                tagebuch.ausPackung(e.datum, e.mahlzeit, e.pName, zahlAus(e.pMenge)!!, packungsEinheit(e.pBasis), formularWerte(e), e.alsZutat)
            } else {
                tagebuch.frei(
                    e.datum, e.mahlzeit, e.freiName.trim(), e.freiKcal.trim().toInt(),
                    optional(e.freiEiweiss), optional(e.freiKh), optional(e.freiFett),
                )
            }
        }
        if (id == null) gesperrt.value = false
        return id
    }
}

private fun BigDecimal?.positiv() = this != null && signum() > 0

/** Leeres optionales Feld → `null`; sonst die Zahl (ungültig ist schon in [gueltig] ausgeschlossen). */
private fun optional(text: String): BigDecimal? = zahlAus(text)

private fun optionalGueltig(text: String) = text.isBlank() || zahlAus(text)?.let { it.signum() >= 0 } == true

/** Portionen > 0, Menge > 0, „Frei“ mit Name und kcal ≥ 0 (ganzzahlig), Makros leer oder ≥ 0; Packung: [packungGueltig]. */
internal fun gueltig(e: Eingaben): Boolean = when (e.weg) {
    EintragenWeg.REZEPT -> e.rezeptId != null && zahlAus(e.portionen).positiv()
    EintragenWeg.ZUTAT -> e.zutatId != null && zahlAus(e.menge).positiv()
    EintragenWeg.PLAN -> e.planEintragId != null
    EintragenWeg.FREI if e.packung != null -> packungGueltig(e)
    EintragenWeg.FREI -> e.freiName.isNotBlank() && e.freiName.trim().length <= MAX_NAME && (e.freiKcal.trim().toIntOrNull() ?: -1) >= 0 &&
        optionalGueltig(e.freiEiweiss) && optionalGueltig(e.freiKh) && optionalGueltig(e.freiFett)
}

private val HUNDERT = BigDecimal(100)
private val TAUSEND = BigDecimal(1000)

private fun imBereich(text: String, max: BigDecimal) = zahlAus(text)?.let { it.signum() >= 0 && it <= max } == true

/** Wie der Zutateneditor (6e2): Name 1–200 Zeichen, Menge > 0, kcal Pflicht 0–1.000, Makros leer oder 0–100. */
private fun packungGueltig(e: Eingaben): Boolean {
    val name = nameBereinigt(e.pName)
    return name.isNotEmpty() && name.length <= MAX_NAME && zahlAus(e.pMenge).positiv() && imBereich(e.pKcal, TAUSEND) &&
        listOf(e.pEiweiss, e.pKh, e.pFett).all { it.isBlank() || imBereich(it, HUNDERT) }
}

/** Die Basis der Packung bestimmt die Einheit der Menge. */
internal fun packungsEinheit(b: NutrientBasis): MeasureUnit = if (b == NutrientBasis.PER_100_ML) MeasureUnit.MILLILITER else MeasureUnit.GRAM

/**
 * Packungswerte, wie sie im Formular stehen: kJ nach der Energie-Regel ([Packungswerte.kjFuer]), Eiweiß, KH und Fett
 * aus den Feldern (leer → keiner), alles andere (Zucker, Ballaststoffe, Salz) unverändert aus der Packung.
 */
internal fun formularWerte(e: Eingaben): Packungswerte {
    val p = requireNotNull(e.packung)
    val werte = p.werte.toMutableMap()
    val felder = listOf(
        Nutrient.ENERGY_KJ to p.kjFuer(e.pKcal),
        Nutrient.PROTEIN_G to zahlAus(e.pEiweiss),
        Nutrient.CARBS_G to zahlAus(e.pKh),
        Nutrient.FAT_G to zahlAus(e.pFett),
    )
    for ((n, wert) in felder) if (wert == null) werte.remove(n) else werte[n] = wert
    return p.copy(basis = e.pBasis, werte = werte)
}

private const val EINGABEN = "eintragen"
private const val AUFTRAG = "eintragenAuftrag"
