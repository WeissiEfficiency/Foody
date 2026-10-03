package de.foody.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.foody.app.R

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ServingsStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val desc = pluralStringResource(R.plurals.servings_count, value, value)
    // Pillenform mit runden Plus/Minus-Knöpfen
    Row(
        modifier.semantics { contentDescription = desc }
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = { if (value > 1) onChange(value - 1) },
            enabled = value > 1,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(Icons.Default.Remove, stringResource(R.string.action_less_servings))
        }
        Text(
            pluralStringResource(R.plurals.servings_count, value, value),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        FilledIconButton(onClick = { onChange(value + 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, stringResource(R.string.action_more_servings))
        }
    }
}

@Composable
fun DecimalField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val invalid = value.isNotBlank() && parseDecimal(value) == null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.error_number)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(optionLabel(it)) }, onClick = { onSelect(it); expanded = false })
            }
        }
    }
}

/** Freitext mit Vorschlägen aus bekannten Einträgen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AutocompleteField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(text, options) {
        options.filter { optionLabel(it).contains(text.trim(), ignoreCase = true) }.take(8)
    }
    ExposedDropdownMenuBox(expanded && filtered.isNotEmpty(), { expanded = it }, modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(it); expanded = true },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded && filtered.isNotEmpty(), { expanded = false }) {
            filtered.forEach {
                DropdownMenuItem(text = { Text(optionLabel(it)) }, onClick = { onSelect(it); expanded = false })
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun FormColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
