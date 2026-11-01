package de.foody.core.data.rezeptimport

import android.content.Context
import androidx.room.Room
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecipePayload
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.decode
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.sync.SyncSpeicher
import de.foody.domain.MeasureUnit
import de.foody.domain.RezeptImport
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * Portionen-Nachtrag: Markdown-Dateien werden über die Quelle ihren Rezepten zugeordnet; nur `defaultServings` und
 * Notiz ändern sich, nie die Mengen. Bei aktivem Sync landet die Änderung über die Trigger in der Outbox.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class PortionenNachtragTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: FoodyDatenbank

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    // --- Hilfen ---

    private val quelle = "https://www.chefkoch.de/rezepte/1/spitzel.html"

    private fun datei(portionen: Int?, url: String? = quelle, name: String = "Spitzel") = "spitzel.md" to buildString {
        appendLine("# $name")
        if (url != null) appendLine("_Quelle: ${url}_")
        if (portionen != null) appendLine("Portionen: $portionen · Vorbereitung: 20 Min.")
        appendLine("## Zutaten")
        appendLine("500 g")
        appendLine("Mehl")
        appendLine("## Zubereitung")
        appendLine("Alles verrühren.")
    }

    /** Notiz eines vor Regel 27 importierten Rezepts: Portionen aus den Mengen geschätzt. */
    private fun geschaetzteNotiz(n: Int, url: String = quelle) = RezeptImport.hinweis(url, n, geschaetzt = true)

    private suspend fun rezeptAnlegen(id: String, portionen: Int, notiz: String?, url: String? = quelle) {
        if (db.zutatDao().nachId("z1") == null) {
            db.zutatDao().upsert(ZutatEntity(id = "z1", canonicalName = "Mehl", createdAt = 1, updatedAt = 1))
        }
        db.rezeptDao().upsert(
            RezeptEntity(
                id = id, name = "Spitzel", defaultServings = portionen, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = notiz, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1, sourceUrl = url,
            ),
        )
        db.rezeptZutatDao().upsert(
            RezeptZutatEntity(
                id = "$id-l0", recipeId = id, ingredientId = "z1", amount = BigDecimal("500"), unit = MeasureUnit.GRAM,
                sortOrder = 0, preparationNote = null, optional = false,
            ),
        )
    }

    private fun nachtrag(jetzt: Long = 1_000) = PortionenNachtrag(db) { jetzt }

    // --- Tests ---

    @Test
    fun geschaetztesRezeptBekommtPortionenDerDatei() = runTest {
        rezeptAnlegen("r1", 8, geschaetzteNotiz(8) + "\nMit Thymian.")
        val zeilenVorher = db.rezeptZutatDao().nachRezept("r1")

        val ergebnis = nachtrag().anwenden(listOf(datei(portionen = 4)))

        assertEquals(NachtragErgebnis(aktualisiert = 1, uebersprungen = 0, ohneRezept = 0), ergebnis)
        val r = db.rezeptDao().nachId("r1")!!
        assertEquals(4, r.defaultServings)
        assertEquals("Quelle: $quelle\nMit Thymian.", r.notes)
        // Wie jede lokale Rezeptänderung: neuer Zeitstempel, Version steigt
        assertEquals(1_000, r.updatedAt)
        assertEquals(2, r.version)
        // Mengen bleiben, wie sie sind
        assertEquals(zeilenVorher, db.rezeptZutatDao().nachRezept("r1"))
    }

    @Test
    fun vonHandGeaendertBleibt() = runTest {
        // Notiz nennt 2, der Nutzer hat inzwischen 3 eingestellt
        rezeptAnlegen("r1", 3, geschaetzteNotiz(2))
        // Hinweis vom Nutzer gelöscht
        rezeptAnlegen("r2", 2, "Quelle: https://example.org/b", url = "https://example.org/b")
        val vorher = db.rezeptDao().alle()

        val ergebnis = nachtrag().anwenden(listOf(datei(portionen = 6), datei(portionen = 6, url = "https://example.org/b")))

        assertEquals(NachtragErgebnis(aktualisiert = 0, uebersprungen = 2, ohneRezept = 0), ergebnis)
        assertEquals(vorher, db.rezeptDao().alle())
    }

    @Test
    fun ohnePassendesRezeptGezaehlt() = runTest {
        rezeptAnlegen("r1", 8, geschaetzteNotiz(8))

        val ergebnis = nachtrag().anwenden(
            listOf(
                datei(portionen = 4, url = "https://example.org/unbekannt"),
                datei(portionen = 4, url = null),
                "kaputt.md" to "kein Rezept",
                datei(portionen = 4),
            ),
        )

        assertEquals(NachtragErgebnis(aktualisiert = 1, uebersprungen = 0, ohneRezept = 3), ergebnis)
    }

    @Test
    fun mehrereDateienZumSelbenRezeptZaehltDieErste() = runTest {
        rezeptAnlegen("r1", 8, geschaetzteNotiz(8))

        val ergebnis = nachtrag().anwenden(listOf(datei(portionen = 4), datei(portionen = 6)))

        // Die erste Datei der Liste entscheidet; die zweite zählt nicht noch einmal
        assertEquals(NachtragErgebnis(aktualisiert = 1, uebersprungen = 0, ohneRezept = 0), ergebnis)
        assertEquals(4, db.rezeptDao().nachId("r1")!!.defaultServings)
    }

    @Test
    fun mehrereRezepteMitDerselbenQuelleEinzelnGeprueft() = runTest {
        rezeptAnlegen("r1", 8, geschaetzteNotiz(8))
        rezeptAnlegen("r2", 5, "Quelle: $quelle")

        val ergebnis = nachtrag().anwenden(listOf(datei(portionen = 4)))

        assertEquals(NachtragErgebnis(aktualisiert = 1, uebersprungen = 1, ohneRezept = 0), ergebnis)
        assertEquals(4, db.rezeptDao().nachId("r1")!!.defaultServings)
        assertEquals(5, db.rezeptDao().nachId("r2")!!.defaultServings)
    }

    @Test
    fun aenderungLandetInOutbox() = runTest {
        rezeptAnlegen("r1", 8, geschaetzteNotiz(8))
        val ablage = FotoAblage(context)
        val speicher = SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))
        speicher.aktivieren("https://foody.example", "konto-1", allesHochladen = false)
        assertEquals(emptyList(), db.syncDao().outbox())

        nachtrag().anwenden(listOf(datei(portionen = 4)))

        assertEquals(listOf("recipe" to "r1"), db.syncDao().outbox().map { it.type to it.recordId })
        val paket = speicher.naechstesPaket(Protocol.MAX_PUSH_RECORDS, Protocol.MAX_PUSH_BYTES)
        val payload = RecordType.RECIPE.decode(paket.single().record.payload!!) as RecipePayload
        assertEquals(4, payload.servings)
    }
}
