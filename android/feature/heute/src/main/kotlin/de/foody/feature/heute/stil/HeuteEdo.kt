package de.foody.feature.heute.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyFrage
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.SyncStatus
import de.foody.core.designsystem.komponenten.balkenanteile
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.EdoKnopf
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.HEUTE_INHALT
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteZustand
import de.foody.feature.heute.MahlzeitAbschnitt
import de.foody.feature.heute.TagebuchHeute
import de.foody.feature.heute.anzeigename
import de.foody.feature.heute.meta
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TAG_MONAT = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

/**
 * „Heute“ des Edo-Stils in Immersiv (Entwurf `S-Heute`): ein Rollbild mit Tuschelandschaft (Sonne im Zinnober, Berge,
 * Nebel, Vögel, der Wochentag senkrecht und ein Siegel), darunter auf dem Papier die nächste Mahlzeit mit „KOCHEN“.
 * Unten die „Speisefolge“ des Tages: je Mahlzeit eine Zeile mit Stempel – gegessen 済 im Zinnober, offen gestrichelt;
 * den Stempel einer geplanten Mahlzeit antippen fragt „Gegessen?“. TalkBack hört die normalen Begriffe.
 */
@Composable
internal fun HeuteEdo(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .padding(top = 14.dp, bottom = ueberLeiste(PLATZ_FUER_LEISTE))
                .testTag(HEUTE_INHALT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Rollbild(zustand, aktionen)
            if (zustand == null) return@Column
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 34.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                zustand.sync?.let { SyncStatus(it) }
                if (zustand.datenbankLeer) {
                    LeererZustandFlaeche(
                        Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", "Verbinde Foody mit deinem Server, um deine Rezepte zu laden.",
                        aktionText = "Server verbinden", onAktion = aktionen.onEinstellungen,
                    )
                }
                Speisefolge(zustand.tagebuch, aktionen)
                zustand.einkauf?.let { e ->
                    Text(
                        "Einkaufszettel · ${e.anzahl} offen ›",
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { aktionen.onEinkauf(e.listeId) }
                            .semantics { contentDescription = "Einkauf, ${e.anzahl} offen" }.padding(vertical = 12.dp),
                        style = Edo.titel(16f), color = FoodyTheme.farben.text,
                    )
                }
                Knoepfe(aktionen)
            }
        }
    }
}

/** Kopfzeile auf dem Papier: „HEUTE ABEND · 24. OKTOBER“, morgen „MORGEN FRÜH · …“. */
internal fun edoKopf(slot: String, morgen: Boolean, datum: LocalDate): String {
    val tag = if (morgen) "MORGEN" else "HEUTE"
    val wann = when (Mahlzeit.ausText(slot)) {
        Mahlzeit.FRUEHSTUECK -> "$tag FRÜH"
        Mahlzeit.MITTAGESSEN -> "$tag MITTAG"
        Mahlzeit.ABENDESSEN -> "$tag ABEND"
        Mahlzeit.SNACK -> "$tag ZWISCHENDURCH"
        null -> "$tag · ${slot.uppercase(Locale.GERMAN)}"
    }
    return "$wann · ${TAG_MONAT.format(datum).uppercase(Locale.GERMAN)}"
}

/** Das Rollbild: Stab, Montierung in Indigo, Papier mit Landschaft und der nächsten Mahlzeit, Stab, zwei Quasten. */
@Composable
private fun Rollbild(zustand: HeuteZustand?, aktionen: HeuteAktionen) {
    val shu = edoShu()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val breite = (maxWidth - 40.dp).coerceAtMost(316.dp)
        Column(Modifier.width(breite + 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(breite + 24.dp).height(10.dp).background(Edo.holz, RoundedCornerShape(5.dp)))
            Column(
                Modifier.width(breite).background(Edo.indigo).drawBehind {
                    // Feines Punktraster der Seide
                    val schritt = 8.dp.toPx()
                    var y = schritt / 2
                    while (y < size.height) {
                        var x = schritt / 2
                        while (x < size.width) {
                            drawCircle(Edo.papier.copy(alpha = 0.18f), 1.dp.toPx(), Offset(x, y))
                            x += schritt
                        }
                        y += schritt
                    }
                }.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
            ) {
                Column(Modifier.fillMaxWidth().background(Edo.papier).border(1.dp, Edo.papierRand)) {
                    Landschaft(zustand?.datum, shu)
                    zustand?.let { Papier(it, aktionen) }
                }
            }
            Box(Modifier.width(breite + 32.dp).height(12.dp).background(Edo.holz, RoundedCornerShape(6.dp)))
            Box(Modifier.width(breite).height(22.dp)) {
                Box(Modifier.offset(x = 40.dp).width(4.dp).height(22.dp).background(shu))
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-40).dp).width(4.dp).height(22.dp).background(shu))
            }
        }
    }
}

