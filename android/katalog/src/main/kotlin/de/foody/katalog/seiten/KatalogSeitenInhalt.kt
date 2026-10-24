package de.foody.katalog.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Abschnittskopf
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.FoodyNavigationsRail
import de.foody.core.designsystem.komponenten.FoodyNavigationsleiste
import de.foody.core.designsystem.komponenten.KartenAktion
import de.foody.core.designsystem.komponenten.Kachel
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.LeererZustandZeile
import de.foody.core.designsystem.komponenten.NavZiel
import de.foody.core.designsystem.komponenten.Naehrwertbalken
import de.foody.core.designsystem.komponenten.RezeptkarteGross
import de.foody.core.designsystem.komponenten.RezeptkarteRaster
import de.foody.core.designsystem.komponenten.SyncAnzeige
import de.foody.core.designsystem.komponenten.SyncStatus
import de.foody.core.designsystem.token.GlasStufe
import de.foody.katalog.KatalogSeite

/** Alle Zustände der Komponenten einer Seite, mit erfundenen Beispielen. */
@Composable
fun KatalogSeitenInhalt(seite: KatalogSeite) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (seite) {
            KatalogSeite.FARBEN_SCHRIFT -> FarbenUndSchrift()
            KatalogSeite.GLAS -> Glas()
            KatalogSeite.REZEPTKARTE -> Rezeptkarten()
            KatalogSeite.KACHEL -> Kacheln()
            KatalogSeite.NAEHRWERTE -> Naehrwerte()
            KatalogSeite.SYNC -> Sync()
            KatalogSeite.LEER -> Leer()
            KatalogSeite.GRUNDBAUSTEINE -> Grundbausteine()
        }
    }
}

@Composable
private fun Beschriftung(text: String) = Augenbraue(text)

@Composable
private fun FarbenUndSchrift() {
    val f = FoodyTheme.farben
    val rollen = listOf(
        "Grund" to f.grund, "Text" to f.text, "Nebentext" to f.nebentext, "Primär" to f.primaer,
        "Auf Primär" to f.aufPrimaer, "Primär-Behälter" to f.primaerBehaelter, "Auf Behälter" to f.aufPrimaerBehaelter,
        "Oliv" to f.oliv, "Safran" to f.safran, "Balkenspur" to f.balkenSpur,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rollen.forEach { (name, farbe) -> Farbfeld(name, farbe) }
    }
    val t = MaterialTheme.typography
    val stufen: List<Pair<String, TextStyle>> = listOf(
        "displayLarge" to t.displayLarge, "headlineLarge" to t.headlineLarge, "headlineSmall" to t.headlineSmall,
        "titleLarge" to t.titleLarge, "titleMedium" to t.titleMedium, "bodyLarge" to t.bodyLarge,
        "bodyMedium" to t.bodyMedium, "labelLarge" to t.labelLarge, "labelSmall" to t.labelSmall,
    )
    stufen.forEach { (name, stil) -> Text("$name – Senf-Krusten-Braten", style = stil, color = f.text) }
}

@Composable
private fun Farbfeld(name: String, farbe: Color) {
    GlasFlaeche(GlasStufe.DICHT, Modifier.width(150.dp), form = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(40.dp).background(farbe, RoundedCornerShape(10.dp)))
            Text(name, style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.text)
            Text("#%06X".format(farbe.toArgb() and 0xFFFFFF), style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext)
        }
    }
}

@Composable
private fun Glas() {
    for (stufe in GlasStufe.entries) {
        Beschriftung("Glas ${stufe.name.lowercase()}")
        GlasFlaeche(stufe, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tagebuch", style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text)
                Text("Noch 760 kcal bis zum Tagesziel.", style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            }
        }
    }
}

