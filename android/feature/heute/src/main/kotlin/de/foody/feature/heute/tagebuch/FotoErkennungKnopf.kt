package de.foody.feature.heute.tagebuch

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.fotos.KameraDatei
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.erkennung.ErkennungLauf
import de.foody.core.erkennung.ErkennungTexte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * „Foto erkennen“ neben „Packung scannen“: Menü mit Kamera und Galerie, während der Erkennung ein Fortschritt statt des
 * Symbols. Der Ablauf liegt in [lauf] (im ViewModel); Fortschritt und Meldung zeigt [FotoErkennungStand].
 */
@Composable
internal fun FotoErkennungKnopf(lauf: ErkennungLauf, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val zustand by lauf.zustand.collectAsStateWithLifecycle()
    var menue by remember { mutableStateOf(false) }

    // Beim Aufbau gelesen (wie beim Scan-Knopf): Nach Drehen oder Prozessende kommt das Kameraergebnis vor den Effekten.
    // Läuft schon eine Erkennung (nach einem Prozessende neu gestartet), bleibt ihr Foto ebenfalls liegen.
    val behalten = remember { lauf.zustand.value.fotoPfad }
    val laeuftSchon = remember { lauf.zustand.value.laeuft }
    LaunchedEffect(Unit) {
        if (!laeuftSchon) {
            withContext(Dispatchers.IO) { mahlzeitOrdner(context).listFiles()?.filter { it.path != behalten }?.forEach { it.delete() } }
        }
    }

    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val datei = lauf.kameraZurueck()?.let(::File)
        when {
            datei == null -> Unit
            !ok -> datei.delete()
            else -> lauf.erkennen(Uri.fromFile(datei).toString(), loeschen = datei.path)
        }
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) lauf.erkennen(uri.toString())
    }

    fun kameraStarten() {
        val datei = KameraDatei.neu(context, KameraDatei.MAHLZEIT, praefix = "mahlzeit-")
        lauf.kameraGestartet(datei.path)
        try {
            kamera.launch(KameraDatei.uri(context, datei))
        } catch (_: ActivityNotFoundException) {
            // Keine Kamera-App: bleibt die Galerie
            lauf.kameraZurueck()
        }
    }

    Box(modifier) {
        FotoMenueKnopf(aktiv = !zustand.laeuft, laeuft = zustand.laeuft) { menue = true }
        DropdownMenu(menue, onDismissRequest = { menue = false }) {
            DropdownMenuItem({ Text(ErkennungTexte.KAMERA) }, {
                menue = false
                kameraStarten()
            })
            DropdownMenuItem({ Text(ErkennungTexte.GALERIE) }, {
                menue = false
                galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
    }
}

/** Unter den Knöpfen: während der Erkennung „Erkennt das Foto …“ mit „Abbrechen“, danach die Meldung (falls eine). */
@Composable
internal fun FotoErkennungStand(lauf: ErkennungLauf) {
    val zustand by lauf.zustand.collectAsStateWithLifecycle()
    val farben = FoodyTheme.farben
    when {
        zustand.laeuft -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ErkennungTexte.LAEUFT, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = farben.nebentext)
            FoodyKnopf(ErkennungTexte.ABBRECHEN, lauf::abbrechen, art = KnopfArt.TEXT)
        }
        zustand.meldung != null -> Text(zustand.meldung.orEmpty(), style = MaterialTheme.typography.bodySmall, color = farben.nebentext)
    }
}

/** Umriss-Knopf „Foto erkennen“ mit Kamera-Symbol, wie „Packung scannen“; während der Erkennung ein Fortschritt. */
@Composable
private fun FotoMenueKnopf(aktiv: Boolean, laeuft: Boolean, onKlick: () -> Unit) {
    val farben = FoodyTheme.farben
    OutlinedButton(
        onClick = onKlick,
        modifier = Modifier.heightIn(min = 48.dp),
        enabled = aktiv,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (aktiv) farben.primaer else farben.nebentext.copy(alpha = 0.38f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = farben.primaer),
    ) {
        if (laeuft) {
            CircularProgressIndicator(Modifier.size(18.dp), color = farben.primaer, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(ErkennungTexte.KNOPF, Modifier.padding(start = 8.dp))
    }
}

/** `cache/mahlzeit/`: höchstens ein Kamerafoto zur Zeit, nach der Erkennung gelöscht. */
private fun mahlzeitOrdner(context: Context): File = KameraDatei.ordner(context, KameraDatei.MAHLZEIT)
