package de.foody.feature.einkauf.stil

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.data.einkauf.ZuletztArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.komponenten.RunderKnopf
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.animeSchatten
import de.foody.core.designsystem.stil.anime.animeSzenenTitel
import de.foody.core.designsystem.stil.szenenWort
import de.foody.core.designsystem.stil.titelGroesse
import de.foody.domain.ShoppingCatalog
import de.foody.feature.einkauf.EinkaufKachel
import de.foody.feature.einkauf.ZULETZT
import de.foody.feature.einkauf.stand
import kotlin.math.min

/**
 * „Einkauf“ im Anime-Stil (R) in Immersiv (Entwurf `R-Einkauf`): schräges „QUEST!“ mit Schatten im Akzent und
 * rundem Knopf „Listen“ (Klemmbrett), darunter der XP-Balken (erledigt/gesamt). Jede Abteilung ist eine weiße Karte mit
 * hartem Schatten und farbigem Abzeichen, darin die Posten zu zweit; gekaufte sind blass, durchgestrichen und tragen
 * ein „GET!“. „Zuletzt verwendet“ und der Katalog sind ebenso Comic-Panels, statt Klappen mit einer waagerecht
 * wischbaren Reihe Kacheln.
 */
internal fun LazyGridScope.animeKopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit) {
    ganzeBreite("anime-kopf") { Kopf(ansicht, onListen) }
}

internal fun LazyGridScope.animePosten(ansicht: EinkaufsAnsicht?, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    jeGruppe("anime", ansicht.gruppiert()) { i, g -> Karte(g, ABZEICHEN[i % ABZEICHEN.size], onUmschalten, onDetails) }
}

/** „Zuletzt verwendet“ als Panel; Antippen setzt den Artikel wieder auf die Liste. */
internal fun LazyGridScope.animeZuletzt(zuletzt: List<ZuletztArtikel>, alle: List<EinkaufsArtikel>, onTipp: (ZuletztArtikel) -> Unit) {
    if (zuletzt.isEmpty()) return
    ganzeBreite("anime-zuletzt") {
        Panel(ZULETZT, Anime.gelb, zuletzt, { it.name }) { z, m -> EinkaufKachel(z.name, "", z.emoji, stand(z.name, alle), { onTipp(z) }, m) }
    }
}

/** Der Katalog: je Abteilung ein Panel mit wischbarer Kachelreihe, zuletzt die „Eigenen Artikel“ der Liste. */
internal fun LazyGridScope.animeKatalog(alle: List<EinkaufsArtikel>, onTipp: (name: String, abteilung: String) -> Unit) {
    ShoppingCatalog.sections.forEachIndexed { i, s ->
        ganzeBreite("anime-katalog-${s.name}") {
            Panel("${s.emoji} ${s.name}", ABZEICHEN[i % ABZEICHEN.size], s.items, { it.name }) { k, m ->
                EinkaufKachel(k.name, "", k.emoji, stand(k.name, alle), { onTipp(k.name, s.name) }, m)
            }
        }
    }
    val eigene = alle.filter { ShoppingCatalog.find(it.name) == null }.distinctBy { it.name.lowercase() }
    if (eigene.isEmpty()) return
    val name = ShoppingCatalog.OWN_ITEMS
    ganzeBreite("anime-katalog-eigene") {
        Panel("🛒 $name", Anime.rosa, eigene, { it.name.lowercase() }) { e, m ->
            EinkaufKachel(e.name, "", e.emoji, stand(e.name, alle), { onTipp(e.name, name) }, m)
        }
    }
}

/** Breite einer Kachel in der wischbaren Reihe; die nächste schaut rechts heraus. */
private val PANEL_KACHEL = 104.dp

/**
 * Comic-Panel: weiße Karte mit Kante und Versatzschatten, oben der [titel] in der bunten Pille [farbe], darunter die
 * [eintraege] als waagerecht wischbare Reihe (gezeichnet wird nur, was im Bild ist).
 */
@Composable
private fun <T> Panel(titel: String, farbe: Color, eintraege: List<T>, schluessel: (T) -> String, kachel: @Composable (T, Modifier) -> Unit) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp)
            .then(if (FoodyTheme.stil.inszeniert) Modifier.animeSchatten(form, f.text, 4.dp) else Modifier)
            .background(Color.White, form).border(2.5.dp, f.text, form).clip(form).padding(top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            titel,
            Modifier.padding(horizontal = 10.dp).background(farbe, Anime.pille).border(2.dp, f.text, Anime.pille).padding(horizontal = 8.dp)
                .semantics { heading() },
            style = Anime.text(13f, FontWeight.ExtraBold),
            color = f.text,
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(eintraege, key = schluessel) { kachel(it, Modifier.width(PANEL_KACHEL)) }
        }
    }
}

