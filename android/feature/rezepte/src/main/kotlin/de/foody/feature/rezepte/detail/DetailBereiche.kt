package de.foody.feature.rezepte.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.RezeptPlatzhalter
import de.foody.core.designsystem.komponenten.RunderKnopf
import de.foody.core.designsystem.komponenten.rezeptBild
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.domain.Quantity
import de.foody.domain.QuantityFormatter
import de.foody.domain.RecipeScaler

/**
 * Detail wie im Entwurf: Bild mit runden Knöpfen, darunter eine Glaskarte mit Gang, Titel und Angaben als Pillen,
 * „Kochen“, dann Zutaten (mit Portionen und „Auf die Einkaufsliste“) und Zubereitung je in einer Glaskarte.
 */
@Composable
internal fun DetailInhalt(z: DetailZustand.Da, a: DetailAktionen) {
    val r = z.voll.rezept
    val angaben = listOfNotNull(zeit(r.prepMinutes, r.cookMinutes), kcalJePortion(z.voll), kcalFuerPortionen(z.voll, z.portionen))
    Kopfbild(z, a)
    Column(Modifier.padding(horizontal = FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s4)) {
        Kopfkarte(r.name, gangZeile(z.voll.einordnung), angaben)
        if (r.archivedAt != null) {
            GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                    Text("Dieses Rezept ist archiviert.", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                    FoodyKnopf("Wiederherstellen", { a.onArchivieren(false) }, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
                }
            }
        }
        FoodyKnopf("Kochen", { a.onKochen(z.portionen) }, Modifier.fillMaxWidth())
        Karte {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zutaten", Modifier.weight(1f).ueberschrift(), style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text)
                Portionen(z.portionen, a.onPortionen)
            }
            z.voll.zeilen.forEach { zeile ->
                val menge = zeile.menge.takeIf { it.signum() > 0 }?.let { m ->
                    val skaliert = RecipeScaler.scale(m, r.defaultServings.coerceAtLeast(1), z.portionen)
                    QuantityFormatter.format(Quantity.of(skaliert, zeile.einheit), zeile.einheit)
                }.orEmpty()
                Row(Modifier.padding(vertical = FoodyAbstaende.s1)) {
                    Text(menge, Modifier.width(88.dp), style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.primaer)
                    Column(Modifier.weight(1f)) {
                        Text(zeile.name + if (zeile.optional) " (optional)" else "", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                        zeile.hinweis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext) }
                    }
                }
            }
            FoodyKnopf("Auf die Einkaufsliste", { a.onEinkauf(z.portionen) }, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
        }
        if (z.voll.schritte.isNotEmpty()) {
            Karte {
                Text("Zubereitung", Modifier.ueberschrift(), style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text)
                z.voll.schritte.forEachIndexed { i, s -> Schritt(i + 1, s) }
            }
        }
        r.notes?.let {
            Karte {
                Text("Notizen", Modifier.ueberschrift(), style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.text)
            }
        }
        Einordnungszeile(z.voll.einordnung)
        r.sourceUrl?.let { Quelle(it) }
        Naehrwerttabelle(z.voll, z.portionen)
    }
}

/** Glaskarte mit Innenabstand für einen Abschnitt des Details. */
@Composable
private fun Karte(inhalt: @Composable ColumnScope.() -> Unit) {
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth(), unschaerfeErlaubt = false) {
        Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), content = inhalt)
    }
}

@Composable
private fun Kopfbild(z: DetailZustand.Da, a: DetailAktionen) {
    val r = z.voll.rezept
    val fav = r.favorite
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
        Box(Modifier.matchParentSize()) { rezeptBild(r.imageUri)?.invoke() ?: RezeptPlatzhalter(Modifier.fillMaxSize()) }
        Row(Modifier.fillMaxWidth().padding(FoodyAbstaende.s2), verticalAlignment = Alignment.CenterVertically) {
            RunderKnopf(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", a.onZurueck)
            Spacer(Modifier.weight(1f))
            RunderKnopf(Icons.Outlined.Edit, "Bearbeiten", a.onBearbeiten)
            RunderKnopf(
                if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                if (fav) "Favorit entfernen" else "Als Favorit merken",
                { a.onFavorit(!fav) },
            )
            Box {
                RunderKnopf(Icons.Outlined.MoreVert, "Mehr", { menu = true })
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Duplizieren") }, onClick = { menu = false; a.onDuplizieren() })
                    if (r.archivedAt == null) {
                        DropdownMenuItem(text = { Text("Archivieren") }, onClick = { menu = false; a.onArchivieren(true) })
                    }
                }
            }
        }
    }
}

