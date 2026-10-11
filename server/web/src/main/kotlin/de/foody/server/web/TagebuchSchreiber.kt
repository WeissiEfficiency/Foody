package de.foody.server.web

import de.foody.domain.IngredientCatalog
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Naehrwerte
import de.foody.domain.Tagebuch
import de.foody.domain.TagebuchArt
import de.foody.server.ServerDeps
import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import de.foody.sync.protocol.TagebuchPayload
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Ergebnis beim Eintragen einer Zutat. */
internal sealed interface ZutatEintragen {
    data class Ergebnis(val schreiben: Schreiben) : ZutatEintragen
    data object Unbekannt : ZutatEintragen
    data object NichtBerechenbar : ZutatEintragen
}

/**
 * Schreibt Tagebuch-Einträge aus dem Browser – nur über `SyncService.push`. Werte werden beim Eintragen mit den Regeln
 * der App berechnet ([Tagebuch]) und danach festgehalten; Ändern skaliert sie nur ([Tagebuch.skaliert]).
 */
internal class TagebuchSchreiber(private val deps: ServerDeps, private val daten: HaushaltsDaten) {
    private val store = RecordStore()

    /** Planeintrag als gegessen – höchstens einmal (geprüft unter der [Haushaltssperre]); 1 Portion wie in der App. */
    fun gegessen(household: String, planEintragId: String): Schreiben = Haushaltssperre.mit(household) {
        val h = daten.fuer(household)
        val slot = h.plan.firstOrNull { it.id == planEintragId } ?: return@mit Schreiben.NichtGefunden
        val rezept = h.rezepte[slot.rezeptId] ?: return@mit Schreiben.NichtGefunden
        if (h.tagebuch.any { it.planEintragId == planEintragId }) return@mit Schreiben.Ok
        val w = Tagebuch.naehrwerteRezept(rezept.domain, h.zutaten, BigDecimal.ONE)
        neu(household, TagebuchPayload(
            datum = slot.datum.toString(), mahlzeit = (Mahlzeit.ausText(slot.mahlzeit) ?: Mahlzeit.ABENDESSEN).name, art = TagebuchArt.REZEPT.name,
            name = rezept.payload.name, rezeptId = rezept.id, planEintragId = planEintragId, portionen = BigDecimal.ONE.toPlainString(),
        ).mit(w))
    }

    fun rezept(household: String, tag: LocalDate, mahlzeit: Mahlzeit, rezeptId: String, portionen: BigDecimal): Schreiben {
        val h = daten.fuer(household)
        val rezept = h.rezepte[rezeptId] ?: return Schreiben.NichtGefunden
        val w = Tagebuch.naehrwerteRezept(rezept.domain, h.zutaten, portionen)
        return neu(household, TagebuchPayload(
            datum = tag.toString(), mahlzeit = mahlzeit.name, art = TagebuchArt.REZEPT.name, name = rezept.payload.name,
            rezeptId = rezept.id, portionen = portionen.toPlainString(),
        ).mit(w))
    }

    /** Zutat aus dem Katalog (Name oder vereinheitlichter Name wie in der App); keine neue Zutat. */
    fun zutat(household: String, tag: LocalDate, mahlzeit: Mahlzeit, zutatName: String, menge: BigDecimal, einheit: MeasureUnit): ZutatEintragen {
        val h = daten.fuer(household)
        val name = zutatName.trim()
        val zutat = h.zutaten.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: h.zutaten.values.firstOrNull { it.name.equals(IngredientCatalog.canonicalName(name), ignoreCase = true) }
            ?: return ZutatEintragen.Unbekannt
        val w = Tagebuch.naehrwerteZutat(zutat, menge, einheit) ?: return ZutatEintragen.NichtBerechenbar
        return ZutatEintragen.Ergebnis(neu(household, TagebuchPayload(
            datum = tag.toString(), mahlzeit = mahlzeit.name, art = TagebuchArt.ZUTAT.name, name = zutat.name,
            zutatId = zutat.id, menge = menge.toPlainString(), einheit = einheit.name,
        ).mit(w)))
    }

