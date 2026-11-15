package de.foody.app.widget

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.R
import de.foody.app.navigation.EINTRAGEN_LINK
import de.foody.core.data.StilWahl
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.domain.TagebuchArt
import de.foody.feature.heute.NaechsteMahlzeit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Widgets „Heute“ und „Kalorien“: nächste Mahlzeit und kcal, Aufbau je Stil (Knöpfe, Ring, Tank), Wörter, Beschreibung
 * für TalkBack und die Links von Fläche, „Kochen“ und „Eintragen“.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class HeuteWidgetTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()
    private val tag = LocalDate.of(2026, 11, 13)

    private fun mahlzeit(slot: String = "ABENDESSEN", morgen: Boolean = false) = NaechsteMahlzeit(
        GeplanteMahlzeit("p1", tag, slot, "r1", "Ofengemüse mit Feta", 2, 540, 45, null, false),
        morgen,
    )

    private fun daten(n: NaechsteMahlzeit? = mahlzeit(), ziel: Int? = 2000) = HeuteDaten(tag, n, 860, ziel)

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun View.sichtbar(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    private fun wahl(stil: FoodyStil) = StilWahl(stil.name, "IMMERSIV")

    private fun tipp(ansicht: View, id: Int): String? {
        ansicht.findViewById<View>(id).performClick()
        return assertNotNull(shadowOf(kontext).nextStartedActivity, "nichts gestartet").dataString
    }

    @Test
    fun texte() {
        assertEquals("860 / 2.000 kcal", kcalText(daten()))
        assertEquals("860 kcal", kcalText(daten(ziel = null)))
        assertEquals("45 Min. · 540 kcal", gerichtInfo(mahlzeit()))
        assertEquals("HEUTE · ABENDESSEN", heuteMarke(FoodyStil.MILCHGLAS, false, mahlzeit()))
        assertEquals("ABENDMAHL", heuteMarke(FoodyStil.SAGE, true, mahlzeit()))
        assertEquals("MORGEN · ABENDESSEN", heuteMarke(FoodyStil.SAGE, true, mahlzeit(morgen = true)))
        assertEquals("Heid auf d’Nacht", heuteMarke(FoodyStil.BAYRISCH, true, mahlzeit()))
        assertEquals("Das Ritual des Abends", heuteMarke(FoodyStil.GRAUEN, true, mahlzeit()))
        assertEquals("NÄCHSTE MISSION", heuteMarke(FoodyStil.KREUZER, true, mahlzeit("MITTAGESSEN")))
        assertEquals("1.140" to "übrig", kalorienRest(daten()))
        assertEquals("140" to "drüber", kalorienRest(HeuteDaten(tag, null, 2140, 2000)))
        assertEquals("860" to "kcal heute", kalorienRest(daten(ziel = null)))
    }

    @Test
    fun kcalWieImTagebuch() {
        fun eintrag(kj: String?) = TagebuchEintragEntity(
            id = kj ?: "leer", datum = tag, mahlzeit = "SNACK", art = TagebuchArt.FREI, name = "x", energieKj = kj?.let(::BigDecimal),
            createdAt = 1, updatedAt = 1,
        )
        assertEquals(1000, kcalSumme(listOf(eintrag("2092"), eintrag("2092"), eintrag(null))))
    }

    @Test
    fun heuteMitKnoepfen() {
        val v = heuteAnsicht(kontext, daten(), StilWahl()).apply(kontext, FrameLayout(kontext))
        assertEquals("Ofengemüse mit Feta", v.text(R.id.widget_titel))
        assertEquals("860 / 2.000 kcal · 45 Min.", v.text(R.id.widget_info))
        assertEquals(false, v.sichtbar(R.id.widget_ring_rahmen))
        assertEquals(true, v.sichtbar(R.id.widget_balken))
        assertEquals(
            "Heute: 860 von 2.000 kcal. Heute, Abendessen: Ofengemüse mit Feta, 45 Minuten.",
            v.findViewById<View>(android.R.id.background).contentDescription,
        )
        assertEquals("foody://kochen/r1?portionen=2", tipp(v, R.id.widget_kochen))
        assertEquals(EINTRAGEN_LINK, tipp(v, R.id.widget_eintragen))
        assertEquals(null, tipp(v, android.R.id.background))
    }

    @Test
    fun ohnePlanNurEintragen() {
        val v = heuteAnsicht(kontext, daten(n = null), wahl(FoodyStil.ANIME)).apply(kontext, FrameLayout(kontext))
        assertEquals("Nichts geplant", v.text(R.id.widget_titel))
        assertEquals(false, v.sichtbar(R.id.widget_kochen_rand))
        assertEquals("+ Eintragen", v.text(R.id.widget_eintragen))
        assertEquals("Heute: 860 von 2.000 kcal. Nichts geplant.", v.findViewById<View>(android.R.id.background).contentDescription)
    }

    @Test
    fun ringUndTankJeStil() {
        val edo = heuteAnsicht(kontext, daten(), wahl(FoodyStil.EDO)).apply(kontext, FrameLayout(kontext))
        assertEquals(true, edo.sichtbar(R.id.widget_ring_rahmen))
        assertEquals("860", edo.text(R.id.widget_ring_zahl))
        assertEquals("/ 2.000", edo.text(R.id.widget_ring_unter))
        assertEquals("45 Min. · 540 kcal", edo.text(R.id.widget_info))
        assertEquals(false, edo.sichtbar(R.id.widget_knoepfe))
        val bayrisch = heuteAnsicht(kontext, daten(), wahl(FoodyStil.BAYRISCH)).apply(kontext, FrameLayout(kontext))
        assertEquals(true, bayrisch.sichtbar(R.id.widget_streifen))
        val kreuzer = heuteAnsicht(kontext, daten(), wahl(FoodyStil.KREUZER)).apply(kontext, FrameLayout(kontext))
        assertEquals(true, kreuzer.sichtbar(R.id.widget_tank))
        assertEquals(false, kreuzer.sichtbar(R.id.widget_ring_rahmen))
        assertEquals("NÄCHSTE MISSION", kreuzer.text(R.id.widget_marke))
        assertEquals("45 MIN. · 540 KCAL", kreuzer.text(R.id.widget_info))
    }

    @Test
    fun kalorien() {
        val v = kalorienAnsicht(kontext, daten(), wahl(FoodyStil.ANIME)).apply(kontext, FrameLayout(kontext))
        assertEquals("1.140", v.text(R.id.widget_ring_zahl))
        assertEquals("übrig", v.text(R.id.widget_ring_unter))
        assertEquals("Kalorien: 860 von 2.000 kcal, 1.140 übrig", v.findViewById<View>(android.R.id.background).contentDescription)
        assertEquals("foody://tagebuch/2026-11-13", tipp(v, android.R.id.background))
    }

    /** [ansicht] in einer Zelle von [breiteDp] × [hoeheDp] bei Systemschrift [schrift]; die Texte, die abgeschnitten („…“) sind. */
    private fun abgeschnitten(ansicht: RemoteViews, breiteDp: Int, hoeheDp: Int, schrift: Float = 2f): List<String> {
        RuntimeEnvironment.setFontScale(schrift)
        try {
            val d = kontext.resources.displayMetrics.density
            val rahmen = FrameLayout(kontext)
            val v = ansicht.apply(kontext, rahmen)
            rahmen.addView(v, FrameLayout.LayoutParams((breiteDp * d).toInt(), (hoeheDp * d).toInt()))
            rahmen.measure(View.MeasureSpec.makeMeasureSpec((breiteDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hoeheDp * d).toInt(), View.MeasureSpec.EXACTLY))
            rahmen.layout(0, 0, rahmen.measuredWidth, rahmen.measuredHeight)
            return listOf(R.id.widget_ring_zahl, R.id.widget_ring_unter, R.id.widget_kochen, R.id.widget_eintragen).mapNotNull { id ->
                val t = v.findViewById<TextView>(id)?.takeIf { it.visibility == View.VISIBLE && it.text.isNotEmpty() } ?: return@mapNotNull null
                val l = t.layout ?: return@mapNotNull null
                val zuHoch = t.layout.height > t.height - t.totalPaddingTop - t.totalPaddingBottom + 1
                if ((0 until l.lineCount).any { l.getEllipsisCount(it) > 0 } || zuHoch) "${kontext.resources.getResourceEntryName(id)}: ${t.text}" else null
            }
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test
    fun grosseSchriftSchneidetKeineZahlAb() {
        // 200 % Systemschrift in einer engen 2×2-Zelle (Testgerät etwa 123 dp) und im 4×2-Widget (Review W6)
        assertEquals(emptyList(), abgeschnitten(kalorienAnsicht(kontext, daten(), wahl(FoodyStil.ANIME)), 123, 123))
        assertEquals(emptyList(), abgeschnitten(heuteAnsicht(kontext, daten(), wahl(FoodyStil.EDO)), 260, 123))
        assertEquals(emptyList(), abgeschnitten(heuteAnsicht(kontext, daten(), StilWahl()), 260, 123))
    }
}
