package de.foody.feature.zutaten

import androidx.compose.runtime.Composable
import de.foody.core.designsystem.komponenten.FoodyFrage

@Composable
internal fun LoeschenFrage(name: String, onLoeschen: () -> Unit, onAbbrechen: () -> Unit) =
    FoodyFrage("„$name“ löschen?", "Die Zutat wird aus dem Katalog entfernt.", "Löschen", onLoeschen, onAbbrechen)

/** Die Zutat steckt noch in Rezepten: statt löschen zusammenführen. */
@Composable
internal fun InVerwendungDialog(rezepte: Int, onZusammenfuehren: () -> Unit, onAbbrechen: () -> Unit) =
    FoodyFrage(null, inVerwendungText(rezepte), "Zusammenführen …", onZusammenfuehren, onAbbrechen)

/** Der neue Name gehört einer anderen Zutat. */
@Composable
internal fun NameVergebenDialog(name: String, onZusammenfuehren: () -> Unit, onAbbrechen: () -> Unit) =
    FoodyFrage(null, "„$name“ gibt es schon. Diese Zutat damit zusammenführen?", "Zusammenführen", onZusammenfuehren, onAbbrechen)

/** Der gescannte Strichcode gehört schon einer anderen Zutat. */
@Composable
internal fun StrichcodeGehoertDialog(name: String, onZusammenfuehren: () -> Unit, onAbbrechen: () -> Unit) =
    FoodyFrage(null, "Diesen Strichcode hat schon „$name“.", "Zusammenführen …", onZusammenfuehren, onAbbrechen)

@Composable
internal fun ZusammenfuehrenFrage(text: String, onJa: () -> Unit, onNein: () -> Unit) =
    FoodyFrage(null, text, "Zusammenführen", onJa, onNein)
