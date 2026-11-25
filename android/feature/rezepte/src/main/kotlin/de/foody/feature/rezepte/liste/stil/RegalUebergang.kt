package de.foody.feature.rezepte.liste.stil

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.stil.grauen.Grauen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Übergang der Bibliothek des Kosmischen Grauens ins Rezept (Runde 2, Buch ausbauen, Idee 2): Ein Tipp auf einen Band,
 * und er wird ein Stück aus dem Regal gezogen und kippt an; dann holt er sich heran, bis sein Einband den Schirm füllt,
 * und der Deckel schlägt nach links auf. Aus der dunklen ersten Seite taucht das Rezept auf (Übergang im NavHost).
 * Ohne Animationen öffnet das Rezept sofort; kehrt man zurück, steht der Band wieder im Regal.
 */

/** Dauer des Übergangs in Millisekunden. */
internal const val REGAL_MS = 1000

/** Die erste Seite unter dem Deckel, wie das Papier des Buchs. */
private val ERSTE_SEITE = Color(0xFF16201C)
private val GOLD = Color(0xFFB59A5A)

/**
 * Ein Augenblick des Übergangs: wie weit der Band aus dem Regal gezogen ist ([zug], 0 bis 1, mit Kippen), wie weit
 * er sich herangeholt hat ([heran], 0 = im Regal, 1 = schirmfüllend) und wie weit der Deckel offen ist ([deckel]).
 */
internal data class RegalStand(val zug: Float, val heran: Float, val deckel: Float)

/** Der Stand bei [t] (0 bis 1): bis 0,25 herausziehen, 0,2 bis 0,7 heranholen, ab 0,68 aufschlagen. */
internal fun regalStand(t: Float): RegalStand = RegalStand(
    zug = glatt((t / 0.25f).coerceIn(0f, 1f)),
    heran = glatt(((t - 0.2f) / 0.5f).coerceIn(0f, 1f)),
    deckel = glatt(((t - 0.68f) / 0.32f).coerceIn(0f, 1f)),
)

private fun glatt(x: Float) = x * x * (3f - 2f * x)

/** Zustand des Übergangs: welcher Band fliegt, wo er stand, welche Farbe sein Einband hat. */
@Stable
internal class RegalUebergang(private val bereich: CoroutineScope, private val bewegt: Boolean) {
    internal val zug = Animatable(0f)

    /** Der Band, der gerade aus dem Regal kommt; `null` = keiner. */
    var band by mutableStateOf<String?>(null)
        private set
    var lage by mutableStateOf<Rect?>(null)
        private set
    var einband by mutableStateOf(Color.Unspecified)
        private set
    var titel by mutableStateOf("")
        private set

    /**
     * Zieht den Band [id] ([titel] steht auf dem Deckel) an [lage] (im Fenster) heraus und ruft danach [ziel]; läuft
     * schon einer, zählt der Tipp nicht.
     */
    fun oeffnen(id: String, titel: String, lage: Rect?, einband: Color, ziel: () -> Unit) {
        if (!bewegt || lage == null) {
            ziel()
            return
        }
        if (zug.isRunning || zug.value > 0f) return
        band = id
        this.lage = lage
        this.einband = einband
        this.titel = titel
        bereich.launch {
            zug.animateTo(1f, tween(REGAL_MS, easing = LinearEasing))
            ziel()
        }
    }

    fun zuruecksetzen() {
        bereich.launch {
            zug.snapTo(0f)
            band = null
        }
    }
}

/** Merkt sich den Übergang und stellt den Band zurück ins Regal, sobald die Bibliothek wieder vorn ist. */
@Composable
internal fun rememberRegalUebergang(): RegalUebergang {
    val bereich = rememberCoroutineScope()
    val bewegt = animationenErlaubt()
    val u = remember(bewegt) { RegalUebergang(bereich, bewegt) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { u.zuruecksetzen() }
    return u
}

/**
 * Die Ebene über der Bibliothek, auf der der Band heranfliegt; [wurzel] ist ihre Lage im Fenster. Gelesen wird der
 * Übergang erst beim Zeichnen. Für TalkBack stumm.
 */
@Composable
internal fun RegalEbene(u: RegalUebergang, wurzel: () -> Offset, modifier: Modifier) {
    val schrift = rememberTextMeasurer()
    Spacer(
        modifier.clearAndSetSemantics { }.drawBehind {
            val t = u.zug.value
            val lage = u.lage ?: return@drawBehind
            if (t <= 0f) return@drawBehind
            regalBlick(regalStand(t), lage.translate(-wurzel()), u.einband, u.titel, schrift)
        },
    )
}

/** Zeichnet den Band im Stand [s]: herausgezogen und gekippt, herangeholt, dann mit aufschlagendem Deckel. */
private fun DrawScope.regalBlick(s: RegalStand, lage: Rect, einband: Color, titel: String, schrift: TextMeasurer) {
    // Herausziehen: ein Fünftel seiner Höhe nach oben, um die untere linke Ecke gekippt
    val gezogen = lage.translate(0f, -0.2f * lage.height * s.zug)
    val ziel = Rect(Offset.Zero, size)
    val rahmen = lerp(gezogen, ziel, s.heran)
    val kippen = -8f * s.zug * (1f - s.heran)
    if (s.deckel > 0f) drawRect(ERSTE_SEITE, rahmen.topLeft, rahmen.size)
    rotate(kippen, Offset(rahmen.left, rahmen.bottom)) {
        // Der Deckel schlägt nach links auf: Er wird um den Rücken (linke Kante) schmaler und dunkler
        val breite = rahmen.width * (1f - s.deckel)
        if (breite <= 0.5f) return@rotate
        val deckel = Rect(rahmen.topLeft, Size(breite, rahmen.height))
        val farbe = lerp(einband, Color.Black, 0.6f * s.deckel)
        drawRect(farbe, deckel.topLeft, deckel.size)
        // Goldener Rahmen und Bünde, mit dem Band wachsend
        val rand = (4.dp.toPx() + 18.dp.toPx() * s.heran).coerceAtMost(deckel.width / 4f)
        val innen = Rect(deckel.left + rand, deckel.top + rand, deckel.right - rand, deckel.bottom - rand)
        if (innen.width > 0f && innen.height > 0f) {
            drawRect(GOLD.copy(alpha = 0.7f * (1f - s.deckel)), innen.topLeft, innen.size, style = Stroke(1.5.dp.toPx()))
        }
        drawRect(Color.Black.copy(alpha = 0.45f), deckel.topLeft, deckel.size, style = Stroke(1.dp.toPx()))
        // Der Titel in Gold, sobald der Deckel groß genug ist; er geht mit dem Deckel auf
        val sichtbar = ((s.heran - 0.4f) / 0.4f).coerceIn(0f, 1f) * (1f - s.deckel)
        if (sichtbar > 0f && innen.width > 40.dp.toPx()) {
            val text = schrift.measure(
                titel, Grauen.name(30f).copy(color = GOLD, textAlign = TextAlign.Center),
                constraints = Constraints(maxWidth = (innen.width * 0.8f).toInt()), maxLines = 4,
            )
            drawText(text, topLeft = Offset(innen.center.x - text.size.width / 2f, innen.top + innen.height * 0.3f), alpha = sichtbar)
        }
    }
}
