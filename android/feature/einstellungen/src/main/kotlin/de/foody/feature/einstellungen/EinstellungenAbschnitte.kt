package de.foody.feature.einstellungen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.foody.core.data.Darstellung
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.zeilenImStil
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAkzent
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.stilText
import de.foody.core.designsystem.token.Farbwelt
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe

/**
 * Ein Abschnitt der Seite: Glasfläche mit Augenbraue. In den Stilen R–V (Immersiv) der Rahmen des Stils ([StilRahmen])
 * mit dem Wort des Stils als Überschrift; [ohneKopfImStil] lässt sie dort weg wie im Entwurf, [sync] = Karte des Abgleichs.
 */
@Composable
internal fun Abschnitt(
    titel: String,
    sage: String? = null,
    anime: String? = null,
    edo: String? = null,
    bayrisch: String? = null,
    grauen: String? = null,
    kreuzer: String? = null,
    ohneKopfImStil: Boolean = false,
    sync: Boolean = false,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    val wort = stilText(titel, sage = sage, anime = anime, edo = edo, bayrisch = bayrisch, grauen = grauen, kreuzer = kreuzer)
    if (zeilenImStil()) {
        // TalkBack liest das normale Wort, auch wenn der Stil Versalien zeigt
        StilRahmen(wort.takeUnless { ohneKopfImStil }, titel, sync, inhalt)
        return
    }
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth(), form = FoodyTheme.formen.gross) {
        Column(Modifier.fillMaxWidth().padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
            Augenbraue(wort, if (wort != titel) Modifier.semantics { contentDescription = titel } else Modifier)
            inhalt()
        }
    }
}

@Composable
private fun Klein(text: String) = Text(
    text,
    style = if (zeilenImStil()) zeilenSchrift().second.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize) else MaterialTheme.typography.bodyMedium,
    color = FoodyTheme.farben.nebentext,
)

/** Text in normaler Größe: im Stil in der Schrift seiner Zeilen. */
@Composable
private fun Satz(text: String, farbe: Color = FoodyTheme.farben.text) =
    Text(text, style = if (zeilenImStil()) zeilenSchrift().first else MaterialTheme.typography.bodyLarge, color = farbe)

/** Schalter-Zeile: im Stil [SchalterZeile], sonst Text mit Material-Schalter. */
@Composable
private fun Schalter(titel: String, info: String, an: Boolean, onWechsel: (Boolean) -> Unit) {
    if (zeilenImStil()) {
        SchalterZeile(titel, info, an, onWechsel)
        return
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(an, role = Role.Switch, onValueChange = onWechsel),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
            Klein(info)
        }
        Switch(an, onCheckedChange = null)
    }
}

/** Höchstens so viele nicht übertragene Einträge zeigt „Konto“ einzeln. */
private const val MAX_PROBLEME = 5

/** Konto: Status, Tipp öffnet „Server verbinden“; darunter, was nicht vollständig übertragen wurde. */
@Composable
internal fun KontoAbschnitt(konto: VerbindungsZustand?, jetzt: Long, onServer: () -> Unit) = Abschnitt(
    "Konto", sage = "Botendienst", anime = "Sync", edo = "Abgleich mit dem Server", bayrisch = "Mit’m Server abgleicha",
    grauen = "Verbindung zum fernen Archiv", kreuzer = "Verbindung zur Basis", sync = true,
) {
    val verbunden = konto?.takeIf { it.verbunden }
    if (zeilenImStil()) {
        val kreuzer = FoodyTheme.stil.stil == FoodyStil.KREUZER
        val titel = when {
            verbunden == null -> "Nicht verbunden"
            kreuzer -> "VERBUNDEN · ${verbunden.adresse.orEmpty().uppercase()}"
            else -> "Verbunden mit ${verbunden.adresse.orEmpty()}"
        }
        VerweisZeile(
            titel,
            if (verbunden != null) "Zuletzt synchronisiert: ${zuletztText(verbunden.letzterSync, jetzt)}" else "Server verbinden",
            onServer,
            anfang = if (verbunden != null) ({ VerbundenMarke() }) else null,
        )
    } else Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onServer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (verbunden != null) "Verbunden mit ${verbunden.adresse.orEmpty()}" else "Nicht verbunden",
                style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text,
            )
            Klein(if (verbunden != null) "Zuletzt synchronisiert: ${zuletztText(verbunden.letzterSync, jetzt)}" else "Server verbinden")
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = FoodyTheme.farben.text)
    }
    val probleme = verbunden?.nichtUebertragen.orEmpty()
    if (probleme.isNotEmpty()) {
        Satz(nichtUebertragenTitel(probleme.size), MaterialTheme.colorScheme.error)
        probleme.take(MAX_PROBLEME).forEach { Klein(problemText(it.typ, it.name, it.code)) }
        if (probleme.size > MAX_PROBLEME) Klein("… und ${probleme.size - MAX_PROBLEME} weitere")
    }
}

