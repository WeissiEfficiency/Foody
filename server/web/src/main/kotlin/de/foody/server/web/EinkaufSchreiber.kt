package de.foody.server.web

import de.foody.domain.DateRange
import de.foody.domain.Dimension
import de.foody.domain.DiffType
import de.foody.domain.EinkaufEingabe
import de.foody.domain.EinkaufsAbgleich
import de.foody.domain.EinkaufsBedarf
import de.foody.domain.MealSlot
import de.foody.domain.MeasureUnit
import de.foody.domain.PantryItem
import de.foody.domain.Quantity
import de.foody.domain.RezeptAufListe
import de.foody.domain.ShoppingDiff
import de.foody.domain.ShoppingDiffEntry
import de.foody.domain.ShoppingNeed
import de.foody.server.ServerDeps
import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import de.foody.sync.protocol.SyncRecord
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Schreibt Einkaufslisten aus dem Browser – nur über `SyncService.push`, mit denselben Regeln wie die App
 * ([EinkaufsBedarf], [ShoppingDiff], [EinkaufsAbgleich]). Bestehende Datensätze werden auf dem gespeicherten Roh-JSON
 * geändert, damit Felder neuerer App-Versionen erhalten bleiben.
 */
internal class EinkaufSchreiber(private val deps: ServerDeps, private val daten: HaushaltsDaten) {
    private val store = RecordStore()

    /** Bedarf im Zeitraum; Planeinträge, deren Rezept es nicht mehr gibt, zählen nicht. */
    fun bedarf(household: String, von: LocalDate, bis: LocalDate, vorrat: Boolean, ausgeschlossen: Set<String> = emptySet()): List<ShoppingNeed> {
        val h = daten.fuer(household)
        val slots = h.plan.filter { it.rezeptId in h.rezepte }.map { MealSlot(it.id, it.datum, it.mahlzeit, it.rezeptId, it.portionen) }
        val pantry = if (vorrat) h.vorrat.map { PantryItem(it.zutatId, Quantity.of(it.menge, it.einheit)) } else emptyList()
        return EinkaufsBedarf.berechnen(DateRange(von, bis), slots, h.rezepte.mapValues { it.value.domain }, h.zutaten, pantry, ausgeschlossen)
    }

    /** Neue Liste aus dem Plan; null, wenn nichts zu kaufen ist (dann wird auch keine Liste angelegt). */
    fun anlegen(household: String, name: String, von: LocalDate, bis: LocalDate, vorrat: Boolean, ausgeschlossen: Set<String>): String? {
        val bedarf = bedarf(household, von, bis, vorrat, ausgeschlossen).filter { !it.toBuy.isZero() }
        if (bedarf.isEmpty()) return null
        val listId = UUID.randomUUID().toString()
        val jetzt = deps.clock.millis()
        val liste = ShoppingListPayload(name, von.toString(), bis.toString(), 1)
        // Liste vor ihren Einträgen: die Referenzprüfung des Servers verlangt sie
        val records = listOf(SyncRecord(
            id = listId, type = RecordType.SHOPPING_LIST, updatedAt = jetzt,
            payload = Protocol.json.encodeToJsonElement(ShoppingListPayload.serializer(), liste) as JsonObject,
        )) + bedarf.mapIndexed { i, n -> neuerEintrag(household, listId, n, i, jetzt) }
        return if (push(household, records) == Schreiben.Ok) listId else null
    }

    /** Vergleich der generierten Einträge mit einer Neuberechnung; null = Liste fremd, gelöscht oder ohne Zeitraum. */
    fun abgleich(household: String, listId: String, vorrat: Boolean): List<ShoppingDiffEntry<WebEintrag>>? {
        val h = daten.fuer(household)
        val liste = h.listen.firstOrNull { it.id == listId } ?: return null
        val von = liste.von ?: return null
        val bis = liste.bis ?: return null
        val neu = bedarf(household, von, bis, vorrat).filter { !it.toBuy.isZero() }
        val alt = h.eintraege.filter { it.listId == listId && !it.payload.manual }
        return ShoppingDiff.diff(alt, ::schluessel, ::menge, neu)
    }

