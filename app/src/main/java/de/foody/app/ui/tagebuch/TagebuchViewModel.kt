package de.foody.app.ui.tagebuch

import de.foody.app.scan.Packung
import de.foody.app.scan.PackungScan
import de.foody.domain.Ingredient
import de.foody.domain.Nutrient
import de.foody.domain.IngredientCatalog
import de.foody.domain.NutrientBasis
import de.foody.domain.NutrientProfile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.data.GoalPreferences
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.TagebuchEintragEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.TagebuchRepository
import de.foody.app.data.repo.newId
import de.foody.app.data.repo.toDomain
import de.foody.domain.EintragWerte
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Naehrwerte
import de.foody.domain.Summe
import de.foody.domain.Tagebuch
import de.foody.domain.TagebuchArt
import de.foody.domain.TagesBilanz
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TagebuchUiState(
    val tag: LocalDate = LocalDate.now(),
    /** Die 7 Tage bis einschließlich [tag] mit kcal je Tag – für die Wochenleiste. */
    val woche: List<Pair<LocalDate, Int>> = emptyList(),
    val ziel: Int? = null,
    val bilanz: TagesBilanz = TagesBilanz(Summe(0, 0, 0, 0, true), emptyMap()),
    val eintraege: Map<Mahlzeit, List<TagebuchEintragEntity>> = emptyMap(),
    /** Geplante, noch nicht als gegessen übernommene Mahlzeiten des Tages. */
    val vorschlaege: Map<Mahlzeit, List<Pair<MealSlotEntity, RecipeEntity>>> = emptyMap(),
    val rezepte: List<RecipeEntity> = emptyList(),
    val zutaten: List<IngredientEntity> = emptyList(),
    val loading: Boolean = true,
)

/** Mahlzeit eines Plan-Eintrags; alte Freitexte zählen als Abendessen (früherer Standard des Planers). */
private fun MealSlotEntity.mahlzeit() = Mahlzeit.ausText(slotType) ?: Mahlzeit.ABENDESSEN

