package de.foody.feature.einstellungen

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.sicherung.Sicherung
import de.foody.core.data.startdaten.StartdatenLader
import de.foody.core.data.sync.SyncSpeicher
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.erkennung.ErkennungEinstellung
import de.foody.core.erkennung.ModellDateien
import de.foody.core.erkennung.ModellVerwaltung
import de.foody.core.scan.ScanEinstellung
import de.foody.core.sync.Ergebnis
import de.foody.core.sync.ErsterSync
import de.foody.core.testing.ViewModelHalter
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Ersatz für das Konto: merkt sich die Aufrufe, Ergebnisse frei vorgebbar. */
internal class ErsatzKonto : KontoZugriff {
    val stand = MutableStateFlow(VerbindungsZustand(verbunden = false, adresse = null, letzterSync = null, letzterFehler = null))
    override val zustand = stand
    var anmeldeErgebnis: Ergebnis<Unit> = Ergebnis.Erfolg(Unit)
    var lokaleDaten = false
    var wiederanmeldung = false
    val aufrufe = mutableListOf<String>()

    override suspend fun anmelden(adresse: String, benutzer: String, passwort: String): Ergebnis<Unit> {
        aufrufe += "anmelden:$adresse:$benutzer"
        return anmeldeErgebnis
    }

    override suspend fun hatLokaleDaten() = lokaleDaten

    override suspend fun istWiederanmeldung() = wiederanmeldung

    override suspend fun aktivieren(modus: ErsterSync): Ergebnis<Unit> {
        aufrufe += "aktivieren:$modus"
        stand.value = VerbindungsZustand(verbunden = true, adresse = "https://foody.example", letzterSync = null, letzterFehler = null)
        return Ergebnis.Erfolg(Unit)
    }

    override suspend fun trennen() {
        aufrufe += "trennen"
        stand.value = VerbindungsZustand(verbunden = false, adresse = null, letzterSync = null, letzterFehler = null)
    }

    override fun jetztSynchronisieren() {
        aufrufe += "jetzt"
    }
}

/** Feste Uhr: Mittwoch, 28.10.2026, 12 Uhr (UTC). */
val TEST_UHR: Clock = Clock.fixed(LocalDate.of(2026, 10, 28).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

/**
 * Echte Datenbank, echte Sicherung und Speicher, Konto als [ErsatzKonto]; Prefs `foody` geleert. ViewModel außerhalb der
 * Komposition anlegen. Room-Abfragen laufen sofort im aufrufenden Thread: Flows wie die Katalog-Anzahl liefern dann schon
 * beim Anlegen des ViewModels (stabile Referenzbilder).
 */
internal class EinstellungenTestUmgebung {
    val kontext: Context = RuntimeEnvironment.getApplication()
    val db: FoodyDatenbank = Room.inMemoryDatabaseBuilder(kontext, FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries()
        .setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }
        .build()
    private val ablage = FotoAblage(kontext)
    val konto = ErsatzKonto()
    val darstellung = DarstellungSpeicher(kontext)
    val ziel = TageszielSpeicher(kontext)
    val sicherung = Sicherung(kontext, db, ablage, SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage)), StartdatenLader(db, kontext))

    init {
        kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    val zutaten = ZutatenRepository(db) { 1L }

    /** Nach dem Leeren der Prefs angelegt: Online-Suche steht auf dem Standard (an). */
    val scan = ScanEinstellung(kontext)

    /** Fotoerkennung ohne Modell, Schalter auf dem Standard (an). */
    val modell = ModellVerwaltung(ModellDateien(File(kontext.filesDir, "modell").apply { deleteRecursively() }), kontext)
    val erkennung = ErkennungEinstellung(
        kontext.also { it.getSharedPreferences("fotoerkennung", Context.MODE_PRIVATE).edit().clear().commit() },
        modell,
    )

    private val viewModels = ViewModelHalter()

    fun vm(kontoZugriff: KontoZugriff = konto) =
        viewModels.behalten(EinstellungenViewModel(kontext, darstellung, ziel, sicherung, zutaten, kontoZugriff, scan, modell, erkennung, TEST_UHR))
            .also { viewModels.zusehen(it.zustand) }

    /** Für `@After`: erst die ViewModels beenden, dann die Datenbank schließen ([ViewModelHalter]). */
    fun schliessen() {
        viewModels.schliessen()
        db.close()
    }

    fun verbinden() {
        konto.stand.value = VerbindungsZustand(verbunden = true, adresse = "http://10.0.2.2:8081", letzterSync = null, letzterFehler = null)
    }
}

/** Sicherung im Format 3 (ZIP mit `backup.json`) mit einem Rezept und einer Zutat (erfunden). */
fun sicherungZip(): ByteArray = ByteArrayOutputStream().also { aus ->
    ZipOutputStream(aus).use { zip ->
        zip.putNextEntry(ZipEntry("backup.json"))
        zip.write(SICHERUNG_JSON.toByteArray())
        zip.closeEntry()
    }
}.toByteArray()

private val SICHERUNG_JSON = """
    {
        "format": 3,
        "ingredients": [
            {"id": "z1", "name": "Tomate", "category": "Gemüse", "density": null, "pieceWeight": "120", "basis": "PER_100_G",
             "energyKj": "75.5", "protein": "0.9", "carbs": "2.6", "fat": "0.2", "fiber": null, "sugar": null, "salt": null,
             "source": "BLS", "createdAt": 1, "updatedAt": 2, "barcode": null}
        ],
        "recipes": [
            {"id": "r1", "name": "Tomatensuppe", "servings": 2, "prep": 10, "cook": 20, "imageUri": null, "notes": null,
             "tags": "", "archivedAt": null, "createdAt": 1, "updatedAt": 2, "favorite": false, "sourceUrl": null, "rating": null,
             "mahlzeiten": null, "gaenge": null}
        ],
        "recipeIngredients": [
            {"id": "rz1", "recipeId": "r1", "ingredientId": "z1", "amount": "500", "unit": "GRAM", "sortOrder": 0,
             "note": null, "optional": false}
        ],
        "steps": [],
        "mealSlots": [],
        "shoppingLists": [],
        "shoppingItems": [],
        "shoppingSources": [],
        "tagebuch": []
    }
""".trimIndent()

/** Kleinste GGUF-Datei (v3) mit `general.architecture` = [architektur]: `clip` ist der Bildteil, sonst der Sprachteil. */
fun ggufDatei(datei: File, architektur: String): File = datei.apply {
    val aus = ByteArrayOutputStream()
    fun bytes(n: Int, f: ByteBuffer.() -> Unit) = aus.write(ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).apply(f).array())
    fun text(s: String) = s.encodeToByteArray().let {
        bytes(8) { putLong(it.size.toLong()) }
        aus.write(it)
    }
    aus.write("GGUF".encodeToByteArray())
    bytes(4) { putInt(3) }
    bytes(8) { putLong(0) }
    bytes(8) { putLong(1) }
    text("general.architecture")
    bytes(4) { putInt(8) }
    text(architektur)
    aus.write(ByteArray(64))
    writeBytes(aus.toByteArray())
}
