package de.foody.feature.heute.stil

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.foody.core.designsystem.animationenErlaubt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Übergang des Kosmischen Grauens von „Heute“ ins Energietagebuch (Runde 2, Übergänge): Ein Tipp aufs Auge, und es
 * reißt sich auf – das Lid weitet sich, die Pupille wird rund –, dann stürzt der Blick auf den Betrachter zu, bis die
 * Pupille den Schirm füllt; aus ihrem Dunkel taucht das Tagebuch auf (Übergang im NavHost). Ohne Animationen öffnet das
 * Tagebuch sofort. Kehrt man zurück, liegt das Auge wieder ruhig da.
 */

/** Dauer des Blicks in Millisekunden. */
internal const val BLICK_MS = 900

/** Das Dunkel der Pupille, in das der Blick fällt. */
internal val PUPILLE = Color(0xFF020403)

/** Ein Augenblick des Übergangs: Weite des Lids (1 = wie sonst), Breite der Pupille (Entwurfseinheiten), Zoom, Dunkel. */
internal data class Blick(val lid: Float, val pupille: Float, val zoom: Float, val dunkel: Float)

/**
 * Der Stand des Übergangs bei [t] (0 bis 1): Bis 0,35 öffnet sich das Auge (Lid ×1,3, Pupille 18 → 48), ab 0,25 stürzt
 * es heran (beschleunigt bis ×16), ab 0,65 fällt das Dunkel bis ganz schwarz.
 */
internal fun blickStand(t: Float): Blick {
    val auf = glatt((t / 0.35f).coerceIn(0f, 1f))
    val sturz = ((t - 0.25f) / 0.75f).coerceIn(0f, 1f)
    return Blick(
        lid = 1f + 0.3f * auf,
        pupille = 18f + 30f * auf,
        zoom = 1f + 15f * sturz * sturz * sturz,
        dunkel = glatt(((t - 0.65f) / 0.3f).coerceIn(0f, 1f)),
    )
}

private fun glatt(x: Float) = x * x * (3f - 2f * x)

/** Zustand des Übergangs auf „Heute“: wo das Auge liegt und wie weit der Blick ist. */
@Stable
internal class AugenUebergang(private val bereich: CoroutineScope, private val bewegt: Boolean) {
    internal val blick = Animatable(0f)

    /** Lage des Auges im Fenster (aus [merken]); ohne sie öffnet das Tagebuch sofort. */
    var lage by mutableStateOf<Rect?>(null)
        private set

    fun merken(k: LayoutCoordinates) {
        lage = Rect(k.positionInRoot(), k.size.toSize())
    }

    /** Startet den Blick und ruft danach [ziel]; läuft er schon, zählt der Tipp nicht. */
    fun oeffnen(ziel: () -> Unit) {
        if (!bewegt || lage == null) {
            ziel()
            return
        }
        if (blick.isRunning || blick.value > 0f) return
        bereich.launch {
            blick.animateTo(1f, tween(BLICK_MS, easing = LinearEasing))
            ziel()
        }
    }

    fun zuruecksetzen() {
        bereich.launch { blick.snapTo(0f) }
    }
}

