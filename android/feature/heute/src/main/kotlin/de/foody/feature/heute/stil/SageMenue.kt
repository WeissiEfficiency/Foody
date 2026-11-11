package de.foody.feature.heute.stil

import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.HeuteZustand
import de.foody.feature.heute.anzeigename
import java.util.Locale

/** Wohin ein Eintrag im Menü „Was tun?“ führt. */
internal enum class SageZiel { KOCHEN, GEGESSEN, REZEPT, EINTRAGEN, CHRONIK, EINKAUF }

/**
 * Ein Eintrag im Menü der Düsteren Sage: [titel] und [aktion] in den Worten des Stils, [zeile] mit den echten Daten,
 * [beschreibung] in den normalen Begriffen für TalkBack.
 */
internal data class SageEintrag(
    val titel: String,
    val zeile: String,
    val aktion: String,
    val beschreibung: String,
    val ziel: SageZiel,
    val rezeptId: String? = null,
    val portionen: Int = 1,
    val mahlzeit: Mahlzeit? = null,
    val vorschlag: GeplanteMahlzeit? = null,
    val listeId: String? = null,
)

/** Die Mahlzeit in den Worten der Sage. */
internal fun Mahlzeit.sageName(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Morgenmahl"
    Mahlzeit.MITTAGESSEN -> "Mittagsmahl"
    Mahlzeit.SNACK -> "Zwischenmahl"
    Mahlzeit.ABENDESSEN -> "Abendmahl"
}

private fun slotSage(slot: String): String = Mahlzeit.ausText(slot)?.sageName() ?: slot

private fun gross(text: String) = text.uppercase(Locale.GERMAN)

/**
 * Was „Heute“ in der Düsteren Sage anbietet, in dieser Reihenfolge: nächste Mahlzeit kochen, geplante Mahlzeiten als
 * gegessen bestätigen, Inspiration, eintragen, Tagebuch, Einkauf. Dieselben Funktionen wie die normale Ansicht, nur
 * als Menü mit einem Hauptknopf.
 */
internal fun sageMenue(z: HeuteZustand): List<SageEintrag> = buildList {
    z.naechste?.let { n ->
        val m = n.mahlzeit
        val wann = if (n.morgen) "für morgen " else ""
        add(
            SageEintrag(
                titel = gross("${slotSage(m.slot)} ${wann}bereiten"),
                zeile = listOfNotNull(m.titel, m.kcalJePortion?.let { "${ganzzahlDeutsch(it)} kcal" }, m.zeitMinuten?.let { "$it Min." })
                    .joinToString(" · "),
                aktion = "AM FEUER KOCHEN",
                beschreibung = "Kochen: ${m.titel}",
                ziel = SageZiel.KOCHEN,
                rezeptId = m.rezeptId,
                portionen = m.portionen,
            ),
        )
    }
    z.tagebuch.mahlzeiten.forEach { a ->
        a.vorschlaege.forEach { v ->
            add(
                SageEintrag(
                    titel = gross("${a.mahlzeit.sageName()} verzehrt?"),
                    zeile = v.titel,
                    aktion = "ALS VERZEHRT EINTRAGEN",
                    beschreibung = "${a.mahlzeit.anzeigename()} geplant: ${v.titel}. Gegessen?",
                    ziel = SageZiel.GEGESSEN,
                    mahlzeit = a.mahlzeit,
                    vorschlag = v,
                ),
            )
        }
    }
    z.inspiration?.let { r ->
        add(
            SageEintrag(
                titel = "EINGEBUNG",
                zeile = listOfNotNull(r.titel, r.kcalJePortion?.let { "${ganzzahlDeutsch(it)} kcal" }).joinToString(" · "),
                aktion = "REZEPT ANSEHEN",
                beschreibung = "Inspiration: ${r.titel}",
                ziel = SageZiel.REZEPT,
                rezeptId = r.id,
            ),
        )
    }
    val leer = z.tagebuch.mahlzeiten.firstOrNull { it.eintraege.isEmpty() && it.vorschlaege.isEmpty() }?.mahlzeit
    add(
        SageEintrag(
            titel = "MAHL EINTRAGEN",
            zeile = leer?.let { "Das ${it.sageName()} ist noch leer." } ?: "Jedes Mahl des Tages ist verzeichnet.",
            aktion = "EINTRAGEN",
            beschreibung = leer?.let { "Eintragen: ${it.anzeigename()}" } ?: "Eintragen",
            ziel = SageZiel.EINTRAGEN,
            mahlzeit = leer,
        ),
    )
    val eintraege = z.tagebuch.mahlzeiten.flatMap { it.eintraege }
    add(
        SageEintrag(
            titel = "CHRONIK DES TAGES",
            zeile = if (eintraege.isEmpty()) {
                "Noch nichts verzeichnet."
            } else {
                eintraege.take(MAX_CHRONIK).joinToString(" · ") { e -> listOfNotNull(e.name, e.kcal?.let(::ganzzahlDeutsch)).joinToString(" ") } +
                    if (eintraege.size > MAX_CHRONIK) " · …" else ""
            },
            aktion = "CHRONIK ÖFFNEN",
            beschreibung = "Alle Einträge",
            ziel = SageZiel.CHRONIK,
        ),
    )
    z.einkauf?.let { e ->
        add(
            SageEintrag(
                titel = "BEIM HÄNDLER",
                zeile = "${e.anzahl} offen" + if (e.artikel.isEmpty()) "" else ": " + e.artikel.take(MAX_CHRONIK).joinToString(", ") { it.name },
                aktion = "ZUM HÄNDLER",
                beschreibung = "Einkauf, ${e.anzahl} offen",
                ziel = SageZiel.EINKAUF,
                listeId = e.listeId,
            ),
        )
    }
}

private const val MAX_CHRONIK = 3