/** Farben der Abteilungs-Abzeichen wie im Entwurf (Grün, Türkis, Orange, Lila, Rosa). */
private val ABZEICHEN = listOf(Anime.mint, Anime.tuerkis, Anime.orange, Anime.lila, Anime.rosa)

/** Schrift gekaufter Posten auf hellem Grau (AA). */
private val GEKAUFT_TEXT = Color(0xFF5E5974)
private val GEKAUFT_GRUND = Color(0xFFF2EEF4)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit) {
    val f = FoodyTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Der große Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                szenenWort("QUEST!", "Einkauf"),
                Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).semantics {
                    heading()
                    contentDescription = "Einkauf"
                },
                style = animeSzenenTitel(titelGroesse(38f)),
                color = f.text,
            )
            // Rund wie das Zahnrad auf „Heute“, mit Klemmbrett
            Box(Modifier.align(Alignment.CenterVertically)) { RunderKnopf(Icons.AutoMirrored.Outlined.Assignment, "Einkaufslisten", onListen) }
        }
        val gesamt = ansicht.gesamt
        if (gesamt > 0) Fortschritt(ansicht.erledigt, gesamt)
    }
}

/** „XP“, der Balken in der Pille und „3/12“; TalkBack hört „3 von 12 erledigt“. */
@Composable
private fun Fortschritt(erledigt: Int, gesamt: Int) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$erledigt von $gesamt erledigt" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Ruhig wie im Entwurf ohne „XP“
        if (FoodyTheme.stil.inszeniert) Text("XP", style = Anime.titel(15f, 0f), color = f.text)
        Box(Modifier.weight(1f).height(16.dp).clip(Anime.pille).background(Color.White).border(2.5.dp, f.text, Anime.pille)) {
            if (erledigt > 0) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(erledigt.toFloat() / gesamt).background(akzent)
                        .border(2.5.dp, f.text, Anime.pille),
                )
            }
        }
        Text("$erledigt/$gesamt", style = Anime.text(14f, FontWeight.ExtraBold), color = f.text)
    }
}

@Composable
private fun Karte(g: PostenGruppe, farbe: Color, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp)
            .then(if (FoodyTheme.stil.inszeniert) Modifier.animeSchatten(form, f.text, 4.dp) else Modifier)
            .background(Color.White, form).border(2.5.dp, f.text, form).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            g.name,
            Modifier.background(farbe, Anime.pille).border(2.dp, f.text, Anime.pille).padding(horizontal = 8.dp).semantics { heading() },
            style = Anime.text(13f, FontWeight.ExtraBold),
            color = f.text,
        )
        g.artikel.chunked(2).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                reihe.forEach { Posten(it, Modifier.weight(1f), onUmschalten, onDetails) }
                if (reihe.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Posten(a: EinkaufsArtikel, modifier: Modifier, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(12.dp)
    val schrift = if (a.gekauft) GEKAUFT_TEXT else f.text
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(form).background(if (a.gekauft) GEKAUFT_GRUND else Color.White)
                .border(2.dp, f.text, form).posten(a, onUmschalten, onDetails).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                a.name, Modifier.weight(1f),
                style = Anime.text(14f, FontWeight.ExtraBold).copy(textDecoration = if (a.gekauft) TextDecoration.LineThrough else null),
                color = schrift,
            )
            a.menge?.let { Text(it, style = Anime.text(11.5f), color = schrift) }
        }
        if (a.gekauft) Get(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-10).dp))
    }
}

/** Gelber Stern „GET!“ an gekauften Posten; ploppt auf, wenn Animationen erlaubt sind. Für TalkBack stumm. */
@Composable
private fun Get(modifier: Modifier) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    val animiert = animationenErlaubt()
    val groesse = remember { Animatable(if (animiert) 0.2f else 1f) }
    LaunchedEffect(Unit) { if (animiert) groesse.animateTo(1f, tween(300)) }
    Box(modifier.size(40.dp, 26.dp).rotate(-12f).scale(groesse.value).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val punkte = listOf(
                0.5f to 0f, 0.62f to 0.3f, 1f to 0.2f, 0.74f to 0.5f, 1f to 0.8f, 0.62f to 0.7f,
                0.5f to 1f, 0.38f to 0.7f, 0f to 0.8f, 0.26f to 0.5f, 0f to 0.2f, 0.38f to 0.3f,
            )
            val stern = Path().apply {
                punkte.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w, y * h) else lineTo(x * w, y * h) }
                close()
            }
            drawPath(stern, Anime.gelb)
        }
        Text("GET!", style = Anime.titel(9f / k, 0f).copy(lineHeight = (10f / k).sp), color = f.text)
    }
}
