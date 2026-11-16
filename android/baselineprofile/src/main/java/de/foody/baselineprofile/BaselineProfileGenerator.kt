package de.foody.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Typischer Weg durch die App: Start auf „Heute“, dann Rezepte (Liste scrollen), Woche, Einkauf und die
 * Einstellungen einmal öffnen. Auf einer frischen Installation sind nur Startzutaten da – das Profil deckt deshalb
 * Start, Leerzustände und die Grundgerüste der Bildschirme ab; das ist der Teil, den jeder Start braucht.
 *
 * [stile] geht denselben Weg in jedem Stil (Immersiv) einmal, damit auch die Szenen vorab kompiliert sind. Es zählt
 * nicht zum Startprofil: Der Start selbst ist in jedem Stil derselbe.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "de.foody.app", includeInStartupProfile = true, maxIterations = 5) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Heute")), TIMEOUT_MS)

        rundgang()
    }

    @Test
    fun stile() = rule.collect(packageName = "de.foody.app", includeInStartupProfile = false, maxIterations = 3) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Heute")), TIMEOUT_MS)
        STILE.forEach { stil ->
            stilWaehlen(stil)
            rundgang()
        }
        // Zurück zum Standard, damit jeder Durchgang (und `generate`) gleich beginnt
        stilWaehlen("Milchglas")
    }

    /** Rezepte (Liste scrollen), Woche, Einkauf, zurück auf „Heute“ und die Einstellungen einmal öffnen. */
    private fun MacrobenchmarkScope.rundgang() {
        tab("Rezepte")
        // Die Liste baut sich nach dem Tab-Wechsel neu auf; ein zu früh gefundenes Objekt ist dann veraltet
        device.waitForIdle()
        try {
            device.wait(Until.findObject(By.scrollable(true)), TIMEOUT_MS)?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(Direction.DOWN)
                list.fling(Direction.UP)
            }
        } catch (_: StaleObjectException) {
            // Liste hat sich währenddessen neu gezeichnet: Scrollen ist für das Profil nur ein Zusatz
        }

        tab("Woche")
        tab("Einkauf")

        tab("Heute")
        if (scrollendKlicken(By.desc("Einstellungen"))) zurueck()
    }

    /**
     * Wählt in den Einstellungen den Stil mit dem Namen [name] und kehrt auf „Heute“ zurück. Manche Szenen (Edo) haben
     * den Knopf „Einstellungen“ unten auf „Heute“, daher wird auch dort gescrollt.
     */
    private fun MacrobenchmarkScope.stilWaehlen(name: String) {
        tab("Heute")
        // Ein Tipp direkt nach dem Wischen kommt manchmal nicht an; offen ist die Seite erst, wenn „Zurück“ da ist
        repeat(VERSUCHE_EINSTELLUNGEN) {
            if (!scrollendKlicken(By.desc("Einstellungen"))) fehler("Einstellungen nicht gefunden (vor $name)")
            if (device.wait(Until.hasObject(By.desc("Zurück")), TIMEOUT_MS) == true) {
                if (!scrollendKlicken(By.text(name))) fehler("Stil $name nicht gefunden")
                zurueck()
                return
            }
        }
        fehler("Einstellungen öffnen sich nicht (vor $name)")
    }

    /** Legt Bildschirmfoto und UI-Baum neben die Profile (`additional_test_output`) und bricht ab. */
    private fun MacrobenchmarkScope.fehler(text: String): Nothing {
        val ordner = File(AUSGABE)
        device.takeScreenshot(File(ordner, "fehler.png"))
        device.dumpWindowHierarchy(File(ordner, "fehler.xml"))
        error(text)
    }

    /**
     * Einmal „Zurück“. Kam der Klick davor nicht an (die Seite war noch nicht offen), verlässt „Zurück“ die App; dann
     * wird sie neu gestartet, damit der Rundgang weitergeht.
     */
    private fun MacrobenchmarkScope.zurueck() {
        device.pressBack()
        device.waitForIdle()
        if (device.currentPackageName != packageName) {
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    /**
     * Sucht [selector], erst nach oben, dann nach unten scrollend: „Heute“ behält seine Position, der Knopf
     * „Einstellungen“ liegt je nach Stil oben (Sage) oder unten (Edo). Die Seiten bauen sich beim Laden noch um; ein
     * gefundenes Objekt kann veraltet sein, daher je Schritt neu suchen.
     */
    private fun MacrobenchmarkScope.scrollendKlicken(selector: BySelector): Boolean {
        device.waitForIdle()
        val richtungen = List(SCROLL_VERSUCHE / 2) { false } + List(SCROLL_VERSUCHE) { true }
        for (nachUnten in richtungen) {
            try {
                device.findObject(selector)?.let {
                    it.click()
                    device.waitForIdle()
                    // Navigation und Themewechsel brauchen einen Moment über das Leerlaufen hinaus
                    Thread.sleep(NACH_KLICK_MS)
                    return true
                }
            } catch (_: StaleObjectException) {
                // Im nächsten Schritt neu suchen
            }
            // In der Bildmitte wischen: Unten liegt die schwebende Navigation, dort scrollt nichts
            val x = device.displayWidth / 2
            val oben = device.displayHeight * 3 / 10
            val unten = device.displayHeight * 6 / 10
            if (nachUnten) device.swipe(x, unten, x, oben, WISCH_SCHRITTE) else device.swipe(x, oben, x, unten, WISCH_SCHRITTE)
            device.waitForIdle()
        }
        return false
    }

    /**
     * In Milchglas trägt das Tab den Namen als Text, in manchen Stilen ein Stilwort und den Namen als Beschreibung.
     * Den Namen als Beschreibung trägt sonst nur der Seitenkopf des Tabs selbst; ein Tipp darauf ändert nichts.
     */
    private fun MacrobenchmarkScope.tab(label: String) {
        val ende = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < ende) {
            try {
                val ziel = device.findObject(By.desc(label)) ?: device.findObject(By.text(label))
                if (ziel != null) {
                    ziel.click()
                    device.waitForIdle()
                    return
                }
            } catch (_: StaleObjectException) {
                // Im nächsten Schritt neu suchen
            }
            Thread.sleep(200)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SCROLL_VERSUCHE = 15
        const val WISCH_SCHRITTE = 20
        const val NACH_KLICK_MS = 700L
        const val VERSUCHE_EINSTELLUNGEN = 3
        const val AUSGABE = "/sdcard/Android/media/de.foody.baselineprofile/additional_test_output"

        /** Anzeigenamen der Stile wie in Einstellungen › Darstellung (`FoodyStil.anzeigename`). */
        val STILE = listOf("Düstere Sage", "Anime", "Edo", "Bayrisch", "Kosmisches Grauen", "Sternenkreuzer")
    }
}