/** Merkt sich den Übergang über Neuaufbauten und setzt ihn zurück, sobald „Heute“ wieder vorn ist. */
@Composable
internal fun rememberAugenUebergang(): AugenUebergang {
    val bereich = rememberCoroutineScope()
    val bewegt = animationenErlaubt()
    val u = remember(bewegt) { AugenUebergang(bereich, bewegt) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { u.zuruecksetzen() }
    return u
}

/**
 * Die Ebene über „Heute“, auf der das Auge heranstürzt; [wurzel] ist die Lage der Ebene im Fenster. Gelesen wird der
 * Blick erst beim Zeichnen: Die Seite setzt sich dafür nicht neu zusammen. Für TalkBack stumm.
 */
@Composable
internal fun AugenBlickEbene(u: AugenUebergang, anteil: Float?, glut: Color, wurzel: () -> Offset, modifier: Modifier, feder: Boolean = false) {
    val pfade = remember { AugenPfade() }
    Spacer(
        modifier.clearAndSetSemantics { }.drawBehind {
            val t = u.blick.value
            val lage = u.lage ?: return@drawBehind
            if (t <= 0f) return@drawBehind
            augenBlick(blickStand(t), lage.translate(-wurzel()), pfade, anteil, glut, feder)
        },
    )
}

/** Zeichnet das Auge an [lage] im Stand [b]: um die Pupille gezoomt, darüber das Dunkel. */
internal fun DrawScope.augenBlick(b: Blick, lage: Rect, pfade: AugenPfade, anteil: Float?, glut: Color, feder: Boolean = false) {
    val s = lage.width / AUGE_BREITE
    val mitte = Offset(lage.left + 150f * s, lage.top + 85f * s)
    withTransform({
        translate(mitte.x, mitte.y)
        scale(s * b.zoom, s * b.zoom, Offset.Zero)
        translate(-150f, -85f)
    }) {
        augeMalen(pfade, anteil, glut, lid = b.lid, pupille = b.pupille, feder = feder)
    }
    if (b.dunkel > 0f) drawRect(PUPILLE.copy(alpha = b.dunkel))
}

/**
 * Das Auge im Maß des Entwurfs (300 × 170): Schein, Lid mit Adern, Iris, Ring mit dem Anteil in der Glut, Pupille,
 * Glanzpunkt. [lid] streckt das Lid senkrecht (Blinzeln < 1, Aufreißen > 1), [pupille] ist ihre Breite. Mit [feder]
 * (Necronomicon) dieselbe Form als Federzeichnung in Tinte, der Ring in [glut] (dort Blut).
 */
internal fun DrawScope.augeMalen(pfade: AugenPfade, anteil: Float?, glut: Color, lid: Float, pupille: Float = 18f, feder: Boolean = false) {
    if (feder) {
        augeFeder(pfade, anteil, glut, lid, pupille)
        return
    }
    val schein = Brush.radialGradient(listOf(glut.copy(alpha = 0.35f), glut.copy(alpha = 0f)), Offset(150f, 85f), 150f)
    val iris = Brush.radialGradient(
        0f to Color(0xFF1B3A2C), 0.7f to Color(0xFF0E2019), 1f to Color(0xFF06100C),
        center = Offset(150f, 85f), radius = 56f,
    )
    drawOval(schein, Offset.Zero, Size(300f, 170f))
    withTransform({ scale(1f, lid, Offset(150f, 85f)) }) {
        drawPath(pfade.lid, Color(0xFFC9C4A8).copy(alpha = 0.12f))
        drawPath(pfade.lid, Color(0xFF7F948A), style = Stroke(1.5f))
        clipPath(pfade.lid) {
            drawPath(pfade.adern, Color(0xFF6A2430).copy(alpha = 0.45f), style = Stroke(1f))
            drawCircle(iris, 56f, Offset(150f, 85f))
            drawCircle(Color(0xFF24382F), 50f, Offset(150f, 85f), style = Stroke(8f))
            if (anteil != null && anteil > 0f) {
                drawArc(glut, -90f, 360f * anteil, false, Offset(100f, 35f), Size(100f, 100f), style = Stroke(8f))
            }
            drawOval(PUPILLE, Offset(150f - pupille / 2f, 51f), Size(pupille, 68f))
            drawCircle(Color.White.copy(alpha = 0.5f), 5f, Offset(166f, 66f))
        }
    }
}

private val TINTE = Color(0xFF17110B)

/**
 * Das Auge des Necronomicon als Federzeichnung: Lid in Tinte mit Wimpern, Schraffur unter dem oberen Lid, Adern in
 * [blut], Iris aus feinen Strahlen, Ring mit dem Anteil in [blut], Pupille als Schlitz. Kein Schein: auf Pergament
 * leuchtet nichts.
 */
internal fun DrawScope.augeFeder(pfade: AugenPfade, anteil: Float?, blut: Color, lid: Float, pupille: Float) {
    val mitte = Offset(150f, 85f)
    withTransform({ scale(1f, lid, mitte) }) {
        clipPath(pfade.lid) {
            // Schatten unter dem oberen Lid: schräge Schraffur, nicht über der Iris
            val iris = Path().apply { addOval(Rect(mitte, 56f)) }
            clipPath(iris, ClipOp.Difference) {
                var x = -40f
                while (x < 320f) {
                    drawLine(TINTE.copy(alpha = 0.28f), Offset(x, 20f), Offset(x + 26f, 58f), 0.9f)
                    x += 6f
                }
            }
            drawPath(pfade.adern, blut.copy(alpha = 0.55f), style = Stroke(1f))
            // Iris: Strahlen von der Pupille nach außen, dazu ein feiner Rand
            for (i in 0 until 40) {
                val w = (i / 40f) * 2f * PI.toFloat()
                val innen = 20f + (i % 3) * 2f
                drawLine(
                    TINTE.copy(alpha = 0.55f),
                    Offset(mitte.x + innen * cos(w), mitte.y + innen * sin(w)),
                    Offset(mitte.x + 44f * cos(w), mitte.y + 44f * sin(w)),
                    0.9f,
                )
            }
            drawCircle(TINTE.copy(alpha = 0.18f), 50f, mitte, style = Stroke(7f))
            if (anteil != null && anteil > 0f) {
                drawArc(blut, -90f, 360f * anteil, false, Offset(100f, 35f), Size(100f, 100f), style = Stroke(7f))
            }
            drawCircle(TINTE, 56f, mitte, style = Stroke(1.4f))
            drawOval(TINTE, Offset(150f - pupille / 2f, 51f), Size(pupille, 68f))
            drawCircle(Color(0xFFF1E6CA), 4.5f, Offset(166f, 66f))
        }
        // Lid zweimal nachgezogen wie mit der Feder, oben Wimpern
        drawPath(pfade.lid, TINTE, style = Stroke(2.2f))
        drawPath(pfade.lid, TINTE.copy(alpha = 0.4f), style = Stroke(1f), alpha = 1f)
        for (i in 2..16) {
            val t = i / 18f
            val p = bezier(t, Offset(10f, 85f), Offset(60f, 15f), Offset(240f, 15f), Offset(290f, 85f))
            val q = bezier(t + 0.01f, Offset(10f, 85f), Offset(60f, 15f), Offset(240f, 15f), Offset(290f, 85f))
            val richtung = Offset(q.y - p.y, -(q.x - p.x)).let { it / it.getDistance() }
            drawLine(TINTE, p, p + richtung * 9f + Offset((t - 0.5f) * 6f, 0f), 1.3f, StrokeCap.Round)
        }
    }
}

/** Punkt bei [t] auf der kubischen Bézierkurve [a]–[d]. */
private fun bezier(t: Float, a: Offset, b: Offset, c: Offset, d: Offset): Offset {
    val u = 1f - t
    return a * (u * u * u) + b * (3f * u * u * t) + c * (3f * u * t * t) + d * (t * t * t)
}
