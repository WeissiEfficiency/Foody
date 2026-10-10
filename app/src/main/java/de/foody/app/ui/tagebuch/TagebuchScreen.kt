package de.foody.app.ui.tagebuch

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.TagebuchEintragEntity
import de.foody.app.ui.common.HeaderAction
import de.foody.app.ui.common.ScreenHeader
import de.foody.app.ui.common.display
import de.foody.app.ui.common.label
import de.foody.app.ui.common.pretty
import de.foody.app.ui.theme.FoodyGlass
import de.foody.domain.Mahlzeit
import de.foody.domain.Summe
import de.foody.domain.TagebuchArt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Reihenfolge im Tag wie im Planer. */
private val ABSCHNITTE = listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.SNACK, Mahlzeit.ABENDESSEN)

/** kcal mit Tausenderpunkt; „≥“ vor unvollständigen Summen. */
internal fun kcalText(kcal: Int, vollstaendig: Boolean = true) =
    (if (vollstaendig) "" else "≥ ") + String.format(Locale.GERMANY, "%,d", kcal)

@Composable
fun TagebuchScreen(vm: TagebuchViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var hinzufuegen by rememberSaveable { mutableStateOf<String?>(null) }
    var bearbeiten by remember { mutableStateOf<TagebuchEintragEntity?>(null) }
    var portionenFuer by remember { mutableStateOf<MealSlotEntity?>(null) }

    Column {
        ScreenHeader(stringResource(R.string.tagebuch_eyebrow), s.tag.pretty()) {
            HeaderAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.tagebuch_vortag)) { vm.verschiebe(-1) }
            HeaderAction(Icons.Outlined.Today, stringResource(R.string.planner_today)) { vm.heute() }
            HeaderAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.tagebuch_folgetag)) { vm.verschiebe(1) }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WochenLeiste(s.woche, s.ziel, s.tag, vm::zeigeTag)
            TagesSumme(s.bilanz.tag, s.ziel)
            if (s.eintraege.isEmpty() && s.vorschlaege.isEmpty() && !s.loading) {
                Text(stringResource(R.string.tagebuch_leer), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ABSCHNITTE.forEach { m ->
                MahlzeitAbschnitt(
                    mahlzeit = m,
                    summe = s.bilanz.jeMahlzeit[m],
                    eintraege = s.eintraege[m].orEmpty(),
                    vorschlaege = s.vorschlaege[m].orEmpty(),
                    onGegessen = { vm.gegessen(it) },
                    onPortionen = { portionenFuer = it },
                    onBearbeiten = { bearbeiten = it },
                    onLoeschen = { vm.loeschen(it.id) },
                    onHinzufuegen = { hinzufuegen = m.name },
                )
            }
        }
    }

    hinzufuegen?.let { name ->
        HinzufuegenDialog(Mahlzeit.valueOf(name), s, vm, onDismiss = { hinzufuegen = null })
    }
    bearbeiten?.let { e ->
        BearbeitenDialog(e, onDismiss = { bearbeiten = null }) { vm.bearbeiten(it); bearbeiten = null }
    }
    portionenFuer?.let { slot ->
        PortionenDialog(s.vorschlaege.values.flatten().firstOrNull { it.first.id == slot.id }?.second?.name.orEmpty(),
            onDismiss = { portionenFuer = null }) { p -> vm.gegessen(slot, p); portionenFuer = null }
    }
}