/**
 * Darstellung: zuerst der Stil. Bei Milchglas folgen Farbwelt und Modus wie bisher; bei einem anderen Stil die
 * Variante und dessen vier Akzente. Den Modus gibt es dort nicht, weil jeder Stil einen festen Grund hat.
 */
@Composable
internal fun DarstellungAbschnitt(
    z: EinstellungenZustand,
    onWahl: (Darstellung) -> Unit,
    onFarbwelt: (Farbwelt) -> Unit,
    onStil: (FoodyStil) -> Unit,
    onVariante: (StilVariante) -> Unit,
    onAkzent: (StilAkzent) -> Unit,
) = Abschnitt(
    "Darstellung", sage = "Gestalt", edo = "Aussehen", bayrisch = "Wia’s ausschaut", grauen = "Der Schein", kreuzer = "Anzeige",
) {
    if (zeilenImStil()) {
        Unterkopf("Stil")
        StilWahl(z.stil, z.variante, onStil)
        Unterkopf("Variante")
        Segmente(StilVariante.entries.map { it to it.anzeigename }, z.variante, onVariante)
        Klein("Mit Muster, Verlauf und kräftigen Kanten.")
        z.stil.akzent(z.akzent)?.let { akzent ->
            Unterkopf("Farbe")
            AkzentReihe(z.stil.akzente, akzent, onAkzent)
        }
        return@Abschnitt
    }
    Text("Stil", style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.text)
    StilWahl(z.stil, z.variante, onStil)
    val akzent = z.stil.akzent(z.akzent)
    if (akzent == null) {
        Text("Farbe", style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.text, modifier = Modifier.padding(top = FoodyAbstaende.s1))
        FarbweltWahl(z.farbwelt, onFarbwelt)
        Klein("Gilt für Hauptfarbe, Verlauf und Glas.")
        Text("Modus", style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.text, modifier = Modifier.padding(top = FoodyAbstaende.s1))
        Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            listOf(Darstellung.SYSTEM to "System", Darstellung.HELL to "Hell", Darstellung.DUNKEL to "Dunkel").forEach { (d, text) ->
                FoodyChip(text, ausgewaehlt = d == z.darstellung, onKlick = { onWahl(d) })
            }
        }
    } else {
        Text("Variante", style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.text, modifier = Modifier.padding(top = FoodyAbstaende.s1))
        Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            StilVariante.entries.forEach { v -> FoodyChip(v.anzeigename, ausgewaehlt = v == z.variante, onKlick = { onVariante(v) }) }
        }
        Klein(
            if (z.variante == StilVariante.IMMERSIV) {
                "Mit Muster, Verlauf und kräftigen Kanten."
            } else {
                "Farben und Schrift des Stils, schlicht."
            },
        )
        Text("Farbe", style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.text, modifier = Modifier.padding(top = FoodyAbstaende.s1))
        AkzentWahl(z.stil, akzent, onAkzent)
    }
}

/** Tagesziel als Zahlenfeld; [fehler] zeigt „500 bis 10.000 kcal“ am Feld. */
@Composable
internal fun TageszielAbschnitt(text: String, fehler: Boolean, onFertig: () -> Unit, onText: (String) -> Unit) = Abschnitt(
    "Tagesziel",
    sage = "Vitalität · Tagesziel",
    ohneKopfImStil = FoodyTheme.stil.stil != FoodyStil.ANIME,
) {
    if (zeilenImStil()) {
        ZielStil(text, fehler, onText, onFertig)
        return@Abschnitt
    }
    OutlinedTextField(
        text, onText, Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onFertig() },
        label = { Text("Tagesziel") },
        suffix = { Text("kcal") },
        singleLine = true,
        isError = fehler,
        supportingText = { Text(if (fehler) "500 bis 10.000 kcal" else "Leer lassen = kein Ziel") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onFertig() }),
    )
}

