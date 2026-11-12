package de.foody.feature.rezepte.detail.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.animeSchatten
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber
import de.foody.domain.Nutrient
import de.foody.domain.NutritionResult
import de.foody.feature.rezepte.detail.DetailZustand
import de.foody.feature.rezepte.detail.mahlzeitName
import de.foody.feature.rezepte.detail.naehrwertZeilen
import de.foody.feature.rezepte.detail.zeit
import java.util.Locale
import kotlin.math.min

/*
 * Die Titelblöcke der Rezept-Szenen R–V: je Stil Bild oder Zeichen, Einordnung, Name (für TalkBack die Überschrift)
 * und Angaben. Große Titel wachsen mit der Systemschrift nur bis 130 %.
 */

/** Ganze kcal je Portion („540“); `null` ohne Energiewerte. */
internal fun kcalZahl(z: DetailZustand.Da): String? =
    z.voll.naehrwerte.perServing(Nutrient.ENERGY_KJ)?.let { ganzzahlDeutsch(NutritionResult.kcal(it)) }

/** Minuten des Rezepts („45“); `null` ohne Zeit. */
internal fun minuten(z: DetailZustand.Da): Int? =
    listOfNotNull(z.voll.rezept.prepMinutes, z.voll.rezept.cookMinutes).takeIf { it.isNotEmpty() }?.sum()

private fun anfang(z: DetailZustand.Da) = z.voll.rezept.name.trim().firstOrNull()?.uppercase() ?: "?"

/** Titelgröße, die mit der Systemschrift nur bis 130 % wächst. */
@Composable
private fun begrenzt(groesse: Float): Float {
    val k = LocalDensity.current.fontScale
    return groesse * min(k, 1.3f) / k
}

/** R: Comic-Panel mit Speedlines und Anfangsbuchstabe, Pille mit Einordnung, schräger Name, Werte als Abzeichen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnimeHeld(z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    val r = z.voll.rezept
    val form = RoundedCornerShape(20.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().padding(end = 5.dp, bottom = 5.dp).height(150.dp).animeSchatten(form, f.text, 5.dp)
                .clip(form).background(FoodyTheme.stil.zier).border(2.5.dp, f.text, form).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val mitte = Offset(size.width / 2, size.height / 2)
                for (i in 0 until 16) {
                    val w = Math.toRadians(i * 22.5).toFloat()
                    val ende = Offset(mitte.x + kotlin.math.cos(w) * size.width, mitte.y + kotlin.math.sin(w) * size.width)
                    drawLine(Color.White.copy(alpha = 0.3f), mitte, ende, 3.dp.toPx(), StrokeCap.Round)
                }
            }
            Text(anfang(z), style = Anime.titel(72f, 0f), color = f.text)
        }
        val pille = listOfNotNull(if (r.favorite) "★ FAVORIT" else null, einordnungGross(z)).joinToString(" · ")
        if (pille.isNotEmpty()) {
            Text(
                pille, Modifier.background(f.text, Anime.pille).padding(horizontal = 10.dp, vertical = 3.dp),
                style = Anime.text(12f, FontWeight.ExtraBold), color = Color.White,
            )
        }
        Text(
            r.name, Modifier.semantics { heading() },
            style = Anime.titel(begrenzt(26f), 0f).copy(textGeometricTransform = TextGeometricTransform(skewX = -0.1f)), color = f.text,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            minuten(z)?.let { Abzeichen("ZEIT", "$it′", Anime.gelb) }
            kcalZahl(z)?.let { Abzeichen("KCAL/PORT.", it, Color.White) }
        }
    }
}

@Composable
private fun Abzeichen(name: String, wert: String, grund: Color) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(12.dp)
    Column(
        Modifier.padding(end = 3.dp, bottom = 3.dp).animeSchatten(form, f.text, 3.dp).background(grund, form).border(2.dp, f.text, form)
            .semantics(mergeDescendants = true) { }.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(name, style = Anime.text(11f, FontWeight.ExtraBold), color = f.text)
        Text(wert, style = Anime.titel(20f, 0f), color = f.text)
    }
}

/** S: Einordnung, Name und Angaben auf Papier, rechts ein Stempel in Zinnober mit dem Anfangsbuchstaben. */
@Composable
internal fun EdoHeld(z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    val r = z.voll.rezept
    val shu = edoShu()
    Row(
        Modifier.fillMaxWidth().background(Edo.papier, Edo.form).border(1.dp, Edo.papierRand, Edo.form).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            einordnungGross(z)?.let { Text(it, style = Edo.kopf(), color = f.nebentext) }
            Text(r.name, Modifier.semantics { heading() }, style = Edo.titel(begrenzt(26f)), color = f.text)
            val angaben = listOfNotNull(angabenZeile(z), if (r.favorite) "Favorit" else null).joinToString(" · ")
            if (angaben.isNotEmpty()) Text(angaben, style = Edo.text(14f), color = f.nebentext)
        }
        Box(
            Modifier.size(56.dp).rotate(-4f).background(shu, Edo.form).border(2.dp, Color.White.copy(alpha = 0.5f), Edo.form)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) { Text(anfang(z), style = Edo.titel(26f), color = Color.White) }
    }
}

