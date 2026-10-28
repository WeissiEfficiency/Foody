package de.foody.app

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.foody.feature.kochen.timer.KochTimerDienst
import de.foody.feature.kochen.timer.KochTimerSpeicher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import javax.inject.Inject
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Der Kochtimer-Dienst mit dem echten Hilt-Graphen. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class KochTimerDienstTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var speicher: KochTimerSpeicher

    @Test
    fun abgelaufenBeimStartErstVordergrundDannStopp() {
        // Ein Timer lief ab, während die App beendet war: der Dienst meldet ihn und beendet sich –
        // aber erst nach startForeground, sonst beendet Android die App
        hilt.inject()
        speicher.starten("r1", 2, "Reistopf", "1 Minute", Duration.ofMillis(1))
        Thread.sleep(5)
        assertTrue(speicher.brauchtDienst())
        val steuerung = Robolectric.buildService(KochTimerDienst::class.java).create()
        val dienst = steuerung.get()
        assertNotNull(shadowOf(dienst).lastForegroundNotification, "startForeground vor dem Beenden")
        assertFalse(shadowOf(dienst).isStoppedBySelf, "erst nach onStartCommand melden und beenden")
        steuerung.startCommand(0, 1)
        assertTrue(speicher.timer.value.single().gemeldet)
        assertTrue(shadowOf(dienst).isStoppedBySelf)
    }
}
