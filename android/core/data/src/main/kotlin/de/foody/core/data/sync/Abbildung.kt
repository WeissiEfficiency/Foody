package de.foody.core.data.sync

import de.foody.api.protocol.IngredientPayload
import de.foody.api.protocol.MealSlotPayload
import de.foody.api.protocol.PayloadValidator
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecipePayload
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.ShoppingItemPayload
import de.foody.api.protocol.ShoppingListPayload
import de.foody.api.protocol.SyncRecord
import de.foody.api.protocol.TagebuchPayload
import de.foody.api.protocol.decode
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.TagebuchArt
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

/**
 * Abbildung zwischen den lokalen Zeilen von `foody.db` und den Sync-Datensätzen des Protokolls 3 – in beide Richtungen.
 *
 * Wire-Formate wie die Sicherung: Zahlen als `toPlainString()`, Datumswerte ISO-8601, Aufzählungen über den Namen.
 * Ein Rezept ist mit seinen Zeilen (`recipe_ingredient`) und Schritten (`instruction_step`) ein Datensatz, ein
 * Einkaufsartikel mit seinen Herkunftszeilen (`shopping_item_source`).
 *
 * Rein lokale Felder ohne Payload-Gegenstück (`createdAt`, `version`, das Rezeptfoto `imageUri`) bleiben beim Anwenden
 * aus der vorhandenen Zeile erhalten; bei einer neuen Zeile gilt `createdAt = updatedAt`, `version = 1`, kein Foto.
 * Das Fotofeld des Rezepts (`photo`) bleibt hier `null` und wird beim Anwenden ignoriert – Fotos regelt der Sync-Speicher.
 */
object Abbildung {
    /**
     * Baut den Sync-Datensatz für die lokale Zeile [typ]/[id]; `baseRev` ist die zuletzt bestätigte Server-Revision.
     *
     * Mit [geloescht] entsteht eine Löschung ohne Payload; ihr Zeitpunkt ist der des Outbox-Eintrags (sonst jetzt).
     * Gibt es die Zeile nicht (mehr), liefert ein lebender Datensatz `null` – es gibt nichts zu senden.
     */
    suspend fun zuSyncRecord(db: FoodyDatenbank, typ: RecordType, id: String, geloescht: Boolean): SyncRecord? {
        val baseRev = db.syncDao().revision(typ.wire, id)?.rev
        if (geloescht) {
            val zeitpunkt = db.syncDao().outboxEintrag(typ.wire, id)?.queuedAt ?: System.currentTimeMillis()
            return SyncRecord(id = id, type = typ, deleted = true, updatedAt = zeitpunkt, baseRev = baseRev)
        }
        val (updatedAt, payload) = when (typ) {
            RecordType.INGREDIENT -> db.zutatDao().nachId(id)?.let { it.updatedAt to json(IngredientPayload.serializer(), zutat(it)) }
            RecordType.RECIPE -> db.rezeptDao().nachId(id)?.let {
                val zeilen = db.rezeptZutatDao().nachRezept(id)
                val schritte = db.arbeitsschrittDao().nachRezept(id)
                it.updatedAt to json(RecipePayload.serializer(), rezept(it, zeilen, schritte))
            }
            RecordType.MEAL_SLOT -> db.planEintragDao().nachId(id)?.let { it.updatedAt to json(MealSlotPayload.serializer(), plan(it)) }
            RecordType.SHOPPING_LIST ->
                db.einkaufslisteDao().nachId(id)?.let { it.updatedAt to json(ShoppingListPayload.serializer(), liste(it)) }
            RecordType.SHOPPING_ITEM -> db.einkaufsartikelDao().nachId(id)?.let {
                val quellen = db.einkaufsartikelQuelleDao().nachArtikel(id)
                it.updatedAt to json(ShoppingItemPayload.serializer(), artikel(it, quellen))
            }
            RecordType.TAGEBUCH_EINTRAG ->
                db.tagebuchDao().nachId(id)?.let { it.updatedAt to json(TagebuchPayload.serializer(), tagebuch(it)) }
        } ?: return null
        return SyncRecord(id = id, type = typ, deleted = false, updatedAt = updatedAt, baseRev = baseRev, payload = payload)
    }

