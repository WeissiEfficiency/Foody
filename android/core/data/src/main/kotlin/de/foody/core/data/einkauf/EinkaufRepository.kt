package de.foody.core.data.einkauf

import androidx.room.withTransaction
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.EinkaufEingabe
import de.foody.domain.ShoppingCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Höchstens so viele Kacheln unter „Zuletzt verwendet“. */
private const val ZULETZT_MAX = 18

/** Längster Artikelname. */
private const val NAME_MAX = 60

/** Name einer Liste ohne Zeitraum. */
internal const val LEERE_LISTE = "Einkauf"

private fun neueId() = UUID.randomUUID().toString()

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

    // ---- Kacheln, Eingabe, Details ----

    suspend fun abhaken(id: String, an: Boolean) = aendern(id) { it.copy(checked = an) }

    /** Katalog- oder eigene Kachel: offen → gekauft, gekauft → wieder offen, sonst neu. Gibt die ID der Liste zurück. */
    suspend fun antippen(listeId: String?, name: String, abteilung: String, ingredientId: String? = null): String =
        db.withTransaction {
            val liste = listeOderNeu(listeId)
            val gleiche = db.einkaufsartikelDao().nachListe(liste).filter { ShoppingCatalog.sameItem(it.name, name) }
            val offen = gleiche.firstOrNull { !it.checked }
            val gekauft = gleiche.firstOrNull { it.checked }
            when {
                offen != null -> speichern(offen.copy(checked = true))
                gekauft != null -> speichern(gekauft.copy(checked = false))
                else -> neu(liste, name, abteilung, ingredientId)
            }
            liste
        }

    /** Zuletzt-Kachel: gekauft → wieder offen, schon offen → nichts, sonst neu (ohne Menge). */
    suspend fun zuletztAntippen(listeId: String?, z: ZuletztArtikel): String = db.withTransaction {
        val liste = listeOderNeu(listeId)
        val gleiche = db.einkaufsartikelDao().nachListe(liste).filter { ShoppingCatalog.sameItem(it.name, z.name) }
        if (gleiche.none { !it.checked }) {
            val gekauft = gleiche.firstOrNull()
            if (gekauft != null) speichern(gekauft.copy(checked = false)) else neu(liste, z.name, z.abteilung, z.ingredientId)
        }
        liste
    }

    /** „Was willst du einkaufen?“ nach `EinkaufEingabe`; leerer Text → `null`, nichts geschieht. */
    suspend fun eingeben(listeId: String?, text: String): String? {
        if (text.isBlank()) return null
        return db.withTransaction {
            val liste = listeOderNeu(listeId)
            val eintraege = db.einkaufsartikelDao().nachListe(liste)
            when (val a = EinkaufEingabe.entscheiden(text, eintraege.map { EinkaufEingabe.Eintrag(it.id, it.name, it.checked) })) {
                is EinkaufEingabe.Aktion.Abhaken -> eintraege.first { it.id == a.id }.let { speichern(it.copy(checked = true)) }
                is EinkaufEingabe.Aktion.Zurueck -> eintraege.first { it.id == a.id }.let { speichern(it.copy(checked = false)) }
                is EinkaufEingabe.Aktion.Neu -> neu(liste, a.name, a.abteilung, null)
                null -> Unit
            }
            liste
        }
    }

    /** Name 1–60 Zeichen (getrimmt), leere Notiz → keine; die Abteilung landet in `category`. */
    suspend fun details(id: String, name: String, notiz: String, abteilung: String) {
        val n = name.trim()
        require(n.length in 1..NAME_MAX) { "Name muss 1–$NAME_MAX Zeichen haben" }
        aendern(id) { it.copy(name = n, note = notiz.trim().ifEmpty { null }, category = abteilung) }
    }

    /** Löscht den Artikel samt Herkunft; ein Plan-Artikel kommt beim Abgleich nicht wieder. */
    suspend fun loeschen(id: String): GeloeschterArtikel? {
        val weg = db.withTransaction {
            val a = db.einkaufsartikelDao().nachId(id) ?: return@withTransaction null
            val herkunft = db.einkaufsartikelQuelleDao().nachArtikel(id)
            db.einkaufsartikelDao().loeschen(id)
            GeloeschterArtikel(a, herkunft)
        } ?: return null
        schluessel(weg.artikel)?.let { ignoriert.hinzufuegen(weg.artikel.listId, listOf(it)) }
        return weg
    }

    /** „Rückgängig“: dieselbe ID, Herkunft zurück; fehlt die Liste inzwischen, geschieht nichts. */
    suspend fun wiederherstellen(g: GeloeschterArtikel) {
        val da = db.withTransaction {
            if (db.einkaufslisteDao().nachId(g.artikel.listId) == null) return@withTransaction false
            db.einkaufsartikelDao().upsert(g.artikel.copy(updatedAt = uhr()))
            g.herkunft.forEach { db.einkaufsartikelQuelleDao().upsert(it) }
            true
        }
        if (da) schluessel(g.artikel)?.let { ignoriert.entfernen(g.artikel.listId, it) }
    }

    suspend fun listeLoeschen(id: String) {
        db.einkaufslisteDao().loeschen(id)
        ignoriert.vergessen(id)
    }

    /** Die gegebene Liste, wenn es sie gibt; sonst eine neue leere „Einkauf“. Nur innerhalb einer Transaktion. */
    private suspend fun listeOderNeu(listeId: String?): String {
        if (listeId != null && db.einkaufslisteDao().nachId(listeId) != null) return listeId
        val jetzt = uhr()
        val neu = EinkaufslisteEntity(id = neueId(), name = LEERE_LISTE, createdAt = jetzt, updatedAt = jetzt)
        db.einkaufslisteDao().upsert(neu)
        return neu.id
    }

    private suspend fun neu(listeId: String, name: String, abteilung: String, ingredientId: String?) {
        val reihenfolge = (db.einkaufsartikelDao().nachListe(listeId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        speichern(
            EinkaufsartikelEntity(
                id = neueId(), listId = listeId, ingredientId = ingredientId, name = name.trim(), manual = true,
                category = abteilung, sortOrder = reihenfolge,
            ),
        )
    }

    private suspend fun speichern(a: EinkaufsartikelEntity) = db.einkaufsartikelDao().upsert(a.copy(updatedAt = uhr()))

    private suspend fun aendern(id: String, f: (EinkaufsartikelEntity) -> EinkaufsartikelEntity) = db.withTransaction {
        db.einkaufsartikelDao().nachId(id)?.let { speichern(f(it)) }
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
