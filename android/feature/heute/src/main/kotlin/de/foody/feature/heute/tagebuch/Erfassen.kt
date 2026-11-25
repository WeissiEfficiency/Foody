package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.WahlZeile
import de.foody.core.designsystem.komponenten.slotName
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.scan.ScanTexte
import de.foody.feature.heute.stil.SageAbschnitt
import de.foody.feature.heute.stil.SageTreffer
import de.foody.feature.heute.stil.SageWerkzeuge
import de.foody.feature.heute.stil.Werkzeug

/** Test-Tag des großen Knopfs „Foto“ in Erfassen. */
const val ERFASSEN_FOTO = "erfassen-foto"

/** Test-Tag des großen Knopfs „Scannen“ in Erfassen. */
const val ERFASSEN_SCANNEN = "erfassen-scannen"

/**
 * Schritt Erfassen: vier große Knöpfe mit Symbol, oben „Foto“ und „Scannen“ (sie starten sofort, ohne Menü; ohne
 * Erkennungsmodell steht „Frei eintragen“ an der Stelle von „Foto“), darunter „Aus der Galerie“ und „Nährwerttabelle“, ein Hinweis,
 * die Suche über Rezepte, Zutaten und den BLS und das noch offene Geplante des Tags. Antippen führt zu Bestätigen.
 * Sage Immersiv zeigt dieselben Wege als Inventarfelder und die Treffer als Liste im Stil ([SageWerkzeuge]).
 *
 * Bis die Einstellung gelesen ist, ob es ein Modell gibt, steht neben „Scannen“ ein leerer Platz gleicher Größe; die
 * Nebenwege und „Nicht dabei?“ kommen erst danach. So springt beim Öffnen kein Knopf von „Frei eintragen“ zu „Foto“.
 */