/** Tuschelandschaft im Maß des Entwurfs (286 × 168); trägt für TalkBack die Überschrift „Heute“. */
@Composable
private fun Landschaft(datum: LocalDate?, shu: Color) {
    val tinte = FoodyTheme.farben.text
    val messer = rememberTextMeasurer()
    val pfade = remember {
        val p = { d: String -> PathParser().parsePathString(d).toPath() }
        listOf(
            p("M0 132L52 70 74 88 118 34 160 92 182 76 236 124 286 104V168H0z"),
            p("M0 150L40 116 84 132 132 96 176 128 220 108 286 140V168H0z"),
            p("M38 50q8-6 16 0M50 42q6-5 12 0M232 76q7-5 14 0"),
            p("M112 34l6-8 6 8-3-1-3 3-3-3z"),
        )
    }
    val wochentag = datum?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.FULL, Locale.GERMAN)?.uppercase(Locale.GERMAN).orEmpty()
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(286f / 168f).semantics {
            heading()
            contentDescription = "Heute"
        },
    ) {
        val s = size.width / 286f
        scale(s, Offset.Zero) {
            drawCircle(shu, 18f, Offset(206f, 46f))
            drawPath(pfade[0], Color(0xFF8C8A84).copy(alpha = 0.55f))
            drawPath(pfade[1], Color(0xFF4A4844).copy(alpha = 0.7f))
            drawRect(Brush.verticalGradient(listOf(Edo.papier.copy(alpha = 0f), Edo.papier), startY = 120f, endY = 168f), Offset(0f, 120f), Size(286f, 48f))
            drawPath(pfade[2], tinte, style = Stroke(1.4f, cap = StrokeCap.Round))
            drawPath(pfade[3], Edo.papier)
        }
        // Wochentag senkrecht, Buchstabe unter Buchstabe; darunter das Siegel
        var y = 10f * s
        val stil = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = (13f * s).toSp(), color = tinte)
        wochentag.forEach { c ->
            val m = messer.measure(c.toString(), stil)
            drawText(m, topLeft = Offset(270f * s - m.size.width / 2f, y))
            y += 13.6f * s
        }
        val siegel = (y + 4f * s).coerceAtMost(150f * s)
        drawRect(shu, Offset(263f * s, siegel), Size(14f * s, 14f * s))
    }
}

