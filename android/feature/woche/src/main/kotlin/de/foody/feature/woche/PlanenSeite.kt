package de.foody.feature.woche

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.FoodyTextfeld
import de.foody.core.designsystem.komponenten.Reiterleiste
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.komponenten.WahlZeile
import de.foody.core.designsystem.komponenten.anzeigename
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.platzFuerLeiste
import de.foody.core.designsystem.komponenten.slotName
import de.foody.core.designsystem.komponenten.zeilenImStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.stilText
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.feature.woche.stil.planenTag
import java.time.LocalDate

/** Test-Tag von „Einplanen“ (das Wort wechselt je Stil). */
const val PLANEN_EINPLANEN = "planen-einplanen"

/** Reiter der Planen-Seite: selbst wählen (Tag, Mahlzeit, Gericht) oder Vorschläge für die leeren Tage. */
internal enum class PlanenReiter { SELBST, VORSCHLAG }

/** Höchstlänge eines eigenen Mahlzeitnamens (wie in [MahlzeitWahl]). */
private const val MAX_NAME = 30

/** Start der Planen-Seite: [reiter] offen, [datum] und [mahlzeit] vorgewählt. Als Text, damit er Drehen übersteht. */
internal data class PlanenStart(val reiter: PlanenReiter, val datum: LocalDate, val mahlzeit: String) {
    fun alsText(): String = "${reiter.name}|$datum|$mahlzeit"

    companion object {
        fun ausText(text: String): PlanenStart {
            val (reiter, datum, mahlzeit) = text.split("|", limit = 3)
            return PlanenStart(PlanenReiter.valueOf(reiter), LocalDate.parse(datum), mahlzeit)
        }
    }
}

/** Was die Planen-Seite auslösen kann; [onEinplanen] bekommt Tag, Mahlzeit, Rezept-ID und Portionen. */
internal class PlanenAktionen(
    val onReiter: (PlanenReiter) -> Unit,
    val onEinplanen: (LocalDate, String, String, Int) -> Unit,
    val onZurueck: () -> Unit,
    val fuellen: FuellenAktionen,
)

/** Wörter der Seite je Stil (Ruhig: die normalen), wie in den Entwürfen. */
@Composable
private fun planenTitel() = stilText("Woche planen", sage = "Den Weg planen", anime = "Planen!", bayrisch = "Wochn plana", kreuzer = "Kurs plotten")

@Composable
private fun reiterWoerter() = listOf(
    stilText("Selbst wählen", sage = "Mahl wählen", anime = "Rezept wählen", bayrisch = "Selber aussuacha", kreuzer = "Manuell"),
    stilText(
        "Vorschlagen lassen", sage = "Leere Tage füllen", anime = "Tage füllen", bayrisch = "Vorschlagn lassn", grauen = "Die Sterne fragen",
        kreuzer = "Navcomputer",
    ),
)

/**
 * Planen wie in den Entwürfen der Stile Q–V: eine eigene Seite mit Zurück und den Reitern „Selbst wählen“ (Tag der
 * Woche, Mahlzeit oder eigener Name, Gericht mit Suche; unten Portionen, die Zusammenfassung und „Einplanen“) und
 * „Vorschlagen lassen“ (der Inhalt von „Leere Tage füllen“). Milchglas behält Mahlzeitwahl, Rezeptwahl und
 * Füllen-Blatt. [fuellen] ist `null`, solange die Vorschläge laden.
 */
