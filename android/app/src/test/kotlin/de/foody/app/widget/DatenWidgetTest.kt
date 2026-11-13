package de.foody.app.widget

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.MainActivity
import de.foody.app.R
import de.foody.app.navigation.EINTRAGEN_LINK
import de.foody.core.data.StilWahl
import de.foody.core.data.heute.ArtikelKurz
import de.foody.core.data.heute.OffenerEinkauf
import de.foody.core.designsystem.stil.FoodyStil
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Widgets „Einkauf“ und „Schnell eintragen“: Inhalt, Wörter je Stil, Beschreibung für TalkBack und der Tipp mit dem
 * passenden Link.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class DatenWidgetTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()

    private fun einkauf(vararg namen: String) = OffenerEinkauf("l1", namen.size, namen.map { ArtikelKurz(it, null) })

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun View.sichtbar(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    private fun wahl(stil: FoodyStil, variante: String = "IMMERSIV") = StilWahl(stil.name, variante)

    private fun tippOeffnet(ansicht: View): String? {
        ansicht.findViewById<View>(android.R.id.background).performClick()
        val gestartet = assertNotNull(shadowOf(kontext).nextStartedActivity, "nichts gestartet")
        assertEquals(ComponentName(kontext, MainActivity::class.java), gestartet.component)
        assertEquals(Intent.ACTION_VIEW, gestartet.action)
        return gestartet.dataString
    }

    @Test
    fun einkaufZeigtVierUndDenRest() {
        val e = einkauf("Paprika", "Feta", "Kürbis", "Kräuterquark", "Zucchini", "Reis")
        val v = einkaufAnsicht(kontext, e, StilWahl()).apply(kontext, FrameLayout(kontext))
        assertEquals("Einkauf", v.text(R.id.widget_titel))
        assertEquals("6 offen", v.text(R.id.widget_zahl))
        assertEquals("Paprika", v.text(R.id.widget_zeile1))
        assertEquals("Kräuterquark", v.text(R.id.widget_zeile4))
        assertEquals("+ 2 weitere", v.text(R.id.widget_fuss))
        assertEquals(false, v.sichtbar(R.id.widget_stempel))
        assertEquals(
            "Einkauf: 6 offen: Paprika, Feta, Kürbis, Kräuterquark und 2 weitere",
            v.findViewById<View>(android.R.id.background).contentDescription,
        )
        assertEquals("foody://einkauf/l1", tippOeffnet(v))
    }

    @Test
    fun einkaufLeer() {
        val v = einkaufAnsicht(kontext, null, StilWahl()).apply(kontext, FrameLayout(kontext))
        assertEquals("Alles erledigt", v.text(R.id.widget_fuss))
        assertEquals(false, v.sichtbar(R.id.widget_zeile1))
        assertEquals("Einkauf: alles erledigt", v.findViewById<View>(android.R.id.background).contentDescription)
        // Ohne Liste öffnet der Tipp einfach die App
        assertNull(tippOeffnet(v))
    }

    @Test
    fun einkaufImStil() {
        val e = einkauf("Paprika", "Feta")
        val edo = einkaufAnsicht(kontext, e, wahl(FoodyStil.EDO)).apply(kontext, FrameLayout(kontext))
        assertEquals(true, edo.sichtbar(R.id.widget_stempel))
        assertEquals("2 Dinge offen", edo.text(R.id.widget_fuss))
        val anime = einkaufAnsicht(kontext, e, wahl(FoodyStil.ANIME)).apply(kontext, FrameLayout(kontext))
        assertEquals("QUEST!", anime.text(R.id.widget_titel))
        assertEquals("○ Paprika", anime.text(R.id.widget_zeile1))
        assertEquals(true, anime.sichtbar(R.id.widget_schatten))
        // Ruhig: Farben des Stils, aber die normalen Wörter
        val ruhig = einkaufAnsicht(kontext, e, wahl(FoodyStil.BAYRISCH, "RUHIG")).apply(kontext, FrameLayout(kontext))
        assertEquals("Einkauf", ruhig.text(R.id.widget_titel))
    }

    @Test
    fun einkaufWoerter() {
        assertEquals("no 9 offn", einkaufTexte(FoodyStil.BAYRISCH, true, 9).fuss)
        assertEquals("noch 9 offen", einkaufTexte(FoodyStil.GRAUEN, true, 9).fuss)
        assertEquals("9 OFFEN", einkaufTexte(FoodyStil.KREUZER, true, 9).fuss)
        assertEquals("DER HÄNDLER", einkaufTexte(FoodyStil.SAGE, true, 9).titel)
        assertEquals("+ 5 weitere", einkaufTexte(FoodyStil.SAGE, true, 9).fuss)
        assertEquals("Alles erledigt", einkaufTexte(FoodyStil.KREUZER, true, 0).fuss)
    }

    @Test
    fun eintragenOeffnetDasBlatt() {
        val v = eintragenAnsicht(kontext, StilWahl()).apply(kontext, FrameLayout(kontext))
        assertEquals("Eintragen", v.text(R.id.widget_titel))
        assertEquals("Suchen · Scannen · Foto", v.text(R.id.widget_fuss))
        assertEquals("Essen eintragen: suchen, scannen oder fotografieren", v.findViewById<View>(android.R.id.background).contentDescription)
        assertEquals(EINTRAGEN_LINK, tippOeffnet(v))
    }

    @Test
    fun eintragenImStil() {
        assertEquals("Proviant erfassen", eintragenAussehen(kontext, wahl(FoodyStil.KREUZER)).titel)
        assertEquals(true, eintragenAussehen(kontext, wahl(FoodyStil.KREUZER)).eckig)
        assertEquals("Eitragn", eintragenAussehen(kontext, wahl(FoodyStil.BAYRISCH)).titel)
        assertEquals("Notieren", eintragenAussehen(kontext, wahl(FoodyStil.GRAUEN)).titel)
        assertEquals("Eintragen", eintragenAussehen(kontext, wahl(FoodyStil.GRAUEN, "RUHIG")).titel)
        val v = eintragenAnsicht(kontext, wahl(FoodyStil.KREUZER)).apply(kontext, FrameLayout(kontext))
        assertEquals("SUCHEN · SCANNEN · FOTO", v.text(R.id.widget_fuss))
    }
}
