package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.anzeigename
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.rezeptBild
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.erkennung.ErkennungLauf
import de.foody.core.erkennung.ErkennungTexte
import de.foody.core.scan.ScanTexte
import de.foody.domain.Mahlzeit

/**
 * Schritt Bestätigen: die Wahl zum Prüfen und Ändern, je nach Quelle Portionen (Rezept), Menge (Zutat), nur zur Ansicht
 * (Geplantes), die Packung (Scan), das Foto mit dem Vorschlag der Erkennung oder die freien Felder; darunter immer die
 * Mahlzeit. Den Knopf „Eintragen“ zeigt die Seite fest darunter.
 */
@Composable
internal fun Bestaetigen(z: EintragenZustand, vm: EintragenViewModel, aufnahme: Aufnahme) {
    val e = z.eingaben
    when (e.weg) {
        EintragenWeg.REZEPT -> {
            PortionenZeile(e, vm)
            KcalDazu(z.kcalDazu)
        }
        EintragenWeg.ZUTAT -> {
            MengeEingabe(z, vm)
            KcalDazu(z.kcalDazu)
        }
        // Gespeichert wird eine Portion mit der Mahlzeit des Plans (ausPlan): nur zur Ansicht
        EintragenWeg.PLAN -> Text(
            listOfNotNull("1 Portion", z.kcalDazu?.let { "${ganzzahlDeutsch(it)} kcal" }).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text,
        )
        EintragenWeg.FREI -> when {
            e.packung != null -> PackungBestaetigen(z, vm, aufnahme)
            e.fotoBild != null -> FotoBestaetigen(e, vm)
            else -> FreieFelder(e, vm)
        }
    }
    MahlzeitZeile(e.mahlzeit, onWahl = if (e.weg == EintragenWeg.PLAN) null else vm::mahlzeit)
}

/** „= 600 kcal“: was der Eintrag dazubringt; ohne Wert (keine Menge, keine Energiewerte) nichts. */
@Composable
private fun KcalDazu(kcal: Int?) {
    kcal?.let { Text("= ${ganzzahlDeutsch(it)} kcal", style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.primaer) }
}

/**
 * Scan: während Katalog bzw. Open Food Facts suchen ein Fortschritt, die Meldung des Scans und, solange die Packung
 * keine Werte hat und nichts mehr sucht, die Foto-Wege zur Nährwerttabelle (der Strichcode hängt sich an); darunter die
 * Packung. So nach jedem Scan ohne Werte (nicht gefunden, keine Tabelle, nicht verfügbar, Fehler) und nach einem
 * Prozessende mitten in der Suche, deren „läuft“ es nicht übersteht: Sonst bliebe nur das leere Formular.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackungBestaetigen(z: EintragenZustand, vm: EintragenViewModel, aufnahme: Aufnahme) {
    val scan = vm.scanLauf.zustand
    val farben = FoodyTheme.farben
    if (scan.laeuft) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            CircularProgressIndicator(Modifier.size(18.dp), color = farben.primaer, strokeWidth = 2.dp)
            Text("Sucht das Produkt …", style = MaterialTheme.typography.bodySmall, color = farben.nebentext)
        }
    }
    scan.meldung?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = farben.nebentext) }
    if (z.eingaben.packung?.werte.isNullOrEmpty() && !scan.laeuft) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            FoodyKnopf(ScanTexte.FOTO, aufnahme.tabelle, art = KnopfArt.TEXT)
            FoodyKnopf(ScanTexte.GALERIE, aufnahme.tabelleGalerie, art = KnopfArt.TEXT)
        }
    }
    PackungEingabe(z.eingaben, z.vorhandeneZutat, vm)
}

/** Mahlzeitfoto: Vorschaubild, Fortschritt der Erkennung mit „Abbrechen“ bzw. ihre Meldung und die Felder. */
@Composable
private fun FotoBestaetigen(e: Eingaben, vm: EintragenViewModel) {
    Box(Modifier.size(96.dp).clip(FoodyTheme.formen.mittel).semantics { contentDescription = "Foto der Mahlzeit" }) {
        rezeptBild(e.fotoBild)?.invoke()
    }
    FotoErkennungStand(vm.fotoLauf)
    FreieFelder(e, vm)
}

/** Unter dem Foto: während der Erkennung „Erkennt das Foto …“ mit „Abbrechen“, danach die Meldung (falls eine). */
@Composable
private fun FotoErkennungStand(lauf: ErkennungLauf) {
    val zustand by lauf.zustand.collectAsStateWithLifecycle()
    val farben = FoodyTheme.farben
    when {
        zustand.laeuft -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            Text(ErkennungTexte.LAEUFT, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = farben.nebentext)
            FoodyKnopf(ErkennungTexte.ABBRECHEN, lauf::abbrechen, art = KnopfArt.TEXT)
        }
        zustand.meldung != null -> Text(zustand.meldung.orEmpty(), style = MaterialTheme.typography.bodySmall, color = farben.nebentext)
    }
}

/**
 * „Mahlzeit“ und darunter die vier Mahlzeiten als Pillen, die gewählte hervorgehoben. Ohne [onWahl] (Geplantes: die
 * Mahlzeit des Plans) nur zur Ansicht.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MahlzeitZeile(mahlzeit: Mahlzeit, onWahl: ((Mahlzeit) -> Unit)?) {
    val farben = FoodyTheme.farben
    if (onWahl == null) {
        val name = mahlzeit.anzeigename()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = "Mahlzeit: $name" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mahlzeit", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = farben.nebentext)
            Text(name, style = MaterialTheme.typography.bodyLarge, color = farben.text)
        }
        return
    }
    Text("Mahlzeit", style = MaterialTheme.typography.bodyLarge, color = farben.nebentext)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        MAHLZEITEN.forEach { m -> FoodyChip(m.anzeigename(), m == mahlzeit, { onWahl(m) }) }
    }
}