@Composable
private fun Rezeptkarten() {
    Beschriftung("Groß mit Aktion")
    RezeptkarteGross("Senf-Krusten-Braten", "4 Portionen · 658 kcal · 95 Min.", {}, augenbraue = "Abendessen · 18:30", aktion = KartenAktion("Kochen") {})
    Beschriftung("Groß, langer Titel, ohne Aktion")
    RezeptkarteGross("Rinderrouladen-Schmorgericht mit Rotkohl und Klößen und Rahmsoße", "6 Portionen · 812 kcal", {}, augenbraue = "Sonntag")
    Beschriftung("Raster")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RezeptkarteRaster("Kürbissuppe mit Ingwer", {}, Modifier.weight(1f), favorit = true, zeit = "25 Min.")
        RezeptkarteRaster("Spaghetti Carbonara", {}, Modifier.weight(1f), zeit = "20 Min.", kcal = "640 kcal")
    }
}

@Composable
private fun Kacheln() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Kachel("Milch", "1 l", {}, Modifier.weight(1f))
        Kachel("Paprika", "3 Stk.", {}, Modifier.weight(1f), untertitel = "Gemüse")
        Kachel("Tomaten", "500 g", {}, Modifier.weight(1f), abgehakt = true)
    }
}

@Composable
private fun Naehrwerte() {
    val faelle = listOf<Pair<String, @Composable () -> Unit>>(
        "Mit Ziel" to { Naehrwertbalken(1240, 2000, 68, 142, 41) },
        "Ohne Ziel" to { Naehrwertbalken(1240, null, 68, 142, 41) },
        "Nichts gegessen" to { Naehrwertbalken(0, 2000, 0, 0, 0) },
        "Über Ziel" to { Naehrwertbalken(2300, 2000, 120, 250, 80) },
    )
    faelle.forEach { (titel, balken) ->
        Beschriftung(titel)
        GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) { Box(Modifier.padding(16.dp)) { balken() } }
    }
}

@Composable
private fun Sync() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SyncStatus(SyncAnzeige.Synchron)
        SyncStatus(SyncAnzeige.Laeuft)
        SyncStatus(SyncAnzeige.Wartet)
        SyncStatus(SyncAnzeige.Fehler("Anmeldung abgelaufen"))
    }
}

@Composable
private fun Leer() {
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
        Box(Modifier.padding(12.dp)) { LeererZustandZeile("Noch kein Snack eingetragen", "Eintragen", {}) }
    }
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
        LeererZustandFlaeche(Icons.Outlined.ShoppingBag, "Nichts einzukaufen", "Plane Rezepte für die Woche oder lege Artikel direkt an.", "Artikel hinzufügen", {})
    }
}

private val ziele = listOf(
    NavZiel("Heute", Icons.Outlined.LightMode),
    NavZiel("Rezepte", Icons.AutoMirrored.Outlined.MenuBook),
    NavZiel("Woche", Icons.Outlined.CalendarMonth),
    NavZiel("Einkauf", Icons.Outlined.ShoppingBag),
)

@Composable
private fun Grundbausteine() {
    Beschriftung("Knöpfe")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FoodyKnopf("Kochen", {})
        FoodyKnopf("Eintragen", {}, art = KnopfArt.UMRISS)
        FoodyKnopf("Alle ansehen", {}, art = KnopfArt.TEXT)
        FoodyKnopf("Deaktiviert", {}, aktiv = false)
    }
    Beschriftung("Chips")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FoodyChip("Alle", true, {})
        FoodyChip("Favoriten", false, {})
        FoodyChip("vegetarisch", false, {})
    }
    Beschriftung("Abschnittskopf und Augenbraue")
    Abschnittskopf("Einkauf", linkText = "6 offen", onLink = {})
    Augenbraue("Sonntag, 18. Oktober")
    Beschriftung("Navigation")
    FoodyNavigationsleiste(ziele, aktiv = 0, onWahl = {})
    Row(verticalAlignment = Alignment.Top) {
        FoodyNavigationsRail(ziele, aktiv = 1, onWahl = {})
        Box(Modifier.size(1.dp))
    }
}
