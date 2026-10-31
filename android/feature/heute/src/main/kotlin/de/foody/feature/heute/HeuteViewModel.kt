package de.foody.feature.heute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.heute.OffenerEinkauf
import de.foody.core.data.heute.RezeptKurz
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.SolangeSichtbar
import de.foody.core.designsystem.komponenten.SyncAnzeige
import de.foody.core.sync.SyncLage
import de.foody.core.sync.SyncLageQuelle
import de.foody.domain.DailyPicks
import de.foody.domain.EintragWerte
import de.foody.domain.Mahlzeit
import de.foody.domain.Summe
import de.foody.domain.Tagebuch
import de.foody.feature.heute.tagebuch.MAHLZEITEN
import de.foody.feature.heute.tagebuch.eintragMahlzeit
import de.foody.feature.heute.tagebuch.werte
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class HeuteZustand(
    val datum: LocalDate,
    val naechste: NaechsteMahlzeit?,
    val inspiration: RezeptKurz?,
    val tagebuch: TagebuchHeute,
    val einkauf: OffenerEinkauf?,
    val sync: SyncAnzeige?,
    val datenbankLeer: Boolean,
)

/** Tagebuch des Tages: Summe gegen das Ziel und die vier Mahlzeiten mit Einträgen und offenen Plan-Vorschlägen. */
data class TagebuchHeute(val summe: Summe, val ziel: Int?, val mahlzeiten: List<MahlzeitAbschnitt>)

data class MahlzeitAbschnitt(val mahlzeit: Mahlzeit, val eintraege: List<EintragKurz>, val vorschlaege: List<GeplanteMahlzeit>)

data class EintragKurz(val id: String, val name: String, val kcal: Int?, val vollstaendig: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HeuteViewModel @Inject constructor(
    heute: HeuteRepository,
    private val tagebuch: TagebuchRepository,
    sync: SyncLageQuelle,
    ziel: TageszielSpeicher,
    zeit: Zeitgeber,
) : ViewModel() {
    private val jetzt = zeit.jetzt().shareIn(viewModelScope, SolangeSichtbar, replay = 1)

    /** Einmal gerechnet für Plan und Vorschläge. */
    private val rezepte = heute.rezepteKurz().shareIn(viewModelScope, SolangeSichtbar, replay = 1)

    /** Plan und Tagebuch des Tages; wechselt um Mitternacht auf den neuen Tag. */
    private val tag = jetzt.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { datum ->
        combine(heute.planHeuteUndMorgen(datum, rezepte), tagebuch.amTag(datum)) { plan, eintraege -> Tag(datum, plan, eintraege) }
    }

    private val umfeld = combine(sync.lage(), ziel.tagesziel(), ::Pair)

    val zustand: StateFlow<HeuteZustand?> =
        combine(jetzt, tag, heute.rezeptVorschlaege(rezepte), heute.offenerEinkauf(), umfeld) { j, t, rezepte, einkauf, (lage, kcalZiel) ->
            heuteZustand(j, t, rezepte, einkauf, lage, kcalZiel)
        }.stateIn(viewModelScope, SolangeSichtbar, null)

    /** „Gegessen?“: trägt den Plan-Eintrag mit einer Portion ein (höchstens einmal). */
    fun gegessen(planEintragId: String) {
        viewModelScope.launch { tagebuch.ausPlan(planEintragId) }
    }
}

private data class Tag(val datum: LocalDate, val plan: List<GeplanteMahlzeit>, val eintraege: List<TagebuchEintragEntity>)

private fun heuteZustand(
    jetzt: LocalDateTime, tag: Tag, rezepte: List<RezeptKurz>, einkauf: OffenerEinkauf?, lage: SyncLage, ziel: Int?,
): HeuteZustand {
    val planHeute = tag.plan.filter { it.datum == tag.datum }
    val inspiration = if (planHeute.isEmpty()) DailyPicks.pick(rezepte, tag.datum, count = 1) { it.id }.firstOrNull() else null
    return HeuteZustand(
        datum = tag.datum,
        naechste = naechsteMahlzeit(tag.plan, jetzt),
        inspiration = inspiration,
        tagebuch = tagebuchHeute(tag.eintraege, planHeute, ziel),
        einkauf = einkauf,
        sync = syncAnzeige(lage.aktiv, lage.laeuft, lage.letzterFehler),
        datenbankLeer = rezepte.isEmpty(),
    )
}

private fun tagebuchHeute(eintraege: List<TagebuchEintragEntity>, planHeute: List<GeplanteMahlzeit>, ziel: Int?): TagebuchHeute {
    val uebernommen = eintraege.mapNotNull { it.planEintragId }.toSet()
    val offen = Tagebuch.offeneVorschlaege(planHeute, { it.planEintragId }, uebernommen)
    val summe = Tagebuch.bilanz(eintraege.map { EintragWerte(it.eintragMahlzeit(), it.werte()) }).tag
    return TagebuchHeute(
        summe = summe,
        ziel = ziel,
        mahlzeiten = MAHLZEITEN.map { m ->
            MahlzeitAbschnitt(
                mahlzeit = m,
                eintraege = eintraege.filter { it.eintragMahlzeit() == m }.map { EintragKurz(it.id, it.name, it.werte().kcal, it.vollstaendig) },
                vorschlaege = offen.filter { (Mahlzeit.ausText(it.slot) ?: Mahlzeit.ABENDESSEN) == m },
            )
        },
    )
}
