package de.foody.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Typischer Weg durch die App: Start, Rezeptliste scrollen, alle Tabs einmal öffnen.
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

        for (tab in listOf("Planer", "Einkauf", "Vorrat", "Mehr", "Rezepte")) {
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