    /**
     * Schreibt [record] in [db], wie sie sie vorfindet: lebend per Upsert (Kindzeilen werden ersetzt – überzählige
     * gelöscht, die übrigen per Upsert geschrieben), gelöscht samt Kindern (Fremdschlüssel-Kaskade). Trigger-Ruhe
     * (`applyingRemote`) und Transaktion setzt der Aufrufer.
     *
     * Ein Datensatz, der den [PayloadValidator] nicht besteht, wirft [IllegalArgumentException], bevor etwas
     * geschrieben wird. Fremdschlüssel- oder Namenskonflikte wirft SQLite (`SQLiteConstraintException`).
     */
    suspend fun anwenden(db: FoodyDatenbank, record: SyncRecord) {
        require(PayloadValidator.validate(record) == null) { "Ungültiger Datensatz ${record.type.wire}/${record.id}" }
        if (record.deleted) return loeschen(db, record.type, record.id)
        val id = record.id
        val zeit = record.updatedAt
        when (val p = record.type.decode(requireNotNull(record.payload))) {
            is IngredientPayload -> db.zutatDao().upsert(zutat(id, p, zeit, db.zutatDao().nachId(id)))
            is RecipePayload -> {
                val rezept = rezept(id, p, zeit, db.rezeptDao().nachId(id))
                val zeilen = p.lines.map { zeile(id, it) }
                val schritte = p.steps.map { ArbeitsschrittEntity(id = it.id, recipeId = id, position = it.position, text = it.text) }
                db.rezeptDao().upsert(rezept)
                val zeilenIds = zeilen.mapTo(HashSet()) { it.id }
                db.rezeptZutatDao().nachRezept(id).filter { it.id !in zeilenIds }.forEach { db.rezeptZutatDao().loeschen(it.id) }
                val schrittIds = schritte.mapTo(HashSet()) { it.id }
                db.arbeitsschrittDao().nachRezept(id).filter { it.id !in schrittIds }.forEach { db.arbeitsschrittDao().loeschen(it.id) }
                zeilen.forEach { db.rezeptZutatDao().upsert(it) }
                schritte.forEach { db.arbeitsschrittDao().upsert(it) }
            }
            is MealSlotPayload -> db.planEintragDao().upsert(plan(id, p, zeit, db.planEintragDao().nachId(id)))
            is ShoppingListPayload -> db.einkaufslisteDao().upsert(liste(id, p, zeit, db.einkaufslisteDao().nachId(id)))
            is ShoppingItemPayload -> {
                val quellen = p.sources.map { quelle(id, it) }
                db.einkaufsartikelDao().upsert(artikel(id, p, zeit))
                val quellIds = quellen.mapTo(HashSet()) { it.id }
                db.einkaufsartikelQuelleDao().nachArtikel(id).filter { it.id !in quellIds }
                    .forEach { db.einkaufsartikelQuelleDao().loeschen(it.id) }
                quellen.forEach { db.einkaufsartikelQuelleDao().upsert(it) }
            }
            is TagebuchPayload -> db.tagebuchDao().upsert(tagebuch(id, p, zeit, db.tagebuchDao().nachId(id)))
            else -> error("Unbekannter Payload-Typ ${p::class.simpleName}")
        }
    }

    /** Löscht die lokale Zeile; Kindzeilen (und am Rezept hängende Plan-Einträge) per Fremdschlüssel-Kaskade. */
    private suspend fun loeschen(db: FoodyDatenbank, typ: RecordType, id: String) {
        when (typ) {
            RecordType.INGREDIENT -> db.zutatDao().loeschen(id)
            RecordType.RECIPE -> {
                db.rezeptDao().loeschen(id)
                // Ein offener Fotowunsch für ein gelöschtes Rezept ist gegenstandslos.
                db.syncDao().loeschenFotoGewuenscht(id)
            }
            RecordType.MEAL_SLOT -> db.planEintragDao().loeschen(id)
            RecordType.SHOPPING_LIST -> db.einkaufslisteDao().loeschen(id)
            RecordType.SHOPPING_ITEM -> db.einkaufsartikelDao().loeschen(id)
            RecordType.TAGEBUCH_EINTRAG -> db.tagebuchDao().loeschen(id)
        }
    }

    private fun <T> json(serializer: KSerializer<T>, payload: T): JsonObject =
        Protocol.json.encodeToJsonElement(serializer, payload) as JsonObject

    private fun BigDecimal.wire(): String = toPlainString()

    // --- Zutat ---

    private fun zutat(e: ZutatEntity) = IngredientPayload(
        name = e.canonicalName,
        category = e.category,
        density = e.densityGPerMl?.wire(),
        pieceWeight = e.pieceWeightG?.wire(),
        basis = e.nutrientBasis?.name,
        energyKj = e.energyKj?.wire(),
        protein = e.protein?.wire(),
        carbs = e.carbs?.wire(),
        fat = e.fat?.wire(),
        fiber = e.fiber?.wire(),
        sugar = e.sugar?.wire(),
        salt = e.salt?.wire(),
        source = e.nutrientSource,
        barcode = e.barcode,
    )