    fun abgleichAnwenden(household: String, listId: String, vorrat: Boolean): Schreiben {
        val eintraege = abgleich(household, listId, vorrat) ?: return Schreiben.NichtGefunden
        val gespeicherteListe = gespeichert(household, RecordType.SHOPPING_LIST, listId) ?: return Schreiben.Weg
        val jetzt = deps.clock.millis()
        val h = daten.fuer(household)
        var reihenfolge = h.eintraege.filter { it.listId == listId }.maxOfOrNull { it.payload.sortOrder + 1 } ?: 0
        val records = mutableListOf<SyncRecord>()
        for (e in eintraege) when (e.type) {
            DiffType.ADDED -> records += neuerEintrag(household, listId, e.new!!, reihenfolge++, jetzt)
            DiffType.CHANGED -> {
                val alt = e.old!!
                val roh = gespeichert(household, RecordType.SHOPPING_ITEM, alt.id) ?: continue
                val n = e.new!!
                val basis = MeasureUnit.baseOf(n.toBuy.dimension)
                val abgehakt = EinkaufsAbgleich.bleibtAbgehakt(alt.payload.checked, menge(alt), n.toBuy)
                val felder = mutableMapOf(
                    "amount" to JsonPrimitive(n.toBuy.amountIn(basis).toPlainString()),
                    "unit" to JsonPrimitive(basis.name),
                    "checked" to JsonPrimitive(abgehakt),
                    "sources" to Protocol.json.encodeToJsonElement(ListSerializer(ShoppingItemPayload.Source.serializer()), herkunft(household, n)),
                )
                // Wie der Trigger der App: Zeitpunkt nur, wenn sich das Häkchen ändert
                if (abgehakt != alt.payload.checked) felder["checkedChangedAt"] = JsonPrimitive(jetzt)
                records += SyncRecord(id = alt.id, type = RecordType.SHOPPING_ITEM, updatedAt = jetzt, baseRev = roh.rev, payload = JsonObject(roh.payload!! + felder))
            }
            DiffType.REMOVED -> if (EinkaufsAbgleich.entfaelltLoeschen(e.old!!.payload.checked)) {
                records += SyncRecord(id = e.old!!.id, type = RecordType.SHOPPING_ITEM, deleted = true, updatedAt = jetzt, baseRev = e.old!!.rev)
            }
            DiffType.UNCHANGED -> Unit
        }
        val version = (gespeicherteListe.payload!!["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        records += SyncRecord(
            id = listId, type = RecordType.SHOPPING_LIST, updatedAt = jetzt, baseRev = gespeicherteListe.rev,
            payload = JsonObject(gespeicherteListe.payload!! + mapOf("version" to JsonPrimitive(version + 1))),
        )
        return push(household, records)
    }

    // --- Liste bearbeiten ---

    /** Liste des Haushalts oder – ohne [listId] – eine neue „Einkauf“; null = [listId] gehört nicht zum Haushalt. */
    private fun zielListe(household: String, listId: String?, records: MutableList<SyncRecord>, jetzt: Long): String? {
        if (!listId.isNullOrEmpty()) return listId.takeIf { id -> daten.fuer(household).listen.any { it.id == id } }
        val neu = UUID.randomUUID().toString()
        records += SyncRecord(
            id = neu, type = RecordType.SHOPPING_LIST, updatedAt = jetzt,
            payload = Protocol.json.encodeToJsonElement(ShoppingListPayload.serializer(), ShoppingListPayload(NEUE_LISTE, version = 1)) as JsonObject,
        )
        return neu
    }

    private fun naechsteReihenfolge(household: String, listId: String) =
        daten.fuer(household).eintraege.filter { it.listId == listId }.maxOfOrNull { it.payload.sortOrder + 1 } ?: 0

    /** Freitext wie in der App ([EinkaufEingabe]); liefert das Ergebnis und die Liste, auf der es gelandet ist. */
    fun eingabe(household: String, listId: String?, text: String): Pair<Schreiben, String?> {
        val jetzt = deps.clock.millis()
        val records = mutableListOf<SyncRecord>()
        val ziel = zielListe(household, listId, records, jetzt) ?: return Schreiben.NichtGefunden to null
        val aufListe = daten.fuer(household).eintraege.filter { it.listId == ziel }
        val aktion = EinkaufEingabe.entscheiden(text, aufListe.map { EinkaufEingabe.Eintrag(it.id, it.payload.name, it.payload.checked) })
            ?: return Schreiben.Ok to ziel
        records += when (aktion) {
            is EinkaufEingabe.Aktion.Abhaken -> abhakenRecord(household, aktion.id, true, jetzt) ?: return Schreiben.Weg to ziel
            is EinkaufEingabe.Aktion.Zurueck -> abhakenRecord(household, aktion.id, false, jetzt) ?: return Schreiben.Weg to ziel
            is EinkaufEingabe.Aktion.Neu -> neuerManuellerEintrag(ziel, null, aktion.name, null, aktion.abteilung, naechsteReihenfolge(household, ziel), jetzt)
        }
        return push(household, records) to ziel
    }

    /** Wie `setChecked` der App: Häkchen setzen und ans Ende rücken (Reihenfolge der Erledigten = zuletzt gekauft). */
    private fun abhakenRecord(household: String, id: String, erledigt: Boolean, jetzt: Long): SyncRecord? {
        val roh = gespeichert(household, RecordType.SHOPPING_ITEM, id) ?: return null
        val listId = (roh.payload!!["listId"] as? JsonPrimitive)?.content.orEmpty()
        val felder = mapOf(
            "checked" to JsonPrimitive(erledigt), "checkedChangedAt" to JsonPrimitive(jetzt),
            "sortOrder" to JsonPrimitive(naechsteReihenfolge(household, listId)),
        )
        return SyncRecord(id = id, type = RecordType.SHOPPING_ITEM, updatedAt = jetzt, baseRev = roh.rev, payload = JsonObject(roh.payload!! + felder))
    }

    private fun neuerManuellerEintrag(listId: String, zutatId: String?, name: String, menge: Quantity?, kategorie: String?, reihenfolge: Int, jetzt: Long): SyncRecord {
        val basis = menge?.let { MeasureUnit.baseOf(it.dimension) }
        val p = ShoppingItemPayload(
            listId = listId, ingredientId = zutatId, name = name, amount = basis?.let { menge.amountIn(it).toPlainString() }, unit = basis?.name,
            checked = false, checkedChangedAt = 0, manual = true, category = kategorie, sortOrder = reihenfolge, sources = emptyList(),
        )
        return SyncRecord(
            id = UUID.randomUUID().toString(), type = RecordType.SHOPPING_ITEM, updatedAt = jetzt,
            payload = Protocol.json.encodeToJsonElement(ShoppingItemPayload.serializer(), p) as JsonObject,
        )
    }

    /**
     * Bearbeiten-Modus: geänderte Notizen ([notizen] = Eintrag → Text, leer = entfernen) und optional ein Eintrag zum
     * Löschen. Fremde IDs → [Schreiben.NichtGefunden]; inzwischen gelöschter Eintrag → [Schreiben.Weg].
     */
    fun bearbeiten(household: String, listId: String, notizen: Map<String, String>, loeschen: String?): Schreiben {
        val h = daten.fuer(household)
        if (h.listen.none { it.id == listId }) return Schreiben.NichtGefunden
        val jetzt = deps.clock.millis()
        val records = mutableListOf<SyncRecord>()
        for ((id, text) in notizen) {
            val e = h.eintraege.firstOrNull { it.id == id && it.listId == listId } ?: continue
            val neu = text.trim().ifEmpty { null }
            if (neu == e.payload.note || id == loeschen) continue
            val roh = gespeichert(household, RecordType.SHOPPING_ITEM, id) ?: continue
            val wert = neu?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull
            records += SyncRecord(id = id, type = RecordType.SHOPPING_ITEM, updatedAt = jetzt, baseRev = roh.rev, payload = JsonObject(roh.payload!! + ("note" to wert)))
        }
        if (loeschen != null) {
            val roh = deps.db.tx { c -> store.get(c, household, RecordType.SHOPPING_ITEM, loeschen) }
            val listeDesEintrags = (roh?.payload?.get("listId") as? JsonPrimitive)?.content
            when {
                roh == null || (!roh.deleted && listeDesEintrags != listId) -> return Schreiben.NichtGefunden
                roh.deleted -> {
                    // Notizen trotzdem speichern; der Hinweis gilt dem schon gelöschten Eintrag
                    if (records.isNotEmpty()) push(household, records)
                    return Schreiben.Weg
                }
                else -> records += SyncRecord(id = loeschen, type = RecordType.SHOPPING_ITEM, deleted = true, updatedAt = jetzt, baseRev = roh.rev)
            }
        }
        return if (records.isEmpty()) Schreiben.Ok else push(household, records)
    }

    /** Liste samt Einträgen löschen – erst die Einträge, dann die Liste (keine lebenden Einträge ohne Liste). */
    fun listeLoeschen(household: String, listId: String): Schreiben {
        val roh = deps.db.tx { c -> store.get(c, household, RecordType.SHOPPING_LIST, listId) } ?: return Schreiben.NichtGefunden
        if (roh.deleted) return Schreiben.Weg
        val jetzt = deps.clock.millis()
        val records = daten.fuer(household).eintraege.filter { it.listId == listId }.map {
            SyncRecord(id = it.id, type = RecordType.SHOPPING_ITEM, deleted = true, updatedAt = jetzt, baseRev = it.rev)
        } + SyncRecord(id = listId, type = RecordType.SHOPPING_LIST, deleted = true, updatedAt = jetzt, baseRev = roh.rev)
        return push(household, records)
    }

    /**
     * Rezept auf eine Liste ([RezeptAufListe]); ohne [listId] auf eine neue „Einkauf“. Liefert (Anzahl Zutaten, Liste)
     * oder null, wenn Rezept oder Liste nicht zum Haushalt gehören.
     */
    fun rezeptAufListe(household: String, rezeptId: String, portionen: Int, listId: String?, heute: LocalDate): Pair<Int, String>? {
        val h = daten.fuer(household)
        val rezept = h.rezepte[rezeptId] ?: return null
        val jetzt = deps.clock.millis()
        val records = mutableListOf<SyncRecord>()
        val ziel = zielListe(household, listId, records, jetzt) ?: return null
        val bedarf = RezeptAufListe.bedarf(rezept.domain, portionen, h.zutaten, heute)
        val bestand = h.eintraege.filter { it.listId == ziel }
        var reihenfolge = naechsteReihenfolge(household, ziel)
        val aenderungen = RezeptAufListe.zusammenfuehren(bedarf, bestand.map {
            RezeptAufListe.Bestand(it.id, it.payload.ingredientId, it.payload.amount?.let { _ -> menge(it) }, it.payload.manual, it.payload.checked)
        })
        for (a in aenderungen) records += when (a) {
            is RezeptAufListe.Aenderung.Erhoehen -> {
                val roh = gespeichert(household, RecordType.SHOPPING_ITEM, a.id) ?: continue
                val basis = MeasureUnit.baseOf(a.neu.dimension)
                SyncRecord(id = a.id, type = RecordType.SHOPPING_ITEM, updatedAt = jetzt, baseRev = roh.rev, payload = JsonObject(roh.payload!! + mapOf(
                    "amount" to JsonPrimitive(a.neu.amountIn(basis).toPlainString()), "unit" to JsonPrimitive(basis.name),
                )))
            }
            is RezeptAufListe.Aenderung.Neu -> {
                val z = h.zutaten[a.bedarf.ingredientId]
                neuerManuellerEintrag(ziel, a.bedarf.ingredientId, z?.name ?: a.bedarf.ingredientId, a.bedarf.toBuy, z?.category, reihenfolge++, jetzt)
            }
        }
        if (push(household, records) != Schreiben.Ok) return null
        return bedarf.size to ziel
    }

    private fun neuerEintrag(household: String, listId: String, n: ShoppingNeed, reihenfolge: Int, jetzt: Long): SyncRecord {
        val zutat = daten.fuer(household).zutaten[n.ingredientId]
        val basis = MeasureUnit.baseOf(n.toBuy.dimension)
        val p = ShoppingItemPayload(
            listId = listId, ingredientId = n.ingredientId, name = zutat?.name ?: n.ingredientId,
            amount = n.toBuy.amountIn(basis).toPlainString(), unit = basis.name, checked = false, checkedChangedAt = 0,
            manual = false, category = zutat?.category, sortOrder = reihenfolge, sources = herkunft(household, n),
        )
        return SyncRecord(
            id = UUID.randomUUID().toString(), type = RecordType.SHOPPING_ITEM, updatedAt = jetzt,
            payload = Protocol.json.encodeToJsonElement(ShoppingItemPayload.serializer(), p) as JsonObject,
        )
    }

    /** Herkunft wie in der App: Rezeptname, Datum des Planeintrags, Beitrag in Basiseinheit. */
    private fun herkunft(household: String, n: ShoppingNeed): List<ShoppingItemPayload.Source> {
        val h = daten.fuer(household)
        val plan = h.plan.associateBy { it.id }
        return n.sources.map { s ->
            val basis = MeasureUnit.baseOf(s.contributed.dimension)
            ShoppingItemPayload.Source(
                id = UUID.randomUUID().toString(), mealSlotId = s.mealSlotId, recipeIngredientId = s.recipeIngredientId,
                recipeName = h.rezepte[s.recipeId]?.payload?.name.orEmpty(),
                // Quellen stammen aus den Planeinträgen des Haushalts – das Datum ist immer da
                date = plan.getValue(s.mealSlotId).datum.toString(),
                amount = s.contributed.amountIn(basis).toPlainString(), unit = basis.name,
            )
        }
    }

    private fun gespeichert(household: String, typ: RecordType, id: String) =
        deps.db.tx { c -> store.get(c, household, typ, id) }?.takeIf { !it.deleted && it.payload != null }

    private fun push(household: String, records: List<SyncRecord>): Schreiben =
        if (deps.sync.push(household, records).results.any { it.status == PushStatus.REJECTED }) Schreiben.Abgelehnt else Schreiben.Ok

    companion object {
        const val NEUE_LISTE = "Einkauf"

        fun menge(e: WebEintrag): Quantity {
            val einheit = e.payload.unit?.let { u -> MeasureUnit.entries.firstOrNull { it.name == u } }
            val wert = e.payload.amount?.toBigDecimalOrNull()
            return if (einheit != null && wert != null) Quantity.of(wert, einheit) else Quantity.zero(Dimension.COUNT)
        }

        fun schluessel(e: WebEintrag): String = "${e.payload.ingredientId}|${menge(e).dimension}"
    }
}
