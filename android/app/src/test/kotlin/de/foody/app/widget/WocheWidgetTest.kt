package de.foody.app.widget

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.R
import de.foody.core.data.StilWahl
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.designsystem.stil.FoodyStil
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Widget „Woche“: Hauptgericht je Tag, Kürzel und Marken je Stil, Beschreibung und der Link auf „Woche“. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WocheWidgetTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()
    private val montag = LocalDate.of(2026, 11, 9)
    private val freitag = montag.plusDays(4)

    private fun eintrag(id: String, tag: Long, slot: String, rezept: String) =
        PlanEintragEntity(id, montag.plusDays(tag), slot, rezept, 2, createdAt = 1, updatedAt = 1)

    private val namen = mapOf("r1" to "Curry", "r2" to "Pfanne", "r3" to "Shakshuka mit Feta")

    private fun daten() = WocheDaten(
        freitag,
        wochenTage(
            montag,
            listOf(
                eintrag("a", 0, "MITTAGESSEN", "r2"), eintrag("b", 0, "ABENDESSEN", "r1"), eintrag("c", 4, "FRUEHSTUECK", "r3"),
                eintrag("d", 5, "ABENDESSEN", "weg"),
            ),
            namen,
        ),
    )

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun View.sichtbar(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    @Test
    fun hauptgerichtJeTag() {
        val tage = daten().tage
        assertEquals(7, tage.size)
        assertEquals("Curry", tage[0].gericht, "Abendessen vor Mittag")
        assertEquals(null, tage[1].gericht)
        assertEquals("Shakshuka mit Feta", tage[4].gericht)
        assertEquals(null, tage[5].gericht, "gelöschtes Rezept")
    }

    @Test
    fun texte() {
        assertEquals("Mo", tagKuerzel(FoodyStil.MILCHGLAS, false, montag))
        assertEquals("FR", tagKuerzel(FoodyStil.KREUZER, true, freitag))
        assertEquals("金", tagKuerzel(FoodyStil.EDO, true, freitag))
        assertEquals("Fr", tagKuerzel(FoodyStil.EDO, false, freitag))
        assertEquals("Shaksh.", gerichtKurz("Shakshuka mit Feta"))
        assertEquals("Curry", gerichtKurz("Curry"))
        assertEquals("—", gerichtKurz(null))
        assertEquals(
            "Woche: Montag Curry, Dienstag nichts geplant, Mittwoch nichts geplant, Donnerstag nichts geplant, " +
                "heute, Freitag Shakshuka mit Feta, Samstag nichts geplant, Sonntag nichts geplant",
            wocheBeschreibung(daten()),
        )
    }

    @Test
    fun ansichtUndLink() {
        val v = wocheAnsicht(kontext, daten(), StilWahl()).apply(kontext, FrameLayout(kontext))
        assertEquals("Mo", v.text(R.id.widget_tag1))
        assertEquals("Curry", v.text(R.id.widget_gericht1))
        assertEquals("—", v.text(R.id.widget_gericht2))
        v.findViewById<View>(android.R.id.background).performClick()
        assertEquals("foody://woche", assertNotNull(shadowOf(kontext).nextStartedActivity).dataString)
    }

    @Test
    fun jeStil() {
        val anime = wocheAnsicht(kontext, daten(), StilWahl("ANIME", "IMMERSIV")).apply(kontext, FrameLayout(kontext))
        assertEquals(false, anime.sichtbar(R.id.widget_tag1), "Kürzel steht in der Box")
        assertEquals(false, anime.sichtbar(R.id.widget_gericht1))
        val edo = wocheAnsicht(kontext, daten(), StilWahl("EDO", "IMMERSIV")).apply(kontext, FrameLayout(kontext))
        assertEquals("月", edo.text(R.id.widget_tag1))
        assertEquals(true, edo.sichtbar(R.id.widget_gericht1))
        // Marken werden gezeichnet, der Schein bekommt Platz
        val sage = markenBild(kontext, Marke(MarkenForm.RAUTE, 12f, 12f, 0xFFC8A45C.toInt(), null, 1f, schein = 0xFFC8A45C.toInt()))
        val dichte = kontext.resources.displayMetrics.density
        assertEquals((24 * dichte).toInt(), sage.width)
    }
}
