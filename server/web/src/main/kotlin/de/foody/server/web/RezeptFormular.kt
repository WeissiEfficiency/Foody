package de.foody.server.web

import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.sync.protocol.ZahlGrenzen
import io.ktor.http.Parameters
import java.math.BigDecimal

/** Eine Zutatenzeile, wie sie im Formular steht (Texte; geprüft wird in [RezeptFormular.pruefen]). */
internal data class FormZeile(val menge: String, val einheit: String, val zutat: String, val hinweis: String, val optional: Boolean) {
    /** Leere Menge = „nach Bedarf“ = 0; ungültig = `null`. */
    fun mengeZahl(): BigDecimal? = when {
        menge.isBlank() -> BigDecimal.ZERO
        // Wie `parseNichtNegativ` der App: keine Exponent-Schreibweise („1e5“) in Eingabefeldern
        menge.contains('e', ignoreCase = true) -> null
        else -> ZahlGrenzen.lesen(menge.trim().replace(',', '.'))?.takeIf { it.signum() >= 0 }
    }

    fun einheitWert(): MeasureUnit? = MeasureUnit.entries.firstOrNull { it.name == einheit }
}

/** Inhalt des Rezept-Editors; liest Formulardaten, prüft sie und lässt sich aus einem gespeicherten Rezept füllen. */
internal data class RezeptFormular(
    val id: String?,
    val rev: Long?,
    val erzwingen: Boolean,
    val name: String,
    val portionen: String,
    val vorbereitung: String,
    val kochzeit: String,
    val tags: String,
    val quelle: String,
    val notizen: String,
    val schritte: String,
    val einordnungFest: Boolean,
    val mahlzeiten: Set<Mahlzeit>,
    val gaenge: Set<Gang>,
    val zeilen: List<FormZeile>,
) {
    fun schrittListe(): List<String> = schritte.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun portionenZahl(): Int? = portionen.trim().toIntOrNull()?.takeIf { it in 1..MAX_PORTIONEN }

    /** Minuten; leer = `null`; ungültig → Fehler in [pruefen]. */
    fun minuten(text: String): Int? = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** Feldname → Meldung; leer = alles in Ordnung. Zeilenfehler unter `zeile_<i>` (i = Index in [zeilen]). */
    fun pruefen(): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "Bitte einen Namen eingeben.")
        else if (name.trim().length > MAX_NAME) put("name", "Der Name ist zu lang (höchstens $MAX_NAME Zeichen).")
        if (portionenZahl() == null) put("portionen", "Portionen: eine ganze Zahl von 1 bis $MAX_PORTIONEN.")
        for ((feld, text) in listOf("vorbereitung" to vorbereitung, "kochzeit" to kochzeit)) {
            if (text.isBlank()) continue
            if (minuten(text)?.takeIf { it in 0..MAX_MINUTEN } == null) put(feld, "Minuten: eine ganze Zahl von 0 bis $MAX_MINUTEN.")
        }
        zeilen.forEachIndexed { i, z ->
            when {
                z.einheitWert() == null -> put("zeile_$i", "Unbekannte Einheit bei „${z.zutat}“.")
                z.mengeZahl() == null -> put("zeile_$i", "Menge bei „${z.zutat}“ ist keine gültige Zahl (leer = nach Bedarf).")
                z.zutat.trim().length > MAX_NAME -> put("zeile_$i", "Zutatenname zu lang.")
            }
        }
    }

    companion object {
        const val MAX_NAME = 200
        const val MAX_PORTIONEN = 99
        const val MAX_MINUTEN = 1440

        fun aus(p: Parameters): RezeptFormular {
            val mengen = p.getAll("zeile_menge").orEmpty()
            val einheiten = p.getAll("zeile_einheit").orEmpty()
            val zutaten = p.getAll("zeile_zutat").orEmpty()
            val hinweise = p.getAll("zeile_hinweis").orEmpty()
            val optional = p.getAll("zeile_optional").orEmpty()
            val zeilen = zutaten.indices.mapNotNull { i ->
                val zutat = zutaten[i].trim()
                if (zutat.isEmpty()) return@mapNotNull null // leere Zeile
                FormZeile(mengen.getOrElse(i) { "" }, einheiten.getOrElse(i) { "" }, zutat, hinweise.getOrElse(i) { "" }.trim(), optional.getOrNull(i) == "1")
            }
            return RezeptFormular(
                id = p["id"]?.takeIf { it.isNotBlank() },
                rev = p["rev"]?.toLongOrNull(),
                erzwingen = p["erzwingen"] == "1",
                name = p["name"].orEmpty(),
                portionen = p["portionen"].orEmpty(),
                vorbereitung = p["vorbereitung"].orEmpty(),
                kochzeit = p["kochzeit"].orEmpty(),
                tags = p["tags"].orEmpty(),
                quelle = p["quelle"].orEmpty().trim(),
                notizen = p["notizen"].orEmpty(),
                schritte = p["schritte"].orEmpty(),
                einordnungFest = p["einordnung"] == "fest",
                mahlzeiten = p.getAll("mahlzeit").orEmpty().mapNotNull { m -> Mahlzeit.entries.firstOrNull { it.name == m } }.toSet(),
                gaenge = p.getAll("gang").orEmpty().mapNotNull { g -> Gang.entries.firstOrNull { it.name == g } }.toSet(),
                zeilen = zeilen,
            )
        }

        /** Formular zum Bearbeiten eines gespeicherten Rezepts. */
        fun aus(r: WebRezept, h: Haushalt): RezeptFormular {
            val p = r.payload
            return RezeptFormular(
                id = r.id, rev = r.rev, erzwingen = false,
                name = p.name, portionen = p.servings.toString(),
                vorbereitung = p.prep?.toString().orEmpty(), kochzeit = p.cook?.toString().orEmpty(),
                tags = p.tags, quelle = p.sourceUrl.orEmpty(), notizen = p.notes.orEmpty(),
                schritte = p.steps.sortedBy { it.position }.joinToString("\n") { it.text },
                einordnungFest = p.mahlzeiten != null || p.gaenge != null,
                mahlzeiten = Mahlzeit.mengeAus(p.mahlzeiten).orEmpty(), gaenge = Gang.mengeAus(p.gaenge).orEmpty(),
                zeilen = p.lines.sortedBy { it.sortOrder }.map { l ->
                    val menge = BigDecimal(l.amount)
                    FormZeile(
                        menge = if (menge.signum() == 0) "" else zahl(menge, 3),
                        einheit = l.unit,
                        zutat = h.zutaten[l.ingredientId]?.name ?: "",
                        hinweis = l.note.orEmpty(),
                        optional = l.optional,
                    )
                },
            )
        }
    }
}