@Composable
internal fun Erfassen(z: EintragenZustand, vm: EintragenViewModel, aufnahme: Aufnahme) {
    // null: noch nicht gelesen
    val fotoKnopf = vm.fotoKnopf.collectAsStateWithLifecycle().value
    val foto by vm.fotoLauf.zustand.collectAsStateWithLifecycle()
    val scan = vm.scanLauf.zustand
    val verfuegbar = vm.scanLauf.verfuegbar
    val scannenMoeglich = verfuegbar && !scan.laeuft
    val sage = FoodyTheme.stil.immersiv(FoodyStil.SAGE)
    // Ein neuer Versuch gehört zu keinem früheren Strichcode
    val tabelle = {
        scan.menueVersuch()
        aufnahme.tabelle()
    }
    if (sage) {
        val frei = Werkzeug("Frei eintragen", "Frei", Icons.Outlined.Edit, vm::freiEintragen)
        SageWerkzeuge(
            listOf(
                when (fotoKnopf) {
                    true -> Werkzeug("Foto", "Foto", Icons.Outlined.PhotoCamera, aufnahme.foto, tag = ERFASSEN_FOTO)
                    false -> frei
                    null -> null
                },
                Werkzeug("Scannen", "Scannen", Icons.Outlined.DocumentScanner, aufnahme.scannen, scannenMoeglich, scan.laeuft, ERFASSEN_SCANNEN),
                Werkzeug("Aus der Galerie", "Galerie", Icons.Outlined.PhotoLibrary, aufnahme.galerie).takeIf { fotoKnopf == true },
                Werkzeug("Nährwerttabelle", "Tabelle", Icons.Outlined.TableChart, tabelle, scannenMoeglich).takeIf { fotoKnopf != null },
            ),
        )
    } else {
        // Gleich hohe Knöpfe je Reihe, auch wenn ein Text bei großer Schrift umbricht
        Reihe { knopf ->
            when (fotoKnopf) {
                true -> GrosserKnopf("Foto", Icons.Outlined.PhotoCamera, aufnahme.foto, knopf.testTag(ERFASSEN_FOTO))
                false -> GrosserKnopf("Frei eintragen", Icons.Outlined.Edit, vm::freiEintragen, knopf)
                null -> Box(knopf)
            }
            GrosserKnopf(
                "Scannen", Icons.Outlined.DocumentScanner, aufnahme.scannen, knopf.testTag(ERFASSEN_SCANNEN),
                aktiv = scannenMoeglich, laeuft = scan.laeuft,
            )
        }
        if (fotoKnopf != null) {
            Reihe { knopf ->
                if (fotoKnopf) GrosserKnopf("Aus der Galerie", Icons.Outlined.PhotoLibrary, aufnahme.galerie, knopf)
                GrosserKnopf("Nährwerttabelle", Icons.Outlined.TableChart, tabelle, knopf, aktiv = scannenMoeglich)
                if (!fotoKnopf) Box(knopf)
            }
        }
    }
    val hinweis = scan.meldung ?: ScanTexte.NICHT_VERFUEGBAR.takeIf { !verfuegbar } ?: foto.meldung
    hinweis?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext) }

    Suche(z.eingaben.suche, vm::suche)
    val abschnitt: @Composable (String) -> Unit = if (sage) { t -> SageAbschnitt(t) } else { t -> Abschnitt(t) }
    val treffer: @Composable (String, String?, () -> Unit) -> Unit =
        if (sage) { t, i, w -> SageTreffer(t, i, w) } else { t, i, w -> WahlZeile(t, gewaehlt = false, w, info = i, knopf = true) }
    if (z.plan.isNotEmpty()) {
        abschnitt("Geplant")
        z.plan.forEach { p ->
            // Sage: die Mahlzeit als Nebenzeile; sonst in einer Zeile
            val titel = if (sage) p.titel else "${p.titel} · ${slotName(p.slot)}"
            treffer(titel, slotName(p.slot).takeIf { sage }) { vm.planWaehlen(p.planEintragId) }
        }
    }
    if (z.rezepte.isNotEmpty()) {
        abschnitt("Rezepte")
        z.rezepte.forEach { r -> treffer(r.titel, null) { vm.rezeptWaehlen(r.id) } }
    }
    if (z.zutaten.isNotEmpty()) {
        abschnitt("Zutaten")
        z.zutaten.forEach { t -> treffer(t.name, null) { vm.zutatWaehlen(t.id) } }
    }
    if (z.bls.isNotEmpty()) {
        abschnitt("Aus dem Bundeslebensmittelschlüssel")
        z.bls.forEach { t -> treffer(t.vorschlagName, blsInfo(t)) { vm.blsWaehlen(t) } }
    }
    if (fotoKnopf == true) FoodyKnopf("Nicht dabei? Frei eintragen", vm::freiEintragen, art = KnopfArt.TEXT)
}

@Composable
private fun Abschnitt(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.nebentext)
}

/** Zwei gleich breite, gleich hohe Knöpfe nebeneinander; [inhalt] bekommt den Modifier für jeden. */
@Composable
private fun Reihe(inhalt: @Composable RowScope.(Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
        inhalt(Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * Großer Knopf mit Symbol über dem Text, in Farben und Form des Stils; mindestens 72 dp hoch, der Text bricht bei großer
 * Schrift um. Während [laeuft] ein Fortschritt statt des Symbols.
 */
@Composable
private fun GrosserKnopf(
    text: String,
    symbol: ImageVector,
    onKlick: () -> Unit,
    modifier: Modifier,
    aktiv: Boolean = true,
    laeuft: Boolean = false,
) {
    val farben = FoodyTheme.farben
    OutlinedButton(
        onClick = onKlick,
        modifier = modifier.heightIn(min = 72.dp),
        enabled = aktiv,
        shape = FoodyTheme.formen.knopf,
        border = BorderStroke(1.5.dp, if (aktiv) farben.primaer else farben.nebentext.copy(alpha = 0.38f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = farben.primaer),
        contentPadding = PaddingValues(horizontal = FoodyAbstaende.s2, vertical = FoodyAbstaende.s3),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s1)) {
            if (laeuft) {
                CircularProgressIndicator(Modifier.size(24.dp), color = farben.primaer, strokeWidth = 2.dp)
            } else {
                Icon(symbol, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
