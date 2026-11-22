package de.foody.feature.heute.tagebuch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasBlatt
import de.foody.core.designsystem.komponenten.BlattTitel
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.Reiterleiste
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.komponenten.WahlZeile
import de.foody.core.designsystem.komponenten.anzeigename
import de.foody.core.designsystem.komponenten.platzFuerLeiste
import de.foody.core.designsystem.komponenten.slotName
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.stilText
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.core.scan.PackungScanKnopf
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.Schnellstart
import kotlinx.coroutines.launch

private val WEGE = listOf(
    EintragenWeg.REZEPT to "Rezept",
    EintragenWeg.ZUTAT to "Zutat",
    EintragenWeg.PLAN to "Aus dem Plan",
    EintragenWeg.FREI to "Frei",
)

/** Wege in der Düsteren Sage (Immersiv). */
private val SAGE_WEGE = mapOf(EintragenWeg.REZEPT to "Eigenes Rezept", EintragenWeg.PLAN to "Vorgesehen", EintragenWeg.FREI to "Frei")

/** Kurzname einer Mahlzeit in der Düsteren Sage wie im Entwurf („Morgen“, „Zwischen“). */
private fun Mahlzeit.sageKurz(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Morgen"
    Mahlzeit.MITTAGESSEN -> "Mittag"
    Mahlzeit.SNACK -> "Zwischen"
    Mahlzeit.ABENDESSEN -> "Abend"
}

/** Reiter beim Eintragen in den Stilen Q–V, wie in den Entwürfen. */
enum class EintragenReiter { SUCHEN, SCANNEN, FOTO, GEPLANT }

/** Bayrisch, Grauen und Kreuzer zeigen Eintragen in den Entwürfen als eigene Seite, Sage, Anime und Edo als Blatt. */
private val ALS_SEITE = setOf(FoodyStil.BAYRISCH, FoodyStil.GRAUEN, FoodyStil.KREUZER)

/** Eintragen als eigene Seite statt Blatt: Der Gastgeber blendet dann seinen Inhalt aus. */
@Composable
fun eintragenAlsSeite(): Boolean = FoodyTheme.stil.stil in ALS_SEITE

/** Erster Reiter nach dem Schnellstart des Widgets. */
private fun startReiter(start: Schnellstart.Art?) = when (start) {
    Schnellstart.Art.SCANNEN -> EintragenReiter.SCANNEN
    Schnellstart.Art.FOTO -> EintragenReiter.FOTO
    else -> EintragenReiter.SUCHEN
}

/** Schnellstart des Widgets als Sofort-Start; „Schnell eintragen“ beginnt normal. */
private fun Schnellstart.Art?.alsSofort(): SofortStart? = when (this) {
    Schnellstart.Art.FOTO -> SofortStart.FOTO
    Schnellstart.Art.SCANNEN -> SofortStart.SCANNEN
    else -> null
}

/**
 * Eintragen (siehe [BlattAuftrag]); schließt nach erfolgreichem Speichern. Milchglas: Blatt von unten mit den Wegen
 * als Chips. Stile Q–V wie in den Entwürfen mit den Reitern „Suchen · Scannen · Foto · Geplant“ ([EintragenReiter]);
 * Bayrisch, Grauen und Kreuzer als eigene Seite ([eintragenAlsSeite]) mit Auswahl und Knopf fest unten.
 */