/** Die nächste Mahlzeit auf dem Papier unter dem Bild; ohne Plan eine Empfehlung oder ein stiller Satz. */
@Composable
private fun Papier(zustand: HeuteZustand, aktionen: HeuteAktionen) {
    val f = FoodyTheme.farben
    val shu = edoShu()
    val n = zustand.naechste
    val r = zustand.inspiration
    Column(
        Modifier.fillMaxWidth().drawBehind { drawLine(Edo.papierLinie, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val kopf = when {
            n != null -> edoKopf(n.mahlzeit.slot, n.morgen, n.mahlzeit.datum)
            r != null -> "EMPFEHLUNG · ${TAG_MONAT.format(zustand.datum).uppercase(Locale.GERMAN)}"
            else -> "HEUTE · ${TAG_MONAT.format(zustand.datum).uppercase(Locale.GERMAN)}"
        }
        Text(kopf, style = Edo.kopf(), color = shu)
        val titel = n?.mahlzeit?.titel ?: r?.titel
        val rezeptId = n?.mahlzeit?.rezeptId ?: r?.id
        if (titel == null || rezeptId == null) {
            Text("Für heute ist nichts vorgesehen.", style = Edo.text(14f), color = f.nebentext)
            return@Column
        }
        Text(
            "$titel ›",
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { aktionen.onRezept(rezeptId) }
                .semantics { contentDescription = "$titel, Rezept öffnen" }.padding(vertical = 4.dp),
            style = Edo.titel(21f), color = f.text,
        )
        val zeile = n?.mahlzeit?.let { meta(it.portionen, it.kcalJePortion, it.zeitMinuten) }
            ?: r?.let { meta(null, it.kcalJePortion, it.zeitMinuten) }.orEmpty()
        if (zeile.isNotEmpty()) Text(zeile, style = Edo.text(13f), color = f.nebentext)
        val breit = Modifier.fillMaxWidth().padding(top = 6.dp)
        if (n != null) {
            EdoKnopf("KOCHEN", { aktionen.onKochen(n.mahlzeit.rezeptId, n.mahlzeit.portionen) }, breit, beschreibung = "Kochen")
        } else {
            EdoKnopf("ANSEHEN", { aktionen.onRezept(rezeptId) }, breit, beschreibung = "Rezept ansehen")
        }
    }
}

/** Name in der linken Spalte der Speisefolge. */
internal fun Mahlzeit.edoName(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Frühstück"
    Mahlzeit.MITTAGESSEN -> "Mittag"
    Mahlzeit.SNACK -> "Snack"
    Mahlzeit.ABENDESSEN -> "Abend"
}

/** Was in der Zeile steht: die Einträge, „Ofengemüse, geplant“ oder „＋ Eintragen“. */
internal fun folgeText(a: MahlzeitAbschnitt): String {
    val v = a.vorschlaege.firstOrNull()
    return when {
        a.eintraege.isNotEmpty() -> a.eintraege.joinToString(", ") { it.name }
        v != null -> "${kurzname(v.titel)}, geplant"
        else -> "＋ Eintragen"
    }
}

/** „Speisefolge ›“ mit kcal gegen das Ziel, Nährwertbalken und je Mahlzeit eine Zeile mit Stempel. */
@Composable
private fun Speisefolge(t: TagebuchHeute, aktionen: HeuteAktionen) {
    val f = FoodyTheme.farben
    val s = t.summe
    val ziel = t.ziel?.takeIf { it > 0 }
    // Geplante Mahlzeit, zu der gerade „Gegessen?“ gefragt wird; übersteht Drehen
    var frage by rememberSaveable { mutableStateOf<String?>(null) }
    Column {
        Row(
            Modifier.fillMaxWidth().drawBehind { drawLine(f.text, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Speisefolge ›",
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aktionen.onAlleEintraege)
                    .semantics { contentDescription = "Tagebuch, alle Einträge" }.padding(vertical = 12.dp),
                style = Edo.titel(18f), color = f.text,
            )
            Row(
                Modifier.clearAndSetSemantics {
                    contentDescription = if (ziel != null) "${ganzzahlDeutsch(s.kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(s.kcal)} kcal"
                },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(ganzzahlDeutsch(s.kcal), style = Edo.titel(20f), color = f.text)
                Text(ziel?.let { " / ${ganzzahlDeutsch(it)} kcal" } ?: " kcal", Modifier.padding(bottom = 3.dp), style = Edo.text(13f), color = f.nebentext)
            }
        }
        val a = balkenanteile(s.kcal, ziel, s.eiweiss, s.kohlenhydrate, s.fett)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).height(6.dp).background(Edo.papierLinie)
                .clearAndSetSemantics { contentDescription = "Eiweiß ${s.eiweiss} g, Kohlenhydrate ${s.kohlenhydrate} g, Fett ${s.fett} g" },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(a.eiweiss to f.makro.eiweiss, a.kh to f.makro.kohlenhydrate, a.fett to f.makro.fett).forEach { (anteil, farbe) ->
                if (anteil > 0f) Box(Modifier.weight(anteil).height(6.dp).background(farbe))
            }
            val rest = 1f - a.eiweiss - a.kh - a.fett
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        t.mahlzeiten.forEach { abschnitt -> Folgezeile(abschnitt, aktionen) { frage = it } }
    }
    val offen = t.mahlzeiten.flatMap { m -> m.vorschlaege.map { m.mahlzeit to it } }.firstOrNull { it.second.planEintragId == frage }
    offen?.let { (mahlzeit, v) -> GegessenFrage(mahlzeit, v, aktionen) { frage = null } }
}

@Composable
private fun GegessenFrage(mahlzeit: Mahlzeit, v: GeplanteMahlzeit, aktionen: HeuteAktionen, onZu: () -> Unit) {
    FoodyFrage(
        titel = "Gegessen?",
        text = "„${v.titel}“ als ${mahlzeit.anzeigename()} eintragen? Bei „Nein“ verschwindet der Vorschlag für heute.",
        ja = "Ja",
        onJa = { onZu(); aktionen.onGegessen(v.planEintragId) },
        onNein = { onZu(); aktionen.onNichtGegessen(v) },
        nein = "Nein",
        onAbbrechen = onZu,
    )
}

/** Eine Zeile der Speisefolge; der Stempel rechts ist bei einer geplanten Mahlzeit ein eigener Knopf. */
@Composable
private fun Folgezeile(a: MahlzeitAbschnitt, aktionen: HeuteAktionen, onFrage: (String) -> Unit) {
    val f = FoodyTheme.farben
    val gegessen = a.eintraege.isNotEmpty()
    val v = a.vorschlaege.firstOrNull()
    val name = a.mahlzeit.anzeigename()
    val kcal = when {
        gegessen -> ganzzahlDeutsch(a.eintraege.sumOf { it.kcal ?: 0 })
        else -> v?.kcalJePortion?.let { ganzzahlDeutsch(it) } ?: "–"
    }
    val (beschreibung, onKlick) = when {
        gegessen -> "$name: ${a.eintraege.joinToString(", ") { it.name }}, " +
            kcalText(a.eintraege.sumOf { it.kcal ?: 0 }, a.eintraege.all { it.vollstaendig && it.kcal != null }) + ", gegessen" to aktionen.onAlleEintraege
        v != null -> "$name: ${v.titel}, geplant, Rezept öffnen" to { aktionen.onRezept(v.rezeptId) }
        else -> "$name eintragen" to { aktionen.onEintragen(a.mahlzeit) }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .drawBehind { drawLine(f.lichtkante, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onKlick)
                .clearAndSetSemantics { contentDescription = beschreibung },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(a.mahlzeit.edoName(), Modifier.widthIn(min = 74.dp), style = Edo.text(12.5f), color = f.nebentext)
            Text(folgeText(a), Modifier.weight(1f), style = Edo.text(15f), color = if (gegessen) f.text else f.nebentext)
            Text(kcal, style = Edo.text(13.5f), color = f.text)
        }
        Stempel(gegessen, v?.let { { onFrage(it.planEintragId) } }, "$name: als gegessen abstempeln")
    }
}

/** Stempel: gegessen 済 im Zinnober, sonst gestrichelt; bei [onKlick] ein Knopf (48 dp). */
@Composable
private fun Stempel(gegessen: Boolean, onKlick: (() -> Unit)?, beschreibung: String) {
    val shu = edoShu()
    Box(
        Modifier.size(48.dp)
            .then(
                if (onKlick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onKlick).semantics { contentDescription = beschreibung }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            Modifier.padding(end = 4.dp).size(26.dp).rotate(-6f).clip(RoundedCornerShape(3.dp))
                .then(
                    if (gegessen) {
                        Modifier.background(shu)
                    } else {
                        Modifier.drawBehind {
                            drawRoundRect(
                                Edo.stempelLeer, cornerRadius = CornerRadius(3.dp.toPx()),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))),
                            )
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Das Zeichen gehört zum Stempel und bleibt so groß wie er (TalkBack liest „gegessen“ an der Zeile)
            if (gegessen) Text("済", style = Edo.titel(14f / LocalDensity.current.fontScale), color = Color.White)
        }
    }
}

/** „＋ Eintragen“, „Scannen · Foto“ und die Einstellungen am Ende. */
@Composable
private fun Knoepfe(aktionen: HeuteAktionen) {
    val f = FoodyTheme.farben
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EdoKnopf("＋ Eintragen", { aktionen.onEintragen(null) }, Modifier.weight(1f), voll = false, beschreibung = "Eintragen")
        EdoKnopf("Scannen · Foto", { aktionen.onEintragen(null) }, Modifier.weight(1f), voll = false, beschreibung = "Scannen oder Foto")
        Box(
            Modifier.size(48.dp).clip(Edo.form).border(1.5.dp, f.text, Edo.form)
                .clickable(role = Role.Button, onClick = aktionen.onEinstellungen)
                .semantics { contentDescription = "Einstellungen" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = f.text)
        }
    }
}

private val PLATZ_FUER_LEISTE = 104.dp