/** T: Rautenband in Weiß-Blau, Art und Herz, Name und Angaben. */
@Composable
internal fun BayrischHeld(z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    val r = z.voll.rezept
    val blau = bayrischBlau()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp).clearAndSetSemantics { }) {
            val b = size.height
            var x = 0f
            var i = 0
            while (x < size.width) {
                val raute = Path().apply {
                    moveTo(x + b / 2, 0f)
                    lineTo(x + b, b / 2)
                    lineTo(x + b / 2, b)
                    lineTo(x, b / 2)
                    close()
                }
                drawPath(raute, if (i % 2 == 0) blau else Color.White)
                x += b * 0.75f
                i++
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (einordnungGross(z) ?: "AUS DA KUCHL"), Modifier.weight(1f),
                style = Bayrisch.text(13f, FontWeight.Bold), color = f.nebentext,
            )
            if (r.favorite) Text("♥", Modifier.clearAndSetSemantics { }, style = Bayrisch.titel(22f), color = Color(0xFFB8322A))
        }
        Text(r.name, Modifier.semantics { heading() }, style = Bayrisch.titel(begrenzt(28f)), color = f.text)
        angabenZeile(z)?.let { Text(it, style = Bayrisch.text(15f), color = f.nebentext) }
    }
}

private val PERGAMENT = Color(0xFFD9CDAE)
private val TINTE = Color(0xFF2A2418)
private val TINTE_NEBEN = Color(0xFF4A4030)

/** U: eine Pergamentseite, die im Dunkel glüht: Band, Name, Angaben. */
@Composable
internal fun GrauenHeld(z: DetailZustand.Da) {
    val glut = FoodyTheme.stil.zier
    val r = z.voll.rezept
    val form = RoundedCornerShape(2.dp)
    Column(
        Modifier.fillMaxWidth().shadow(18.dp, form, ambientColor = glut, spotColor = glut).background(PERGAMENT, form)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val band = listOfNotNull(einordnungZeile(z), if (r.favorite) "♦ Favorit" else null).joinToString(" · ")
        if (band.isNotEmpty()) Text(band, style = Grauen.text(14f, kursiv = true), color = TINTE_NEBEN)
        Text(r.name, Modifier.semantics { heading() }, style = Grauen.titel(begrenzt(26f)), color = TINTE)
        angabenZeile(z)?.let { Text(it, style = Grauen.text(15f), color = TINTE_NEBEN) }
    }
}

/** V: Karte mit abgeschnittenen Ecken in Amber: Sektor, Name, FLUGZEIT · ENERGIE · EIWEISS. */
@Composable
internal fun KreuzerHeld(z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    val r = z.voll.rezept
    val amber = kreuzerAmber()
    val form = Kreuzer.ecke()
    Column(
        Modifier.fillMaxWidth().background(amber.copy(alpha = 0.07f), form).border(1.dp, amber.copy(alpha = 0.45f), form)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(kreuzerSektor(z), style = Kreuzer.mono(11.5f, 0.1f), color = f.primaer)
        Text(r.name, Modifier.semantics { heading() }, style = Kreuzer.titel(begrenzt(24f)), color = f.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kennwert("FLUGZEIT", zeit(r.prepMinutes, r.cookMinutes) ?: "–", Modifier.weight(1f))
            Kennwert("ENERGIE", kcalZahl(z)?.let { "$it kcal" } ?: "–", Modifier.weight(1f))
            Kennwert("EIWEISS", naehrwertZeilen(z.voll, 1).firstOrNull { it.name == "Eiweiß" }?.jePortion ?: "–", Modifier.weight(1f))
        }
    }
}

/** „SYSTEM · ABENDESSEN · FAVORIT“: die Kopfzeile der Karte. */
internal fun kreuzerSektor(z: DetailZustand.Da): String =
    listOfNotNull(
        "SYSTEM", z.voll.einordnung.mahlzeiten.minByOrNull { it.ordinal }?.let(::mahlzeitName), if (z.voll.rezept.favorite) "FAVORIT" else null,
    ).joinToString(" · ").uppercase(Locale.GERMAN)

@Composable
private fun Kennwert(name: String, wert: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Text(name, style = Kreuzer.mono(10f, 0.12f), color = FoodyTheme.farben.nebentext)
        Text(wert, style = Kreuzer.mono(14f), color = FoodyTheme.stil.zier)
    }
}