@Composable
internal fun PlanenSeite(start: PlanenStart, karten: List<RezeptKarte>, fuellen: FuellenZustand?, a: PlanenAktionen) {
    BackHandler(onBack = a.onZurueck)
    val bekannt = Mahlzeit.ausText(start.mahlzeit)
    var datum by rememberSaveable { mutableStateOf(start.datum.toString()) }
    var mahlzeit by rememberSaveable { mutableStateOf((bekannt ?: Mahlzeit.ABENDESSEN).name) }
    var eigener by rememberSaveable { mutableStateOf(if (bekannt == null) start.mahlzeit else "") }
    var rezeptId by rememberSaveable { mutableStateOf<String?>(null) }
    var portionen by rememberSaveable { mutableStateOf<Int?>(null) }
    val rezept = karten.firstOrNull { it.id == rezeptId }
    val tag = LocalDate.parse(datum)
    val slot = eigener.trim().ifEmpty { mahlzeit }
    val n = portionen ?: rezept?.portionen ?: 2
    val stil = FoodyTheme.stil
    // Grauen und Kreuzer: Überzeile wie im Entwurf (Immersiv)
    val ueberzeile = if (stil.variante == StilVariante.IMMERSIV) {
        stilText("", grauen = "Die Sterne befragen", kreuzer = "Navigation · ${wochenTitel(montagVon(tag)).substringBefore(" ·")}").ifEmpty { null }
    } else {
        null
    }

    Column(Modifier.fillMaxSize().widthIn(max = FoodyBreiten.inhalt).imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2, bottom = FoodyAbstaende.s3),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            Seitenkopf(planenTitel(), ueberzeile = ueberzeile, onZurueck = a.onZurueck, beschreibung = "Woche planen")
            val woerter = reiterWoerter()
            Reiterleiste(
                woerter, start.reiter.ordinal, { a.onReiter(PlanenReiter.entries[it]) },
                beschreibungen = listOf("Selbst wählen", "Vorschlagen lassen"),
            )
            when (start.reiter) {
                PlanenReiter.SELBST -> SelbstWaehlen(
                    SelbstWahl(tag, mahlzeit, eigener, rezeptId),
                    karten,
                    onTag = { datum = it.toString() },
                    onMahlzeit = { mahlzeit = it.name; eigener = "" },
                    onEigener = { eigener = it.take(MAX_NAME) },
                    onRezept = { rezeptId = it; portionen = null },
                )
                PlanenReiter.VORSCHLAG -> fuellen?.let { FuellenInhalt(it, karten.associate { k -> k.id to k.name }, a.fuellen) }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = FoodyAbstaende.s4).padding(top = FoodyAbstaende.s2, bottom = platzFuerLeiste() + FoodyAbstaende.s3),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
        ) {
            when (start.reiter) {
                PlanenReiter.SELBST -> {
                    // Portionen fest unten, damit sie nach dem Wählen nicht unter einer langen Rezeptliste liegen
                    if (rezept != null) {
                        val neu = { x: Int -> portionen = x.coerceIn(1, 99) }
                        if (zeilenImStil()) PortionenStil(n, neu) else PortionenNormal(n, neu)
                    }
                    val zusammen = listOfNotNull(tagLang(tag), slotName(slot), rezept?.name)
                    Text(zusammen.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
                    FoodyKnopf(
                        stilText(
                            "Einplanen", sage = "Auf die Karte setzen", anime = "In die Woche! ▶", bayrisch = "Eiplana", grauen = "Es sei beschlossen",
                            kreuzer = "Kurs bestätigen",
                        ),
                        { rezept?.let { a.onEinplanen(tag, slot, it.id, n) } },
                        Modifier.fillMaxWidth().testTag(PLANEN_EINPLANEN),
                        aktiv = rezept != null && slot.isNotBlank(),
                    )
                }
                PlanenReiter.VORSCHLAG -> fuellen?.let { FuellenKnoepfe(it, a.fuellen) }
            }
        }
    }
}

/** Was im Reiter „Selbst wählen“ gewählt ist. */
private class SelbstWahl(val tag: LocalDate, val mahlzeit: String, val eigener: String, val rezeptId: String?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelbstWaehlen(
    w: SelbstWahl,
    karten: List<RezeptKarte>,
    onTag: (LocalDate) -> Unit,
    onMahlzeit: (Mahlzeit) -> Unit,
    onEigener: (String) -> Unit,
    onRezept: (String) -> Unit,
) {
    var suche by rememberSaveable { mutableStateOf("") }
    val stil = FoodyTheme.stil.stil
    val abstand = Arrangement.spacedBy(FoodyAbstaende.s2)
    Augenbraue(stilText("Tag", bayrisch = "Wöcher Tag?", kreuzer = "Sprung"))
    val montag = montagVon(w.tag)
    FlowRow(horizontalArrangement = abstand, verticalArrangement = abstand) {
        (0L..6L).forEach { i ->
            val d = montag.plusDays(i)
            FoodyChip("${planenTag(stil, d)} ${d.dayOfMonth}.", d == w.tag, { onTag(d) }, beschreibung = tagLang(d))
        }
    }
    Augenbraue(stilText("Mahlzeit", bayrisch = "Wann?", kreuzer = "Orbit"))
    FlowRow(horizontalArrangement = abstand, verticalArrangement = abstand) {
        TAGES_MAHLZEITEN.forEach { m ->
            FoodyChip(m.anzeigename(), w.eigener.isBlank() && w.mahlzeit == m.name, { onMahlzeit(m) })
        }
    }
    FoodyTextfeld(stilText("Eigener Name, z. B. Essen bei Mia", sage = "Eigenes Mahl"), w.eigener, onWert = onEigener)
    Augenbraue(stilText("Gericht", bayrisch = "Wos gibt’s?", kreuzer = "Zielsystem"))
    FoodyTextfeld("Rezept suchen", suche) { suche = it }
    val treffer = auswahlSortiert(karten, suche, Mahlzeit.ausText(w.eigener.trim().ifEmpty { w.mahlzeit }))
    treffer.forEach { k ->
        WahlZeile(k.name, k.id == w.rezeptId, { onRezept(k.id) }, wert = k.kcalJePortion?.let { "${ganzzahlDeutsch(it)} kcal" })
    }
}
