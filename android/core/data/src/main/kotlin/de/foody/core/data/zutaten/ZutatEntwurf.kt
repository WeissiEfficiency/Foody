package de.foody.core.data.zutaten

import de.foody.api.protocol.ZahlGrenzen
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.NutrientBasis
import de.foody.domain.NutritionResult
import java.math.BigDecimal

/** Prüfbare Felder des Zutateneditors. */
enum class ZutatFeld { NAME, KCAL, EIWEISS, KOHLENHYDRATE, ZUCKER, FETT, BALLASTSTOFFE, SALZ, DICHTE, STUECKGEWICHT, STRICHCODE }

/**
 * Was der Editor zeigt, als Texte. Gespeichert werden später nur Felder, deren Text sich gegenüber dem Öffnen geändert
 * hat – so bleibt etwa der kJ-Wert exakt, wenn das kcal-Feld unberührt ist.
 */
data class ZutatEntwurf(
    val name: String,
    val kategorie: String?,
    val basis: NutrientBasis?,
    val kcal: String,
    val eiweiss: String,
    val kohlenhydrate: String,
    val zucker: String,
    val fett: String,
    val ballaststoffe: String,
    val salz: String,
    val dichte: String,
    val stueckgewicht: String,
    val strichcode: String,
    val quelle: String,
) {
    companion object {
        fun aus(z: ZutatEntity): ZutatEntwurf = ZutatEntwurf(
            name = z.canonicalName,
            kategorie = z.category,
            basis = z.nutrientBasis,
            kcal = z.energyKj?.let { NutritionResult.kcal(it).toString() }.orEmpty(),
            eiweiss = zahlText(z.protein),
            kohlenhydrate = zahlText(z.carbs),
            zucker = zahlText(z.sugar),
            fett = zahlText(z.fat),
            ballaststoffe = zahlText(z.fiber),
            salz = zahlText(z.salt),
            dichte = zahlText(z.densityGPerMl),
            stueckgewicht = zahlText(z.pieceWeightG),
            strichcode = z.barcode.orEmpty(),
            quelle = z.nutrientSource.orEmpty(),
        )
    }
}

/** Höchstlänge eines Namens (wie das Sync-Protokoll). */
const val NAME_MAX = 200

private val STRICHCODE = Regex("[0-9]{8,14}")
private val NULL = BigDecimal.ZERO

/** Regel einer Zahl: erlaubt? und der Fehlertext. */
private class Regel(val text: String, val erlaubt: (BigDecimal) -> Boolean)

private val NAEHRSTOFF = Regel("0 bis 100") { it >= NULL && it <= BigDecimal(100) }

private val ZAHLENREGELN: List<Triple<ZutatFeld, (ZutatEntwurf) -> String, Regel>> = listOf(
    Triple(ZutatFeld.KCAL, { it.kcal }, Regel("0 bis 1.000") { it >= NULL && it <= BigDecimal(1000) }),
    Triple(ZutatFeld.EIWEISS, { it.eiweiss }, NAEHRSTOFF),
    Triple(ZutatFeld.KOHLENHYDRATE, { it.kohlenhydrate }, NAEHRSTOFF),
    Triple(ZutatFeld.ZUCKER, { it.zucker }, NAEHRSTOFF),
    Triple(ZutatFeld.FETT, { it.fett }, NAEHRSTOFF),
    Triple(ZutatFeld.BALLASTSTOFFE, { it.ballaststoffe }, NAEHRSTOFF),
    Triple(ZutatFeld.SALZ, { it.salz }, NAEHRSTOFF),
    Triple(ZutatFeld.DICHTE, { it.dichte }, Regel("Mehr als 0, höchstens 5") { it > NULL && it <= BigDecimal(5) }),
    Triple(ZutatFeld.STUECKGEWICHT, { it.stueckgewicht }, Regel("Mehr als 0, höchstens 10.000") { it > NULL && it <= BigDecimal(10_000) }),
)

/**
 * Fehler je Feld – geprüft wird nur, was sich gegenüber [vorher] geändert hat (alte Werte außerhalb der Regeln
 * blockieren das Speichern nicht). Leeres Feld = kein Wert, immer erlaubt (außer beim Namen).
 */
fun pruefen(vorher: ZutatEntwurf, nachher: ZutatEntwurf): Map<ZutatFeld, String> {
    val fehler = LinkedHashMap<ZutatFeld, String>()
    if (nachher.name != vorher.name) {
        val name = nameBereinigt(nachher.name)
        when {
            name.isEmpty() -> fehler[ZutatFeld.NAME] = "Name fehlt"
            name.length > NAME_MAX -> fehler[ZutatFeld.NAME] = "Höchstens 200 Zeichen"
        }
    }
    for ((feld, text, regel) in ZAHLENREGELN) {
        val t = text(nachher)
        if (t == text(vorher) || t.isBlank()) continue
        val zahl = zahlAus(t)
        if (zahl == null || !regel.erlaubt(zahl)) fehler[feld] = regel.text
    }
    val code = nachher.strichcode.trim()
    if (nachher.strichcode != vorher.strichcode && code.isNotEmpty() && !STRICHCODE.matches(code)) {
        fehler[ZutatFeld.STRICHCODE] = "8 bis 14 Ziffern"
    }
    return fehler
}

/** Name getrimmt, Leerraum zu einem Leerzeichen. */
fun nameBereinigt(text: String): String = text.trim().replace(Regex("""\s+"""), " ")

/** Zahl aus einem Feld („1,5“ oder „1.5“); leer, ungültig, mit Exponent oder außerhalb der Sync-Grenzen → `null`. */
internal fun zahlAus(text: String): BigDecimal? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty() || t.contains('e', ignoreCase = true)) return null
    return ZahlGrenzen.lesen(t)
}

/** Zahl für ein Feld: ohne Nullen am Ende, mit Komma; `null` → leer. */
internal fun zahlText(wert: BigDecimal?): String = wert?.stripTrailingZeros()?.toPlainString()?.replace('.', ',').orEmpty()
