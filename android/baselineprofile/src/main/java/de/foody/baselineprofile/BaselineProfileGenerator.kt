package de.foody.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Typischer Weg durch die App: Start, Rezeptliste scrollen, alle Tabs und die wichtigsten Dialoge einmal öffnen –
 * Planer („Gericht planen“ mit Mahlzeit-/Gang-Filter), Tagebuch (Eintrag mit den Reitern Rezept, Zutat, Frei),
 * Einkauf und unter „Mehr“ Vorrat und Zutatenkatalog.
 * Auf einer frischen Installation sind keine Rezepte da – das Profil deckt deshalb Start, Leerzustände
 * und die Grundgerüste der Bildschirme ab; das ist der Teil, den jeder Start braucht.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "de.foody.app", includeInStartupProfile = true, maxIterations = 5) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Was kochen wir?")), TIMEOUT_MS)

        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            list.fling(Direction.UP)
        }

        tab("Planer")
        openAndClose(By.text("Gericht planen"), By.text("Abbrechen"))

        tab("Einkauf")

        tab("Tagebuch")
        if (click(By.desc("Zu Mittagessen hinzufügen"))) {
            device.wait(Until.hasObject(By.text("Zutat")), TIMEOUT_MS)
            click(By.text("Zutat"))
            click(By.text("Frei"))
            click(By.text("Abbrechen"))
        }

        // „Mehr“: Vorrat und Zutatenkatalog liegen dort als Einträge (die Abschnittstitel heißen genauso).
        tab("Mehr")
        openAndClose(By.text("Vorrat öffnen"), null)
        openAndClose(By.textContains("Nährwerte verwalten"), null)

        tab("Rezepte")
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
