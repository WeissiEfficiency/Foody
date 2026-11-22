package de.foody.app.navigation

import android.content.Intent
import androidx.navigation.NavHostController
import de.foody.feature.heute.tagebuch.EintragenStart
import de.foody.feature.heute.tagebuch.SofortStart
import java.time.LocalDate

/** Tipp auf ein Homescreen-Widget: womit Eintragen beginnt ([sofort] `null` = mit Erfassen). */
enum class WidgetStart(val sofort: SofortStart?) {
    /** Widget „Scannen“ ([SCAN_LINK]): gleich der Strichcode-Scan. */
    SCANNEN(SofortStart.SCANNEN),

    /** Widget „KI-Foto“ ([FOTO_LINK]): gleich die Kamera für die Fotoerkennung. */
    FOTO(SofortStart.FOTO),

    /** Widget „Schnell eintragen“ und dessen Knopf im Heute-Widget ([EINTRAGEN_LINK]): Erfassen (Foto, Scannen, Suche). */
    EINTRAGEN(null),
}

/** Der Link eines Widgets ([SCAN_LINK], [FOTO_LINK], [EINTRAGEN_LINK]) als [WidgetStart]; andere Links → `null`. */
fun Intent.widgetStart(): WidgetStart? = when (data?.toString()) {
    SCAN_LINK -> WidgetStart.SCANNEN
    FOTO_LINK -> WidgetStart.FOTO
    EINTRAGEN_LINK -> WidgetStart.EINTRAGEN
    else -> null
}

/**
 * Tipp auf ein Widget, beim Kaltstart wie bei laufender App: zurück zu „Heute“ wie ein Tipp auf den Tab (eine offene
 * Seite Eintragen geht dabei zu), darauf Eintragen für heute. Jeder Tipp öffnet eine neue Seite (neuer Auftrag), also
 * auch beim zweiten Tipp einen neuen Scan bzw. ein neues Foto; Zurück führt nach „Heute“.
 */
fun NavHostController.widgetStartOeffnen(start: WidgetStart) {
    popBackStack<Heute>(inclusive = false)
    navigate(EintragenStart(LocalDate.now(), null, start.sofort).alsZiel())
}
