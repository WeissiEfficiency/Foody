package de.foody.app.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.foody.app.R
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.ui.theme.EyebrowStyle
import de.foody.domain.StepTimerParser
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch

/**
 * Schritt-für-Schritt-Kochmodus: ein Arbeitsschritt pro Seite, große Schrift, darunter die Zutaten,
 * die in diesem Schritt erwähnt werden – mit der Menge für die gewählte Portionenzahl.
 * Erkannte Zeitangaben („20 Minuten“) lassen sich als Timer starten.
 * Das Display bleibt an, solange der Modus offen ist.
 */
@Composable
fun CookModeDialog(
    recipeName: String,
    steps: List<InstructionStepEntity>,
    stepLines: List<List<StepLine>>,
    onClose: () -> Unit,
) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        val pager = rememberPagerState { steps.size }
        val scope = rememberCoroutineScope()
        val running = rememberRunningTimers()
        val stepTimers = remember(steps) { steps.map { StepTimerParser.find(it.text) } }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) }
                    Text(recipeName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                }
                LinearProgressIndicator(
                    progress = { (pager.currentPage + 1f) / steps.size.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(6.dp).clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    drawStopIndicator = {},
                )
                HorizontalPager(pager, Modifier.weight(1f)) { page ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            stringResource(R.string.step_of, page + 1, steps.size).uppercase(),
                            style = EyebrowStyle, color = MaterialTheme.colorScheme.primary,
                        )
                        Text(steps[page].text, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 34.sp))
                        StepTimerChips(stepTimers.getOrNull(page).orEmpty()) { t ->
                            running += RunningTimer(t.label, System.currentTimeMillis() + t.duration.toMillis())
                        }
                        val lines = stepLines.getOrNull(page).orEmpty()
                        if (lines.isNotEmpty()) {
                            Column(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(stringResource(R.string.cook_mode_you_need).uppercase(), style = EyebrowStyle,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                lines.forEach { (l, stepAmount) ->
                                    Row {
                                        Column(Modifier.weight(0.4f)) {
                                            // Im Schritt genannte Teilmenge zuerst, Gesamtmenge als Orientierung darunter
                                            Text(stepAmount ?: l.amountText ?: stringResource(R.string.amount_as_needed),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            if (stepAmount != null && l.amountText != null) {
                                                Text(stringResource(R.string.cook_mode_of_total, l.amountText),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                            }
                                        }
                                        Text(l.name, style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
                RunningTimersBar(running)
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val first = pager.currentPage == 0
                    val last = pager.currentPage == steps.lastIndex
                    OutlinedButton(
                        { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        enabled = !first, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(50),
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cook_mode_previous)) }
                    Button(
                        { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier.weight(2f).height(56.dp), shape = RoundedCornerShape(50),
                    ) {
                        Text(stringResource(if (last) R.string.cook_mode_done else R.string.cook_mode_next), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(8.dp))
                        Icon(if (last) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }
    }
}

