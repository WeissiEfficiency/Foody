package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.NutritionResult
import java.math.BigDecimal
import java.math.RoundingMode

/** Quelle von Nährwerten, die im Editor eingegeben wurden (wie v1). */
const val EIGENE_ANGABE = "Eigene Angabe"

/**
 * Überträgt nur die Felder, deren Text sich von [vorher] zu [nachher] geändert hat, auf diesen (aktuellen) Stand:
 * Unberührtes bleibt exakt, auch ein Sync-Stand, der während des Bearbeitens kam. Wurden Nährwerte geändert: ohne Werte
 * keine Basis und keine Quelle; mit Werten Basis mindestens „je 100 g“ und – wenn die Quelle unberührt blieb –
 * [EIGENE_ANGABE] (sonst gälte eine bearbeitete Startzutat beim nächsten Startdaten-Update als unverändert).
 *
 * Mit [scan] (Packung im Editor übernommen): kcal wie gescannt → genauer kJ-Wert der Packung ([energieKj]); steht die
 * Quelle des Scans im Entwurf, bleibt sie – auch beim erneuten Scan einer Zutat, die sie schon trägt.
 */
internal fun ZutatEntity.mitAenderungen(
    vorher: ZutatEntwurf,
    nachher: ZutatEntwurf,
    mitName: Boolean = true,
    scan: Packungswerte? = null,
): ZutatEntity {
    fun zahl(alt: String, neu: String, wert: BigDecimal?): BigDecimal? = if (alt == neu) wert else zahlAus(neu)
    fun text(alt: String, neu: String, wert: String?): String? = if (alt == neu) wert else neu.trim().ifEmpty { null }

    // Mit Scan zählt auch ein gleicher kcal-Text (1500 statt 1502 kJ ergeben beide „359“): der genaue Packungswert gilt
    val kcalNeu = nachher.kcal != vorher.kcal ||
        (scan?.werte?.get(Nutrient.ENERGY_KJ) != null && nachher.kcal.trim() == scan.kcalText && energyKj != scan.werte[Nutrient.ENERGY_KJ])
    var z = copy(
        canonicalName = if (mitName && nachher.name != vorher.name) nameBereinigt(nachher.name) else canonicalName,
        category = if (nachher.kategorie != vorher.kategorie) nachher.kategorie else category,
        nutrientBasis = if (nachher.basis != vorher.basis) nachher.basis else nutrientBasis,
        energyKj = if (kcalNeu) energieKj(nachher.kcal, scan) else energyKj,
        protein = zahl(vorher.eiweiss, nachher.eiweiss, protein),
        carbs = zahl(vorher.kohlenhydrate, nachher.kohlenhydrate, carbs),
        sugar = zahl(vorher.zucker, nachher.zucker, sugar),
        fat = zahl(vorher.fett, nachher.fett, fat),
        fiber = zahl(vorher.ballaststoffe, nachher.ballaststoffe, fiber),
        salt = zahl(vorher.salz, nachher.salz, salt),
        densityGPerMl = zahl(vorher.dichte, nachher.dichte, densityGPerMl),
        pieceWeightG = zahl(vorher.stueckgewicht, nachher.stueckgewicht, pieceWeightG),
        barcode = text(vorher.strichcode, nachher.strichcode, barcode),
        nutrientSource = text(vorher.quelle, nachher.quelle, nutrientSource),
    )
    val naehrwerteGeaendert = kcalNeu || nachher.basis != vorher.basis ||
        listOf<(ZutatEntwurf) -> String>({ it.eiweiss }, { it.kohlenhydrate }, { it.zucker }, { it.fett }, { it.ballaststoffe }, { it.salz })
            .any { it(vorher) != it(nachher) }
    if (!naehrwerteGeaendert) return z
    val hatWerte = listOf(z.energyKj, z.protein, z.carbs, z.sugar, z.fat, z.fiber, z.salt).any { it != null }
    z = if (hatWerte) {
        z.copy(
            nutrientBasis = z.nutrientBasis ?: NutrientBasis.PER_100_G,
            nutrientSource = if (nachher.quelle == vorher.quelle && nachher.quelle != scan?.quelle) EIGENE_ANGABE else z.nutrientSource,
        )
    } else {
        z.copy(nutrientBasis = null, nutrientSource = null)
    }
    return z
}

/** kcal → kJ; ×4,184 hängt drei Nachkommastellen an, die Sync-Grenze erlaubt höchstens [KJ_STELLEN]. */
internal fun kjAusKcal(kcal: BigDecimal): BigDecimal =
    NutritionResult.kcalToKj(kcal).let { if (it.scale() > KJ_STELLEN) it.setScale(KJ_STELLEN, RoundingMode.HALF_UP) else it }

private const val KJ_STELLEN = 20