/** Sieben Balken bis zum gewählten Tag; Tagesziel als gestrichelte Linie, über dem Ziel korallenrot. */
@Composable
private fun WochenLeiste(woche: List<Pair<LocalDate, Int>>, ziel: Int?, gewaehlt: LocalDate, onTag: (LocalDate) -> Unit) {
    if (woche.isEmpty()) return
    val skala = maxOf(woche.maxOf { it.second }, ziel ?: 0, 1).toFloat()
    val primary = MaterialTheme.colorScheme.primary
    val ueber = MaterialTheme.colorScheme.tertiary
    val linie = MaterialTheme.colorScheme.outline
    val barHoehe = 72.dp
    Surface(shape = MaterialTheme.shapes.medium, color = FoodyGlass.fill, border = FoodyGlass.border, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).height(barHoehe + 24.dp).drawBehind {
                ziel?.let {
                    val y = barHoehe.toPx() * (1 - it / skala)
                    drawLine(linie, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            woche.forEach { (tag, kcal) ->
                val farbe = if (ziel != null && kcal > ziel) ueber else primary
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).clickable { onTag(tag) }
                        .alpha(if (tag == gewaehlt) 1f else 0.55f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(barHoehe).width(18.dp), contentAlignment = Alignment.BottomCenter) {
                        if (kcal > 0) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(kcal / skala).clip(RoundedCornerShape(6.dp)).background(farbe))
                        }
                    }
                    Text(
                        tag.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMANY).take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tag == gewaehlt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagesSumme(summe: Summe, ziel: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val text = if (ziel != null) {
            stringResource(R.string.tagebuch_summe_ziel, kcalText(summe.kcal, summe.vollstaendig), kcalText(ziel))
        } else {
            stringResource(R.string.tagebuch_summe, kcalText(summe.kcal, summe.vollstaendig))
        }
        Text(text, style = MaterialTheme.typography.titleLarge)
        if (ziel != null) {
            LinearProgressIndicator(
                progress = { (summe.kcal.toFloat() / ziel).coerceAtMost(1f) },
                color = if (summe.kcal > ziel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.tagebuch_makros, summe.eiweiss, summe.kohlenhydrate, summe.fett),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MahlzeitAbschnitt(
    mahlzeit: Mahlzeit,
    summe: Summe?,
    eintraege: List<TagebuchEintragEntity>,
    vorschlaege: List<Pair<MealSlotEntity, RecipeEntity>>,
    onGegessen: (MealSlotEntity) -> Unit,
    onPortionen: (MealSlotEntity) -> Unit,
    onBearbeiten: (TagebuchEintragEntity) -> Unit,
    onLoeschen: (TagebuchEintragEntity) -> Unit,
    onHinzufuegen: () -> Unit,
) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(mahlzeit.label()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            summe?.let {
                Text(stringResource(R.string.tagebuch_summe, kcalText(it.kcal, it.vollstaendig)),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onHinzufuegen) { Icon(Icons.Default.Add, stringResource(R.string.tagebuch_hinzufuegen_fuer, stringResource(mahlzeit.label()))) }
        }
        vorschlaege.forEach { (slot, rezept) ->
            Surface(
                shape = MaterialTheme.shapes.medium, color = FoodyGlass.fill, border = FoodyGlass.border,
                modifier = Modifier.fillMaxWidth().alpha(0.75f).combinedClickable(onClick = {}, onLongClick = { onPortionen(slot) }),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tagebuch_geplant, rezept.name), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    FilledTonalButton({ onGegessen(slot) }, shape = RoundedCornerShape(50)) { Text(stringResource(R.string.tagebuch_gegessen)) }
                }
            }
        }
        eintraege.forEach { e -> EintragZeile(e, onBearbeiten, onLoeschen) }
    }
}

@Composable
private fun EintragZeile(e: TagebuchEintragEntity, onBearbeiten: (TagebuchEintragEntity) -> Unit, onLoeschen: (TagebuchEintragEntity) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = { onBearbeiten(e) }, shape = MaterialTheme.shapes.medium, color = FoodyGlass.fill, border = FoodyGlass.border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                detail(e)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val kcal = e.energieKj?.let { de.foody.domain.Naehrwerte(it, null, null, null, true).kcal }
            Text(
                if (kcal == null) "–" else stringResource(R.string.tagebuch_summe, kcalText(kcal, e.vollstaendig)),
                style = MaterialTheme.typography.labelLarge,
            )
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.tagebuch_bearbeiten)) }, { menu = false; onBearbeiten(e) })
                    DropdownMenuItem({ Text(stringResource(R.string.action_delete)) }, { menu = false; onLoeschen(e) })
                }
            }
        }
    }
}

@Composable
private fun detail(e: TagebuchEintragEntity): String? = when (e.art) {
    TagebuchArt.REZEPT -> e.portionen?.let { portionenText(it) }
    TagebuchArt.ZUTAT -> e.menge?.let { m -> "${m.display()} ${e.einheit?.symbol.orEmpty()}".trim() }
    TagebuchArt.FREI -> null
}

@Composable
internal fun portionenText(p: BigDecimal): String {
    val ganz = p.stripTrailingZeros().scale() <= 0 && p.toInt() == 1
    return pluralStringResource(R.plurals.tagebuch_portionen, if (ganz) 1 else 2, p.display(1))
}
