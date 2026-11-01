package de.foody.feature.rezepte

import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.rezepte.ImportErgebnis
import de.foody.core.data.rezepte.RezeptImporter
import de.foody.core.data.rezeptimport.NachtragErgebnis
import de.foody.core.designsystem.FoodyTheme
import de.foody.feature.rezepte.importieren.ImportBlatt
import de.foody.feature.rezepte.importieren.ImportViewModel
import de.foody.feature.rezepte.importieren.ImportZustand
import de.foody.feature.rezepte.importieren.nachtragText
import de.foody.feature.rezepte.importieren.zusammenfassung
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Ersatz für den Import: meldet Fortschritt und wartet auf [weiter], dann [ergebnis]. */
private class ErsatzImporter(private val ergebnis: ImportErgebnis) : RezeptImporter {
    val weiter = CompletableDeferred<Unit>()

    override suspend fun dateien(uris: List<Uri>, onFortschritt: (Int, Int) -> Unit): ImportErgebnis {
        onFortschritt(1, 3)
        weiter.await()
        return ergebnis
    }

    override suspend fun ordner(baum: Uri, onFortschritt: (Int, Int) -> Unit): ImportErgebnis = dateien(emptyList(), onFortschritt)

    var nachtragOrdner: Uri? = null

    override suspend fun portionenNachtragen(baum: Uri): NachtragErgebnis {
        nachtragOrdner = baum
        weiter.await()
        return NachtragErgebnis(aktualisiert = 2, uebersprungen = 1, ohneRezept = 3)
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class ImportBlattTest {
    @get:Rule
    val compose = createComposeRule()

    private val ergebnis = ImportErgebnis(listOf("r1"), uebersprungen = 2, fehler = listOf("x.md", "y.md"))

    @Test
    fun zusammenfassungTexte() {
        assertEquals("1 importiert · 2 übersprungen (schon vorhanden) · 2 Fehler: x.md, y.md", zusammenfassung(ergebnis))
        assertEquals("3 importiert", zusammenfassung(ImportErgebnis(listOf("a", "b", "c"), 0, emptyList())))
    }

    @Test
    fun fortschrittUndZusammenfassung() {
        val importer = ErsatzImporter(ergebnis)
        val vm = ImportViewModel(importer)
        compose.setContent { FoodyTheme { ImportBlatt(onSchliessen = {}, vm = vm) } }
        compose.warteAufText("Dateien wählen")
        compose.runOnIdle { vm.dateien(listOf(Uri.parse("file:///a.md"))) }
        compose.warteAufText("Importiere 1 von 3 …")
        importer.weiter.complete(Unit)
        compose.warteAufText(zusammenfassung(ergebnis))
        assertEquals(ImportZustand.Fertig(ergebnis), vm.zustand.value)
    }

    @Test
    fun nachtragTexte() {
        assertEquals(
            "2 Rezepte aktualisiert · 1 schon richtig · 3 Dateien ohne passendes Rezept",
            nachtragText(NachtragErgebnis(2, 1, 3)),
        )
        assertEquals("1 Rezept aktualisiert", nachtragText(NachtragErgebnis(1, 0, 0)))
        assertEquals("Nichts nachzutragen", nachtragText(NachtragErgebnis(0, 0, 0)))
    }

    /** R10: Portionen aus einem Ordner nachtragen, mit Zusammenfassung. */
    @Test
    fun portionenNachtragen() {
        val importer = ErsatzImporter(ergebnis)
        val vm = ImportViewModel(importer)
        compose.setContent { FoodyTheme { ImportBlatt(onSchliessen = {}, vm = vm) } }
        compose.warteAufText("Portionen nachtragen")
        val ordner = Uri.parse("file:///rezepte")
        compose.runOnIdle { vm.portionen(ordner) }
        compose.warteAufText("Dateien werden gelesen …")
        importer.weiter.complete(Unit)
        compose.warteAufText(nachtragText(NachtragErgebnis(2, 1, 3)))
        assertEquals(ordner, importer.nachtragOrdner)
    }
}
