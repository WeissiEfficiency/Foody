package de.foody.core.data.zutaten

import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity

/**
 * Füllt Lücken aus [andere] (wie v1 `fillFrom`): Nährwerte samt Quelle als Block nur, wenn diese Zutat keine hat;
 * Kategorie, Dichte, Stückgewicht und Strichcode einzeln, wo leer. Genutzt von Sync, Startdaten und Katalog.
 */
internal fun ZutatEntity.ergaenztUm(andere: ZutatEntity): ZutatEntity {
    val mitNaehrwerten = if (nutrientBasis == null && andere.nutrientBasis != null) {
        copy(
            nutrientBasis = andere.nutrientBasis, energyKj = andere.energyKj, protein = andere.protein, carbs = andere.carbs,
            fat = andere.fat, fiber = andere.fiber, sugar = andere.sugar, salt = andere.salt, nutrientSource = andere.nutrientSource,
        )
    } else {
        this
    }
    return mitNaehrwerten.copy(
        category = category ?: andere.category,
        densityGPerMl = densityGPerMl ?: andere.densityGPerMl,
        pieceWeightG = pieceWeightG ?: andere.pieceWeightG,
        barcode = barcode ?: andere.barcode,
    )
}

/**
 * Führt [quelle] in [ziel] zusammen: Rezeptzeilen, Einkaufsartikel und Tagebucheinträge umhängen (die Trigger merken
 * die Eltern vor), Quelle löschen, dann das Ziel um die Angaben der Quelle ergänzen – geschrieben nur, wenn sich etwas
 * ändert. Ohne eigene Transaktion; der Aufrufer hält sie. Gibt zurück, ob das Ziel geändert wurde.
 */
internal suspend fun zusammenfuehrenSchritte(db: FoodyDatenbank, quelle: ZutatEntity, ziel: ZutatEntity, jetzt: Long): Boolean {
    val dao = db.syncDao()
    dao.rezeptZeilenUmhaengen(quelle.id, ziel.id)
    dao.einkaufsartikelUmhaengen(quelle.id, ziel.id)
    dao.tagebuchUmhaengen(quelle.id, ziel.id)
    db.zutatDao().loeschen(quelle.id)
    val ergaenzt = ziel.ergaenztUm(quelle)
    if (ergaenzt == ziel) return false
    db.zutatDao().upsert(ergaenzt.copy(updatedAt = jetzt))
    return true
}
