package de.foody.feature.einkauf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Reiterleiste
import de.foody.core.designsystem.komponenten.RunderKnopf
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.stil.stilText
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten

/** Die Blätter, die in den Stilen Q–V Reiter der Listen-Seite sind, in der Reihenfolge der Entwürfe. */
internal val LISTEN_REITER: List<Blatt> = listOf(Blatt.Vorschau, Blatt.Abgleich, Blatt.Frueher)

/**
 * Listen wie in den Entwürfen der Stile Q–V: eine eigene Seite mit Zurück und den Reitern „Neue Liste · Abgleich ·
 * Frühere“ (Edo, Bayrisch, Grauen, Kreuzer „Vorschau“ statt „Neue Liste“). [inhalt] ist das jeweilige Blatt
 * ([VorschauBlatt], [AbgleichBlatt], [FruehereBlatt]), das hier ohne Blatt und Titel erscheint. Ohne Zeitraum (Liste
 * nicht aus dem Plan) erklärt der Reiter „Abgleich“, warum es nichts abzugleichen gibt. Mit [onLoeschen] (eine Liste
 * wird angezeigt, auch eine frühere) steht rechts im Kopf, auf jedem Reiter, der Papierkorb „Diese Liste löschen“; ein
 * Menü ⋮ wie bei Milchglas gibt es in Q–V nicht.
 */
@Composable
internal fun ListenSeite(
    blatt: Blatt,
    mitZeitraum: Boolean,
    onReiter: (Blatt) -> Unit,
    onZurueck: () -> Unit,
    onLoeschen: (() -> Unit)?,
    inhalt: @Composable () -> Unit,
) {
    BackHandler(onBack = onZurueck)
    val immersiv = FoodyTheme.stil.inszeniert
    // Grauen und Kreuzer: Überzeile wie im Entwurf (Immersiv)
    val ueberzeile = if (immersiv) stilText("", grauen = "Das Archiv der Expeditionen", kreuzer = "Fracht · Dock 7").ifEmpty { null } else null
    Column(Modifier.fillMaxSize().widthIn(max = FoodyBreiten.inhalt)) {
        Column(
            Modifier.fillMaxWidth().padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            val titel = stilText(
                "Einkaufslisten", sage = "Listen des Händlers", anime = "Listen!", bayrisch = "Eikaufszettl", grauen = "Listen", kreuzer = "Ladelisten",
            )
            Seitenkopf(
                titel,
                ueberzeile = ueberzeile,
                onZurueck = onZurueck,
                beschreibung = "Einkaufslisten",
            ) {
                if (onLoeschen != null) RunderKnopf(Icons.Outlined.Delete, "Diese Liste löschen", onLoeschen)
            }
            val vorschau = stilText("Neue Liste", edo = "Vorschau", bayrisch = "Vorschau", grauen = "Vorschau", kreuzer = "Vorschau")
            Reiterleiste(
                listOf(vorschau, "Abgleich", "Frühere"),
                LISTEN_REITER.indexOf(blatt),
                { i -> LISTEN_REITER[i].takeIf { it != blatt }?.let(onReiter) },
                beschreibungen = listOf("Neue Liste", "Abgleich", "Frühere Listen"),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (blatt == Blatt.Abgleich && !mitZeitraum) {
                Text(
                    stilText(
                        "Diese Liste ist nicht aus dem Plan entstanden; abgleichen lassen sich nur Listen aus dem Plan.",
                        sage = "Diese Liste entstand nicht aus der Karte; abgleichen lassen sich nur Listen aus der Karte.",
                    ),
                    Modifier.padding(FoodyAbstaende.s4),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FoodyTheme.farben.nebentext,
                )
            } else {
                CompositionLocalProvider(LocalAufListenSeite provides true) { inhalt() }
            }
        }
    }
}
