package de.foody.baselineprofile

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kurztest der R8-Variante (E4): „Foody Vorschau“ ist gebaut wie der Release (R8, gleiche Keep-Regeln), nur mit dem
 * Debug-Schlüssel signiert. Start, dann „Eintragen“ → „Frei“: Dort lädt der Scan-Knopf ML Kit vor, und genau da stürzte
 * der Release ab, weil R8 Konstruktoren entfernt hatte, die ML Kit per Reflexion braucht (Z1) – der Release-Build
 * selbst war grün. In der CI auf dem Gradle Managed Device:
 *   ./gradlew :baselineprofile:ciDeviceVorschauAndroidTest
 *     -Pandroid.testInstrumentationRunnerArguments.class=de.foody.baselineprofile.ReleaseStartTest
 */
@RunWith(AndroidJUnit4::class)
class ReleaseStartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun startUndFreiOeffnen() {
        val kontext = instrumentation.context
        val start = checkNotNull(kontext.packageManager.getLaunchIntentForPackage(PAKET)) { "$PAKET ist nicht installiert" }
        kontext.startActivity(start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

        warte(By.pkg(PAKET).text("Heute"), "Start")
        klick(By.pkg(PAKET).text("Eintragen"))
        klick(By.pkg(PAKET).text("Frei"))
        // Der Absturz kam kurz nach dem Erscheinen des Reiters; stirbt die App, ist ihr Fenster weg
        SystemClock.sleep(NACHLAUF_MS)
        device.waitForIdle()
        assertTrue("App nach „Frei“ nicht mehr zu sehen (abgestürzt?)", device.hasObject(By.pkg(PAKET).text("Frei")))
    }

    private fun warte(selector: BySelector, was: String) {
        assertTrue("$was: nicht erschienen", device.wait(Until.hasObject(selector), TIMEOUT_MS))
    }

    private fun klick(selector: BySelector) {
        val ziel = checkNotNull(device.wait(Until.findObject(selector), TIMEOUT_MS)) { "$selector nicht gefunden" }
        ziel.click()
        device.waitForIdle()
    }

    private companion object {
        const val PAKET = "de.foody.app.vorschau"
        const val TIMEOUT_MS = 15_000L
        const val NACHLAUF_MS = 3_000L
    }
}