@Composable
fun EintragenBlatt(auftrag: BlattAuftrag, onFertig: () -> Unit, vm: EintragenViewModel) {
    LaunchedEffect(auftrag.id) { vm.oeffnen(auftrag.datum, auftrag.mahlzeit, auftrag = auftrag.id, sofort = auftrag.start.alsSofort()) }
    val z by vm.zustand.collectAsStateWithLifecycle()
    val moeglich by vm.speichernMoeglich.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()
    val speichern = { bereich.launch { if (vm.speichern() != null) onFertig() } }
    if (FoodyTheme.stil.stil == FoodyStil.MILCHGLAS) return MitWegen(z, vm, moeglich, onFertig) { speichern() }

    var reiter by rememberSaveable(auftrag.id) { mutableStateOf(startReiter(auftrag.start)) }
    val wahl = { r: EintragenReiter ->
        if (r != reiter) {
            reiter = r
            vm.reiter(r)
        }
    }
    val knopf = @Composable {
        SpeichernKnopf(reiter, moeglich) { speichern() }
    }
    if (eintragenAlsSeite()) {
        BackHandler(onBack = onFertig)
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxSize().widthIn(max = FoodyBreiten.inhalt)) {
                Column(
                    Modifier.fillMaxWidth().padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2),
                    verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
                ) {
                    val immersiv = FoodyTheme.stil.variante == StilVariante.IMMERSIV
                    // Grauen und Kreuzer: Überzeile wie im Entwurf (Immersiv)
                    val ueberzeile = if (immersiv) stilText("", grauen = "Was dargebracht wird", kreuzer = "Proviant erfassen").ifEmpty { null } else null
                    Seitenkopf(eintragenTitel(), ueberzeile = ueberzeile, onZurueck = onFertig, beschreibung = "Eintragen")
                    MahlzeitChips(z.eingaben, vm)
                    ReiterKopf(reiter, vm, wahl)
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = FoodyAbstaende.s4).padding(top = FoodyAbstaende.s3, bottom = FoodyAbstaende.s3),
                    verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
                ) { ReiterInhalt(reiter, z, vm) }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = FoodyAbstaende.s4).padding(bottom = platzFuerLeiste() + FoodyAbstaende.s3),
                    verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
                ) {
                    Auswahl(z, vm)
                    knopf()
                }
            }
        }
    } else {
        GlasBlatt(onFertig) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = FoodyAbstaende.s4).padding(bottom = FoodyAbstaende.s6),
                verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
            ) {
                BlattTitel(eintragenTitel(), beschreibung = "Eintragen", zeichen = "記")
                MahlzeitChips(z.eingaben, vm)
                ReiterKopf(reiter, vm, wahl)
                ReiterInhalt(reiter, z, vm)
                Auswahl(z, vm)
                knopf()
            }
        }
    }
}

/** Milchglas: das Blatt mit den Wegen als Chips (kein Entwurf mit Reitern). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MitWegen(z: EintragenZustand, vm: EintragenViewModel, moeglich: Boolean, onFertig: () -> Unit, onSpeichern: () -> Unit) {
    val e = z.eingaben
    GlasBlatt(onFertig) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = FoodyAbstaende.s4).padding(bottom = FoodyAbstaende.s6),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            BlattTitel(eintragenTitel(), beschreibung = "Eintragen", zeichen = "記")
            MahlzeitChips(e, vm)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                WEGE.forEach { (w, text) -> FoodyChip(stilText(text, sage = SAGE_WEGE[w]), w == e.weg, { vm.weg(w) }, beschreibung = text) }
            }
            when (e.weg) {
                EintragenWeg.REZEPT -> RezeptWahl(z, vm)
                EintragenWeg.ZUTAT -> ZutatWahl(z, vm)
                EintragenWeg.PLAN -> PlanWahl(z, vm)
                EintragenWeg.FREI -> FreiEingabe(z, vm)
            }
            FoodyKnopf(speichernText(), onSpeichern, Modifier.fillMaxWidth().testTag(EINTRAGEN_SPEICHERN), aktiv = moeglich)
        }
    }
}

@Composable
private fun speichernText() =
    stilText("Eintragen", sage = "In die Chronik", anime = "Eintragen! ✓", bayrisch = "Eitragn", grauen = "Ins Logbuch", kreuzer = "Ins Bordbuch")

/** Speichern; unter „Geplant“ mit den Wörtern der Entwürfe fürs Gegessene. */
@Composable
private fun SpeichernKnopf(reiter: EintragenReiter, moeglich: Boolean, onKlick: () -> Unit) {
    val text = if (reiter == EintragenReiter.GEPLANT) {
        stilText(
            "Eintragen", sage = "Als verzehrt eintragen", anime = "Gegessen!", edo = "Als gegessen eintragen", bayrisch = "Eitragn",
            grauen = "Ins Logbuch", kreuzer = "Ins Bordbuch",
        )
    } else {
        speichernText()
    }
    FoodyKnopf(text, onKlick, Modifier.fillMaxWidth().testTag(EINTRAGEN_SPEICHERN), aktiv = moeglich)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MahlzeitChips(e: Eingaben, vm: EintragenViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        MAHLZEITEN.forEach { m ->
            FoodyChip(stilText(m.anzeigename(), sage = m.sageKurz()), m == e.mahlzeit, { vm.mahlzeit(m) }, beschreibung = m.anzeigename())
        }
    }
}

