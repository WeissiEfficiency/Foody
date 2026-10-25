package de.foody.core.data.einkauf

import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.ShoppingCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/** Höchstens so viele Kacheln unter „Zuletzt verwendet“. */
private const val ZULETZT_MAX = 18

/**
 * Einkaufslisten der V3: lesen als Flows, schreiben in Transaktionen (die Trigger tragen alles in die Outbox).
 * Aktuell ist die zuletzt angelegte Liste. Regeln aus `:domain`: `EinkaufsBedarf`, `ShoppingDiff`, `EinkaufsAbgleich`,
 * `RezeptAufListe`, `EinkaufEingabe`, `ShoppingCatalog`.
 */
class EinkaufRepository(
    private val db: FoodyDatenbank,
    private val uhr: () -> Long,
    private val ignoriert: IgnorierteSchluessel,
) {
    fun aktuelleListeId(): Flow<String?> = db.einkaufslisteDao().alleFlow().map { it.firstOrNull()?.id }.distinctUntilChanged()

    fun liste(id: String): Flow<EinkaufsAnsicht?> = combine(
        db.einkaufslisteDao().nachIdFlow(id),
        db.einkaufsartikelDao().nachListeFlow(id),
        db.einkaufsartikelQuelleDao().nachListeFlow(id),
    ) { liste, artikel, quellen ->
        liste ?: return@combine null
        val quellenJe = quellen.groupBy { it.shoppingItemId }
        val alle = artikel.map { it.alsArtikel(quellenJe[it.id].orEmpty()) }
        val reihenfolge = ShoppingCatalog.sectionOrder.withIndex().associate { (i, s) -> s to i }
        val offen = alle.filter { !it.gekauft }.groupBy { it.abteilung }
            .toSortedMap(compareBy { reihenfolge[it] ?: Int.MAX_VALUE })
            .map { (name, liste) -> Abteilung(name, abteilungsEmoji(name), liste) }
        EinkaufsAnsicht(liste.id, liste.name, liste.rangeStart, liste.rangeEnd, offen, alle)
    }

    fun zuletzt(): Flow<List<ZuletztArtikel>> = db.einkaufsartikelDao().gekauftFlow().map { gekauft ->
        val gesehen = mutableListOf<String>()
        gekauft.filter { a ->
            (gesehen.none { ShoppingCatalog.sameItem(it, a.name) }).also { neu -> if (neu) gesehen += a.name }
        }.take(ZULETZT_MAX).map { a ->
            ZuletztArtikel(a.name, ShoppingCatalog.emojiFor(a.name, a.category), abteilungVon(a.category, a.name), a.ingredientId)
        }
    }

    fun fruehere(): Flow<List<ListenKopf>> = combine(
        db.einkaufslisteDao().alleFlow(),
        db.einkaufsartikelDao().alleFlow(),
    ) { listen, artikel ->
        val anzahl = artikel.groupingBy { it.listId }.eachCount()
        listen.drop(1).map { l ->
            ListenKopf(l.id, l.name, Instant.ofEpochMilli(l.createdAt).atZone(ZoneId.systemDefault()).toLocalDate(), anzahl[l.id] ?: 0)
        }
    }

    private fun EinkaufsartikelEntity.alsArtikel(quellen: List<EinkaufsartikelQuelleEntity>) = EinkaufsArtikel(
        id = id,
        name = name,
        emoji = ShoppingCatalog.emojiFor(name, category),
        abteilung = abteilungVon(category, name),
        menge = mengeText(amount, unit),
        notiz = note,
        gekauft = checked,
        plan = !manual,
        herkunft = herkunftText(quellen),
        ingredientId = ingredientId,
    )
}
