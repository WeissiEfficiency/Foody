package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.NutritionResult
import java.math.BigDecimal

/**
 * Nährwerte einer gescannten Packung (Strichcode oder Foto), je 100 g bzw. ml. [basis] `null`: Das Foto hat keine
 * Basis erkannt. [quelle] etwa „Open Food Facts“ oder „Packung (Foto)“.
 */
data class Packungswerte(
    val name: String?,
    val basis: NutrientBasis?,
    val werte: Map<Nutrient, BigDecimal>,
    val strichcode: String?,
    val quelle: String,
) {
    /** Angezeigte kcal aus dem kJ-Wert; `null` ohne Energie. */
    val kcal: Int? get() = werte[Nutrient.ENERGY_KJ]?.let(NutritionResult::kcal)

    /** kcal als Feldtext; leer ohne Energie. */
    val kcalText: String get() = kcal?.toString().orEmpty()

    /** Wert als Feldtext, ungerundet mit Komma; leer, wenn die Packung ihn nicht hat. */
    fun feldText(n: Nutrient): String = zahlText(werte[n])
}

/** Makro-Felder des Editors und ihr Nährstoff. */
private val MAKROS: List<Triple<ZutatFeld, Nutrient, (ZutatEntwurf, String) -> ZutatEntwurf>> = listOf(
    Triple(ZutatFeld.EIWEISS, Nutrient.PROTEIN_G) { e, t -> e.copy(eiweiss = t) },
    Triple(ZutatFeld.KOHLENHYDRATE, Nutrient.CARBS_G) { e, t -> e.copy(kohlenhydrate = t) },
    Triple(ZutatFeld.ZUCKER, Nutrient.SUGAR_G) { e, t -> e.copy(zucker = t) },
    Triple(ZutatFeld.FETT, Nutrient.FAT_G) { e, t -> e.copy(fett = t) },
    Triple(ZutatFeld.BALLASTSTOFFE, Nutrient.FIBER_G) { e, t -> e.copy(ballaststoffe = t) },
    Triple(ZutatFeld.SALZ, Nutrient.SALT_G) { e, t -> e.copy(salz = t) },
)

/**
 * Übernimmt die Packung in den Entwurf: kcal und Makros, soweit vorhanden; die Basis, wenn bekannt; die Quelle; den
 * Strichcode nur bei leerem Feld. Was die Packung nicht hat, bleibt. Liefert dazu die übernommenen (markierten) Felder.
 */
fun ZutatEntwurf.mitPackung(p: Packungswerte): Pair<ZutatEntwurf, Set<ZutatFeld>> {
    val markiert = LinkedHashSet<ZutatFeld>()
    var e = copy(basis = p.basis ?: basis, quelle = p.quelle)
    if (p.kcal != null) {
        e = e.copy(kcal = p.kcalText)
        markiert += ZutatFeld.KCAL
    }
    for ((feld, n, setzen) in MAKROS) {
        if (p.werte[n] == null) continue
        e = setzen(e, p.feldText(n))
        markiert += feld
    }
    if (strichcode.isBlank() && !p.strichcode.isNullOrBlank()) {
        e = e.copy(strichcode = p.strichcode)
        markiert += ZutatFeld.STRICHCODE
    }
    return e to markiert
}

/**
 * Energie-Regel für gescannte Werte: Gleicht [kcalText] dem kcal-Text von [scan], gilt der genaue kJ-Wert der Packung;
 * sonst wird aus dem Text umgerechnet ([kjAusKcal]). Leer oder ungültig → `null`.
 */
internal fun energieKj(kcalText: String, scan: Packungswerte?): BigDecimal? =
    if (scan != null && kcalText == scan.kcalText) scan.werte[Nutrient.ENERGY_KJ] else zahlAus(kcalText)?.let(::kjAusKcal)

/**
 * Überträgt die Packung als Block: die Basis (falls bekannt, sonst die bisherige, mindestens je 100 g), alle Werte der
 * Packung und ihre Quelle. Nährwerte, die die Packung nicht hat, bleiben. Den Strichcode setzt der Aufrufer.
 */
internal fun ZutatEntity.mitPackungswerten(p: Packungswerte): ZutatEntity {
    if (p.werte.isEmpty()) return this
    fun w(n: Nutrient, alt: BigDecimal?) = p.werte[n] ?: alt
    return copy(
        nutrientBasis = p.basis ?: nutrientBasis ?: NutrientBasis.PER_100_G,
        energyKj = w(Nutrient.ENERGY_KJ, energyKj),
        protein = w(Nutrient.PROTEIN_G, protein),
        carbs = w(Nutrient.CARBS_G, carbs),
        sugar = w(Nutrient.SUGAR_G, sugar),
        fat = w(Nutrient.FAT_G, fat),
        fiber = w(Nutrient.FIBER_G, fiber),
        salt = w(Nutrient.SALT_G, salt),
        nutrientSource = p.quelle,
    )
}
