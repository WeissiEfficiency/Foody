package de.foody.core.data.woche

import androidx.room.withTransaction
import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.data.rezepte.einordnung
import de.foody.domain.Abwechslung
import de.foody.domain.DayNutrition
import de.foody.domain.LeereTageFuellen
import de.foody.domain.Mahlzeit
import de.foody.domain.PlanDurchschnitt
import de.foody.domain.PlanTag
import de.foody.domain.RecipeProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * Der Wochenplan: lesen, ändern, „Leere Tage füllen“. kcal und Nährwerte über dieselben Abbildungen wie Karte,
 * Detail und „Heute“; Tageswerte je Person ([DayNutrition]).
 */
class WocheRepository(
    private val db: FoodyDatenbank,
    private val rezepte: RezeptRepository,
    private val uhr: () -> Long,
    /** Wo [woche] rechnet; Tests geben `EmptyCoroutineContext`, damit nichts auf einem fremden Thread nachläuft. */
    private val rechnen: CoroutineContext = Dispatchers.Default,
) {
    /** Die sieben Tage ab [montag]; neu bei jeder Änderung an Plan, Rezepten, Zeilen oder Zutaten. */
    fun woche(montag: LocalDate): Flow<WochenAnsicht> = combine(
        db.planEintragDao().zwischen(montag, montag.plusDays(6)),
        db.rezeptDao().alleFlow(),
        db.rezeptZutatDao().alleFlow(),
        db.zutatDao().alleFlow(),
    ) { plan, rezeptListe, zeilen, zutaten ->
        val katalog = zutaten.associate { it.id to it.alsIngredient() }
        val rezeptNach = rezeptListe.associateBy { it.id }
        val zeilenJe = zeilen.groupBy { it.recipeId }
        val recipe = rezeptListe.associate { it.id to it.alsRecipe(zeilenJe[it.id].orEmpty()) }
        val tage = (0L..6L).map { versatz ->
            val datum = montag.plusDays(versatz)
            // Mit CASCADE verschwinden Einträge mit ihrem Rezept; ohne Rezept gibt es keinen Eintrag
            val eintraege = plan.filter { it.date == datum && it.recipeId in rezeptNach }
                .sortedWith(compareBy({ mahlzeitReihenfolge(it.slotType) }, { it.slotType.lowercase() }, { it.id }))
            WochenTag(
                datum,
                eintraege.map { p ->
                    val r = rezeptNach.getValue(p.recipeId)
                    PlanEintrag(
                        p.id, p.date, p.slotType, r.id, r.name, r.imageUri,
                        RecipeProfiles.profile(recipe.getValue(r.id), katalog).kcalPerServing, p.servings, p.cookedAt != null,
                    )
                },
                DayNutrition.of(eintraege.map { recipe.getValue(it.recipeId) }, katalog),
            )
        }
        val durchschnitt = PlanDurchschnitt.kcal(
            tage.mapNotNull { t -> t.naehrwerte?.let { PlanTag(t.eintraege.map { it.mahlzeit }, it) } },
        )
        WochenAnsicht(montag, tage, durchschnitt)
    }.flowOn(rechnen)

    /** Rezepte für die Auswahl beim Hinzufügen: die Karten ohne archivierte. */
    fun rezeptAuswahl(): Flow<List<RezeptKarte>> = rezepte.karten().map { k -> k.filterNot { it.archiviert } }

    /**
     * Legt einen Eintrag an; Portionen 1–99 (`null` = die des Rezepts), Name wie [umbenennen]. `null`, wenn es das
     * Rezept nicht (mehr) gibt – etwa per Sync gelöscht, während die Auswahl offen war.
     */
    suspend fun hinzufuegen(datum: LocalDate, mahlzeit: String, rezeptId: String, portionen: Int?): String? {
        val text = mahlzeitText(mahlzeit)
        return db.withTransaction {
            val r = db.rezeptDao().nachId(rezeptId) ?: return@withTransaction null
            val jetzt = uhr()
            val e = PlanEintragEntity(
                UUID.randomUUID().toString(), datum, text, rezeptId, (portionen ?: r.defaultServings).coerceIn(MIN_PORTIONEN, MAX_PORTIONEN),
                createdAt = jetzt, updatedAt = jetzt,
            )
            db.planEintragDao().upsert(e)
            e.id
        }
    }

    suspend fun portionen(id: String, n: Int) = aendern(id) { it.copy(servings = n.coerceIn(MIN_PORTIONEN, MAX_PORTIONEN)) }

    suspend fun verschieben(id: String, datum: LocalDate) = aendern(id) { it.copy(date = datum) }

    /** Getrimmt, 1–30 Zeichen; ein bekannter Name („abendessen“) wird als Mahlzeit gespeichert. */
    suspend fun umbenennen(id: String, mahlzeit: String) {
        val text = mahlzeitText(mahlzeit)
        aendern(id) { it.copy(slotType = text) }
    }

    /** Entfernt den Eintrag und gibt ihn für „Rückgängig“ zurück; `null`, wenn es ihn nicht gab. */
    suspend fun entfernen(id: String): PlanEintragEntity? = db.withTransaction {
        db.planEintragDao().nachId(id)?.also { db.planEintragDao().loeschen(id) }
    }

    /** Legt einen entfernten Eintrag mit derselben ID wieder an; gibt es sein Rezept nicht mehr, still nichts. */
    suspend fun wiederherstellen(e: PlanEintragEntity) = db.withTransaction {
        if (db.rezeptDao().nachId(e.recipeId) != null) db.planEintragDao().upsert(e.copy(updatedAt = uhr()))
    }

    /** Setzt bzw. löscht `cookedAt` (zählt für „Oft gekocht“). */
    suspend fun gekocht(id: String, an: Boolean) {
        val jetzt = uhr()
        aendern(id) { it.copy(cookedAt = jetzt.takeIf { an }) }
    }

    /** Tage der Woche ab [heute] ohne Eintrag dieser [mahlzeit] (auch in anderer Schreibweise gespeichert). */
    suspend fun leereTage(montag: LocalDate, mahlzeit: Mahlzeit, heute: LocalDate): List<LocalDate> {
        val belegt = db.planEintragDao().zwischen(montag, montag.plusDays(6)).first()
            .filter { Mahlzeit.ausText(it.slotType) == mahlzeit }.map { it.date }.toSet()
        return (0L..6L).map { montag.plusDays(it) }.filter { it >= heute && it !in belegt }
    }

    /** Vorschlag „Tag → Rezept-ID“ über [LeereTageFuellen]; gleicher [seed] → gleiches Ergebnis; [ausgeschlossen] nie. */
    suspend fun vorschlagen(
        tage: List<LocalDate>,
        mahlzeit: Mahlzeit,
        seed: Long,
        ausgeschlossen: Set<String> = emptySet(),
    ): Map<LocalDate, String> {
        if (tage.isEmpty()) return emptyMap()
        val katalog = db.zutatDao().alle().associate { it.id to it.canonicalName }
        val pflicht = db.rezeptZutatDao().alle().filterNot { it.optional }.groupBy({ it.recipeId }, { katalog[it.ingredientId].orEmpty() })
        val plan = db.planEintragDao().alle()
        val merkmaleJe = db.rezeptDao().alle().associate { r -> r.id to Abwechslung.merkmale(r.name, pflicht[r.id].orEmpty()) }
        val gekochtJe = plan.filter { it.cookedAt != null }.groupingBy { it.recipeId }.eachCount()
        val zuletztJe = plan.groupBy { it.recipeId }.mapValues { (_, p) -> p.maxOf { it.date } }
        val kandidaten = db.rezeptDao().alle().filter { it.archivedAt == null }.map { r ->
            LeereTageFuellen.Rezept(r.id, r.einordnung(), r.favorite, gekochtJe[r.id] ?: 0, zuletztJe[r.id], merkmaleJe.getValue(r.id))
        }
        val von = tage.min().minusDays(LeereTageFuellen.OHNE_WIEDERHOLUNG_TAGE)
        val bis = tage.max().plusDays(NACHBARTAGE)
        val belegt = plan.filter { it.date in von..bis }.groupBy { it.date }
            .mapValues { (_, p) -> p.flatMap { merkmaleJe[it.recipeId].orEmpty() }.toSet() }
        return LeereTageFuellen.vorschlagen(kandidaten, tage, mahlzeit, seed, belegt, ausgeschlossen)
    }

    /**
     * Legt den Vorschlag in einer Transaktion an (Portionen = Standardportionen); Rezepte, die es inzwischen nicht mehr
     * gibt, werden übersprungen. Liefert die Zahl der angelegten Einträge.
     */
    suspend fun uebernehmen(vorschlag: Map<LocalDate, String>, mahlzeit: Mahlzeit): Int = db.withTransaction {
        val jetzt = uhr()
        vorschlag.entries.count { (datum, rezeptId) ->
            val r = db.rezeptDao().nachId(rezeptId) ?: return@count false
            db.planEintragDao().upsert(
                PlanEintragEntity(UUID.randomUUID().toString(), datum, mahlzeit.name, r.id, r.defaultServings.coerceIn(MIN_PORTIONEN, MAX_PORTIONEN), createdAt = jetzt, updatedAt = jetzt),
            )
            true
        }
    }

    private suspend fun aendern(id: String, f: (PlanEintragEntity) -> PlanEintragEntity) = db.withTransaction {
        val e = db.planEintragDao().nachId(id) ?: return@withTransaction
        db.planEintragDao().upsert(f(e).copy(updatedAt = uhr()))
    }

    private companion object {
        const val MIN_PORTIONEN = 1
        const val MAX_PORTIONEN = 99

        /** So viele Tage nach dem Zeitraum zählen für die Abwechslung mit. */
        const val NACHBARTAGE = 7L
    }
}

private const val MAX_NAME = 30

/** Gespeicherter Mahlzeit-Text: bekannte Namen als `Mahlzeit.name`, sonst getrimmt (1–30 Zeichen). */
internal fun mahlzeitText(eingabe: String): String {
    val t = eingabe.trim()
    require(t.length in 1..MAX_NAME) { "Mahlzeit muss 1–$MAX_NAME Zeichen haben" }
    return Mahlzeit.ausText(t)?.name ?: t
}
