package de.foody.feature.heute.tagebuch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.alsIngredient
import de.foody.core.data.bls.BlsNachschlag
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.heute.RezeptKurz
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.data.zutaten.ZutatErgebnis
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.data.zutaten.nameBereinigt
import de.foody.core.data.zutaten.zahlAus
import de.foody.core.data.zutaten.zahlText
import de.foody.core.data.zutaten.zutatMitNamen
import de.foody.core.designsystem.SolangeAppOffen
import de.foody.core.erkennung.ErkennungEinstellung
import de.foody.core.erkennung.ErkennungLauf
import de.foody.core.erkennung.FotoGedaechtnis
import de.foody.core.erkennung.MahlzeitErkenner
import de.foody.core.scan.PackungScan
import de.foody.core.scan.ScanAusgang
import de.foody.core.scan.ScanLauf
import de.foody.domain.BlsDichte
import de.foody.domain.BlsTreffer
import de.foody.domain.FotoPortion
import de.foody.domain.FotoVorschlag
import de.foody.domain.IngredientCatalog
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.Tagebuch
import de.foody.feature.heute.mahlzeitNachUhrzeit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class EintragenWeg { REZEPT, ZUTAT, PLAN, FREI }

/** Erfassen: suchen, fotografieren, scannen oder Geplantes wählen. Bestätigen: die Wahl prüfen und eintragen. */
enum class EintragenSchritt { ERFASSEN, BESTAETIGEN }

data class ZutatKurz(val id: String, val name: String)

/** Eingaben der Seite; Zahlen als Text, wie getippt. Serializable: Sie liegen im SavedStateHandle (Prozessende). */
data class Eingaben(
    /**
     * `true`, sobald [EintragenViewModel.oeffnen] lief. Vorher (etwa im Startwert von [EintragenViewModel.zustand],
     * bevor er rechnet) gelten die Angaben des Ziels.
     */
    val geoeffnet: Boolean = false,
    val schritt: EintragenSchritt = EintragenSchritt.ERFASSEN,
    /** Kamera bzw. Scanner gleich beim Öffnen; bricht der Nutzer sie ab, geht die Seite zu. `null`, sobald etwas kam. */
    val sofort: SofortStart? = null,
    val datum: LocalDate = LocalDate.EPOCH,
    val mahlzeit: Mahlzeit = Mahlzeit.ABENDESSEN,
    /** Mahlzeit beim Öffnen; „Zurück“ aus Geplantem stellt sie wieder her (Geplantes nimmt die des Plans). */
    val mahlzeitStart: Mahlzeit = Mahlzeit.ABENDESSEN,
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
    /** Vorschlag der Fotoerkennung; markiert die freien Felder, solange sie dem angezeigten Wert gleichen. */
    val foto: FotoStand? = null,
    /** Uri des Fotos fürs Vorschaubild in Bestätigen. */
    val fotoBild: String? = null,
    /** Datei des Kamerafotos; gelöscht bei „Zurück“ und wenn die Seite endet. */
    val fotoDatei: String? = null,
) : java.io.Serializable

/**
 * Vorschlag der Fotoerkennung (Phase 6): [modell] wie erkannt (Schlüssel fürs Gedächtnis), [basis] die
 * Ausgangswerte – die eigenen vom letzten Mal, wenn [ausGedaechtnis] –, [portion] die gewählte Portion.
 */
data class FotoStand(
    val modell: FotoVorschlag,
    val basis: FotoVorschlag,
    val ausGedaechtnis: Boolean,
    val portion: FotoPortion = FotoPortion.NORMAL,
) : java.io.Serializable {
    /** Was in den Feldern steht bzw. stand: [basis] für die [portion]. */
    val angezeigt: FotoWerte get() = basis.mal(portion.faktor).werte()
}

/** Vorschlag der Fotoerkennung als Feldtexte (`""`: kein Wert geliefert). */
data class FotoWerte(val name: String, val kcal: String, val eiweiss: String, val kh: String, val fett: String)

/** Feldtexte: Name bereinigt und höchstens 200 Zeichen, Zahlen wie getippt (mit Komma). */
internal fun FotoVorschlag.werte() = FotoWerte(
    nameBereinigt(name).take(MAX_NAME), kcal?.toString().orEmpty(), zahlText(eiweiss), zahlText(kohlenhydrate), zahlText(fett),
)