/** Zutatenkatalog: ganze Zeile mit Anzahl und „›“; darunter der Schalter „Online-Produktsuche“ (ganze Zeile tippbar). */
@Composable
internal fun ZutatenAbschnitt(anzahl: Int?, onlineSuche: Boolean, onZutaten: () -> Unit, onOnlineSuche: (Boolean) -> Unit) = Abschnitt(
    "Zutaten",
    sage = "Wissen",
    ohneKopfImStil = FoodyTheme.stil.stil.let { it != FoodyStil.ANIME && it != FoodyStil.EDO },
) {
    if (zeilenImStil()) {
        VerweisZeile(
            stilText("Zutatenkatalog", grauen = "Verzeichnis der Substanzen", kreuzer = "Ressourcen-Datenbank"),
            anzahl?.let { if (FoodyTheme.stil.stil == FoodyStil.KREUZER) "$it EINTRÄGE" else "$it Zutaten" },
            onZutaten,
        )
    } else Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onZutaten),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Zutatenkatalog", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        anzahl?.let { Klein(it.toString()) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = FoodyTheme.farben.text)
    }
    Schalter("Online-Produktsuche", "Open Food Facts; gesendet wird nur die Produktnummer", onlineSuche, onOnlineSuche)
    // CC BY 4.0 verlangt die Nennung des Herausgebers (ADR 0018)
    Klein("Nährwerte zum Nachschlagen: Max Rubner-Institut (2025), Bundeslebensmittelschlüssel (BLS) 4.0, Lizenz CC BY 4.0")
}

/**
 * Fotoerkennung: Schalter „Foto erkennen“ (nur dieses Gerät), Stand des Modells, installieren bzw. ersetzen und
 * entfernen. Ohne Modell erscheint der Foto-Knopf im Tagebuch nicht.
 */
@Composable
internal fun FotoErkennungAbschnitt(
    z: EinstellungenZustand,
    onSchalter: (Boolean) -> Unit,
    onInstallieren: () -> Unit,
    onEntfernen: () -> Unit,
) = Abschnitt("Fotoerkennung", sage = "Die Seherin") {
    Schalter("Foto erkennen", "Läuft nur auf diesem Gerät; das Foto verlässt es nicht.", z.fotoErkennung, onSchalter)
    val bytes = z.modellBytes
    Satz(if (bytes != null) "Modell installiert · ${groesseText(bytes)}" else "Kein Modell installiert")
    if (bytes == null) Klein("Die beiden Modelldateien (Sprachteil und mmproj) einmal wählen, z. B. aus OpenCloud.")
    FoodyKnopf(
        when {
            z.laeuft == Aktion.MODELL_INSTALLIEREN -> "Läuft …"
            bytes != null -> "Modell ersetzen"
            else -> "Modell installieren"
        },
        onInstallieren, Modifier.fillMaxWidth(), art = if (bytes != null) KnopfArt.UMRISS else KnopfArt.PRIMAER, aktiv = z.laeuft == null,
    )
    if (bytes != null) {
        FoodyKnopf(
            if (z.laeuft == Aktion.MODELL_ENTFERNEN) "Läuft …" else "Modell entfernen", onEntfernen, Modifier.fillMaxWidth(),
            art = KnopfArt.UMRISS, aktiv = z.laeuft == null,
        )
    }
}

@Composable
internal fun SicherungAbschnitt(z: EinstellungenZustand, onErstellen: () -> Unit, onWiederherstellen: () -> Unit) = Abschnitt(
    "Sicherung",
    sage = "Abschrift des Lagers",
) {
    Klein("Rezepte, Zutaten, Plan, Einkauf, Tagebuch und Fotos als ZIP-Datei.")
    FoodyKnopf(
        if (z.laeuft == Aktion.ERSTELLEN) "Läuft …" else "Sicherung erstellen", onErstellen, Modifier.fillMaxWidth(),
        aktiv = z.laeuft == null,
    )
    FoodyKnopf(
        if (z.laeuft == Aktion.WIEDERHERSTELLEN) "Läuft …" else "Wiederherstellen", onWiederherstellen, Modifier.fillMaxWidth(),
        art = KnopfArt.UMRISS, aktiv = z.laeuft == null && !z.gesperrt,
    )
    if (z.gesperrt) Klein(ERST_TRENNEN)
}

@Composable
internal fun DatenAbschnitt(z: EinstellungenZustand, onLoeschen: () -> Unit) = Abschnitt("Daten") {
    FoodyKnopf(
        if (z.laeuft == Aktion.LOESCHEN) "Läuft …" else "Alle Daten löschen", onLoeschen, Modifier.fillMaxWidth(),
        art = KnopfArt.UMRISS, aktiv = z.laeuft == null && !z.gesperrt,
    )
    if (z.gesperrt) Klein(ERST_TRENNEN)
}

@Composable
internal fun HinweiseAbschnitt() = Abschnitt("Hinweise") {
    Klein("Nährwerte sind rechnerische Näherungswerte und ersetzen keine ernährungsmedizinische Beratung.")
    Klein("Ohne verbundenen Server bleiben alle Daten auf diesem Gerät. Mit einem eigenen Server werden sie nur dorthin übertragen.")
}
