package de.foody.feature.heute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import de.foody.core.data.heute.OffenerEinkauf
import de.foody.core.data.heute.RezeptKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Abschnittskopf
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.Kachel
import de.foody.core.designsystem.komponenten.KartenAktion
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.LeererZustandZeile
import de.foody.core.designsystem.komponenten.Naehrwertbalken
import de.foody.core.designsystem.komponenten.RezeptkarteGross
import de.foody.core.designsystem.komponenten.SyncAnzeige
import de.foody.core.designsystem.komponenten.SyncStatus
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.komponenten.rezeptBild
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATUM = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/**
 * Augenbraue mit Datum, Titel, Sync-Status (nur verbunden) und Profilsymbol für die Einstellungen. Ab 130 % Schrift
 * steht der Sync-Status unter dem Titel, sonst bliebe für Datum und Titel kaum Breite („He-ute“, V2).
 */
@Composable
internal fun HeuteKopf(datum: LocalDate?, sync: SyncAnzeige?, onEinstellungen: () -> Unit) {
    val syncDarunter = LocalDensity.current.fontScale > 1.2f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Column(Modifier.weight(1f)) {
            datum?.let { Augenbraue(DATUM.format(it)) }
            Text("Heute", Modifier.ueberschrift(), style = MaterialTheme.typography.headlineLarge, color = FoodyTheme.farben.text)
            if (syncDarunter) sync?.let { SyncStatus(it, Modifier.padding(top = FoodyAbstaende.s2)) }
        }
        if (!syncDarunter) sync?.let { SyncStatus(it) }
        IconButton(onClick = onEinstellungen) {
            Icon(Icons.Outlined.AccountCircle, contentDescription = "Einstellungen", tint = FoodyTheme.farben.text)
        }
    }
}

@Composable
internal fun NaechsteMahlzeitKarte(n: NaechsteMahlzeit, aktionen: HeuteAktionen) {
    val m = n.mahlzeit
    RezeptkarteGross(
        titel = m.titel,
        meta = meta(m.portionen, m.kcalJePortion, m.zeitMinuten),
        onKlick = { aktionen.onRezept(m.rezeptId) },
        augenbraue = "${slotName(m.slot)} · ${if (n.morgen) "Morgen" else "Heute"}",
        bild = rezeptBild(m.bild),
        aktion = KartenAktion("Kochen") { aktionen.onKochen(m.rezeptId, m.portionen) },
    )
}

@Composable
internal fun InspirationKarte(r: RezeptKurz, onRezept: (String) -> Unit) {
    RezeptkarteGross(
        titel = r.titel,
        meta = meta(null, r.kcalJePortion, r.zeitMinuten),
        onKlick = { onRezept(r.id) },
        augenbraue = "Inspiration",
        bild = rezeptBild(r.bild),
        aktion = KartenAktion("Ansehen") { onRezept(r.id) },
    )
}

/** „2 Portionen · 717 kcal je Portion · 30 Min.“ – fehlende Angaben entfallen. */
internal fun meta(portionen: Int?, kcal: Int?, minuten: Int?): String = listOfNotNull(
    portionen?.let { if (it == 1) "1 Portion" else "$it Portionen" },
    kcal?.let { "$it kcal je Portion" },
    minuten?.let { "$it Min." },
).joinToString(" · ")

@Composable
internal fun TagebuchBereich(t: TagebuchHeute, aktionen: HeuteAktionen) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Abschnittskopf("Tagebuch", linkText = "Alle Einträge", onLink = aktionen.onAlleEintraege)
        GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
                Naehrwertbalken(t.summe.kcal, t.ziel, t.summe.eiweiss, t.summe.kohlenhydrate, t.summe.fett)
                t.mahlzeiten.forEach { MahlzeitZeilen(it, aktionen) }
                FoodyKnopf("Eintragen", { aktionen.onEintragen(null) }, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
            }
        }
    }
}

@Composable
private fun MahlzeitZeilen(a: MahlzeitAbschnitt, aktionen: HeuteAktionen) {
    val name = a.mahlzeit.anzeigename()
    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s1)) {
        Augenbraue(name)
        a.eintraege.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                Spacer(Modifier.padding(FoodyAbstaende.s1))
                Text(kcalText(e.kcal, e.vollstaendig), style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            }
        }
        a.vorschlaege.forEach { v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${v.titel} · geplant", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.nebentext,
                )
                FoodyKnopf("Gegessen?", { aktionen.onGegessen(v.planEintragId) }, art = KnopfArt.TEXT)
            }
        }
        if (a.eintraege.isEmpty() && a.vorschlaege.isEmpty()) {
            LeererZustandZeile("Noch kein $name", "Eintragen", { aktionen.onEintragen(a.mahlzeit) })
        }
    }
}

/** Bis zu sechs offene Artikel der jüngsten Liste als Kacheln, zwei je Reihe. */
@Composable
internal fun EinkaufBereich(e: OffenerEinkauf, onEinkauf: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Abschnittskopf("Einkauf", linkText = "${e.anzahl} offen", onLink = { onEinkauf(e.listeId) })
        e.artikel.take(MAX_KACHELN).chunked(2).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                reihe.forEach { a -> Kachel(a.name, a.menge.orEmpty(), { onEinkauf(e.listeId) }, Modifier.weight(1f)) }
                if (reihe.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private const val MAX_KACHELN = 6
