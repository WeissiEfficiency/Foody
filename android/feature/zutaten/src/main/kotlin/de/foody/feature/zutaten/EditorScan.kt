package de.foody.feature.zutaten

import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.data.zutaten.ZutatEntwurf
import de.foody.core.data.zutaten.ZutatFeld
import de.foody.core.scan.ScanAusgang
import de.foody.core.scan.ScanTexte
import de.foody.domain.Nutrient

/** Meldung, wenn ein gescannter Strichcode schon an der geöffneten Zutat hängt. */
internal const val GEHOERT_SCHON = "Diese Packung gehört schon zu dieser Zutat"

/** Text eines Feldes im Entwurf. */
private fun ZutatEntwurf.text(f: ZutatFeld): String = when (f) {
    ZutatFeld.NAME -> name
    ZutatFeld.KCAL -> kcal
    ZutatFeld.EIWEISS -> eiweiss
    ZutatFeld.KOHLENHYDRATE -> kohlenhydrate
    ZutatFeld.ZUCKER -> zucker
    ZutatFeld.FETT -> fett
    ZutatFeld.BALLASTSTOFFE -> ballaststoffe
    ZutatFeld.SALZ -> salz
    ZutatFeld.DICHTE -> dichte
    ZutatFeld.STUECKGEWICHT -> stueckgewicht
    ZutatFeld.STRICHCODE -> strichcode
}

/** Text, den der Scan in [f] schreibt (`null`: Feld kommt nicht aus der Packung). */
private fun Packungswerte.scanText(f: ZutatFeld): String? = when (f) {
    ZutatFeld.KCAL -> kcalText
    ZutatFeld.EIWEISS -> feldText(Nutrient.PROTEIN_G)
    ZutatFeld.KOHLENHYDRATE -> feldText(Nutrient.CARBS_G)
    ZutatFeld.ZUCKER -> feldText(Nutrient.SUGAR_G)
    ZutatFeld.FETT -> feldText(Nutrient.FAT_G)
    ZutatFeld.BALLASTSTOFFE -> feldText(Nutrient.FIBER_G)
    ZutatFeld.SALZ -> feldText(Nutrient.SALT_G)
    ZutatFeld.STRICHCODE -> strichcode
    else -> null
}

/** Markierungen nach einer Änderung: nur Felder, deren Text noch dem Scanwert gleicht; zurückgesetzte kommen nicht wieder. */
internal fun nochMarkiert(markiert: Set<ZutatFeld>, e: ZutatEntwurf, scan: Packungswerte?): Set<ZutatFeld> =
    if (scan == null) emptySet() else markiert.filterTo(LinkedHashSet()) { e.text(it) == scan.scanText(it) }

/** Meldung, wenn „Online abrufen“ keine Werte brachte. */
internal fun abrufMeldung(a: ScanAusgang): String =
    if (a is ScanAusgang.NichtGefunden) ScanTexte.nichtGefunden(a.grund) else ScanTexte.FEHLER
