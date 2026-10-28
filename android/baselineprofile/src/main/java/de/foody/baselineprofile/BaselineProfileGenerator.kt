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

/**
 * Typischer Weg durch die App: Start auf „Heute“, dann Rezepte (Liste scrollen), Woche, Einkauf und die
 * Einstellungen einmal öffnen. Auf einer frischen Installation sind nur Startzutaten da – das Profil deckt deshalb
 * Start, Leerzustände und die Grundgerüste der Bildschirme ab; das ist der Teil, den jeder Start braucht.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "de.foody.app", includeInStartupProfile = true, maxIterations = 5) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Heute")), TIMEOUT_MS)

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
        openAndClose(By.desc("Einstellungen"), null)
    }

    private fun MacrobenchmarkScope.tab(label: String) {
        click(By.text(label))
    }

    /** Öffnet [open] und schließt wieder über [close] oder – ohne – mit „Zurück“. */
    private fun MacrobenchmarkScope.openAndClose(open: BySelector, close: BySelector?) {
        if (!click(open)) return
        if (close == null || !click(close)) {
            device.pressBack()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.click(selector: BySelector): Boolean {
        val target = device.wait(Until.findObject(selector), TIMEOUT_MS) ?: return false
        target.click()
        device.waitForIdle()
        return true
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