    private fun zutat(id: String, p: IngredientPayload, updatedAt: Long, vorhanden: ZutatEntity?) = ZutatEntity(
        id = id,
        canonicalName = p.name,
        category = p.category,
        densityGPerMl = p.density?.let(::BigDecimal),
        pieceWeightG = p.pieceWeight?.let(::BigDecimal),
        nutrientBasis = p.basis?.let(NutrientBasis::valueOf),
        energyKj = p.energyKj?.let(::BigDecimal),
        protein = p.protein?.let(::BigDecimal),
        carbs = p.carbs?.let(::BigDecimal),
        fat = p.fat?.let(::BigDecimal),
        fiber = p.fiber?.let(::BigDecimal),
        sugar = p.sugar?.let(::BigDecimal),
        salt = p.salt?.let(::BigDecimal),
        nutrientSource = p.source,
        createdAt = vorhanden?.createdAt ?: updatedAt,
        updatedAt = updatedAt,
        version = vorhanden?.version ?: 1,
        barcode = p.barcode,
    )

    // --- Rezept ---

    private fun rezept(r: RezeptEntity, zeilen: List<RezeptZutatEntity>, schritte: List<ArbeitsschrittEntity>) = RecipePayload(
        name = r.name,
        servings = r.defaultServings,
        prep = r.prepMinutes,
        cook = r.cookMinutes,
        photo = null,
        notes = r.notes,
        tags = r.tags,
        archivedAt = r.archivedAt,
        favorite = r.favorite,
        sourceUrl = r.sourceUrl,
        rating = r.rating,
        lines = zeilen.map {
            RecipePayload.Line(
                id = it.id,
                ingredientId = it.ingredientId,
                amount = it.amount.wire(),
                unit = it.unit.name,
                sortOrder = it.sortOrder,
                note = it.preparationNote,
                optional = it.optional,
            )
        },
        steps = schritte.map { RecipePayload.Step(id = it.id, position = it.position, text = it.text) },
        mahlzeiten = r.mahlzeiten,
        gaenge = r.gaenge,
    )

    private fun rezept(id: String, p: RecipePayload, updatedAt: Long, vorhanden: RezeptEntity?) = RezeptEntity(
        id = id,
        name = p.name,
        defaultServings = p.servings,
        prepMinutes = p.prep,
        cookMinutes = p.cook,
        imageUri = vorhanden?.imageUri,
        notes = p.notes,
        tags = p.tags,
        archivedAt = p.archivedAt,
        createdAt = vorhanden?.createdAt ?: updatedAt,
        updatedAt = updatedAt,
        version = vorhanden?.version ?: 1,
        favorite = p.favorite,
        sourceUrl = p.sourceUrl,
        rating = p.rating,
        mahlzeiten = p.mahlzeiten,
        gaenge = p.gaenge,
    )

    private fun zeile(rezeptId: String, l: RecipePayload.Line) = RezeptZutatEntity(
        id = l.id,
        recipeId = rezeptId,
        ingredientId = l.ingredientId,
        amount = BigDecimal(l.amount),
        unit = MeasureUnit.valueOf(l.unit),
        sortOrder = l.sortOrder,
        preparationNote = l.note,
        optional = l.optional,
    )

    // --- Wochenplan ---

    private fun plan(e: PlanEintragEntity) = MealSlotPayload(
        date = e.date.toString(),
        slotType = e.slotType,
        recipeId = e.recipeId,
        servings = e.servings,
        cookedAt = e.cookedAt,
    )

    private fun plan(id: String, p: MealSlotPayload, updatedAt: Long, vorhanden: PlanEintragEntity?) = PlanEintragEntity(
        id = id,
        date = LocalDate.parse(p.date),
        // Ältere Geräte senden noch Freitext („Abendessen“): auf den festen Schlüssel abbilden, Unbekanntes bleibt.
        slotType = Mahlzeit.ausText(p.slotType)?.name ?: p.slotType,
        recipeId = p.recipeId,
        servings = p.servings,
        cookedAt = p.cookedAt,
        createdAt = vorhanden?.createdAt ?: updatedAt,
        updatedAt = updatedAt,
    )

    // --- Einkaufsliste ---