/** „Suchen · Scannen · Foto · Geplant“ (Sage „Suchen · Siegel · Deuten · Geplant“); ohne Erkennungsmodell „Frei“ statt „Foto“. */
@Composable
private fun ReiterKopf(reiter: EintragenReiter, vm: EintragenViewModel, onWahl: (EintragenReiter) -> Unit) {
    val fotoKnopf by vm.fotoKnopf.collectAsStateWithLifecycle()
    val foto = if (fotoKnopf) "Foto" else "Frei"
    Reiterleiste(
        listOf("Suchen", stilText("Scannen", sage = "Siegel"), if (fotoKnopf) stilText("Foto", sage = "Deuten") else "Frei", "Geplant"),
        reiter.ordinal,
        { onWahl(EintragenReiter.entries[it]) },
        beschreibungen = listOf("Suchen", "Scannen", foto, "Geplant"),
    )
}

@Composable
private fun ColumnScope.ReiterInhalt(reiter: EintragenReiter, z: EintragenZustand, vm: EintragenViewModel) {
    when (reiter) {
        EintragenReiter.SUCHEN -> SuchenReiter(z, vm)
        EintragenReiter.SCANNEN -> ScannenReiter(z, vm)
        EintragenReiter.FOTO -> FotoUndFelder(z, vm, mitScan = false)
        EintragenReiter.GEPLANT -> PlanWahl(z, vm)
    }
}

/** Eine Suche über Rezepte, Zutaten und den BLS wie im Entwurf; die Quelle steht unter dem Namen. */
@Composable
private fun SuchenReiter(z: EintragenZustand, vm: EintragenViewModel) {
    val e = z.eingaben
    Suche(e.suche, stilText("Rezept oder Zutat suchen", sage = "Im Verzeichnis suchen"), vm::suche)
    val rezept = stilText("Rezept", sage = "Eigenes Rezept")
    z.rezepte.forEach { r -> WahlZeile(r.titel, e.weg == EintragenWeg.REZEPT && r.id == e.rezeptId, { vm.rezeptWaehlen(r.id) }, info = rezept) }
    if (e.suche.isBlank()) return
    z.zutaten.forEach { t -> WahlZeile(t.name, e.weg == EintragenWeg.ZUTAT && t.id == e.zutatId, { vm.zutatWaehlen(t.id) }, info = "Zutat") }
    if (z.bls.isNotEmpty()) {
        Text(
            stilText("Aus dem Bundeslebensmittelschlüssel", sage = "Aus dem großen Verzeichnis"),
            style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.nebentext,
        )
        z.bls.forEach { t -> BlsZeile(t) { vm.blsWaehlen(t) } }
    }
}

/** Scan-Knopf und die schon gescannten Produkte; nach einem Scan die Packung ([PackungEingabe]). */
@Composable
private fun ScannenReiter(z: EintragenZustand, vm: EintragenViewModel) {
    val e = z.eingaben
    if (e.packung != null && e.weg == EintragenWeg.FREI) {
        PackungEingabe(e, z.vorhandeneZutat, vm)
        FoodyKnopf("Zurück zu Frei", vm::packungVerwerfen, art = KnopfArt.TEXT)
        return
    }
    PackungScanKnopf(vm.scanLauf)
    if (z.gescannt.isNotEmpty()) {
        Text(
            stilText("Gescannte Produkte", sage = "Bereits entzifferte Waren"),
            style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.nebentext,
        )
        z.gescannt.forEach { p -> WahlZeile(p.name, e.weg == EintragenWeg.ZUTAT && p.id == e.zutatId, { vm.produktWaehlen(p) }) }
    }
}