    fun frei(household: String, tag: LocalDate, mahlzeit: Mahlzeit, name: String, kcal: Int, eiweiss: BigDecimal?, kh: BigDecimal?, fett: BigDecimal?): Schreiben =
        neu(household, TagebuchPayload(datum = tag.toString(), mahlzeit = mahlzeit.name, art = TagebuchArt.FREI.name, name = name.trim())
            .mit(Tagebuch.freiWerte(kcal, eiweiss, kh, fett)))

    /** Werte im Formular: Rezept → [portionen], Zutat → [menge] (skaliert); frei → [name] und neue Werte aus [frei]. */
    fun aendern(household: String, id: String, mahlzeit: Mahlzeit, portionen: BigDecimal?, menge: BigDecimal?, name: String?, frei: Naehrwerte?): Schreiben {
        val roh = deps.db.tx { c -> store.get(c, household, RecordType.TAGEBUCH_EINTRAG, id) } ?: return Schreiben.NichtGefunden
        if (roh.deleted || roh.payload == null) return Schreiben.Weg
        val alt = daten.fuer(household).tagebuch.firstOrNull { it.id == id } ?: return Schreiben.Weg
        val felder = mutableMapOf<String, JsonElement>("mahlzeit" to JsonPrimitive(mahlzeit.name))
        val werte = when (alt.art) {
            TagebuchArt.REZEPT -> Tagebuch.skaliert(alt.art, alt.werte, alt.portionen, portionen).also { portionen?.let { felder["portionen"] = JsonPrimitive(it.toPlainString()) } }
            TagebuchArt.ZUTAT -> Tagebuch.skaliert(alt.art, alt.werte, alt.menge, menge).also { menge?.let { felder["menge"] = JsonPrimitive(it.toPlainString()) } }
            TagebuchArt.FREI -> (frei ?: alt.werte).also { name?.let { felder["name"] = JsonPrimitive(it.trim()) } }
        }
        felder += wertFelder(werte)
        return push(household, SyncRecord(id = id, type = RecordType.TAGEBUCH_EINTRAG, updatedAt = deps.clock.millis(), baseRev = roh.rev, payload = JsonObject(roh.payload!! + felder)))
    }

    fun loeschen(household: String, id: String): Schreiben {
        val roh = deps.db.tx { c -> store.get(c, household, RecordType.TAGEBUCH_EINTRAG, id) } ?: return Schreiben.NichtGefunden
        if (roh.deleted) return Schreiben.Weg
        return push(household, SyncRecord(id = id, type = RecordType.TAGEBUCH_EINTRAG, deleted = true, updatedAt = deps.clock.millis(), baseRev = roh.rev))
    }

    private fun TagebuchPayload.mit(w: Naehrwerte) = copy(
        energieKj = w.energieKj?.toPlainString(), eiweiss = w.eiweiss?.toPlainString(), kohlenhydrate = w.kohlenhydrate?.toPlainString(),
        fett = w.fett?.toPlainString(), vollstaendig = w.vollstaendig,
    )

    private fun wertFelder(w: Naehrwerte): Map<String, JsonElement> {
        fun j(x: BigDecimal?): JsonElement = x?.let { JsonPrimitive(it.toPlainString()) } ?: JsonNull
        return mapOf("energieKj" to j(w.energieKj), "eiweiss" to j(w.eiweiss), "kohlenhydrate" to j(w.kohlenhydrate), "fett" to j(w.fett),
            "vollstaendig" to JsonPrimitive(w.vollstaendig))
    }

    private fun neu(household: String, p: TagebuchPayload): Schreiben = push(household, SyncRecord(
        id = UUID.randomUUID().toString(), type = RecordType.TAGEBUCH_EINTRAG, updatedAt = deps.clock.millis(),
        payload = Protocol.json.encodeToJsonElement(TagebuchPayload.serializer(), p) as JsonObject,
    ))

    private fun push(household: String, record: SyncRecord): Schreiben =
        if (deps.sync.push(household, listOf(record)).results.single().status == PushStatus.REJECTED) Schreiben.Abgelehnt else Schreiben.Ok
}
