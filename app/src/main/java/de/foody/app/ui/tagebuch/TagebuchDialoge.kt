package de.foody.app.ui.tagebuch

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.LaunchedEffect
import de.foody.app.scan.Packung
import de.foody.app.ui.common.PackungScanKnopf
import de.foody.app.ui.common.display
import de.foody.domain.Ingredient
import de.foody.domain.Naehrwerte
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.NutrientProfile
import de.foody.domain.Tagebuch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.app.R
import de.foody.app.data.db.TagebuchEintragEntity
import de.foody.app.data.repo.einordnung
import de.foody.app.ui.common.DecimalField
import de.foody.app.ui.common.DropdownField
import de.foody.app.ui.common.FormColumn
import de.foody.app.ui.common.label
import de.foody.app.ui.common.parseNichtNegativ
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.PlanAuswahl
import de.foody.domain.TagebuchArt
import java.math.BigDecimal
import kotlinx.coroutines.launch

private val HALB = BigDecimal("0.5")
private val MAX_PORTIONEN = BigDecimal("10")

@Composable
private fun chipFarben() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondary,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
)

/** Portionen in halben Schritten von 0,5 bis 10. */
@Composable
internal fun PortionenStepper(wert: BigDecimal, onChange: (BigDecimal) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalIconButton({ onChange((wert - HALB).max(HALB)) }, enabled = wert > HALB) {
            Icon(Icons.Default.Remove, stringResource(R.string.tagebuch_weniger))
        }
        Text(portionenText(wert), style = MaterialTheme.typography.titleSmall)
        FilledTonalIconButton({ onChange((wert + HALB).min(MAX_PORTIONEN)) }, enabled = wert < MAX_PORTIONEN) {
            Icon(Icons.Default.Add, stringResource(R.string.tagebuch_mehr))
        }
    }
}