/** Unter den Reitern: Portionen des gewählten Rezepts oder Menge der gewählten Zutat. */
@Composable
private fun Auswahl(z: EintragenZustand, vm: EintragenViewModel) {
    val e = z.eingaben
    when {
        e.weg == EintragenWeg.REZEPT && e.rezeptId != null -> PortionenZeile(e, vm)
        e.weg == EintragenWeg.ZUTAT && e.zutatId != null -> MengeEingabe(e, vm)
    }
}

@Composable
private fun RezeptWahl(z: EintragenZustand, vm: EintragenViewModel) {
    Suche(z.eingaben.suche, "Rezept suchen", vm::suche)
    z.rezepte.forEach { r -> Treffer(r.titel, r.id == z.eingaben.rezeptId) { vm.rezeptWaehlen(r.id) } }
    // Gescannte Produkte zur Suche: Antippen wechselt zu „Zutat“ mit dem Produkt
    if (z.produkte.isNotEmpty()) {
        Text("Gescannte Produkte", style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.nebentext)
        z.produkte.forEach { p -> WahlZeile(p.name, gewaehlt = false, { vm.produktWaehlen(p) }, knopf = true) }
    }
    PortionenZeile(z.eingaben, vm)
}

@Composable
private fun ZutatWahl(z: EintragenZustand, vm: EintragenViewModel) {
    Suche(z.eingaben.suche, "Zutat suchen", vm::suche)
    z.zutaten.forEach { t -> Treffer(t.name, t.id == z.eingaben.zutatId) { vm.zutatWaehlen(t.id) } }
    if (z.bls.isNotEmpty()) {
        Text(
            stilText("Aus dem Bundeslebensmittelschlüssel", sage = "Aus dem großen Verzeichnis"),
            style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.nebentext,
        )
        z.bls.forEach { t -> BlsZeile(t) { vm.blsWaehlen(t) } }
    }
    MengeEingabe(z.eingaben, vm)
}

@Composable
private fun PlanWahl(z: EintragenZustand, vm: EintragenViewModel) {
    if (z.plan.isEmpty()) {
        Text(
            stilText("Für diesen Tag ist nichts Geplantes mehr offen.", sage = "Für diesen Tag ist nichts Weiteres vorgesehen."),
            color = FoodyTheme.farben.nebentext,
        )
    }
    z.plan.forEach { p ->
        Treffer("${p.titel} · ${slotName(p.slot)}", p.planEintragId == z.eingaben.planEintragId) { vm.planWaehlen(p.planEintragId) }
    }
}

/**
 * „Frei“: oben der Scan-Knopf und (mit Modell) „Foto erkennen“; nach einem Scan die Packung ([PackungEingabe]) statt der
 * freien Felder. Werte aus dem Foto sind markiert, solange sie dem Vorschlag gleichen.
 */
@Composable
private fun FreiEingabe(z: EintragenZustand, vm: EintragenViewModel) {
    val e = z.eingaben
    if (e.packung != null) {
        PackungEingabe(e, z.vorhandeneZutat, vm)
        FoodyKnopf("Zurück zu Frei", vm::packungVerwerfen, art = KnopfArt.TEXT)
        return
    }
    FotoUndFelder(z, vm, mitScan = true)
}

/** Knöpfe (Scan nur in „Frei“ von Milchglas, Foto mit Modell), Vorschlag aus dem Foto und die freien Felder ([FreieFelder]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FotoUndFelder(z: EintragenZustand, vm: EintragenViewModel, mitScan: Boolean) {
    val e = z.eingaben
    val fotoKnopf by vm.fotoKnopf.collectAsStateWithLifecycle()
    if (mitScan || fotoKnopf) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            if (mitScan) PackungScanKnopf(vm.scanLauf)
            if (fotoKnopf) FotoErkennungKnopf(vm.fotoLauf)
        }
    }
    if (fotoKnopf) FotoErkennungStand(vm.fotoLauf)
    FreieFelder(e, vm)
}
