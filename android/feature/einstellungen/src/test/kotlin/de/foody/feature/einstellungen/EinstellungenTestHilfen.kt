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
import de.foody.core.sync.Ergebnis
import de.foody.core.sync.ErsterSync
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
 * Komposition anlegen.
 */
internal class EinstellungenTestUmgebung {
    val kontext: Context = RuntimeEnvironment.getApplication()
    val db: FoodyDatenbank = Room.inMemoryDatabaseBuilder(kontext, FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
    private val ablage = FotoAblage(kontext)
    val konto = ErsatzKonto()
    val darstellung = DarstellungSpeicher(kontext)
    val ziel = TageszielSpeicher(kontext)
    val sicherung = Sicherung(kontext, db, ablage, SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage)), StartdatenLader(db, kontext))

    init {
        kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    fun vm(kontoZugriff: KontoZugriff = konto) = EinstellungenViewModel(kontext, darstellung, ziel, sicherung, kontoZugriff, TEST_UHR)

    fun verbinden() {
        konto.stand.value = VerbindungsZustand(verbunden = true, adresse = "http://10.0.2.2:8081", letzterSync = null, letzterFehler = null)
    }
}

/** `backup.json` einer v1-Sicherung mit einem Rezept und zwei Vorratseinträgen (erfunden). */
val V1_SICHERUNG = """
    {
        "formatVersion": 1,
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
        "pantry": [
            {"id": "p1", "ingredientId": "z1", "amount": "3", "unit": "PIECE", "bestBefore": null, "updatedAt": 1},
            {"id": "p2", "ingredientId": "z1", "amount": "1", "unit": "PIECE", "bestBefore": null, "updatedAt": 1}
        ],
        "shoppingLists": [],
        "shoppingItems": [],
        "shoppingSources": [],
        "tagebuch": []
    }
""".trimIndent()
