package de.foody.app

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.foody.core.sync.SyncAusloeser
import de.foody.core.sync.SyncKonfiguration
import de.foody.core.sync.SyncKonto
import de.foody.feature.einstellungen.KontoZugriff
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
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

    @Inject lateinit var kontoZugriff: KontoZugriff

    @Test
    fun graphLiefertSyncKontoUndAusloeser() {
        hilt.inject()
        assertTrue(::konto.isInitialized && ::ausloeser.isInitialized)
        assertTrue(konfiguration.lokalesHttpErlaubt, "Debug-Build erlaubt http zum Emulator-Host")
    }

    @Test
    fun kontoZugriffLiestSyncZustand() {
        hilt.inject()
        val z = runBlocking { kontoZugriff.zustand.first() }
        assertTrue(!z.verbunden && z.adresse == null, "frische Installation ist nicht verbunden")
    }
}