private fun TagebuchEintragEntity.werte() = Naehrwerte(energieKj, eiweiss, kohlenhydrate, fett, vollstaendig)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TagebuchViewModel @Inject constructor(
    private val tagebuch: TagebuchRepository,
    plan: PlanRepository,
    private val recipes: RecipeRepository,
    private val ingredients: IngredientRepository,
    goals: GoalPreferences,
    private val saved: SavedStateHandle,
    /** „Von Packung scannen“ im Reiter „Frei“. */
    val scan: PackungScan,
) : ViewModel() {
    private val tagEpoch = saved.getStateFlow("tag", LocalDate.now().toEpochDay())

    private val woche = tagEpoch.flatMapLatest { t ->
        val tag = LocalDate.ofEpochDay(t)
        tagebuch.observeRange(tag.minusDays(6), tag)
    }
    private val slots = tagEpoch.flatMapLatest { t -> LocalDate.ofEpochDay(t).let { plan.observeRange(it, it) } }

    val state = combine(
        combine(tagEpoch, woche, slots, ::Triple),
        recipes.observeAll(),
        ingredients.observeAll(),
        goals.dailyKcal,
        // Alle übernommenen Plan-Einträge, nicht nur die der Woche: auch ein später verschobener bleibt erledigt
        tagebuch.observeUebernommenePlanIds(),
    ) { (t, wocheEintraege, tagSlots), alleRezepte, alleZutaten, ziel, uebernommenIds ->
        val tag = LocalDate.ofEpochDay(t)
        val heute = wocheEintraege.filter { it.datum == tag }
        val rezepte = alleRezepte.associateBy { it.id }
        val uebernommen = uebernommenIds.toSet()
        TagebuchUiState(
            tag = tag,
            woche = (6L downTo 0L).map { d ->
                val day = tag.minusDays(d)
                day to Tagebuch.bilanz(wocheEintraege.filter { it.datum == day }.map { it.eintragWerte() }).tag.kcal
            },
            ziel = ziel,
            bilanz = Tagebuch.bilanz(heute.map { it.eintragWerte() }),
            eintraege = heute.groupBy { Mahlzeit.ausText(it.mahlzeit) ?: Mahlzeit.ABENDESSEN },
            vorschlaege = Tagebuch.offeneVorschlaege(tagSlots, { it.id }, uebernommen)
                .mapNotNull { s -> rezepte[s.recipeId]?.let { s to it } }
                .groupBy { it.first.mahlzeit() },
            rezepte = alleRezepte.filter { it.archivedAt == null },
            zutaten = alleZutaten,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagebuchUiState())

    private fun TagebuchEintragEntity.eintragWerte() = EintragWerte(Mahlzeit.ausText(mahlzeit) ?: Mahlzeit.ABENDESSEN, werte())

    fun zeigeTag(d: LocalDate) { saved["tag"] = d.toEpochDay() }
    fun verschiebe(tage: Long) { saved["tag"] = tagEpoch.value + tage }
    fun heute() = zeigeTag(LocalDate.now())

    private val tag get() = LocalDate.ofEpochDay(tagEpoch.value)

    private suspend fun zutatenMap() = ingredients.observeAll().first().associate { it.id to it.toDomain() }

    private suspend fun rezeptWerte(rezeptId: String, portionen: BigDecimal): Pair<RecipeEntity, Naehrwerte>? {
        val r = recipes.get(rezeptId) ?: return null
        return r to Tagebuch.naehrwerteRezept(r.toDomain(recipes.getIngredients(rezeptId)), zutatenMap(), portionen)
    }

    private fun eintrag(
        datum: LocalDate, m: Mahlzeit, art: TagebuchArt, name: String, w: Naehrwerte,
        rezeptId: String? = null, portionen: BigDecimal? = null, zutatId: String? = null, menge: BigDecimal? = null, einheit: MeasureUnit? = null,
    ): TagebuchEintragEntity {
        val now = System.currentTimeMillis()
        return TagebuchEintragEntity(
            id = newId(), datum = datum, mahlzeit = m.name, art = art, name = name, rezeptId = rezeptId, portionen = portionen,
            zutatId = zutatId, menge = menge, einheit = einheit, energieKj = w.energieKj, eiweiss = w.eiweiss,
            kohlenhydrate = w.kohlenhydrate, fett = w.fett, vollstaendig = w.vollstaendig, createdAt = now, updatedAt = now,
        )
    }

    /** Übernimmt einen geplanten Eintrag als gegessen – höchstens einmal, auch bei doppeltem Tipp. */
    fun gegessen(slot: MealSlotEntity, portionen: BigDecimal = BigDecimal.ONE) = viewModelScope.launch {
        val (r, w) = rezeptWerte(slot.recipeId, portionen) ?: return@launch
        tagebuch.uebernehmen(slot.id, eintrag(slot.date, slot.mahlzeit(), TagebuchArt.REZEPT, r.name, w, r.id, portionen))
    }

    fun rezeptEintragen(m: Mahlzeit, rezeptId: String, portionen: BigDecimal) = viewModelScope.launch {
        val datum = tag
        val (r, w) = rezeptWerte(rezeptId, portionen) ?: return@launch
        tagebuch.save(eintrag(datum, m, TagebuchArt.REZEPT, r.name, w, r.id, portionen))
    }

    /** Vorschau „≈ … kcal“ im Dialog; `null`, wenn die Menge nicht berechenbar ist. */
    fun zutatVorschau(zutatId: String, menge: BigDecimal, einheit: MeasureUnit): Naehrwerte? =
        state.value.zutaten.firstOrNull { it.id == zutatId }?.let { Tagebuch.naehrwerteZutat(it.toDomain(), menge, einheit) }

    /** `false`, wenn die Zutat keine Nährwerte hat oder die Einheit nicht umrechenbar ist (dann „frei eintragen“). */
    suspend fun zutatEintragen(m: Mahlzeit, zutatId: String, menge: BigDecimal, einheit: MeasureUnit): Boolean {
        val zutat = ingredients.get(zutatId) ?: return false
        val w = Tagebuch.naehrwerteZutat(zutat.toDomain(), menge, einheit) ?: return false
        tagebuch.save(eintrag(tag, m, TagebuchArt.ZUTAT, zutat.canonicalName, w, zutatId = zutatId, menge = menge, einheit = einheit))
        return true
    }

    fun freiEintragen(m: Mahlzeit, name: String, kcal: Int, eiweiss: BigDecimal?, kohlenhydrate: BigDecimal?, fett: BigDecimal?) =
        viewModelScope.launch {
            val w = Naehrwerte(BigDecimal(kcal).multiply(KJ_JE_KCAL), eiweiss, kohlenhydrate, fett, vollstaendig = true)
            tagebuch.save(eintrag(tag, m, TagebuchArt.FREI, name.trim(), w))
        }

    /**
     * Speichert Änderungen. Die Werte bleiben festgehalten: Ändern sich Portionen bzw. Menge, werden sie im selben
     * Verhältnis skaliert – nie aus dem heutigen Rezept neu berechnet (ADR 0007). Nur die Mahlzeit zu ändern lässt sie unberührt.
     */
    fun bearbeiten(e: TagebuchEintragEntity) = viewModelScope.launch {
        val alt = tagebuch.get(e.id) ?: return@launch
        val faktor = when (e.art) {
            TagebuchArt.REZEPT -> verhaeltnis(e.portionen, alt.portionen)
            TagebuchArt.ZUTAT -> verhaeltnis(e.menge, alt.menge)
            TagebuchArt.FREI -> null
        }
        fun BigDecimal?.mal() = this?.let { v -> faktor?.let { v.multiply(it) } ?: v }
        tagebuch.save(
            if (faktor == null) e
            else e.copy(energieKj = alt.energieKj.mal(), eiweiss = alt.eiweiss.mal(), kohlenhydrate = alt.kohlenhydrate.mal(), fett = alt.fett.mal()),
        )
    }

    /** neu ÷ alt; `null` ohne Änderung oder ohne brauchbaren alten Wert (dann bleiben die Werte, wie sie sind). */
    private fun verhaeltnis(neu: BigDecimal?, alt: BigDecimal?): BigDecimal? {
        if (neu == null || alt == null || alt.signum() <= 0 || neu.compareTo(alt) == 0) return null
        return neu.divide(alt, 10, java.math.RoundingMode.HALF_UP)
    }

    /**
     * Gibt es im Katalog schon eine Zutat mit diesem Namen? (Häkchen heißt dann „Werte aktualisieren“.) Der eigene Name
     * geht vor; sonst über [IngredientCatalog] vereinheitlicht wie beim Import: „Mehl“ findet „Weizenmehl“.
     */
    suspend fun zutatMitNamen(name: String): IngredientEntity? =
        name.trim().takeIf { it.isNotEmpty() }?.let { ingredients.findByName(it) ?: ingredients.findByName(IngredientCatalog.canonicalName(it)) }

    /**
     * Trägt eine gescannte Packung ein. Mit [alsZutat] wird die Zutat angelegt bzw. – wenn es den Namen schon gibt –
     * mit den gescannten Werten aktualisiert und als Zutat-Eintrag gebucht; ohne entsteht ein freier Eintrag.
     * Der Eintrag nutzt immer die gescannten Werte. `false`, wenn die Menge nicht berechenbar ist (z. B. Stück).
     */
    suspend fun packungEintragen(m: Mahlzeit, name: String, menge: BigDecimal, einheit: MeasureUnit, packung: Packung, alsZutat: Boolean): Boolean {
        val n = name.trim()
        val profil = NutrientProfile(packung.basis ?: NutrientBasis.PER_100_G, packung.werte)
        val w = Tagebuch.naehrwerteZutat(Ingredient("", n, nutrients = profil), menge, einheit) ?: return false
        val datum = tag
        if (!alsZutat) {
            tagebuch.save(eintrag(datum, m, TagebuchArt.FREI, n, w))
            return true
        }
        val jetzt = System.currentTimeMillis()
        val kanonisch = IngredientCatalog.canonicalName(n)
        val basis = zutatMitNamen(n) ?: IngredientEntity(
            id = newId(), canonicalName = kanonisch, category = IngredientCatalog.guessCategory(kanonisch),
            createdAt = jetzt, updatedAt = jetzt, version = 0,
        )
        fun wert(x: Nutrient, alt: BigDecimal?) = packung.werte[x] ?: alt
        val zutat = basis.copy(
            nutrientBasis = packung.basis ?: NutrientBasis.PER_100_G,
            energyKj = wert(Nutrient.ENERGY_KJ, basis.energyKj), protein = wert(Nutrient.PROTEIN_G, basis.protein),
            carbs = wert(Nutrient.CARBS_G, basis.carbs), fat = wert(Nutrient.FAT_G, basis.fat), fiber = wert(Nutrient.FIBER_G, basis.fiber),
            sugar = wert(Nutrient.SUGAR_G, basis.sugar), salt = wert(Nutrient.SALT_G, basis.salt),
            nutrientSource = packung.quelle, barcode = basis.barcode ?: packung.strichcode,
        )
        ingredients.save(zutat)
        tagebuch.save(eintrag(datum, m, TagebuchArt.ZUTAT, zutat.canonicalName, w, zutatId = zutat.id, menge = menge, einheit = einheit))
        return true
    }

    fun loeschen(id: String) = viewModelScope.launch { tagebuch.delete(id) }

    companion object {
        val KJ_JE_KCAL = BigDecimal("4.184")
    }
}
