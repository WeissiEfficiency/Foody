package de.foody.feature.rezepte.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Abschnittskopf
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.RezeptPlatzhalter
import de.foody.core.designsystem.token.GlasStufe
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.domain.Quantity
import de.foody.domain.QuantityFormatter
import de.foody.feature.rezepte.rezeptBild

@Composable
internal fun DetailInhalt(z: DetailZustand.Da, a: DetailAktionen) {
    val r = z.voll.rezept
    Kopfbild(r.name, listOfNotNull(zeit(r.prepMinutes, r.cookMinutes), kcalJePortion(z.voll)).joinToString(" · "), r.imageUri, a.onZurueck)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (r.archivedAt != null) {
            GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dieses Rezept ist archiviert.", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                    FoodyKnopf("Wiederherstellen", { a.onArchivieren(false) }, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
                }
            }
        }
        Aktionen(z, a)
        Portionen(z.portionen, a.onPortionen)
        Abschnittskopf("Zutaten")
        z.voll.zeilen.forEach { zeile ->
            val menge = zeile.menge.takeIf { it.signum() > 0 }?.let { m ->
                val skaliert = mengeSkaliert(m, r.defaultServings, z.portionen)
                QuantityFormatter.format(Quantity.of(skaliert, zeile.einheit), zeile.einheit)
            }.orEmpty()
            Row {
                Text(menge, Modifier.width(96.dp), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                Column(Modifier.weight(1f)) {
                    Text(zeile.name + if (zeile.optional) " (optional)" else "", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                    zeile.hinweis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext) }
                }
            }
        }
        if (z.voll.schritte.isNotEmpty()) {
            Abschnittskopf("Zubereitung")
            z.voll.schritte.forEachIndexed { i, s ->
                Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
            }
        }
        r.notes?.let {
            Abschnittskopf("Notizen")
            Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.text)
        }
        Einordnungszeile(z.voll.einordnung)
        r.sourceUrl?.let { Quelle(it) }
        Naehrwerttabelle(z.voll, z.portionen)
    }
}

@Composable
private fun Kopfbild(name: String, meta: String, bild: String?, onZurueck: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(260.dp)) {
        rezeptBild(bild)?.invoke() ?: RezeptPlatzhalter(Modifier.fillMaxSize())
        // Glaskreis hinter dem Pfeil, damit er auch auf dunklen Fotos lesbar bleibt
        GlasFlaeche(GlasStufe.BILD, Modifier.padding(8.dp), form = CircleShape) {
            IconButton(onClick = onZurueck) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = FoodyTheme.farben.text)
            }
        }
        GlasFlaeche(GlasStufe.BILD, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(name, style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            }
        }
    }
}

@Composable
private fun Aktionen(z: DetailZustand.Da, a: DetailAktionen) {
    val fav = z.voll.rezept.favorite
    Row(verticalAlignment = Alignment.CenterVertically) {
        FoodyKnopf("Kochen", { a.onKochen(z.portionen) }, Modifier.weight(1f))
        IconButton(onClick = { a.onFavorit(!fav) }) {
            Icon(
                if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (fav) "Favorit entfernen" else "Als Favorit merken", tint = FoodyTheme.farben.primaer,
            )
        }
        IconButton(onClick = a.onBearbeiten) { Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten", tint = FoodyTheme.farben.text) }
        IconButton(onClick = a.onDuplizieren) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplizieren", tint = FoodyTheme.farben.text) }
        IconButton(onClick = { a.onEinkauf(z.portionen) }) {
            Icon(Icons.Outlined.AddShoppingCart, contentDescription = "Auf die Einkaufsliste", tint = FoodyTheme.farben.text)
        }
        if (z.voll.rezept.archivedAt == null) {
            IconButton(onClick = { a.onArchivieren(true) }) { Icon(Icons.Outlined.Archive, contentDescription = "Archivieren", tint = FoodyTheme.farben.text) }
        }
    }
}

@Composable
private fun Portionen(n: Int, onSchritt: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSchritt(false) }) { Icon(Icons.Outlined.Remove, contentDescription = "Weniger Portionen", tint = FoodyTheme.farben.text) }
        Text(if (n == 1) "1 Portion" else "$n Portionen", style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text)
        IconButton(onClick = { onSchritt(true) }) { Icon(Icons.Outlined.Add, contentDescription = "Mehr Portionen", tint = FoodyTheme.farben.text) }
    }
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
    Text(
        "Quelle: $url", Modifier.clickable { runCatching { uri.openUri(url) } },
        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline), color = FoodyTheme.farben.primaer,
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