/** Glaskarte unter dem Bild: Gang und Einordnung als Überzeile, Titel, Zeit und kcal als Pillen. */
@Composable
private fun Kopfkarte(name: String, ueberzeile: String?, angaben: List<String>) {
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth().padding(top = FoodyAbstaende.s1)) {
        Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            if (ueberzeile != null) Augenbraue(ueberzeile, farbe = FoodyTheme.farben.primaer)
            Text(name, Modifier.ueberschrift(), style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
            if (angaben.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                    angaben.forEach { Angabe(it) }
                }
            }
        }
    }
}

@Composable
private fun Angabe(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = FoodyTheme.farben.aufPrimaerBehaelter,
        modifier = Modifier
            .clip(CircleShape)
            .background(FoodyTheme.farben.primaerBehaelter)
            .padding(horizontal = FoodyAbstaende.s3, vertical = FoodyAbstaende.s1),
    )
}

/** Ein Schritt mit Nummer im Kreis. */
@Composable
private fun Schritt(nummer: Int, text: String) {
    Row(Modifier.padding(vertical = FoodyAbstaende.s1), horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
        Box(
            Modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp).clip(CircleShape).background(FoodyTheme.farben.primaerBehaelter),
            contentAlignment = Alignment.Center,
        ) {
            Text("$nummer", style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.aufPrimaerBehaelter)
        }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
    }
}

@Composable
private fun Portionen(n: Int, onSchritt: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSchritt(false) }) { Icon(Icons.Outlined.Remove, contentDescription = "Weniger Portionen", tint = FoodyTheme.farben.text) }
        Text(if (n == 1) "1 Portion" else "$n Portionen", style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.text)
        IconButton(onClick = { onSchritt(true) }) { Icon(Icons.Outlined.Add, contentDescription = "Mehr Portionen", tint = FoodyTheme.farben.text) }
    }
}

/** „Hauptspeise · Abendessen“ für die Überzeile; nur sicher bekannte Einordnung, vermutete bleibt unten in der Zeile. */
internal fun gangZeile(e: Einordnung): String? {
    val gaenge = if (e.gaengeVermutet) emptyList() else e.gaenge.sortedBy { it.ordinal }.map(::gangName)
    val mahlzeiten = if (e.mahlzeitenVermutet) emptyList() else e.mahlzeiten.sortedBy { it.ordinal }.map(::mahlzeitName)
    return (gaenge + mahlzeiten).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun Einordnungszeile(e: Einordnung) {
    val mahlzeiten = e.mahlzeiten.sortedBy { it.ordinal }.joinToString(", ") { mahlzeitName(it) }.ifEmpty { "alle" }
    val gaenge = e.gaenge.sortedBy { it.ordinal }.joinToString(", ") { gangName(it) }.ifEmpty { "–" }
    val vermutet = if (e.mahlzeitenVermutet || e.gaengeVermutet) " (vermutet)" else ""
    Text("Mahlzeit: $mahlzeiten · Gang: $gaenge$vermutet", style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
}

@Composable
private fun Quelle(url: String) {
    val uri = LocalUriHandler.current
    // Als Link-Annotation: TalkBack meldet einen Link; ohne Browser stürzt das Öffnen nicht ab
    val link = LinkAnnotation.Url(
        url, TextLinkStyles(SpanStyle(color = FoodyTheme.farben.primaer, textDecoration = TextDecoration.Underline)),
    ) { runCatching { uri.openUri(url) } }
    Text(
        buildAnnotatedString {
            append("Quelle: ")
            withLink(link) { append(url) }
        },
        style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext,
    )
}

internal fun zeit(vorbereitung: Int?, kochen: Int?): String? =
    listOfNotNull(vorbereitung, kochen).takeIf { it.isNotEmpty() }?.sum()?.let { "$it Min." }

internal fun mahlzeitName(m: Mahlzeit) = when (m) {
    Mahlzeit.FRUEHSTUECK -> "Frühstück"
    Mahlzeit.MITTAGESSEN -> "Mittagessen"
    Mahlzeit.ABENDESSEN -> "Abendessen"
    Mahlzeit.SNACK -> "Snack"
}

internal fun gangName(g: Gang) = when (g) {
    Gang.VORSPEISE -> "Vorspeise"
    Gang.HAUPTSPEISE -> "Hauptspeise"
    Gang.NACHSPEISE -> "Nachspeise"
    Gang.BROTZEIT -> "Brotzeit"
}
