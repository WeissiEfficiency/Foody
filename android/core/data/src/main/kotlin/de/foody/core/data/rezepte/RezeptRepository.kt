package de.foody.core.data.rezepte

import androidx.room.withTransaction
import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.ImportedIngredient
import de.foody.domain.Ingredient
import de.foody.domain.IngredientCatalog
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.NutritionCalculator
import de.foody.domain.RecipeProfiles
import de.foody.domain.RezeptEinordnung
import de.foody.domain.alsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.util.UUID

/**
 * Rezepte lesen und schreiben. kcal und Nährwerte immer über `:domain` mit denselben Abbildungen wie „Heute“
 * (`DomainAbbildung`), damit Karte, Detail und „Heute“ dieselbe Zahl zeigen.
 */
class RezeptRepository(
    private val db: FoodyDatenbank,
    private val ablage: FotoAblage,
    private val uhr: () -> Long,
) {
    /** Alle Rezepte (auch archivierte); neu berechnet bei jeder Änderung an Rezepten, Zeilen, Zutaten oder Plan. */
    fun karten(): Flow<List<RezeptKarte>> = combine(
        db.rezeptDao().alleFlow(),
        db.rezeptZutatDao().alleFlow(),
        db.zutatDao().alleFlow(),
        db.planEintragDao().gekochtJeRezept(),
    ) { rezepte, zeilen, zutaten, gekocht ->
        val katalog = zutaten.associate { it.id to it.alsIngredient() }
        val jeRezept = zeilen.groupBy { it.recipeId }
        val gekochtJe = gekocht.associate { it.recipeId to it.anzahl }
        rezepte.map { it.karte(jeRezept[it.id].orEmpty(), katalog, gekochtJe[it.id] ?: 0) }
    }

    /** Ein Rezept vollständig; `null`, wenn es die ID nicht (mehr) gibt. */
    fun voll(id: String): Flow<RezeptVoll?> = combine(
        db.rezeptDao().nachIdFlow(id),
        db.rezeptZutatDao().nachRezeptFlow(id),
        db.arbeitsschrittDao().nachRezeptFlow(id),
        db.zutatDao().alleFlow(),
    ) { rezept, zeilen, schritte, zutaten ->
        rezept ?: return@combine null
        val katalog = zutaten.associate { it.id to it.alsIngredient() }
        RezeptVoll(
            rezept = rezept,
            zeilen = zeilen.map { z ->
                val zutat = katalog[z.ingredientId]
                RezeptZeile(z.ingredientId, zutat?.name.orEmpty(), z.amount, z.unit, z.preparationNote, z.optional, zutat?.nutrients != null)
            },
            schritte = schritte.map { it.text },
            naehrwerte = NutritionCalculator.calculate(rezept.alsRecipe(zeilen), katalog),
            einordnung = rezept.einordnung(),
        )
    }

    /**
     * Legt an oder ändert in einer Transaktion; Zeilen und Schritte werden ersetzt. Unbekannte Zutaten kommen in den
     * Katalog (Name und Kategorie wie v1). Favorit, Bewertung, Archiv und Anlagedatum bleiben. Gibt es die ID nicht
     * (mehr), wird nichts geschrieben ([Gespeichert.Geloescht]).
     */
    suspend fun speichern(e: RezeptEntwurf): Gespeichert {
        var altesBild: String? = null
        val ergebnis = db.withTransaction {
            val vorher = e.id?.let { db.rezeptDao().nachId(it) ?: return@withTransaction Gespeichert.Geloescht }
            val id = vorher?.id ?: neueId()
            val jetzt = uhr()
            altesBild = vorher?.imageUri
            db.rezeptDao().upsert(
                RezeptEntity(
                    id = id, name = e.name.trim(), defaultServings = e.portionen, prepMinutes = e.vorbereitung,
                    cookMinutes = e.kochzeit, imageUri = e.bild, notes = e.notizen?.trim()?.ifEmpty { null },
                    tags = e.tags.trim(), archivedAt = vorher?.archivedAt, createdAt = vorher?.createdAt ?: jetzt,
                    updatedAt = jetzt, version = (vorher?.version ?: 0) + 1, favorite = vorher?.favorite ?: false,
                    sourceUrl = e.quelle?.trim()?.ifEmpty { null }, rating = vorher?.rating,
                    mahlzeiten = alsText(e.mahlzeiten, vorher?.mahlzeiten, Mahlzeit.entries),
                    gaenge = alsText(e.gaenge, vorher?.gaenge, Gang.entries),
                ),
            )
            val zutaten = mutableMapOf<String, String>()
            db.rezeptZutatDao().loeschenFuerRezept(id)
            e.zeilen.forEachIndexed { i, z ->
                val zutatId = zutaten.getOrPut(z.name.lowercase()) { zutatId(z.name, jetzt) }
                // Ohne Menge („1 Prise Salz“): Menge 0 in Stück, die Angabe steht im Hinweis – wie beim Import
                val menge = z.amount
                val einheit = if (menge == null) MeasureUnit.PIECE else z.unit ?: MeasureUnit.PIECE
                db.rezeptZutatDao().upsert(
                    RezeptZutatEntity(neueId(), id, zutatId, menge ?: BigDecimal.ZERO, einheit, i, z.note, z.optional),
                )
            }
            db.arbeitsschrittDao().loeschenFuerRezept(id)
            e.schritte.map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, text ->
                db.arbeitsschrittDao().upsert(ArbeitsschrittEntity(neueId(), id, i, text))
            }
            Gespeichert.Ok(id)
        }
        if (ergebnis is Gespeichert.Ok && altesBild != e.bild) fotoAufraeumen(altesBild)
        return ergebnis
    }

    /** Das Rezept als Entwurf (Zeilen mit Katalognamen); `null`, wenn es die ID nicht gibt. */
    suspend fun entwurf(id: String): RezeptEntwurf? {
        val r = db.rezeptDao().nachId(id) ?: return null
        val katalog = db.zutatDao().alle().associateBy { it.id }
        val zeilen = db.rezeptZutatDao().nachRezept(id).map { z ->
            val ohneMenge = z.amount.signum() == 0
            ImportedIngredient(
                name = katalog[z.ingredientId]?.canonicalName.orEmpty(), amount = z.amount.takeUnless { ohneMenge },
                unit = z.unit.takeUnless { ohneMenge }, note = z.preparationNote, group = null, optional = z.optional,
            )
        }
        return RezeptEntwurf(
            id = r.id, name = r.name, portionen = r.defaultServings, vorbereitung = r.prepMinutes, kochzeit = r.cookMinutes,
            tags = r.tags, zeilen = zeilen, schritte = db.arbeitsschrittDao().nachRezept(id).map { it.text }, notizen = r.notes,
            quelle = r.sourceUrl, mahlzeiten = Mahlzeit.mengeAus(r.mahlzeiten), gaenge = Gang.mengeAus(r.gaenge), bild = r.imageUri,
        )
    }

    /** Kopie „Name (Kopie)“ mit neuen IDs, ohne Quelle (sonst gälte sie als Dublette des Imports) und ohne Favorit. */
    suspend fun duplizieren(id: String): String? {
        val e = entwurf(id) ?: return null
        return (speichern(e.copy(id = null, name = "${e.name} (Kopie)", quelle = null)) as? Gespeichert.Ok)?.id
    }

    suspend fun archivieren(id: String, archiviert: Boolean) {
        val jetzt = uhr()
        db.rezeptDao().archivSetzen(id, jetzt.takeIf { archiviert }, jetzt)
    }

    suspend fun favorit(id: String, an: Boolean) = db.rezeptDao().favoritSetzen(id, an, uhr())

    /** Setzt das Foto; das alte wird entfernt, wenn kein Rezept es mehr nutzt. */
    suspend fun fotoSetzen(id: String, link: String?) {
        val alt = db.rezeptDao().nachId(id)?.imageUri
        db.rezeptDao().bildSetzen(id, link, uhr())
        if (alt != link) fotoAufraeumen(alt)
    }

    /**
     * Löscht nur dieses eine eigene Foto, wenn kein Rezept es mehr nutzt – nicht alle unverknüpften: ein offener
     * Editor kann gerade ein neues, noch nicht gespeichertes Foto halten.
     */
    private suspend fun fotoAufraeumen(link: String?) {
        if (link == null || !ablage.istEigeneDatei(link) || db.rezeptDao().anzahlMitBild(link) > 0) return
        ablage.datei(link)?.delete()
        db.syncDao().loeschenFotoLokal(link)
    }

    /** Zutat per Name (ohne Groß/klein), sonst über den vereinheitlichten Namen, sonst neu im Katalog. */
    private suspend fun zutatId(name: String, jetzt: Long): String {
        val dao = db.zutatDao()
        dao.nachNameOhneGross(name)?.let { return it.id }
        val kanonisch = IngredientCatalog.canonicalName(name)
        dao.nachNameOhneGross(kanonisch)?.let { return it.id }
        val neu = ZutatEntity(
            id = neueId(), canonicalName = kanonisch, category = IngredientCatalog.guessCategory(kanonisch),
            createdAt = jetzt, updatedAt = jetzt,
        )
        dao.upsert(neu)
        return neu.id
    }
}

private fun neueId() = UUID.randomUUID().toString()


internal fun RezeptEntity.einordnung(): Einordnung =
    RezeptEinordnung.einordnen(name, tagsAus(tags), Mahlzeit.mengeAus(mahlzeiten), Gang.mengeAus(gaenge))

private fun RezeptEntity.karte(zeilen: List<RezeptZutatEntity>, katalog: Map<String, Ingredient>, gekocht: Int): RezeptKarte {
    val profil = RecipeProfiles.profile(alsRecipe(zeilen), katalog)
    return RezeptKarte(
        id = id,
        name = name,
        bild = imageUri,
        kcalJePortion = profil.kcalPerServing,
        eiweissJePortion = profil.proteinPerServing,
        zeitMinuten = listOfNotNull(prepMinutes, cookMinutes).takeIf { it.isNotEmpty() }?.sum(),
        favorit = favorite,
        gekocht = gekocht,
        diaeten = profil.diets,
        einordnung = einordnung(),
        tags = tagsAus(tags),
        zutatNamen = zeilen.mapNotNull { katalog[it.ingredientId]?.name },
        archiviert = archivedAt != null,
        angelegt = createdAt,
    )
}