@Composable
internal fun PortionenDialog(name: String, onDismiss: () -> Unit, onConfirm: (BigDecimal) -> Unit) {
    var portionen by rememberSaveable { mutableStateOf(BigDecimal.ONE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = { PortionenStepper(portionen) { portionen = it } },
        confirmButton = { TextButton({ onConfirm(portionen) }) { Text(stringResource(R.string.tagebuch_gegessen)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private enum class Reiter { REZEPT, ZUTAT, FREI }

/** Hinzufügen zu einer Mahlzeit: aus einem Rezept, einer Katalog-Zutat mit Menge oder frei. */
@Composable
internal fun HinzufuegenDialog(mahlzeit: Mahlzeit, s: TagebuchUiState, vm: TagebuchViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reiter by rememberSaveable { mutableIntStateOf(0) }
    // Rezept
    var rezeptId by rememberSaveable { mutableStateOf<String?>(null) }
    var alle by rememberSaveable { mutableStateOf(false) }
    var suche by rememberSaveable { mutableStateOf("") }
    var portionen by rememberSaveable { mutableStateOf(BigDecimal.ONE) }
    // Zutat
    var zutatId by rememberSaveable { mutableStateOf<String?>(null) }
    var zutatSuche by rememberSaveable { mutableStateOf("") }
    var menge by rememberSaveable { mutableStateOf("") }
    var einheit by rememberSaveable { mutableStateOf(MeasureUnit.GRAM) }
    var keineWerte by rememberSaveable { mutableStateOf(false) }
    // Frei
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var eiweiss by rememberSaveable { mutableStateOf("") }
    var kh by rememberSaveable { mutableStateOf("") }
    var fett by rememberSaveable { mutableStateOf("") }

    val sichtbar = remember(s.rezepte, mahlzeit, alle, suche) {
        PlanAuswahl.filtern(s.rezepte, { it.einordnung() }, mahlzeit, emptySet(), alle)
            .filter { suche.isBlank() || it.name.contains(suche.trim(), ignoreCase = true) }
    }
    val gewaehltesRezept = PlanAuswahl.auswahlBehalten(rezeptId, sichtbar) { it.id }
    val mengeZahl = parseNichtNegativ(menge)?.takeIf { it.signum() > 0 }
    // Leere Felder sind erlaubt (unbekannt), Ungültiges nicht
    fun optOk(text: String) = text.isBlank() || parseNichtNegativ(text) != null
    val vorschau = zutatId?.let { id -> mengeZahl?.let { vm.zutatVorschau(id, it, einheit) } }
    val kcalZahl = kcal.toIntOrNull()?.takeIf { it > 0 }
    // Packungsmodus im Reiter „Frei“: gescannte Werte je 100 g/ml plus gegessene Menge
    var packung by remember { mutableStateOf<Packung?>(null) }
    var pMenge by rememberSaveable { mutableStateOf("") }
    var pKcal by rememberSaveable { mutableStateOf("") }
    var pEiweiss by rememberSaveable { mutableStateOf("") }
    var pKh by rememberSaveable { mutableStateOf("") }
    var pFett by rememberSaveable { mutableStateOf("") }
    var alsZutat by rememberSaveable { mutableStateOf(true) }
    var vorhanden by remember { mutableStateOf<String?>(null) }
    fun kcalJe100(p: Packung) = p.werte[Nutrient.ENERGY_KJ]?.let { Naehrwerte(it, null, null, null, true).kcal }
    fun packungUebernehmen(p: Packung) {
        packung = p
        if (name.isBlank() || p.name != null) name = p.name ?: name
        pKcal = kcalJe100(p)?.toString().orEmpty()
        pEiweiss = p.werte[Nutrient.PROTEIN_G]?.display(1).orEmpty()
        pKh = p.werte[Nutrient.CARBS_G]?.display(1).orEmpty()
        pFett = p.werte[Nutrient.FAT_G]?.display(1).orEmpty()
    }
    /** Die Packung mit den (ggf. korrigierten) Feldern; kcal unverändert → genauer kJ-Wert der Packung bleibt. */
    fun bearbeitetePackung(): Packung? {
        val p = packung ?: return null
        val werte = p.werte.toMutableMap()
        val kcalNeu = pKcal.toIntOrNull()
        if (kcalNeu == null) werte.remove(Nutrient.ENERGY_KJ)
        else if (kcalNeu != kcalJe100(p)) werte[Nutrient.ENERGY_KJ] = BigDecimal(kcalNeu).multiply(TagebuchViewModel.KJ_JE_KCAL)
        listOf(Nutrient.PROTEIN_G to pEiweiss, Nutrient.CARBS_G to pKh, Nutrient.FAT_G to pFett).forEach { (n, t) ->
            parseNichtNegativ(t)?.let { werte[n] = it } ?: werte.remove(n)
        }
        return p.copy(werte = werte)
    }
    val pEinheit = if (packung?.basis == NutrientBasis.PER_100_ML) MeasureUnit.MILLILITER else MeasureUnit.GRAM
    val pMengeZahl = parseNichtNegativ(pMenge)?.takeIf { it.signum() > 0 }
    val pVorschau = bearbeitetePackung()?.let { p ->
        pMengeZahl?.let { m -> Tagebuch.naehrwerteZutat(Ingredient("", name, nutrients = NutrientProfile(p.basis, p.werte)), m, pEinheit) }
    }
    var geprueft by remember { mutableStateOf(false) }
    // Hat der Nutzer das Häkchen selbst gesetzt, gilt seine Wahl – auch wenn die Namensprüfung erst danach fertig wird
    var vomNutzer by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(name, packung) {
        if (packung != null) {
            val neu = vm.zutatMitNamen(name)?.canonicalName
            // Vorschlag nur beim ersten Mal und wenn sich „gibt es schon“ ändert, nicht bei jedem Tastendruck.
            // Vorhandene Zutat nicht still überschreiben: dann ist „aktualisieren“ standardmäßig aus.
            if (!vomNutzer && (!geprueft || neu != vorhanden)) alsZutat = neu == null
            vorhanden = neu
            geprueft = true
        }
    }

    val aktiv = Reiter.entries[reiter]
    // Gegen Doppeltipp: nach dem ersten Tipp gesperrt, bis das Speichern fertig ist
    var sendet by remember { mutableStateOf(false) }
    val kannHinzufuegen = when (aktiv) {
        Reiter.REZEPT -> gewaehltesRezept != null
        Reiter.ZUTAT -> zutatId != null && mengeZahl != null
        Reiter.FREI -> if (packung != null) name.isNotBlank() && pVorschau != null && optOk(pEiweiss) && optOk(pKh) && optOk(pFett)
        else name.isNotBlank() && kcalZahl != null && optOk(eiweiss) && optOk(kh) && optOk(fett)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tagebuch_hinzufuegen_fuer, stringResource(mahlzeit.label()))) },
        text = {
            FormColumn {
                PrimaryTabRow(selectedTabIndex = reiter, containerColor = Color.Transparent) {
                    listOf(R.string.tagebuch_tab_rezept, R.string.tagebuch_tab_zutat, R.string.tagebuch_tab_frei).forEachIndexed { i, t ->
                        Tab(reiter == i, { reiter = i }, text = { Text(stringResource(t)) })
                    }
                }
                when (aktiv) {
                    Reiter.REZEPT -> {
                        OutlinedTextField(suche, { suche = it }, placeholder = { Text(stringResource(R.string.recipe_search)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.planner_alle_rezepte), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(alle, { alle = it })
                        }
                        Auswahlliste(sichtbar.map { it.id to it.name }, gewaehltesRezept) { rezeptId = it }
                        PortionenStepper(portionen) { portionen = it }
                    }
                    Reiter.ZUTAT -> {
                        OutlinedTextField(zutatSuche, { zutatSuche = it; zutatId = null; keineWerte = false },
                            placeholder = { Text(stringResource(R.string.tagebuch_zutat_suchen)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth())
                        val treffer = s.zutaten.filter { zutatSuche.isNotBlank() && it.canonicalName.contains(zutatSuche.trim(), ignoreCase = true) }.take(20)
                        Auswahlliste(treffer.map { it.id to it.canonicalName }, zutatId) { id -> zutatId = id; keineWerte = false }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DecimalField(menge, { menge = it; keineWerte = false }, stringResource(R.string.field_amount), Modifier.weight(1f))
                            DropdownField(stringResource(R.string.field_unit), einheit, MeasureUnit.entries.toList(), { it.symbol },
                                { einheit = it; keineWerte = false }, Modifier.weight(1f))
                        }
                        when {
                            vorschau?.kcal != null -> Text(stringResource(R.string.tagebuch_vorschau, kcalText(vorschau.kcal!!, vorschau.vollstaendig)),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            keineWerte || (zutatId != null && mengeZahl != null) -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.tagebuch_keine_werte), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton({
                                    name = s.zutaten.firstOrNull { it.id == zutatId }?.canonicalName ?: zutatSuche
                                    reiter = Reiter.FREI.ordinal
                                }) { Text(stringResource(R.string.tagebuch_tab_frei)) }
                            }
                        }
                    }
                    Reiter.FREI -> if (packung != null) {
                        Text(stringResource(R.string.scan_uebernommen, packung!!.quelle), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        DecimalField(pMenge, { pMenge = it }, "${stringResource(R.string.field_amount)} (${pEinheit.symbol})")
                        Text(stringResource(if (pEinheit == MeasureUnit.MILLILITER) R.string.tagebuch_je_100ml else R.string.tagebuch_je_100g),
                            style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DecimalField(pKcal, { v -> pKcal = v.filter(Char::isDigit).take(4) }, "kcal", Modifier.weight(1f), markiert = true)
                            DecimalField(pEiweiss, { pEiweiss = it }, stringResource(R.string.tagebuch_eiweiss), Modifier.weight(1f), markiert = pEiweiss.isNotEmpty())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DecimalField(pKh, { pKh = it }, stringResource(R.string.tagebuch_kh), Modifier.weight(1f), markiert = pKh.isNotEmpty())
                            DecimalField(pFett, { pFett = it }, stringResource(R.string.tagebuch_fett), Modifier.weight(1f), markiert = pFett.isNotEmpty())
                        }
                        pVorschau?.kcal?.let {
                            Text(stringResource(R.string.tagebuch_vorschau, kcalText(it, pVorschau.vollstaendig)),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(
                            Modifier.fillMaxWidth().toggleable(alsZutat, role = Role.Checkbox) { alsZutat = it; vomNutzer = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(alsZutat, null)
                            Text(
                                vorhanden?.let { stringResource(R.string.tagebuch_zutat_aktualisieren, it) } ?: stringResource(R.string.tagebuch_als_zutat),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton({ packung = null }) { Text(stringResource(R.string.tagebuch_zurueck_zu_frei)) }
                    } else {
                        PackungScanKnopf(vm.scan, ::packungUebernehmen, onImKatalog = { z ->
                            // Strichcode kennt der Katalog schon: direkt die Zutat mit Menge eintragen
                            zutatId = z.id
                            zutatSuche = z.canonicalName
                            reiter = Reiter.ZUTAT.ordinal
                        })
                        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(kcal, { v -> kcal = v.filter(Char::isDigit).take(5) }, label = { Text("kcal") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DecimalField(eiweiss, { eiweiss = it }, stringResource(R.string.tagebuch_eiweiss), Modifier.weight(1f))
                            DecimalField(kh, { kh = it }, stringResource(R.string.tagebuch_kh), Modifier.weight(1f))
                            DecimalField(fett, { fett = it }, stringResource(R.string.tagebuch_fett), Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = kannHinzufuegen && !sendet, onClick = {
                sendet = true
                when (aktiv) {
                    Reiter.REZEPT -> { vm.rezeptEintragen(mahlzeit, gewaehltesRezept!!, portionen); onDismiss() }
                    Reiter.ZUTAT -> scope.launch {
                        if (vm.zutatEintragen(mahlzeit, zutatId!!, mengeZahl!!, einheit)) onDismiss() else { keineWerte = true; sendet = false }
                    }
                    Reiter.FREI -> if (packung != null) {
                        scope.launch {
                            if (vm.packungEintragen(mahlzeit, name, pMengeZahl!!, pEinheit, bearbeitetePackung()!!, alsZutat)) onDismiss() else sendet = false
                        }
                    } else {
                        vm.freiEintragen(mahlzeit, name, kcalZahl!!, parseNichtNegativ(eiweiss), parseNichtNegativ(kh), parseNichtNegativ(fett))
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Kurze, scrollbare Auswahl (ID, Name) mit hervorgehobenem Treffer. */
@Composable
private fun Auswahlliste(eintraege: List<Pair<String, String>>, gewaehlt: String?, onSelect: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
        items(eintraege, key = { it.first }) { (id, name) ->
            Text(
                name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .background(if (id == gewaehlt) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelect(id) }.padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/** Bearbeiten: Portionen bzw. Menge, bei freien Einträgen Name und kcal; Mahlzeit immer. */
@Composable
internal fun BearbeitenDialog(e: TagebuchEintragEntity, onDismiss: () -> Unit, onSave: (TagebuchEintragEntity) -> Unit) {
    var mahlzeit by rememberSaveable(e.id) { mutableStateOf(Mahlzeit.ausText(e.mahlzeit) ?: Mahlzeit.ABENDESSEN) }
    var portionen by rememberSaveable(e.id) { mutableStateOf(e.portionen ?: BigDecimal.ONE) }
    var menge by rememberSaveable(e.id) { mutableStateOf(e.menge?.stripTrailingZeros()?.toPlainString()?.replace('.', ',').orEmpty()) }
    var name by rememberSaveable(e.id) { mutableStateOf(e.name) }
    val anfangsKcal = e.energieKj?.let { de.foody.domain.Naehrwerte(it, null, null, null, true).kcal }
    var kcal by rememberSaveable(e.id) { mutableStateOf(anfangsKcal?.toString().orEmpty()) }
    val ok = when (e.art) {
        TagebuchArt.REZEPT -> true
        TagebuchArt.ZUTAT -> parseNichtNegativ(menge)?.signum() == 1
        TagebuchArt.FREI -> name.isNotBlank() && (kcal.toIntOrNull() ?: 0) > 0
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.name) },
        text = {
            FormColumn {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mahlzeit.entries.forEach { m ->
                        FilterChip(mahlzeit == m, { mahlzeit = m }, label = { Text(stringResource(m.label())) },
                            shape = RoundedCornerShape(50), colors = chipFarben())
                    }
                }
                when (e.art) {
                    TagebuchArt.REZEPT -> PortionenStepper(portionen) { portionen = it }
                    TagebuchArt.ZUTAT -> DecimalField(menge, { menge = it }, "${stringResource(R.string.field_amount)} (${e.einheit?.symbol.orEmpty()})")
                    TagebuchArt.FREI -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
                        OutlinedTextField(kcal, { v -> kcal = v.filter(Char::isDigit).take(5) }, label = { Text("kcal") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(
                    when (e.art) {
                        TagebuchArt.REZEPT -> e.copy(mahlzeit = mahlzeit.name, portionen = portionen)
                        TagebuchArt.ZUTAT -> e.copy(mahlzeit = mahlzeit.name, menge = parseNichtNegativ(menge))
                        TagebuchArt.FREI -> e.copy(
                            mahlzeit = mahlzeit.name, name = name.trim(),
                            // Nur bei geänderten kcal neu umrechnen, sonst bleibt der genaue kJ-Wert
                            energieKj = if (kcal.toIntOrNull() == anfangsKcal) e.energieKj
                            else BigDecimal(kcal.toInt()).multiply(TagebuchViewModel.KJ_JE_KCAL),
                        )
                    },
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
