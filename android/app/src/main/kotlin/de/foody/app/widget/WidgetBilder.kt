package de.foody.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap

/*
 * Gezeichnete Teile der Widgets als Bitmap (RemoteViews können nicht selbst zeichnen): Ring, Balken, Tank (Kreuzer)
 * und der Rautenstreifen (Bayrisch). Maße in dp, gezeichnet in der Dichte des Geräts.
 */

private fun Context.px(dp: Float) = dp * resources.displayMetrics.density

/** Ring mit [anteil] (0 bis 1, darüber voll) auf der [spur]; [breite] ist die Strichstärke. */
internal fun ringBild(context: Context, groesse: Float, anteil: Float, @ColorInt spur: Int, @ColorInt farbe: Int, breite: Float): Bitmap {
    val g = context.px(groesse).toInt().coerceAtLeast(1)
    val b = context.px(breite)
    val bild = createBitmap(g, g)
    val c = Canvas(bild)
    val stift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = b
        strokeCap = Paint.Cap.ROUND
    }
    val rechteck = RectF(b / 2, b / 2, g - b / 2, g - b / 2)
    c.drawArc(rechteck, 0f, 360f, false, stift.apply { color = spur })
    val a = anteil.coerceIn(0f, 1f)
    if (a > 0f) c.drawArc(rechteck, -90f, 360f * a, false, stift.apply { color = farbe })
    return bild
}

/** Waagrechter Balken mit [anteil] in [farbe] auf der [spur], wahlweise mit Rand ([rand]). */
internal fun balkenBild(context: Context, breite: Float, hoehe: Float, anteil: Float, @ColorInt spur: Int, @ColorInt farbe: Int, @ColorInt rand: Int?): Bitmap {
    val w = context.px(breite).toInt().coerceAtLeast(1)
    val h = context.px(hoehe).toInt().coerceAtLeast(1)
    val bild = createBitmap(w, h)
    val c = Canvas(bild)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p.apply { color = spur })
    c.drawRect(0f, 0f, w * anteil.coerceIn(0f, 1f), h.toFloat(), p.apply { color = farbe })
    if (rand != null) {
        val s = context.px(1f)
        c.drawRect(s / 2, s / 2, w - s / 2, h - s / 2, p.apply { color = rand; style = Paint.Style.STROKE; strokeWidth = s })
    }
    return bild
}

/** Tank des Kreuzers: senkrechter Rahmen mit Teilstrichen, von unten zu [anteil] in [farbe] gefüllt. */
internal fun tankBild(context: Context, breite: Float, hoehe: Float, anteil: Float, @ColorInt linie: Int, @ColorInt farbe: Int): Bitmap {
    val w = context.px(breite).toInt().coerceAtLeast(1)
    val h = context.px(hoehe).toInt().coerceAtLeast(1)
    val bild = createBitmap(w, h)
    val c = Canvas(bild)
    val s = context.px(1f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = linie; strokeWidth = s }
    var y = h - context.px(14f)
    while (y > 0) {
        c.drawLine(0f, y, w.toFloat(), y, p)
        y -= context.px(14f)
    }
    val innen = context.px(2f)
    val oben = h - (h - 2 * innen) * anteil.coerceIn(0f, 1f) - innen
    c.drawRect(innen, oben, w - innen, h - innen, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = farbe })
    c.drawRect(s / 2, s / 2, w - s / 2, h - s / 2, p.apply { style = Paint.Style.STROKE })
    return bild
}

/** Streifen aus weiß-blauen Rauten (Bayrisch) mit dunkler Linie unten, [breite] × [hoehe]. */
internal fun rautenBild(context: Context, breite: Float, hoehe: Float, @ColorInt blau: Int, @ColorInt linie: Int): Bitmap {
    val w = context.px(breite).toInt().coerceAtLeast(1)
    val h = context.px(hoehe).toInt().coerceAtLeast(1)
    val bild = createBitmap(w, h)
    val c = Canvas(bild)
    c.drawColor(0xFFFFFFFF.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blau }
    val raute = h.toFloat()
    var x = 0f
    var i = 0
    while (x < w) {
        if (i % 2 == 0) {
            c.drawPath(
                Path().apply {
                    moveTo(x + raute / 2, 0f)
                    lineTo(x + raute, h / 2f)
                    lineTo(x + raute / 2, h.toFloat())
                    lineTo(x, h / 2f)
                    close()
                },
                p,
            )
        }
        x += raute / 2
        i++
    }
    val s = context.px(2f)
    c.drawRect(0f, h - s, w.toFloat(), h.toFloat(), Paint().apply { color = linie })
    return bild
}