/** Markiert, solange das Feld dem Vorschlag aus dem Foto gleicht (und der Vorschlag den Wert hatte). */
internal fun wieImFoto(feld: String, foto: String?) = !foto.isNullOrEmpty() && feld.trim() == foto

/** „Geschätzt aus dem Foto – bitte prüfen“, solange noch ein Feld dem Vorschlag gleicht. */
internal fun fotoHinweis(e: Eingaben): Boolean {
    val f = e.foto?.angezeigt ?: return false
    return listOf(e.freiName to f.name, e.freiKcal to f.kcal, e.freiEiweiss to f.eiweiss, e.freiKh to f.kh, e.freiFett to f.fett)
        .any { (feld, wert) -> wieImFoto(feld, wert) }
}

/** Eingaben plus Trefferlisten: Suche als Teilstring ohne Groß/klein, höchstens [MAX_TREFFER]. */
data class EintragenZustand(
    val eingaben: Eingaben,
    val rezepte: List<RezeptKurz>,
    val zutaten: List<ZutatKurz>,
    val plan: List<GeplanteMahlzeit>,
    /** Name der Katalog-Zutat zum Packungsnamen ([zutatMitNamen]); `null`: Das Häkchen legt eine neue an. */
    val vorhandeneZutat: String? = null,
    /** Treffer im BLS zur Zutatensuche, ohne die, die schon so im Katalog stehen. */
    val bls: List<BlsTreffer> = emptyList(),
    /** Titel des gewählten Rezepts bzw. Geplanten oder Name der Zutat; `null` bei „Frei“ oder ohne Wahl. */
    val auswahlName: String? = null,
    /** kcal, die der Eintrag dazubringt (Rezept × Portionen, Zutat in der Menge, Geplantes je Portion); sonst `null`. */
    val kcalDazu: Int? = null,
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
    erkenner: MahlzeitErkenner,
    erkennung: ErkennungEinstellung,
    /** Eigene Korrekturen nach einem Foto (Phase 6): nachschlagen beim Vorschlag, merken beim Speichern. */
    private val gedaechtnis: FotoGedaechtnis,
    bls: BlsNachschlag,
    private val zutatenRepo: ZutatenRepository,
) : ViewModel() {
    private val eingaben = gespeichert.getMutableStateFlow(EINGABEN, Eingaben())

    init {
        // Stirbt der Prozess, während der Google-Scanner offen ist, kommt sein Ergebnis nicht mehr an (Rückruf im alten
        // Prozess): wieder am Ausgangspunkt, ohne dass ein späteres „Abbrechen“ die Seite schließt
        if (eingaben.value.sofort == SofortStart.SCANNEN) eingaben.update { it.copy(sofort = null) }
    }

    /**
     * Der Scan-Knopf; Erkennung läuft im viewModelScope weiter, auch wenn gedreht wird. Schon der gelesene Strichcode
     * öffnet Bestätigen ([scanGelesen]), die Packung füllt sich, sobald Katalog bzw. Open Food Facts antworten.
     */
    val scanLauf = ScanLauf(scan, viewModelScope, gespeichert, ::packungUebernehmen, ::imKatalog, ::scanGelesen, ::ohneTreffer)

    /** Der Foto-Knopf; die Erkennung läuft im viewModelScope und übersteht Drehen und Prozessende. */
    val fotoLauf = ErkennungLauf(erkenner, viewModelScope, gespeichert, ::fotoUebernehmen)

    /**
     * Foto-Knopf zeigen: Schalter an und Modell auf diesem Gerät (auf dem Tablet ohne Modell nie). `null`, bis die
     * Einstellung gelesen ist: Erfassen hält dann den Platz frei, statt den Knopf zu tauschen.
     */
    val fotoKnopf: StateFlow<Boolean?> = erkennung.sichtbar.stateIn(viewModelScope, SolangeAppOffen, null)

    /** Während des Speicherns und nach Erfolg gesperrt (bis zum nächsten [oeffnen]): ein Doppeltipp legt nichts doppelt an. */
    private val gesperrt = MutableStateFlow(false)

    /** Einmal gerechnet für Plan und Rezeptsuche; nur solange die Seite offen ist. */
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

    /** Katalog für die Suche: alle Zutaten, gescannte Produkte (mit Strichcode) eingeschlossen. */
    private val katalogSicht = katalog.map { z ->
        KatalogSicht(
            zutaten = z.sortedBy { it.canonicalName.lowercase() }.map { ZutatKurz(it.id, it.canonicalName) },
            nachId = z.associateBy { it.id },
        )
    }

    /** BLS-Treffer zur Suche, nur im Schritt Erfassen; läuft im Speicher, bricht bei neuer Eingabe ab. */
    private val blsTreffer = eingaben.map { if (it.schritt == EintragenSchritt.ERFASSEN) it.suche.trim() else "" }
        .distinctUntilChanged()
        .mapLatest { s -> bls.suchen(s) }

    val zustand: StateFlow<EintragenZustand> =
        combine(eingaben, heute.rezeptVorschlaege(rezepte), katalogSicht, offenerPlan, blsTreffer) { e, rezepte, k, plan, bls ->
            val s = e.suche.trim()
            // Rezepte auch ohne Suchtext, Zutaten (gescannte Produkte eingeschlossen) erst mit
            val gefunden = if (s.isEmpty()) emptyList() else k.zutaten.filter { passt(it.name, s) }.take(MAX_TREFFER)
            val namen = k.zutaten.map { it.name.lowercase() }.toSet()
            EintragenZustand(
                eingaben = e,
                rezepte = rezepte.filter { it.titel.contains(s, ignoreCase = true) }.take(MAX_TREFFER),
                zutaten = gefunden,
                plan = plan,
                vorhandeneZutat = e.packung?.let { vorhandene(e.pName) },
                bls = bls.filterNot { it.vorschlagName.lowercase() in namen },
                auswahlName = auswahlName(e, rezepte, k.nachId, plan),
                kcalDazu = kcalDazu(e, rezepte, k.nachId, plan),
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

    private val schliessenKanal = Channel<Unit>(Channel.CONFLATED)

    /** Ein Wert: Der Sofort-Start ist abgebrochen (Kamera oder Scanner ohne Ergebnis), die Seite soll zugehen. */
    val schliessen: Flow<Unit> = schliessenKanal.receiveAsFlow()

    /**
     * Neue Seite: alles zurück, Schritt Erfassen; Mahlzeit aus der angetippten Zeile, sonst nach Uhrzeit. Derselbe
     * [auftrag] erneut (Neuaufbau nach Drehen) lässt die Eingaben stehen; ein neuer bricht eine laufende Fotoerkennung
     * und Packungssuche ab und vergisst die Scan-Meldung. [sofort]: gleich Kamera bzw. Scanner (ohne Play-Dienste
     * bleibt es beim Erfassen).
     */
    fun oeffnen(datum: LocalDate, mahlzeit: Mahlzeit?, uhrzeit: LocalTime = LocalTime.now(), auftrag: String? = null, sofort: SofortStart? = null) {
        if (auftrag != null && auftrag == letzterAuftrag) return
        letzterAuftrag = auftrag
        fotoLauf.abbrechen()
        scanLauf.abbrechen()
        scanLauf.zustand.menueVersuch()
        fotoDateiLoeschen()
        val m = mahlzeit ?: mahlzeitNachUhrzeit(uhrzeit)
        val scannen = sofort == SofortStart.SCANNEN && scanLauf.verfuegbar
        eingaben.value = Eingaben(
            geoeffnet = true, datum = datum, mahlzeit = m, mahlzeitStart = m, sofort = sofort.takeIf { it != SofortStart.SCANNEN || scannen },
        )
        gesperrt.value = false
        // Widgets: nur einmal je Auftrag (der Abgleich oben), also nicht erneut nach Drehen oder Prozessende. Die Kamera
        // startet die Seite (sie hält die Launcher), auch ohne Modell: Die Kamera braucht keins, Bestätigen zeigt dann das
        // Vorschaubild, leere Felder und die Meldung der Erkennung.
        if (scannen) scanLauf.strichcode()
        if (sofort == SofortStart.FOTO) fotoLauf.kameraAnfragen() else fotoLauf.kameraAnfrageErledigt()
    }

    fun mahlzeit(m: Mahlzeit) = eingaben.update { it.copy(mahlzeit = m) }
    fun suche(text: String) = eingaben.update { it.copy(suche = text) }
    fun rezeptWaehlen(id: String) = eingaben.update { it.bestaetigen().copy(rezeptId = id, weg = EintragenWeg.REZEPT) }
    fun portionen(text: String) = eingaben.update { it.copy(portionen = text) }

    /** − / + in Schritten von 0,5; nie unter 0,5. */
    fun portionenSchritt(mehr: Boolean) = eingaben.update {
        val alt = zahlAus(it.portionen) ?: BigDecimal.ONE
        it.copy(portionen = zahlText((if (mehr) alt + SCHRITT else alt - SCHRITT).max(SCHRITT)))
    }

    /** Zutat gewählt: Einheit nach ihrer Nährwert-Basis (je 100 ml → ml, sonst g). */
    fun zutatWaehlen(id: String) = eingaben.update { it.bestaetigen().copy(zutatId = id, weg = EintragenWeg.ZUTAT, einheit = einheitFuer(id)) }

    /** Einheit der Zutat [id] nach ihrer Basis im Katalog; unbekannt oder ohne Basis g. */
    private fun einheitFuer(id: String): MeasureUnit =
        packungsEinheit(katalog.value.firstOrNull { it.id == id }?.nutrientBasis ?: NutrientBasis.PER_100_G)

    /**
     * BLS-Treffer gewählt: in den Katalog übernehmen (heißt schon eine Zutat so, diese nehmen), auswählen, mit
     * Stückgewicht die Einheit „Stück“, bei Flüssigem „ml“, und den Fokus auf die Menge. Läuft im viewModelScope
     * (übersteht Drehen).
     */
    fun blsWaehlen(t: BlsTreffer): Job = viewModelScope.launch {
        val id = when (val r = zutatenRepo.ausBls(t)) {
            is ZutatErgebnis.Gespeichert -> r.id
            is ZutatErgebnis.NameVergeben -> r.id
            else -> return@launch
        }
        eingaben.update {
            val einheit = when {
                t.stueckG != null -> MeasureUnit.PIECE
                BlsDichte.gProMl(t.lebensmittel) != null -> MeasureUnit.MILLILITER
                else -> MeasureUnit.GRAM
            }
            it.bestaetigen().copy(zutatId = id, suche = t.vorschlagName, einheit = einheit, weg = EintragenWeg.ZUTAT)
        }
        fokus.trySend(Unit)
    }

    fun menge(text: String) = eingaben.update { it.copy(menge = text) }
    fun einheit(u: MeasureUnit) = eingaben.update { it.copy(einheit = u) }
    fun freiName(text: String) = eingaben.update { it.copy(freiName = text) }
    fun freiKcal(text: String) = eingaben.update { it.copy(freiKcal = text) }
    fun freiEiweiss(text: String) = eingaben.update { it.copy(freiEiweiss = text) }
    fun freiKh(text: String) = eingaben.update { it.copy(freiKh = text) }
    fun freiFett(text: String) = eingaben.update { it.copy(freiFett = text) }

    /** Geplantes gewählt: mit der Mahlzeit des Plans (so speichert es [TagebuchRepository.ausPlan] ohnehin). */
    fun planWaehlen(id: String) {
        val mahlzeit = zustand.value.plan.firstOrNull { it.planEintragId == id }?.let { Mahlzeit.ausText(it.slot) }
        eingaben.update { it.bestaetigen().copy(planEintragId = id, weg = EintragenWeg.PLAN, mahlzeit = mahlzeit ?: it.mahlzeit) }
    }

    /** „Nicht dabei? Frei eintragen“: Bestätigen mit leeren Feldern. */
    fun freiEintragen() = eingaben.update { it.erfassenStand().bestaetigen().copy(weg = EintragenWeg.FREI) }

    /**
     * Foto da (Kamera oder Galerie): Bestätigen in „Frei“ mit leeren Feldern, die die Erkennung füllt. [datei], das
     * Kamerafoto, bleibt fürs Vorschaubild, bis „Zurück“ oder das Ende der Seite es löscht.
     */
    fun fotoGemacht(bildUri: String, datei: String?) {
        eingaben.update { it.erfassenStand().bestaetigen().copy(weg = EintragenWeg.FREI, fotoBild = bildUri, fotoDatei = datei) }
        fotoLauf.erkennen(bildUri)
    }

    /** Kamera oder Scanner ohne Ergebnis zurück: Beim Sofort-Start geht die Seite zu ([schliessen]), sonst bleibt alles. */
    fun aufnahmeAbgebrochen() {
        if (eingaben.value.sofort == null) return
        eingaben.update { it.copy(sofort = null) }
        schliessenKanal.trySend(Unit)
    }

    /**
     * „Zurück“: aus Bestätigen nach Erfassen (`true`) – Fotoerkennung und Packungssuche brechen ab (eine späte Antwort
     * holt nicht wieder nach Bestätigen), Scan-Meldung und Foto-Angebot sind vergessen, das Kamerafoto wird gelöscht,
     * Wahl, Felder, Packung und Foto sind verworfen; Suche und Datum bleiben, nach Geplantem gilt wieder die Mahlzeit vom
     * Öffnen. In Erfassen `false`: Die Seite geht zu.
     */
    fun zurueck(): Boolean {
        val e = eingaben.value
        if (e.schritt != EintragenSchritt.BESTAETIGEN) return false
        fotoLauf.abbrechen()
        scanLauf.abbrechen()
        scanLauf.zustand.menueVersuch()
        fotoDateiLoeschen()
        eingaben.value = e.erfassenStand().copy(mahlzeit = if (e.weg == EintragenWeg.PLAN) e.mahlzeitStart else e.mahlzeit)
        return true
    }

    /**
     * Das Kamerafoto fürs Vorschaubild, gleich aus den gesicherten Eingaben: Nach einem Prozessende steht in [zustand]
     * anfangs noch der Startwert, das Aufräumen darf das Foto trotzdem nicht löschen.
     */
    internal val fotoDatei: String? get() = eingaben.value.fotoDatei

    private fun fotoDateiLoeschen() {
        eingaben.value.fotoDatei?.let { File(it).delete() }
    }

    /** Die Seite endet: Das Kamerafoto wird nicht mehr gebraucht. */
    override fun onCleared() {
        fotoDateiLoeschen()
    }

    /**
     * Scan-Ergebnis: Bestätigen in „Frei“ mit der Packung. Name bereinigt (höchstens 200 Zeichen) – ohne Namen in [p]
     * (Foto der Nährwerttabelle) bleibt der getippte, ohne Strichcode der bisherige –, Werte ungerundet, Häkchen nach
     * dem Namen. Eine schon getippte Menge bleibt (Bestätigen ist offen, während Open Food Facts
     * sucht und bevor das Tabellenfoto kommt).
     */
    fun packungUebernehmen(p: Packungswerte) {
        val gescannt = nameBereinigt(p.name.orEmpty()).take(MAX_NAME)
        eingaben.update {
            val name = gescannt.ifEmpty { it.pName }
            val packung = if (p.strichcode == null) p.copy(strichcode = it.packung?.strichcode) else p
            it.bestaetigen().copy(
                weg = EintragenWeg.FREI, packung = packung, pName = name, pBasis = p.basis ?: NutrientBasis.PER_100_G,
                pKcal = p.kcalText, pEiweiss = p.feldText(Nutrient.PROTEIN_G), pKh = p.feldText(Nutrient.CARBS_G),
                pFett = p.feldText(Nutrient.FAT_G), alsZutat = vorhandene(name) == null, alsZutatVomNutzer = false,
            )
        }
    }

    /** Strichcode gelesen: Bestätigen mit der leeren Packung und dem Code, während Katalog und Open Food Facts noch suchen. */
    private fun scanGelesen(code: String) = packungUebernehmen(leerePackung(code))

    /**
     * Scan ohne Treffer. Ohne Werte (nicht gefunden, keine Tabelle, nicht verfügbar, Fehler): Bestätigen mit der leeren
     * Packung, falls noch keine da ist; Meldung und Foto-Knöpfe zeigt [scanLauf]. Lädt der Scanner noch, bleibt es beim
     * Erfassen; abgebrochen wie [aufnahmeAbgebrochen].
     */
    private fun ohneTreffer(a: ScanAusgang) {
        when (a) {
            is ScanAusgang.NichtGefunden -> leerePackungZeigen(a.strichcode)
            is ScanAusgang.KeineTabelle -> leerePackungZeigen(a.strichcode)
            ScanAusgang.NichtVerfuegbar, ScanAusgang.Fehler -> leerePackungZeigen(null)
            ScanAusgang.WirdGeladen -> eingaben.update { it.copy(sofort = null) }
            ScanAusgang.Abgebrochen -> aufnahmeAbgebrochen()
            // Kommen über onPackung bzw. onImKatalog
            is ScanAusgang.Gefunden, is ScanAusgang.ImKatalog -> Unit
        }
    }

    private fun leerePackungZeigen(code: String?) {
        if (eingaben.value.packung == null) packungUebernehmen(leerePackung(code))
    }

    /**
     * Vorschlag aus dem Foto: ersetzt die freien Felder; kennt das Gedächtnis den Namen, mit den eigenen Werten vom
     * letzten Mal statt der Schätzung. Gespeichert erst mit „Eintragen“.
     */
    suspend fun fotoUebernehmen(v: FotoVorschlag) {
        val gemerkt = gedaechtnis.nachschlagen(v.name)
        val stand = FotoStand(v, gemerkt ?: v, ausGedaechtnis = gemerkt != null)
        val w = stand.angezeigt
        eingaben.update { it.copy(freiName = w.name, freiKcal = w.kcal, freiEiweiss = w.eiweiss, freiKh = w.kh, freiFett = w.fett, foto = stand) }
    }

    /** Portion zum Vorschlag: rechnet kcal und Makros um, die noch dem bisher angezeigten Wert gleichen; Getipptes bleibt. */
    fun fotoPortion(p: FotoPortion) = eingaben.update {
        val stand = it.foto ?: return@update it
        val alt = stand.angezeigt
        val neuerStand = stand.copy(portion = p)
        val neu = neuerStand.angezeigt
        fun feld(wert: String, vorher: String, nachher: String) = if (wieImFoto(wert, vorher)) nachher else wert
        it.copy(
            freiKcal = feld(it.freiKcal, alt.kcal, neu.kcal), freiEiweiss = feld(it.freiEiweiss, alt.eiweiss, neu.eiweiss),
            freiKh = feld(it.freiKh, alt.kh, neu.kh), freiFett = feld(it.freiFett, alt.fett, neu.fett), foto = neuerStand,
        )
    }

    /**
     * Den Strichcode trägt schon eine Zutat: Bestätigen mit ihr (Einheit nach ihrer Basis), Suche auf ihrem Namen, Fokus
     * auf die Menge. Die leere Packung vom Lesen ([scanGelesen]) ist damit erledigt.
     */
    fun imKatalog(t: ScanAusgang.ImKatalog) {
        eingaben.update {
            it.bestaetigen().copy(weg = EintragenWeg.ZUTAT, zutatId = t.id, suche = t.name, einheit = einheitFuer(t.id), packung = null)
        }
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

    /** Teilstring ohne Groß/klein oder der Katalogname zur Eingabe („Semmel“ findet „Brötchen“). */
    private fun passt(name: String, suche: String): Boolean =
        name.contains(suche, ignoreCase = true) || (suche.isNotEmpty() && name.equals(IngredientCatalog.canonicalName(suche), ignoreCase = true))

    /**
     * Speichert nach dem gewählten Weg; `null`, wenn die Eingaben ungültig sind oder Rezept/Zutat/Plan fehlen. Das
     * Schreiben läuft zu Ende, auch wenn die Seite mittendrin gedreht wird; nur ein Fehler gibt „Speichern“ wieder frei.
     */
    suspend fun speichern(): String? {
        val e = eingaben.value
        if (!gueltig(e) || !gesperrt.compareAndSet(expect = false, update = true)) return null
        var fertig = false
        try {
            return withContext(NonCancellable) { schreiben(e).also { fertig = true } }
        } finally {
            if (!fertig) gesperrt.value = false
        }
    }

    private suspend fun schreiben(e: Eingaben): String? {
        val id = when (e.weg) {
            EintragenWeg.REZEPT -> tagebuch.ausRezept(e.datum, e.mahlzeit, e.rezeptId!!, zahlAus(e.portionen)!!)
            EintragenWeg.ZUTAT -> tagebuch.ausZutat(e.datum, e.mahlzeit, e.zutatId!!, zahlAus(e.menge)!!, e.einheit)
            EintragenWeg.PLAN -> tagebuch.ausPlan(e.planEintragId!!)
            EintragenWeg.FREI -> if (e.packung != null) {
                tagebuch.ausPackung(e.datum, e.mahlzeit, e.pName, zahlAus(e.pMenge)!!, packungsEinheit(e.pBasis), formularWerte(e), e.alsZutat)
            } else {
                val kcal = e.freiKcal.trim().toInt()
                val gespeichert = FotoVorschlag(e.freiName.trim(), kcal, optional(e.freiEiweiss), optional(e.freiKh), optional(e.freiFett))
                tagebuch.frei(e.datum, e.mahlzeit, gespeichert.name, kcal, gespeichert.eiweiss, gespeichert.kohlenhydrate, gespeichert.fett)
                    .also { e.foto?.let { f -> gedaechtnis.merken(f.modell, f.ausGedaechtnis, f.portion, gespeichert) } }
            }
        }
        if (id == null) gesperrt.value = false
        return id
    }
}

private data class KatalogSicht(val zutaten: List<ZutatKurz>, val nachId: Map<String, ZutatEntity>)

/** Weiter zu Bestätigen; ein Sofort-Start ist damit erledigt. */
private fun Eingaben.bestaetigen() = copy(schritt = EintragenSchritt.BESTAETIGEN, sofort = null)

/** Erfassen mit Datum, Mahlzeit und Suche; Wahl, Felder, Packung und Foto sind verworfen. */
private fun Eingaben.erfassenStand() = Eingaben(geoeffnet = geoeffnet, datum = datum, mahlzeit = mahlzeit, mahlzeitStart = mahlzeitStart, suche = suche)

/** Packung ohne Werte zum Selbst-Ausfüllen; [code] merkt sich die Zutat beim Speichern. */
private fun leerePackung(code: String?) = Packungswerte(null, null, emptyMap(), code, PackungScan.QUELLE_HAND)

/** Titel bzw. Name der Wahl aus den ganzen Listen, nicht den Treffern der Suche. */
private fun auswahlName(e: Eingaben, rezepte: List<RezeptKurz>, zutaten: Map<String, ZutatEntity>, plan: List<GeplanteMahlzeit>): String? =
    when (e.weg) {
        EintragenWeg.REZEPT -> rezepte.firstOrNull { it.id == e.rezeptId }?.titel
        EintragenWeg.ZUTAT -> e.zutatId?.let(zutaten::get)?.canonicalName
        EintragenWeg.PLAN -> plan.firstOrNull { it.planEintragId == e.planEintragId }?.titel
        EintragenWeg.FREI -> null
    }

/**
 * kcal der Wahl wie [auswahlName] aus den ganzen Listen: Rezept je Portion × Portionen (kaufmännisch gerundet), Zutat
 * in Menge und Einheit, Geplantes je Portion. `null` ohne Wahl, ohne Menge > 0 oder ohne Energiewerte.
 */
private fun kcalDazu(e: Eingaben, rezepte: List<RezeptKurz>, zutaten: Map<String, ZutatEntity>, plan: List<GeplanteMahlzeit>): Int? =
    when (e.weg) {
        EintragenWeg.REZEPT -> {
            val jePortion = rezepte.firstOrNull { it.id == e.rezeptId }?.kcalJePortion
            val portionen = zahlAus(e.portionen)?.takeIf { it.positiv() }
            if (jePortion == null || portionen == null) null else (BigDecimal(jePortion) * portionen).setScale(0, RoundingMode.HALF_UP).toInt()
        }
        EintragenWeg.ZUTAT -> {
            val zutat = e.zutatId?.let(zutaten::get)
            val menge = zahlAus(e.menge)?.takeIf { it.positiv() }
            if (zutat == null || menge == null) null else Tagebuch.naehrwerteZutat(zutat.alsIngredient(), menge, e.einheit)?.kcal
        }
        EintragenWeg.PLAN -> plan.firstOrNull { it.planEintragId == e.planEintragId }?.kcalJePortion
        EintragenWeg.FREI -> null
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
