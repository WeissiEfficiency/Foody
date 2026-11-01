package de.foody.core.data.sync

import androidx.room.Room
import de.foody.api.protocol.PayloadValidator
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecipePayload
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncRevisionEntity
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.TagebuchArt
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Abbildung lokaler Zeilen auf Sync-Datensätze und zurück: lokal → Record → in eine leere Datenbank anwenden ergibt
 * dieselben Zeilen; die erzeugten Payloads bestehen den [PayloadValidator] des Protokolls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class AbbildungTest {
    private lateinit var quelle: FoodyDatenbank
    private lateinit var ziel: FoodyDatenbank

    private fun neueDb() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK)
        .allowMainThreadQueries()
        .build()

    @Before
    fun setUp() {
        quelle = neueDb()
        ziel = neueDb()
    }

    @After
    fun tearDown() {
        quelle.close()
        ziel.close()
    }

    private val tag = LocalDate.of(2026, 10, 18)

    // Lokale Felder ohne Payload-Gegenstück (createdAt, version) so gewählt, wie sie beim Neuanlegen entstehen.
    private val zutat = ZutatEntity(
        id = "z1", canonicalName = "Milch", category = "Milchprodukte", densityGPerMl = BigDecimal("1.03"),
        pieceWeightG = null, nutrientBasis = NutrientBasis.PER_100_ML, energyKj = BigDecimal("268"),
        protein = BigDecimal("3.4"), carbs = BigDecimal("4.80"), fat = BigDecimal("0.15"), fiber = BigDecimal("0"),
        sugar = BigDecimal("4.8"), salt = BigDecimal("0.1"), nutrientSource = "BLS", createdAt = 20, updatedAt = 20,
        barcode = "4012345678901",
    )
    private val zutat2 = ZutatEntity(id = "z2", canonicalName = "Mehl", createdAt = 21, updatedAt = 21)

    private val rezept = RezeptEntity(
        id = "r1", name = "Pfannkuchen", defaultServings = 4, prepMinutes = 10, cookMinutes = 20, imageUri = null,
        notes = "Teig ruhen lassen", tags = "süß,schnell", archivedAt = null, createdAt = 30, updatedAt = 30,
        favorite = true, sourceUrl = "https://example.org/p", rating = 4, mahlzeiten = "FRUEHSTUECK,SNACK",
        gaenge = "NACHSPEISE",
    )
    private val zeilen = listOf(
        RezeptZutatEntity("zr1", "r1", "z2", BigDecimal("250"), MeasureUnit.GRAM, 0, "gesiebt", false),
        RezeptZutatEntity("zr2", "r1", "z1", BigDecimal("0.50"), MeasureUnit.LITER, 1, null, true),
        RezeptZutatEntity("zr3", "r1", "z1", BigDecimal("2"), MeasureUnit.TABLESPOON, 2, null, false),
    )
    private val schritte = listOf(
        ArbeitsschrittEntity("s1", "r1", 0, "Verrühren"),
        ArbeitsschrittEntity("s2", "r1", 1, "Ausbacken"),
    )
    private val plan = PlanEintragEntity(
        id = "p1", date = tag, slotType = "MITTAGESSEN", recipeId = "r1", servings = 2, cookedAt = 77,
        createdAt = 40, updatedAt = 40,
    )
    private val liste = EinkaufslisteEntity(
        id = "l1", name = "Woche", rangeStart = tag, rangeEnd = tag.plusDays(6), generationVersion = 2,
        createdAt = 50, updatedAt = 50,
    )
    private val artikel = EinkaufsartikelEntity(
        id = "a1", listId = "l1", ingredientId = "z1", name = "Milch", amount = BigDecimal("1.5"),
        unit = MeasureUnit.LITER, checked = true, manual = false, category = "Milchprodukte", sortOrder = 3,
        note = "Bio", updatedAt = 60, checkedChangedAt = 55,
    )
    private val quellen = listOf(
        EinkaufsartikelQuelleEntity("q1", "a1", "p1", "zr2", "Pfannkuchen", tag, BigDecimal("0.5"), MeasureUnit.LITER),
        EinkaufsartikelQuelleEntity("q2", "a1", "p2", "zr2", "Pfannkuchen", tag.plusDays(1), BigDecimal("1"), MeasureUnit.LITER),
    )
    private val tagebuch = TagebuchEintragEntity(
        id = "t1", datum = tag, mahlzeit = "ABENDESSEN", art = TagebuchArt.REZEPT, name = "Pfannkuchen",
        rezeptId = "r1", planEintragId = "p1", portionen = BigDecimal("1.5"), energieKj = BigDecimal("1200.5"),
        eiweiss = BigDecimal("12"), kohlenhydrate = BigDecimal("40"), fett = BigDecimal("8"), vollstaendig = false,
        createdAt = 70, updatedAt = 70,
    )

    private suspend fun befuellen(db: FoodyDatenbank) {
        db.zutatDao().upsert(zutat)
        db.zutatDao().upsert(zutat2)
        db.rezeptDao().upsert(rezept)
        zeilen.forEach { db.rezeptZutatDao().upsert(it) }
        schritte.forEach { db.arbeitsschrittDao().upsert(it) }
        db.planEintragDao().upsert(plan)
        db.einkaufslisteDao().upsert(liste)
        db.einkaufsartikelDao().upsert(artikel)
        quellen.forEach { db.einkaufsartikelQuelleDao().upsert(it) }
        db.tagebuchDao().upsert(tagebuch)
    }

    /** Wie der Sync-Speicher (Task 7): beim Anwenden ruhen Outbox- und Pflege-Trigger. */
    private suspend fun anwendenWieSync(db: FoodyDatenbank, record: SyncRecord) {
        db.syncDao().upsertZustand(SyncZustandEntity(applyingRemote = true))
        Abbildung.anwenden(db, record)
        db.syncDao().upsertZustand(SyncZustandEntity(applyingRemote = false))
    }

    private suspend fun record(db: FoodyDatenbank, typ: RecordType, id: String): SyncRecord {
        val r = assertNotNull(Abbildung.zuSyncRecord(db, typ, id, geloescht = false))
        assertNull(PayloadValidator.validate(r), "Payload von $typ/$id muss gültig sein: ${r.payload}")
        // Über das Netz geht der Datensatz als JSON: auch die Rundreise durch den Text muss halten.
        return Protocol.json.decodeFromString(SyncRecord.serializer(), Protocol.json.encodeToString(SyncRecord.serializer(), r))
    }

    private suspend fun alleZeilen(db: FoodyDatenbank): List<List<Any>> = listOf(
        db.zutatDao().alle(),
        db.rezeptDao().alle(),
        db.rezeptZutatDao().alle(),
        db.arbeitsschrittDao().alle(),
        db.planEintragDao().alle(),
        db.einkaufslisteDao().alle(),
        db.einkaufsartikelDao().alle(),
        db.einkaufsartikelQuelleDao().alle(),
        db.tagebuchDao().alle(),
    )

    @Test
    fun jederTypRundreise() = runTest {
        befuellen(quelle)
        val ids = listOf(
            RecordType.INGREDIENT to "z1", RecordType.INGREDIENT to "z2", RecordType.RECIPE to "r1",
            RecordType.MEAL_SLOT to "p1", RecordType.SHOPPING_LIST to "l1", RecordType.SHOPPING_ITEM to "a1",
            RecordType.TAGEBUCH_EINTRAG to "t1",
        )
        assertEquals(RecordType.entries.toSet(), ids.map { it.first }.toSet())
        val records = ids.map { (typ, id) -> record(quelle, typ, id) }
        records.forEach {
            assertEquals(false, it.deleted)
            assertNull(it.rev)
        }
        // Fotos kommen mit dem Sync-Speicher; hier bleibt das Fotofeld leer.
        val rezeptPayload = Protocol.json.decodeFromJsonElement(RecipePayload.serializer(), assertNotNull(records[2].payload))
        assertNull(rezeptPayload.photo)
        assertEquals(30, records[2].updatedAt)

        records.forEach { anwendenWieSync(ziel, it) }

        assertEquals(alleZeilen(quelle), alleZeilen(ziel))
        // Anwenden ist idempotent.
        records.forEach { anwendenWieSync(ziel, it) }
        assertEquals(alleZeilen(quelle), alleZeilen(ziel))
    }

    @Test
    fun anwendenBehaeltLokaleFelder() = runTest {
        befuellen(quelle)
        befuellen(ziel)
        ziel.zutatDao().upsert(zutat.copy(createdAt = 5, version = 7, canonicalName = "Alte Milch"))
        ziel.rezeptDao().upsert(rezept.copy(createdAt = 6, version = 3, imageUri = "file:/bild.jpg", name = "Alt"))
        anwendenWieSync(ziel, record(quelle, RecordType.INGREDIENT, "z1"))
        anwendenWieSync(ziel, record(quelle, RecordType.RECIPE, "r1"))
        assertEquals(zutat.copy(createdAt = 5, version = 7), ziel.zutatDao().nachId("z1"))
        // Das Fotofeld des Datensatzes wird hier ignoriert, der lokale Link bleibt.
        assertEquals(rezept.copy(createdAt = 6, version = 3, imageUri = "file:/bild.jpg"), ziel.rezeptDao().nachId("r1"))
    }

    @Test
    fun einordnungUndMahlzeitUeberstehenSync() = runTest {
        befuellen(quelle)
        // "" = bewusst keine Mahlzeit, null = vermuten – beides muss getrennt ankommen.
        quelle.rezeptDao().upsert(rezept.copy(mahlzeiten = "", gaenge = null))
        val rezeptRecord = record(quelle, RecordType.RECIPE, "r1")
        ziel.zutatDao().upsert(zutat)
        ziel.zutatDao().upsert(zutat2)
        anwendenWieSync(ziel, rezeptRecord)
        val angekommen = assertNotNull(ziel.rezeptDao().nachId("r1"))
        assertEquals("", angekommen.mahlzeiten)
        assertNull(angekommen.gaenge)

        quelle.rezeptDao().upsert(rezept.copy(mahlzeiten = null, gaenge = "HAUPTSPEISE,BROTZEIT"))
        anwendenWieSync(ziel, record(quelle, RecordType.RECIPE, "r1"))
        val zweites = assertNotNull(ziel.rezeptDao().nachId("r1"))
        assertNull(zweites.mahlzeiten)
        assertEquals("HAUPTSPEISE,BROTZEIT", zweites.gaenge)

        anwendenWieSync(ziel, record(quelle, RecordType.MEAL_SLOT, "p1"))
        assertEquals("MITTAGESSEN", ziel.planEintragDao().nachId("p1")?.slotType)
        // Ältere Geräte senden die Mahlzeit noch als Freitext: auf den festen Schlüssel abbilden.
        val alt = record(quelle, RecordType.MEAL_SLOT, "p1").let { r ->
            val p = assertNotNull(r.payload)
            r.copy(id = "p2", payload = JsonObject(p + ("slotType" to JsonPrimitive("Abendessen"))))
        }
        anwendenWieSync(ziel, alt)
        assertEquals("ABENDESSEN", ziel.planEintragDao().nachId("p2")?.slotType)
        // Unbekannter Freitext bleibt, wie er ist.
        val frei = alt.copy(
            id = "p3",
            payload = JsonObject(assertNotNull(alt.payload) + ("slotType" to JsonPrimitive("Brunch"))),
        )
        anwendenWieSync(ziel, frei)
        assertEquals("Brunch", ziel.planEintragDao().nachId("p3")?.slotType)
    }

    @Test
    fun strichcodeUeberstehtSync() = runTest {
        quelle.zutatDao().upsert(zutat)
        quelle.zutatDao().upsert(zutat2)
        val mit = record(quelle, RecordType.INGREDIENT, "z1")
        assertEquals("4012345678901", mit.payload?.get("barcode")?.let { (it as JsonPrimitive).content })
        anwendenWieSync(ziel, mit)
        assertEquals("4012345678901", ziel.zutatDao().nachId("z1")?.barcode)
        // Ohne Strichcode entfällt das Feld; ein vorhandener lokaler Strichcode wird mit dem Server-Stand entfernt.
        val ohne = record(quelle, RecordType.INGREDIENT, "z2")
        assertNull(ohne.payload?.get("barcode"))
        anwendenWieSync(ziel, ohne.copy(id = "z1", payload = JsonObject(assertNotNull(ohne.payload) + ("name" to JsonPrimitive("Milch")))))
        assertNull(ziel.zutatDao().nachId("z1")?.barcode)
    }

    @Test
    fun rezeptKinderWerdenErsetzt() = runTest {
        befuellen(ziel)
        // Server-Stand: eine Zeile geändert, eine neu, zwei entfallen; ein Schritt geändert, einer entfällt.
        quelle.zutatDao().upsert(zutat)
        quelle.zutatDao().upsert(zutat2)
        quelle.rezeptDao().upsert(rezept.copy(updatedAt = 99))
        val neueZeilen = listOf(
            zeilen[1].copy(amount = BigDecimal("0.75"), sortOrder = 0),
            RezeptZutatEntity("zr9", "r1", "z2", BigDecimal("100"), MeasureUnit.GRAM, 1, null, false),
        )
        val neueSchritte = listOf(schritte[1].copy(position = 0, text = "Nur ausbacken"))
        neueZeilen.forEach { quelle.rezeptZutatDao().upsert(it) }
        neueSchritte.forEach { quelle.arbeitsschrittDao().upsert(it) }

        anwendenWieSync(ziel, record(quelle, RecordType.RECIPE, "r1"))

        assertEquals(neueZeilen.sortedBy { it.id }, ziel.rezeptZutatDao().alle())
        assertEquals(neueSchritte, ziel.arbeitsschrittDao().alle())
        assertEquals(99, ziel.rezeptDao().nachId("r1")?.updatedAt)
    }

    @Test
    fun einkaufsartikelQuellenWerdenErsetzt() = runTest {
        befuellen(ziel)
        quelle.einkaufslisteDao().upsert(liste)
        quelle.einkaufsartikelDao().upsert(artikel.copy(checked = false, checkedChangedAt = 80, updatedAt = 81))
        quelle.einkaufsartikelQuelleDao().upsert(quellen[1].copy(contributedAmount = BigDecimal("2")))

        anwendenWieSync(ziel, record(quelle, RecordType.SHOPPING_ITEM, "a1"))

        assertEquals(listOf(quellen[1].copy(contributedAmount = BigDecimal("2"))), ziel.einkaufsartikelQuelleDao().alle())
        // Zeitstempel kommen unverändert an (die Pflege-Trigger ruhen beim Anwenden).
        assertEquals(artikel.copy(checked = false, checkedChangedAt = 80, updatedAt = 81), ziel.einkaufsartikelDao().nachId("a1"))
    }

    @Test
    fun loeschungEntferntMitKindern() = runTest {
        befuellen(ziel)
        val rezeptWeg = SyncRecord(id = "r1", type = RecordType.RECIPE, deleted = true, updatedAt = 100)
        anwendenWieSync(ziel, rezeptWeg)
        assertNull(ziel.rezeptDao().nachId("r1"))
        assertEquals(emptyList(), ziel.rezeptZutatDao().alle())
        assertEquals(emptyList(), ziel.arbeitsschrittDao().alle())
        // Der Plan-Eintrag hängt per Fremdschlüssel am Rezept.
        assertEquals(emptyList(), ziel.planEintragDao().alle())

        anwendenWieSync(ziel, SyncRecord(id = "a1", type = RecordType.SHOPPING_ITEM, deleted = true, updatedAt = 100))
        assertEquals(emptyList(), ziel.einkaufsartikelDao().alle())
        assertEquals(emptyList(), ziel.einkaufsartikelQuelleDao().alle())

        for ((typ, id) in listOf(RecordType.SHOPPING_LIST to "l1", RecordType.TAGEBUCH_EINTRAG to "t1", RecordType.INGREDIENT to "z1")) {
            anwendenWieSync(ziel, SyncRecord(id = id, type = typ, deleted = true, updatedAt = 100))
        }
        assertEquals(emptyList(), ziel.einkaufslisteDao().alle())
        assertEquals(emptyList(), ziel.tagebuchDao().alle())
        assertEquals(listOf("z2"), ziel.zutatDao().alle().map { it.id })
        // Löschen eines unbekannten Datensatzes ist kein Fehler.
        anwendenWieSync(ziel, SyncRecord(id = "gibtsnicht", type = RecordType.MEAL_SLOT, deleted = true, updatedAt = 100))
    }

    @Test
    fun geloeschterDatensatzOhnePayload() = runTest {
        quelle.syncDao().upsertRevision(SyncRevisionEntity("recipe", "r1", 17))
        val r = assertNotNull(Abbildung.zuSyncRecord(quelle, RecordType.RECIPE, "r1", geloescht = true))
        assertTrue(r.deleted)
        assertNull(r.payload)
        assertEquals(17, r.baseRev)
        assertNull(PayloadValidator.validate(r))
        // Nicht (mehr) vorhandene Zeile als lebender Datensatz: nichts zu senden.
        assertNull(Abbildung.zuSyncRecord(quelle, RecordType.RECIPE, "r1", geloescht = false))
    }

    @Test
    fun loeschzeitpunktAusDerOutbox() = runTest {
        quelle.zutatDao().upsert(zutat2)
        quelle.syncDao().upsertZustand(SyncZustandEntity(active = true))
        quelle.zutatDao().loeschen("z2")
        val vorgemerkt = assertNotNull(quelle.syncDao().outboxEintrag("ingredient", "z2"))
        val r = assertNotNull(Abbildung.zuSyncRecord(quelle, RecordType.INGREDIENT, "z2", geloescht = true))
        assertEquals(vorgemerkt.queuedAt, r.updatedAt)
    }

    @Test
    fun lebenderDatensatzTraegtBaseRev() = runTest {
        quelle.zutatDao().upsert(zutat)
        assertNull(record(quelle, RecordType.INGREDIENT, "z1").baseRev)
        quelle.syncDao().upsertRevision(SyncRevisionEntity("ingredient", "z1", 5))
        assertEquals(5, record(quelle, RecordType.INGREDIENT, "z1").baseRev)
    }

    @Test
    fun ungueltigerPayloadSchreibtNichts() = runTest {
        val kaputt = SyncRecord(
            id = "z1", type = RecordType.INGREDIENT, updatedAt = 1,
            payload = buildJsonObject {
                put("name", JsonPrimitive("Milch"))
                put("energyKj", JsonPrimitive("1E999999999"))
            },
        )
        assertFailsWith<IllegalArgumentException> { anwendenWieSync(ziel, kaputt) }
        assertNull(ziel.zutatDao().nachId("z1"))
        assertFailsWith<IllegalArgumentException> {
            Abbildung.anwenden(ziel, SyncRecord(id = "z1", type = RecordType.INGREDIENT, updatedAt = 1))
        }
    }
}
