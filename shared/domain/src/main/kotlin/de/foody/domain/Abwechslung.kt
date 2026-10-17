package de.foody.domain

/**
 * Grundlage und Eiweißquelle eines Gerichts – damit der Wochenvorschlag nicht dreimal hintereinander Nudeln bringt.
 * Erkannt aus Rezeptname und Pflichtzutaten über Stichwörter; Kleinigkeiten wie Speckwürfel oder Reisessig zählen nicht.
 */
object Abwechslung {
    enum class Merkmal { NUDELN, REIS, KARTOFFELN, TEIG, HUELSENFRUECHTE, GEFLUEGEL, RIND_SCHWEIN, FISCH }

    /** Wie ein Stichwort ein Wort trifft: irgendwo darin, am Anfang oder am Ende („Basmatireis“, aber nicht „Reisessig“). */
    private enum class Lage { TEIL, ANFANG, ENDE }

    private class Stichwort(val text: String, val lage: Lage)

    private fun teil(vararg w: String) = w.map { Stichwort(it, Lage.TEIL) }
    private fun anfang(vararg w: String) = w.map { Stichwort(it, Lage.ANFANG) }
    private fun ende(vararg w: String) = w.map { Stichwort(it, Lage.ENDE) }

    private val regeln: Map<Merkmal, List<Stichwort>> = mapOf(
        Merkmal.NUDELN to teil(
            "nudel", "spaghetti", "pasta", "penne", "fusilli", "tagliatelle", "lasagne", "makkaroni", "maccheroni",
            "farfalle", "linguine", "rigatoni", "tortellini", "tortelloni", "ravioli", "gnocchi", "spätzle", "ramen",
        ),
        Merkmal.REIS to ende("reis") + teil("risotto", "paella"),
        Merkmal.KARTOFFELN to teil("kartoffel", "erdäpfel", "rösti", "pommes", "wedges"),
        Merkmal.TEIG to teil("pizza", "flammkuchen", "zwiebelkuchen", "quiche", "calzone") + anfang("pide"),
        Merkmal.HUELSENFRUECHTE to teil("linse", "kichererbse", "kidney") + anfang("dal"),
        Merkmal.GEFLUEGEL to teil("hähnchen", "hühner", "chicken", "truthahn") + anfang("pute", "ente") + ende("huhn"),
        Merkmal.RIND_SCHWEIN to teil("schwein", "gulasch", "wurst", "würst", "salsiccia", "schnitzel", "meatball", "frikadell") +
            anfang("rind", "hack", "kalb", "lamm") + ende("hack"),
        Merkmal.FISCH to teil(
            "fisch", "lachs", "garnele", "shrimp", "kabeljau", "forelle", "dorsch", "scampi", "muschel", "thunfisch",
        ),
    )

    /** Würzendes und Bindendes trägt kein Gericht: „Fischsauce“, „Hühnerbrühe“, „Kartoffelstärke“ zählen nicht. */
    private val beiwerk = listOf("sauce", "soße", "sosse", "brühe", "fond", "essig", "mehl", "stärke", "paste", "pulver", "gewürz", "öl")

    fun merkmale(rezeptName: String, pflichtZutaten: List<String>): Set<Merkmal> {
        val woerter = (pflichtZutaten + rezeptName).flatMap { it.lowercase().split(Regex("[^\\p{L}]+")) }
            .filter { wort -> wort.isNotEmpty() && beiwerk.none { wort.endsWith(it) } }
        return regeln.filterValues { stichwoerter -> woerter.any { wort -> stichwoerter.any { trifft(it, wort) } } }.keys
    }

    private fun trifft(s: Stichwort, wort: String) = when (s.lage) {
        Lage.TEIL -> s.text in wort
        Lage.ANFANG -> wort.startsWith(s.text)
        Lage.ENDE -> wort.endsWith(s.text)
    }
}
