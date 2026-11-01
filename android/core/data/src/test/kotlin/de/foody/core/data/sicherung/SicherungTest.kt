package de.foody.core.data.sicherung

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import de.foody.core.data.Einstellungsschluessel
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
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.startdaten.StartdatenLader
import de.foody.core.data.sync.SyncSpeicher
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.TagebuchArt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sicherung als ZIP: Format 3 schreiben und lesen, Sicherungen der v1 ablehnen, Fotos, Sperre bei
 * aktivem Sync und Schutz vor manipulierten oder beschädigten Dateien (nichts halb importiert).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SicherungTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val offen = mutableListOf<FoodyDatenbank>()
    private lateinit var db: FoodyDatenbank
    private lateinit var ablage: FotoAblage
    private lateinit var sync: SyncSpeicher
    private lateinit var sicherung: Sicherung
    private val ordner get() = File(context.filesDir, Einstellungsschluessel.FOTO_ORDNER)

    private fun neueDatenbank(): FoodyDatenbank = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK)
        .allowMainThreadQueries()
        .build()
        .also { offen += it }

    private fun sicherungFuer(d: FoodyDatenbank): Sicherung {
        val s = SyncSpeicher(d, ablage, FotoIndex(d.syncDao(), ablage))
        return Sicherung(context, d, ablage, s, StartdatenLader(d, context))
    }

    @Before
    fun setUp() {
        ordner.deleteRecursively()
        db = neueDatenbank()
        ablage = FotoAblage(context)
        sync = SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))
        sicherung = Sicherung(context, db, ablage, sync, StartdatenLader(db, context))
    }

    @After
    fun tearDown() {
        offen.forEach { it.close() }
        ordner.deleteRecursively()
    }

    // --- Hilfen ---

    private fun zutat(id: String, name: String = "Zutat $id") = ZutatEntity(id = id, canonicalName = name, createdAt = 1, updatedAt = 1)

    private fun rezept(id: String, bild: String? = null) = RezeptEntity(
        id = id, name = "Rezept $id", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = bild,
        notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
    )

    private suspend fun schreiben(s: Sicherung = sicherung): ByteArray = ByteArrayOutputStream().also { s.schreiben(it) }.toByteArray()

    private suspend fun lesen(bytes: ByteArray, s: Sicherung = sicherung): ImportErgebnis = s.lesen(ByteArrayInputStream(bytes))

    private fun zip(vararg eintraege: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { aus ->
        ZipOutputStream(aus).use { z -> eintraege.forEach { (name, inhalt) -> z.putNextEntry(ZipEntry(name)); z.write(inhalt); z.closeEntry() } }
    }.toByteArray()

    private fun eintraege(zipBytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { z -> generateSequence { z.nextEntry }.forEach { put(it.name, z.readBytes()) } }
    }

    private fun zeilen(d: FoodyDatenbank = db): Map<String, Int> {
        val tabellen = listOf(
            "ingredient", "recipe", "recipe_ingredient", "instruction_step", "meal_slot", "shopping_list", "shopping_item",
            "shopping_item_source", "tagebuch_eintrag", "sync_outbox",
        )
        return tabellen.associateWith { t ->
            d.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $t").use { it.moveToFirst(); it.getInt(0) }
        }
    }

    private fun fotodateien(): Set<String> = ordner.list().orEmpty().toSet()

    /** Kleines gültiges JPEG (der Verkleinerungsversuch nach dem Import liest es ohne libjpeg-Fehler). */
    private fun kleinesJpeg(farbe: Int = Color.RED): ByteArray {
        val bild = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(farbe) }
        return ByteArrayOutputStream().also { bild.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** Kleiner gültiger Bestand: eine Zutat, ein Rezept mit Zeile und Schritt. */
    private suspend fun kleinerBestand() {
        db.zutatDao().upsert(zutat("z1", "Mehl"))
        db.rezeptDao().upsert(rezept("r1"))
        db.rezeptZutatDao().upsert(RezeptZutatEntity("rz1", "r1", "z1", BigDecimal("200"), MeasureUnit.GRAM, 0, null, false))
        db.arbeitsschrittDao().upsert(ArbeitsschrittEntity("s1", "r1", 1, "Rühren"))
    }

    /** `backup.json` im Format 3 mit Freitext-Mahlzeit (aus v1-Zeiten migrierte Planeinträge) und Fotoverweis. */
    private val formatDreiJson = """
        {
            "format": 3,
            "ingredients": [
                {"id": "z1", "name": "Tomate", "category": "Gemüse", "density": null, "pieceWeight": "120", "basis": "PER_100_G",
                 "energyKj": "75.5", "protein": "0.9", "carbs": "2.6", "fat": "0.2", "fiber": null, "sugar": null, "salt": null,
                 "source": "BLS", "createdAt": 1, "updatedAt": 2, "barcode": "4006040002031"}
            ],
            "recipes": [
                {"id": "r1", "name": "Tomatensuppe", "servings": 2, "prep": 10, "cook": 20,
                 "imageUri": "foody-backup-photo:1.jpg", "notes": "Lecker", "tags": "Suppe", "archivedAt": null,
                 "createdAt": 1, "updatedAt": 2, "favorite": true, "sourceUrl": "https://example.org/suppe", "rating": 4,
                 "mahlzeiten": "MITTAGESSEN", "gaenge": null}
            ],
            "recipeIngredients": [
                {"id": "rz1", "recipeId": "r1", "ingredientId": "z1", "amount": "500", "unit": "GRAM", "sortOrder": 0,
                 "note": "gewürfelt", "optional": false}
            ],
            "steps": [{"id": "s1", "recipeId": "r1", "position": 1, "text": "Kochen."}],
            "mealSlots": [
                {"id": "m1", "date": "2026-10-10", "slotType": "Frühstück", "recipeId": "r1", "servings": 2,
                 "cookedAt": null, "createdAt": 1, "updatedAt": 2}
            ],
            "shoppingLists": [{"id": "l1", "name": "Woche", "start": "2026-10-10", "end": "2026-10-16", "version": 1, "createdAt": 1, "updatedAt": 2}],
            "shoppingItems": [
                {"id": "a1", "listId": "l1", "ingredientId": "z1", "name": "Tomate", "amount": "500", "unit": "GRAM",
                 "checked": true, "manual": false, "category": "Gemüse", "sortOrder": 0, "note": "Bio"}
            ],
            "shoppingSources": [
                {"id": "q1", "itemId": "a1", "mealSlotId": "m1", "recipeIngredientId": "rz1", "recipeName": "Tomatensuppe",
                 "date": "2026-10-10", "amount": "500", "unit": "GRAM"}
            ],
            "tagebuch": [
                {"id": "t1", "datum": "2026-10-10", "mahlzeit": "SNACK", "art": "ZUTAT", "name": "Tomate", "zutatId": "z1",
                 "menge": "150", "einheit": "GRAM", "energieKj": "113.25", "vollstaendig": false, "createdAt": 1, "updatedAt": 2}
            ]
        }
    """.trimIndent()

    // --- Tests ---

    @Test
    fun rundreiseFormatDrei() = runTest {
        db.zutatDao().upsert(
            ZutatEntity(
                id = "z1", canonicalName = "Joghurt", category = "Milch", densityGPerMl = BigDecimal("1.03"),
                pieceWeightG = BigDecimal("150"), nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("380.5"),
                protein = BigDecimal("3.5"), carbs = BigDecimal("4.0"), fat = BigDecimal("3.8"), fiber = BigDecimal("0"),
                sugar = BigDecimal("4.0"), salt = BigDecimal("0.10"), nutrientSource = "Packung", createdAt = 1, updatedAt = 2,
                barcode = "4006040002031",
            ),
        )
        db.zutatDao().upsert(zutat("z2", "Hafer"))
        db.rezeptDao().upsert(
            rezept("r1").copy(
                prepMinutes = 5, cookMinutes = 0, notes = "Notiz", tags = "Frühstück", archivedAt = 9, favorite = true,
                sourceUrl = "https://example.org/porridge", rating = 5, mahlzeiten = "FRUEHSTUECK", gaenge = "",
            ),
        )
        db.rezeptZutatDao().upsert(RezeptZutatEntity("rz1", "r1", "z1", BigDecimal("150.5"), MeasureUnit.GRAM, 0, "kalt", true))
        db.rezeptZutatDao().upsert(RezeptZutatEntity("rz2", "r1", "z2", BigDecimal("50"), MeasureUnit.GRAM, 1, null, false))
        db.arbeitsschrittDao().upsert(ArbeitsschrittEntity("s1", "r1", 1, "Mischen."))
        db.planEintragDao().upsert(PlanEintragEntity("m1", LocalDate.of(2026, 10, 12), "FRUEHSTUECK", "r1", 2, cookedAt = 5, createdAt = 1, updatedAt = 2))
        db.einkaufslisteDao().upsert(EinkaufslisteEntity("l1", "Woche", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18), 3, 1, 2))
        db.einkaufsartikelDao().upsert(
            EinkaufsartikelEntity(
                id = "a1", listId = "l1", ingredientId = "z1", name = "Joghurt", amount = BigDecimal("300"), unit = MeasureUnit.GRAM,
                checked = true, manual = false, category = "Milch", sortOrder = 0, note = "Bio", updatedAt = 7, checkedChangedAt = 6,
            ),
        )
        db.einkaufsartikelDao().upsert(EinkaufsartikelEntity(id = "a2", listId = "l1", name = "Kerzen", manual = true, sortOrder = 1))
        db.einkaufsartikelQuelleDao().upsert(
            EinkaufsartikelQuelleEntity("q1", "a1", "m1", "rz1", "Porridge", LocalDate.of(2026, 10, 12), BigDecimal("300"), MeasureUnit.GRAM),
        )
        db.tagebuchDao().upsert(
            TagebuchEintragEntity(
                id = "t1", datum = LocalDate.of(2026, 10, 10), mahlzeit = "SNACK", art = TagebuchArt.ZUTAT, name = "Joghurt",
                zutatId = "z1", menge = BigDecimal("150"), einheit = MeasureUnit.GRAM, energieKj = BigDecimal("380.5"),
                vollstaendig = false, createdAt = 1, updatedAt = 2,
            ),
        )

        val bytes = schreiben()
        val text = eintraege(bytes).getValue("backup.json").decodeToString()
        assertTrue(Regex(""""format"\s*:\s*3""").containsMatchIn(text), text)
        assertFalse("pantry" in text, "Format 3 enthält keinen Vorrat")

        val ziel = neueDatenbank()
        val ergebnis = lesen(bytes, sicherungFuer(ziel))
        assertEquals(ImportErgebnis.Fertig(rezepte = 1, zutaten = 2, fotos = 0), ergebnis)

        assertEquals(db.zutatDao().alle(), ziel.zutatDao().alle())
        assertEquals(db.rezeptDao().alle(), ziel.rezeptDao().alle())
        assertEquals(db.rezeptZutatDao().alle(), ziel.rezeptZutatDao().alle())
        assertEquals(db.arbeitsschrittDao().alle(), ziel.arbeitsschrittDao().alle())
        assertEquals(db.planEintragDao().alle(), ziel.planEintragDao().alle())
        assertEquals(db.einkaufslisteDao().alle(), ziel.einkaufslisteDao().alle())
        // Mit Zeitstempeln: „Zuletzt gekauft“ sortiert nach checkedChangedAt, der Sync vergleicht updatedAt
        assertEquals(db.einkaufsartikelDao().alle(), ziel.einkaufsartikelDao().alle())
        assertEquals(6, ziel.einkaufsartikelDao().nachId("a1")?.checkedChangedAt)
        assertEquals(db.einkaufsartikelQuelleDao().alle(), ziel.einkaufsartikelQuelleDao().alle())
        assertEquals(db.tagebuchDao().alle(), ziel.tagebuchDao().alle())
        // Inaktiver Sync: die Trigger merken beim Import nichts vor, später geht nichts Falsches hinaus
        assertTrue(ziel.syncDao().outbox().isEmpty())
    }

    @Test
    fun importErsetztVorhandeneDaten() = runTest {
        db.zutatDao().upsert(zutat("z1", "Mehl"))
        db.rezeptDao().upsert(rezept("r1"))
        val bytes = schreiben()

        db.rezeptDao().upsert(rezept("r2"))
        db.zutatDao().upsert(zutat("z9", "Salz"))
        assertIs<ImportErgebnis.Fertig>(lesen(bytes))

        assertEquals(listOf("r1"), db.rezeptDao().alle().map { it.id })
        assertEquals(listOf("z1"), db.zutatDao().alle().map { it.id })
        assertTrue(db.syncDao().outbox().isEmpty())
    }

    @Test
    fun formatDreiWirdGelesen() = runTest {
        val foto = kleinesJpeg()
        val bytes = zip("photos/1.jpg" to foto, "backup.json" to formatDreiJson.toByteArray())

        val ergebnis = lesen(bytes)

        assertEquals(ImportErgebnis.Fertig(rezepte = 1, zutaten = 1, fotos = 1), ergebnis)
        val r = assertNotNull(db.rezeptDao().nachId("r1"))
        assertEquals("Tomatensuppe", r.name)
        assertEquals(true, r.favorite)
        assertEquals(4, r.rating)
        assertEquals("MITTAGESSEN", r.mahlzeiten)
        val datei = assertNotNull(ablage.datei(r.imageUri!!), "Foto liegt als eigene Datei im Fotoordner")
        assertContentEquals(foto, datei.readBytes())
        assertEquals("4006040002031", db.zutatDao().nachId("z1")?.barcode)
        assertEquals(BigDecimal("75.5"), db.zutatDao().nachId("z1")?.energyKj)
        assertEquals("FRUEHSTUECK", db.planEintragDao().nachId("m1")?.slotType, "Alter Freitext wird zugeordnet")
        assertEquals("Bio", db.einkaufsartikelDao().nachId("a1")?.note)
        assertEquals(1, db.einkaufsartikelQuelleDao().alle().size)
        assertEquals(BigDecimal("113.25"), db.tagebuchDao().nachId("t1")?.energieKj)
        assertTrue(db.syncDao().outbox().isEmpty())
    }

    /** C8: Sicherungen der v1 (mit Vorrat, auch als reines JSON) liest die App nicht mehr; nichts wird geändert. */
    @Test
    fun vEinsWirdAbgelehnt() = runTest {
        kleinerBestand()
        val vorher = zeilen()
        val vEins = formatDreiJson.replace("\"format\": 3", "\"formatVersion\": 1")
            .replace("\"shoppingLists\"", "\"pantry\": [{\"id\": \"p1\", \"ingredientId\": \"z1\"}],\n    \"shoppingLists\"")

        for (datei in listOf(zip("photos/1.jpg" to kleinesJpeg(), "backup.json" to vEins.toByteArray()), vEins.toByteArray())) {
            val fehler = assertIs<ImportErgebnis.Fehler>(lesen(datei))
            assertTrue("v1" in fehler.grund, fehler.grund)
        }
        // Ganz ohne Formatangabe (die ältesten v1-Dateien)
        val ohneAngabe = zip("backup.json" to formatDreiJson.replace("\"format\": 3,", "").toByteArray())
        assertTrue("v1" in assertIs<ImportErgebnis.Fehler>(lesen(ohneAngabe)).grund)

        assertEquals(vorher, zeilen())
        assertTrue(fotodateien().isEmpty(), "${fotodateien()}")
    }

    @Test
    fun fotosUeberstehenSicherung() = runTest {
        val inhalt = kleinesJpeg(Color.BLUE)
        val link = ablage.ablegen(inhalt)
        val galerie = "content://media/external/images/media/7"
        db.rezeptDao().upsert(rezept("r1", link))
        db.rezeptDao().upsert(rezept("r2", link))
        db.rezeptDao().upsert(rezept("r3", galerie))
        db.rezeptDao().upsert(rezept("r4"))

        val bytes = schreiben()
        val dateien = eintraege(bytes)
        assertEquals(1, dateien.keys.count { it.startsWith("photos/") }, "Gemeinsames Foto nur einmal: ${dateien.keys}")

        val ergebnis = lesen(bytes)
        assertEquals(ImportErgebnis.Fertig(rezepte = 4, zutaten = 0, fotos = 1), ergebnis)
        val neu = assertNotNull(db.rezeptDao().nachId("r1")?.imageUri)
        assertNotEquals(link, neu, "Wiederhergestellt als neue eigene Datei")
        assertEquals(neu, db.rezeptDao().nachId("r2")?.imageUri)
        assertContentEquals(inhalt, assertNotNull(ablage.datei(neu)).readBytes(), "Byte-gleich: der Hash bleibt stabil")
        assertEquals(galerie, db.rezeptDao().nachId("r3")?.imageUri, "Galerie-Link bleibt erhalten")
        assertNull(db.rezeptDao().nachId("r4")?.imageUri)
        assertEquals(setOf(ablage.datei(neu)!!.name), fotodateien(), "Nicht mehr benutzte alte Datei aufgeräumt")
    }

    @Test
    fun fremderDateipfadWirdVerworfen() = runTest {
        val privat = File(context.filesDir.parentFile, "databases/foody.db").toURI().toString()
        val text = formatDreiJson.replace("foody-backup-photo:1.jpg", privat)
        assertIs<ImportErgebnis.Fertig>(lesen(zip("backup.json" to text.toByteArray())))
        assertNull(db.rezeptDao().nachId("r1")?.imageUri, "Pfad in den privaten Speicher wird nicht übernommen")
    }

    /**
     * Abbruch genau beim Abschluss der Import-Transaktion (deterministisch: die Abfrage-Rückmeldung von Room läuft
     * synchron auf dem Transaktions-Thread und bricht beim äußersten `END TRANSACTION` ab). Die Daten sind dann
     * übernommen – ihre Fotos dürfen nicht als „vorgemerkt“ wieder gelöscht werden.
     */
    @Test
    fun abbruchBeimAbschlussBehaeltFotos() = runTest {
        // Callback läuft auf dem Transaktions-Thread: Zustand threadsicher halten.
        val jobRef = AtomicReference<Job>()
        val tiefe = AtomicInteger(0)
        val scharf = AtomicBoolean(false)

        @Suppress("DEPRECATION")
        val ziel = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ ->
                if (scharf.get()) {
                    if (sql.startsWith("BEGIN")) tiefe.incrementAndGet()
                    if (sql.startsWith("END TRANSACTION") && tiefe.decrementAndGet() == 0) {
                        scharf.set(false)
                        jobRef.get().cancel()
                    }
                }
            }, Runnable::run)
            .build()
            .also { offen += it }
        val s = sicherungFuer(ziel)
        val foto = kleinesJpeg()
        val bytes = zip("photos/1.jpg" to foto, "backup.json" to formatDreiJson.toByteArray())

        scharf.set(true)
        // Erst merken, dann starten: der Callback sieht den Job sicher.
        val job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) { s.lesen(ByteArrayInputStream(bytes)) }
        jobRef.set(job)
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(scharf.get(), "Abbruch beim Abschluss der Transaktion ausgelöst")
        val r = assertNotNull(ziel.rezeptDao().nachId("r1"), "Import übernommen")
        val datei = assertNotNull(ablage.datei(assertNotNull(r.imageUri)))
        assertTrue(datei.isFile, "Foto des übernommenen Rezepts bleibt: ${fotodateien()}")
        assertContentEquals(foto, datei.readBytes())
    }

    @Test
    fun importBeiAktivemSyncGesperrt() = runTest {
        kleinerBestand()
        val bytes = zip("photos/1.jpg" to byteArrayOf(1, 2, 3), "backup.json" to formatDreiJson.toByteArray())
        sync.aktivieren("https://foody.example", "konto", allesHochladen = false)
        val vorher = zeilen()
        val dateienVorher = fotodateien()

        assertEquals(ImportErgebnis.SyncAktiv, lesen(bytes))

        assertEquals(vorher, zeilen())
        assertEquals(dateienVorher, fotodateien())
        assertEquals("Rezept r1", db.rezeptDao().nachId("r1")?.name)
    }

    @Test
    fun zipSlipWirdAbgelehnt() = runTest {
        kleinerBestand()
        val vorher = zeilen()
        val bytes = zip(
            "photos/1.jpg" to byteArrayOf(1),
            "../evil" to "boom".toByteArray(),
            "backup.json" to formatDreiJson.toByteArray(),
        )

        assertTrue("Dateipfade" in assertIs<ImportErgebnis.Fehler>(lesen(bytes)).grund)

        assertEquals(vorher, zeilen())
        assertTrue(fotodateien().isEmpty(), "Vorgemerkte Fotos wieder entfernt: ${fotodateien()}")
        val boese = context.filesDir.parentFile!!.walkTopDown().filter { it.name == "evil" }.toList()
        assertTrue(boese.isEmpty(), "Keine Datei außerhalb des Fotoordners: $boese")

        for (name in listOf("photos/../../evil.jpg", "/data/evil.jpg", "photos\\..\\evil.jpg")) {
            val fehler = assertIs<ImportErgebnis.Fehler>(lesen(zip(name to byteArrayOf(1), "backup.json" to formatDreiJson.toByteArray())), name)
            assertTrue("Dateipfade" in fehler.grund, "$name: ${fehler.grund}")
        }
        assertEquals(vorher, zeilen())
    }

    @Test
    fun zuGrosseDateiWirdAbgelehnt() = runTest {
        kleinerBestand()
        val vorher = zeilen()

        // Grenzen wie in der v1: ein Foto höchstens 32 MB, backup.json höchstens 64 MB
        val grossesFoto = zip("photos/1.jpg" to ByteArray((32 shl 20) + 1), "backup.json" to formatDreiJson.toByteArray())
        val fehler = assertIs<ImportErgebnis.Fehler>(lesen(grossesFoto))
        assertTrue("groß" in fehler.grund, fehler.grund)

        val grossesJson = zip("backup.json" to (formatDreiJson + " ".repeat((64 shl 20) + 1 - formatDreiJson.length + 1)).toByteArray())
        assertIs<ImportErgebnis.Fehler>(lesen(grossesJson))

        assertEquals(vorher, zeilen())
        assertTrue(fotodateien().isEmpty(), "${fotodateien()}")
    }

    @Test
    fun unbekanntesFormatWirdAbgelehnt() = runTest {
        kleinerBestand()
        val vorher = zeilen()

        val neuer = lesen(zip("backup.json" to """{"format": 99, "ingredients": []}""".toByteArray()))
        val fehler = assertIs<ImportErgebnis.Fehler>(neuer)
        assertTrue("Format" in fehler.grund && "99" in fehler.grund, fehler.grund)

        val keineZahl = lesen(zip("backup.json" to formatDreiJson.replace("\"format\": 3", "\"format\": \"drei\"").toByteArray()))
        assertTrue("Format" in assertIs<ImportErgebnis.Fehler>(keineZahl).grund)
        assertIs<ImportErgebnis.Fehler>(lesen(zip("photos/1.jpg" to byteArrayOf(1))), "Ohne backup.json")
        assertIs<ImportErgebnis.Fehler>(lesen("kein json".toByteArray()))

        assertEquals(vorher, zeilen())
        assertTrue(fotodateien().isEmpty())
    }

    @Test
    fun abgebrochenerImportAendertNichts() = runTest {
        kleinerBestand()
        val vorher = zeilen()

        // Beschädigte Zip: nach backup.json und einem vollständigen Foto bricht das zweite Foto mitten im Inhalt ab
        val ganz = zip(
            "backup.json" to formatDreiJson.toByteArray(),
            "photos/1.jpg" to Random(1).nextBytes(20_000),
            "photos/2.jpg" to Random(2).nextBytes(100_000),
        )
        val kaputt = ganz.copyOf(ganz.size - 60_000)
        assertTrue("beschädigt" in assertIs<ImportErgebnis.Fehler>(lesen(kaputt)).grund)
        assertEquals(vorher, zeilen())
        assertTrue(fotodateien().isEmpty(), "Vorgemerkte Fotos wieder entfernt: ${fotodateien()}")

        // Fehler erst beim Schreiben in die Datenbank (Verweis auf eine fehlende Zutat): alles zurückgerollt
        val verweisFehlt = formatDreiJson.replace("\"ingredientId\": \"z1\", \"amount\": \"500\"", "\"ingredientId\": \"fehlt\", \"amount\": \"500\"")
        assertNotEquals(formatDreiJson, verweisFehlt)
        val verweis = assertIs<ImportErgebnis.Fehler>(lesen(zip("photos/1.jpg" to byteArrayOf(1), "backup.json" to verweisFehlt.toByteArray())))
        assertTrue("Verweise" in verweis.grund, verweis.grund)
        assertEquals(vorher, zeilen())
        assertEquals("Rezept r1", db.rezeptDao().nachId("r1")?.name)
        assertTrue(fotodateien().isEmpty())

        // Ungültige Werte (Einheit, Zahl außerhalb der Grenzen)
        assertIs<ImportErgebnis.Fehler>(lesen(zip("backup.json" to formatDreiJson.replace("\"unit\": \"GRAM\"", "\"unit\": \"EIMER\"").toByteArray())))
        assertIs<ImportErgebnis.Fehler>(lesen(zip("backup.json" to formatDreiJson.replace("\"75.5\"", "\"1E999999999\"").toByteArray())))
        assertEquals(vorher, zeilen())
    }
}
