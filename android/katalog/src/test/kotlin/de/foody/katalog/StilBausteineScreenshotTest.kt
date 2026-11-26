package de.foody.katalog

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.komponenten.BlattTitel
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.FoodyNavigationsleiste
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.NavZiel
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.testing.FoodyBildvergleich
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Die gemeinsamen Bausteine in den Stilen R–V (Immersiv): Seitenkopf mit und ohne Zurück, Blatt-Titel und die vier
 * Knöpfe (Primär, Umriss, Text, inaktiv), Chips gewählt und offen. Ein Bild je Stil, damit Schrift, Form und Farben sichtbar geprüft werden.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class StilBausteineScreenshotTest {
    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    @Config(qualifiers = "w411dp-h560dp")
    fun bausteine() {
        for (stil in listOf(FoodyStil.ANIME, FoodyStil.EDO, FoodyStil.BAYRISCH, FoodyStil.GRAUEN, FoodyStil.KREUZER)) {
            captureRoboImage("src/test/screenshots/stil_bausteine_${stil.name.lowercase()}.png", roborazziOptions = FoodyBildvergleich) {
                FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV, unschaerfe = false) {
                    FoodyHintergrund(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Seitenkopf("Einkauf", ueberzeile = "Seitenkopf")
                            Seitenkopf("Zutatenkatalog", onZurueck = {})
                            BlattTitel("Frühere Listen")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FoodyKnopf("Speichern", {}, Modifier.weight(1f))
                                FoodyKnopf("Abbrechen", {}, Modifier.weight(1f), art = KnopfArt.UMRISS)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FoodyKnopf("+ Eintragen", {}, Modifier.weight(1f), art = KnopfArt.TEXT)
                                FoodyKnopf("Übernehmen (0)", {}, Modifier.weight(1f), aktiv = false)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FoodyChip("Vegetarisch", ausgewaehlt = true, onKlick = {})
                                FoodyChip("Schnell", ausgewaehlt = false, onKlick = {})
                            }
                        }
                    }
                }
            }
        }
    }

    /** Beide Gesichter des Grauens: Siegel, Wahlwörter und die Leiste (Ominös in Glas, Necronomicon in Leder). */
    @Test
    @Config(qualifiers = "w411dp-h560dp")
    fun grauenGesichterMitLeiste() {
        for ((variante, name) in listOf(StilVariante.RUHIG to "ominoes", StilVariante.IMMERSIV to "necronomicon")) {
            captureRoboImage("src/test/screenshots/stil_grauen_leiste_$name.png", roborazziOptions = FoodyBildvergleich) {
                FoodyTheme(stil = FoodyStil.GRAUEN, variante = variante, unschaerfe = false) {
                    FoodyHintergrund(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Seitenkopf("Expeditionsliste", ueberzeile = "Vor dem Aufbruch")
                            FoodyKnopf("Das Ritual beginnen", {})
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FoodyChip("Mittagessen", ausgewaehlt = true, onKlick = {})
                                FoodyChip("Abendessen", ausgewaehlt = false, onKlick = {})
                            }
                            Spacer(Modifier.weight(1f))
                            FoodyNavigationsleiste(
                                listOf(
                                    NavZiel("Heute", Icons.Outlined.LightMode),
                                    NavZiel("Rezepte", Icons.AutoMirrored.Outlined.MenuBook),
                                    NavZiel("Woche", Icons.Outlined.CalendarMonth),
                                    NavZiel("Einkauf", Icons.Outlined.ShoppingBag),
                                ),
                                aktiv = 1, onWahl = {},
                            )
                        }
                    }
                }
            }
        }
    }
}
