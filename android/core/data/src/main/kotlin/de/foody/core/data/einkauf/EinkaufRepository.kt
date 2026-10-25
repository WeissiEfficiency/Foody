package de.foody.core.data.einkauf

import androidx.room.withTransaction
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.domain.EinkaufEingabe
import de.foody.domain.IngredientCatalog
import de.foody.domain.MeasureUnit
import de.foody.domain.Quantity
import de.foody.domain.RezeptAufListe
import de.foody.domain.ShoppingCatalog
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
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

    /**
     * Gekaufte Artikel aller Listen, neueste zuerst, einer je Artikel (gleicher Schlüssel wie `ShoppingCatalog.sameItem`).
     * Bricht nach [ZULETZT_MAX] ab und rechnet nicht auf dem Main-Thread: die Kaufhistorie wächst über Jahre.
     */
    fun zuletzt(): Flow<List<ZuletztArtikel>> = db.einkaufsartikelDao().gekauftFlow().map { gekauft ->
        gekauft.asSequence()
            .distinctBy { IngredientCatalog.canonicalName(it.name).lowercase() }
            .take(ZULETZT_MAX)
            .map { a -> ZuletztArtikel(a.name, ShoppingCatalog.emojiFor(a.name, a.category), abteilungVon(a.category, a.name), a.ingredientId) }
            .toList()
    }.flowOn(Dispatchers.Default)

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

    // ---- Liste aus dem Plan ----

    suspend fun planVorschau(von: LocalDate, bis: LocalDate): List<VorschauZeile> =
        PlanBedarf.laden(db, von, bis).let { p -> p.bedarf.map(p::zeile) }

    /**
     * Neue Liste „26.10.–1.11.“ mit allen nicht abgewählten Plan-Artikeln samt Herkunft; die abgewählten werden
     * ignoriert, die offenen manuellen Artikel der bisherigen Liste wandern mit. Bleibt nichts übrig: `null`, keine Liste.
     */
    suspend fun ausPlanAnlegen(von: LocalDate, bis: LocalDate, abgewaehlt: Set<String>): String? {
        val id = db.withTransaction {
            val p = PlanBedarf.laden(db, von, bis)
            val bedarf = p.bedarf.filter { it.key !in abgewaehlt }
            if (bedarf.isEmpty()) return@withTransaction null
            val vorher = db.einkaufslisteDao().alleFlow().first().firstOrNull()
            // Später angelegt als die bisherige, auch wenn die Uhr gleich steht: die neue ist danach die aktuelle
            val jetzt = maxOf(uhr(), (vorher?.createdAt ?: Long.MIN_VALUE) + 1)
            val liste = EinkaufslisteEntity(
                id = neueId(), name = listenName(von, bis), rangeStart = von, rangeEnd = bis, createdAt = jetzt, updatedAt = jetzt,
            )
            db.einkaufslisteDao().upsert(liste)
            bedarf.forEachIndexed { i, n ->
                val a = p.artikel(n, liste.id, i, uhr())
                db.einkaufsartikelDao().upsert(a)
                p.quellen(n, a.id).forEach { db.einkaufsartikelQuelleDao().upsert(it) }
            }
            vorher?.let { alt ->
                db.einkaufsartikelDao().nachListe(alt.id).filter { it.manual && !it.checked }.forEachIndexed { i, m ->
                    speichern(m.copy(listId = liste.id, sortOrder = bedarf.size + i))
                }
            }
            liste.id
        } ?: return null
        ignoriert.hinzufuegen(id, abgewaehlt)
        return id
    }

    // ---- Abgleich ----

    /**
     * Was „Übernehmen“ an der Liste ändern würde; neu bei jeder Änderung an Liste, Artikeln, Plan, Rezepten, Zeilen,
     * Zutaten oder ignorierten Schlüsseln. Liste ohne Zeitraum oder fehlend: leer.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun abgleich(listeId: String): Flow<List<AbgleichZeile>> = db.einkaufslisteDao().nachIdFlow(listeId).flatMapLatest { liste ->
        val von = liste?.rangeStart
        val bis = liste?.rangeEnd
        if (von == null || bis == null) return@flatMapLatest flowOf(emptyList())
        combine(
            db.einkaufsartikelDao().nachListeFlow(listeId),
            db.planEintragDao().zwischen(von, bis),
            db.rezeptDao().alleFlow(),
            db.rezeptZutatDao().alleFlow(),
            db.zutatDao().alleFlow(),
        ) { artikel, _, _, _, _ -> artikel }
            .combine(ignoriert.fluss(listeId)) { artikel, ign -> artikel to ign }
            .map { (artikel, ign) -> aenderungen(artikel, PlanBedarf.laden(db, von, bis), ign).map { it.zeile } }
    }.distinctUntilChanged()

    /** Wendet alle Zeilen außer den abgewählten an (die ignoriert werden); gibt die Zahl der angewendeten zurück. */
    suspend fun abgleichen(listeId: String, abgewaehlt: Set<String>): Int {
        val angewendet = db.withTransaction {
            val liste = db.einkaufslisteDao().nachId(listeId) ?: return@withTransaction 0
            val von = liste.rangeStart ?: return@withTransaction 0
            val bis = liste.rangeEnd ?: return@withTransaction 0
            val p = PlanBedarf.laden(db, von, bis)
            val artikel = db.einkaufsartikelDao().nachListe(listeId)
            var reihenfolge = (artikel.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val anwenden = aenderungen(artikel, p, ignoriert.lesen(listeId)).filter { it.zeile.schluessel !in abgewaehlt }
            for (a in anwenden) {
                val neu = a.neu
                when (a.zeile.art) {
                    AbgleichArt.NEU -> {
                        val e = p.artikel(neu!!, listeId, reihenfolge++, uhr())
                        db.einkaufsartikelDao().upsert(e)
                        p.quellen(neu, e.id).forEach { db.einkaufsartikelQuelleDao().upsert(it) }
                    }
                    AbgleichArt.ENTFAELLT -> db.einkaufsartikelDao().loeschen(a.alt!!.id)
                    AbgleichArt.MEHR, AbgleichArt.WENIGER, AbgleichArt.WIEDER_OFFEN -> {
                        val alt = a.alt!!
                        speichern(
                            alt.copy(amount = neu!!.toBuy.amountIn(p.basis(neu)), unit = p.basis(neu), checked = alt.checked && a.zeile.art != AbgleichArt.WIEDER_OFFEN),
                        )
                        db.einkaufsartikelQuelleDao().loeschenNachArtikel(alt.id)
                        p.quellen(neu, alt.id).forEach { db.einkaufsartikelQuelleDao().upsert(it) }
                    }
                }
            }
            anwenden.size
        }
        ignoriert.hinzufuegen(listeId, abgewaehlt)
        return angewendet
    }

    // ---- Rezept → Liste ----

    /**
     * Die Zutaten eines Rezepts für [portionen] auf die aktuelle Liste (ohne Liste: neue „Einkauf“) nach
     * `RezeptAufListe`: gleiche Zutat + Dimension addiert auf einen offenen manuellen Artikel, sonst neuer manueller.
     * Gibt die Zahl der betroffenen Artikel zurück; nichts einzukaufen oder Rezept fehlt → 0, keine Liste.
     */
    suspend fun rezeptAufListe(rezeptId: String, portionen: Int): Int = db.withTransaction {
        val rezept = db.rezeptDao().nachId(rezeptId) ?: return@withTransaction 0
        val zutaten = db.zutatDao().alle().associateBy { it.id }
        val heute = Instant.ofEpochMilli(uhr()).atZone(ZoneId.systemDefault()).toLocalDate()
        val bedarf = RezeptAufListe.bedarf(
            rezept.alsRecipe(db.rezeptZutatDao().nachRezept(rezeptId)), portionen, zutaten.mapValues { it.value.alsIngredient() }, heute,
        )
        if (bedarf.isEmpty()) return@withTransaction 0
        val liste = listeOderNeu(db.einkaufslisteDao().alleFlow().first().firstOrNull()?.id)
        val vorhanden = db.einkaufsartikelDao().nachListe(liste)
        val bestand = vorhanden.map {
            RezeptAufListe.Bestand(it.id, it.ingredientId, it.unit?.let { u -> Quantity.of(it.amount ?: BigDecimal.ZERO, u) }, it.manual, it.checked)
        }
        var reihenfolge = (vorhanden.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val aenderungen = RezeptAufListe.zusammenfuehren(bedarf, bestand)
        for (a in aenderungen) when (a) {
            is RezeptAufListe.Aenderung.Erhoehen -> {
                val basis = MeasureUnit.baseOf(a.neu.dimension)
                speichern(vorhanden.first { it.id == a.id }.copy(amount = a.neu.amountIn(basis), unit = basis))
            }
            is RezeptAufListe.Aenderung.Neu -> {
                val n = a.bedarf
                val basis = MeasureUnit.baseOf(n.toBuy.dimension)
                val z = zutaten[n.ingredientId]
                speichern(
                    EinkaufsartikelEntity(
                        id = neueId(), listId = liste, ingredientId = n.ingredientId, name = z?.canonicalName ?: n.ingredientId,
                        amount = n.toBuy.amountIn(basis), unit = basis, manual = true, category = z?.category, sortOrder = reihenfolge++,
                    ),
                )
            }
        }
        aenderungen.size
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
