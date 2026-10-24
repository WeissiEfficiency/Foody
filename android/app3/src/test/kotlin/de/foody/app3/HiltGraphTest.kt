package de.foody.app3

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.foody.core.sync.SyncAusloeser
import de.foody.core.sync.SyncKonfiguration
import de.foody.core.sync.SyncKonto
import javax.inject.Inject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** Der ganze Hilt-Graph der App baut – inklusive SyncKonfiguration, die nur die App liefert. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HiltGraphTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var konto: SyncKonto
    @Inject lateinit var ausloeser: SyncAusloeser
    @Inject lateinit var konfiguration: SyncKonfiguration

    @Test
    fun graphLiefertSyncKontoUndAusloeser() {
        hilt.inject()
        assertTrue(::konto.isInitialized && ::ausloeser.isInitialized)
        assertTrue(konfiguration.lokalesHttpErlaubt, "Debug-Build erlaubt http zum Emulator-Host")
    }
}
