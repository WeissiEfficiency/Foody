package de.foody.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.R
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals

/** Der Beobachter fragt nur Daten ab, für die ein Widget auf dem Homescreen liegt (Review W2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WidgetBeobachterTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(kontext, FoodyDatenbank::class.java).addCallback(FoodyDatenbank.CALLBACK).build()
    private val beobachter = WidgetBeobachter(
        WidgetDatenQuelle(db, HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, TageszielSpeicher(kontext)),
        DarstellungSpeicher(kontext),
    )

    @After
    fun schliessen() = db.close()

    @Test
    fun ohneWidgetsBeobachtetErNichts() {
        beobachter.sicherstellen(kontext)
        assertEquals(emptySet(), beobachter.laufende())
    }

    @Test
    fun nurWasAufDemHomescreenLiegt() {
        shadowOf(AppWidgetManager.getInstance(kontext)).createWidget(KalorienWidget::class.java, R.layout.widget_kalorien)
        beobachter.sicherstellen(kontext)
        assertEquals(setOf("heute"), beobachter.laufende())
        // Ein weiteres Widget derselben Daten startet keinen zweiten Lauf, ein neues Widget seinen eigenen
        shadowOf(AppWidgetManager.getInstance(kontext)).createWidget(EinkaufWidget::class.java, R.layout.widget_einkauf)
        beobachter.sicherstellen(kontext)
        assertEquals(setOf("heute", "einkauf"), beobachter.laufende())
    }
}
