package de.foody.app.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import de.foody.core.data.StilWahl
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.heute.ArtikelKurz
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.data.heute.OffenerEinkauf
import de.foody.feature.heute.NaechsteMahlzeit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals

/**
 * Referenzbilder der Widgets mit Beispieldaten im Stil Milchglas, in voller Auflösung und mit gerundeten Ecken. Sie sind
 * zugleich die Vorschaubilder in der Widget-Auswahl (`res/drawable-nodpi/widget_vorschau_*.png`, `android:previewImage`):
 * Nach einer gewollten Änderung erst `recordRoborazziDebug`, dann die Bilder dorthin kopieren; [vorschauGleichReferenz]
 * merkt, wenn das vergessen wurde.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class, qualifiers = "xhdpi")
class WidgetBildTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()
    private val milchglas = StilWahl()
    private val tag = LocalDate.of(2026, 11, 13)

    // Volle Auflösung: Die Bilder sind auch die Vorschau in der Auswahl; ein Pixel mehr oder weniger zählt nicht.
    private val optionen = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))

    private fun mahlzeit() = NaechsteMahlzeit(GeplanteMahlzeit("p1", tag, "ABENDESSEN", "r1", "Ofengemüse mit Feta", 2, 540, 45, null, false), false)

    private fun heute() = HeuteDaten(tag, mahlzeit(), 860, 2000)

    private fun woche(): WocheDaten {
        val montag = tag.minusDays(4)
        fun eintrag(id: String, t: Long, rezept: String) = PlanEintragEntity(id, montag.plusDays(t), "ABENDESSEN", rezept, 2, createdAt = 1, updatedAt = 1)
        val namen = mapOf("r1" to "Curry", "r2" to "Pfanne", "r3" to "Ofengemüse mit Feta", "r4" to "Linsensuppe")
        return WocheDaten(tag, wochenTage(montag, listOf(eintrag("a", 0, "r1"), eintrag("b", 1, "r2"), eintrag("c", 2, "r4"), eintrag("d", 4, "r3")), namen))
    }

    private fun einkauf() =
        OffenerEinkauf("l1", 6, listOf("Paprika", "Feta", "Kürbis", "Kräuterquark", "Zucchini", "Reis").map { ArtikelKurz(it, null) })

    /** [ansicht] in [breiteDp] × [hoeheDp], mit den Ecken gerundet wie im Launcher (Robolectric rundet nicht selbst). */
    private fun bild(ansicht: RemoteViews, breiteDp: Int, hoeheDp: Int): Bitmap {
        val dichte = kontext.resources.displayMetrics.density
        val b = (breiteDp * dichte).roundToInt()
        val h = (hoeheDp * dichte).roundToInt()
        val rahmen = FrameLayout(kontext)
        rahmen.addView(ansicht.apply(kontext, rahmen), FrameLayout.LayoutParams(b, h))
        rahmen.measure(View.MeasureSpec.makeMeasureSpec(b, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        rahmen.layout(0, 0, b, h)
        val radius = 24 * dichte
        return createBitmap(b, h).also {
            val leinwand = Canvas(it)
            leinwand.clipPath(Path().apply { addRoundRect(0f, 0f, b.toFloat(), h.toFloat(), radius, radius, Path.Direction.CW) })
            rahmen.draw(leinwand)
        }
    }

    private fun bilder() = mapOf(
        "einkauf" to bild(einkaufAnsicht(kontext, einkauf(), milchglas), 160, 160),
        "eintragen" to bild(eintragenAnsicht(kontext, milchglas), 160, 160),
        "heute" to bild(heuteAnsicht(kontext, heute(), milchglas), 320, 160),
        "kalorien" to bild(kalorienAnsicht(kontext, heute(), milchglas), 160, 160),
        "woche" to bild(wocheAnsicht(kontext, woche(), milchglas), 320, 90),
    )

    @Test
    fun widgetsInMilchglas() {
        bilder().forEach { (name, b) -> b.captureRoboImage("src/test/screenshots/widget_$name.png", optionen) }
    }

    @Test
    fun vorschauGleichReferenz() {
        listOf("einkauf", "eintragen", "heute", "kalorien", "woche").forEach { name ->
            val referenz = File("src/test/screenshots/widget_$name.png")
            val vorschau = File("src/main/res/drawable-nodpi/widget_vorschau_$name.png")
            assertContentEquals(referenz.readBytes(), vorschau.readBytes(), "Vorschau $name veraltet: Referenzbild nach res/drawable-nodpi kopieren")
        }
    }
}