    private fun liste(e: EinkaufslisteEntity) = ShoppingListPayload(
        name = e.name,
        start = e.rangeStart?.toString(),
        end = e.rangeEnd?.toString(),
        version = e.generationVersion,
    )

    private fun liste(id: String, p: ShoppingListPayload, updatedAt: Long, vorhanden: EinkaufslisteEntity?) = EinkaufslisteEntity(
        id = id,
        name = p.name,
        rangeStart = p.start?.let(LocalDate::parse),
        rangeEnd = p.end?.let(LocalDate::parse),
        generationVersion = p.version,
        createdAt = vorhanden?.createdAt ?: updatedAt,
        updatedAt = updatedAt,
    )

    // --- Einkaufsartikel ---

    private fun artikel(e: EinkaufsartikelEntity, quellen: List<EinkaufsartikelQuelleEntity>) = ShoppingItemPayload(
        listId = e.listId,
        ingredientId = e.ingredientId,
        name = e.name,
        amount = e.amount?.wire(),
        unit = e.unit?.name,
        checked = e.checked,
        checkedChangedAt = e.checkedChangedAt,
        manual = e.manual,
        category = e.category,
        sortOrder = e.sortOrder,
        note = e.note,
        sources = quellen.map {
            ShoppingItemPayload.Source(
                id = it.id,
                mealSlotId = it.mealSlotId,
                recipeIngredientId = it.recipeIngredientId,
                recipeName = it.recipeName,
                date = it.date.toString(),
                amount = it.contributedAmount.wire(),
                unit = it.unit.name,
            )
        },
    )

    /** Ein Einkaufsartikel hat keine rein lokalen Felder; alles kommt aus dem Datensatz. */
    private fun artikel(id: String, p: ShoppingItemPayload, updatedAt: Long) = EinkaufsartikelEntity(
        id = id,
        listId = p.listId,
        ingredientId = p.ingredientId,
        name = p.name,
        amount = p.amount?.let(::BigDecimal),
        unit = p.unit?.let(MeasureUnit::valueOf),
        checked = p.checked,
        manual = p.manual,
        category = p.category,
        sortOrder = p.sortOrder,
        note = p.note,
        updatedAt = updatedAt,
        checkedChangedAt = p.checkedChangedAt,
    )

    private fun quelle(artikelId: String, q: ShoppingItemPayload.Source) = EinkaufsartikelQuelleEntity(
        id = q.id,
        shoppingItemId = artikelId,
        mealSlotId = q.mealSlotId,
        recipeIngredientId = q.recipeIngredientId,
        recipeName = q.recipeName,
        date = LocalDate.parse(q.date),
        contributedAmount = BigDecimal(q.amount),
        unit = MeasureUnit.valueOf(q.unit),
    )

    // --- Tagebuch ---

    private fun tagebuch(e: TagebuchEintragEntity) = TagebuchPayload(
        datum = e.datum.toString(),
        mahlzeit = e.mahlzeit,
        art = e.art.name,
        name = e.name,
        rezeptId = e.rezeptId,
        planEintragId = e.planEintragId,
        portionen = e.portionen?.wire(),
        zutatId = e.zutatId,
        menge = e.menge?.wire(),
        einheit = e.einheit?.name,
        energieKj = e.energieKj?.wire(),
        eiweiss = e.eiweiss?.wire(),
        kohlenhydrate = e.kohlenhydrate?.wire(),
        fett = e.fett?.wire(),
        vollstaendig = e.vollstaendig,
    )

    private fun tagebuch(id: String, p: TagebuchPayload, updatedAt: Long, vorhanden: TagebuchEintragEntity?) = TagebuchEintragEntity(
        id = id,
        datum = LocalDate.parse(p.datum),
        mahlzeit = p.mahlzeit,
        art = TagebuchArt.valueOf(p.art),
        name = p.name,
        rezeptId = p.rezeptId,
        planEintragId = p.planEintragId,
        portionen = p.portionen?.let(::BigDecimal),
        zutatId = p.zutatId,
        menge = p.menge?.let(::BigDecimal),
        einheit = p.einheit?.let(MeasureUnit::valueOf),
        energieKj = p.energieKj?.let(::BigDecimal),
        eiweiss = p.eiweiss?.let(::BigDecimal),
        kohlenhydrate = p.kohlenhydrate?.let(::BigDecimal),
        fett = p.fett?.let(::BigDecimal),
        vollstaendig = p.vollstaendig,
        createdAt = vorhanden?.createdAt ?: updatedAt,
        updatedAt = updatedAt,
    )
}
