package de.foody.sync.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Datensatztyp. Die Deklarationsreihenfolge ist die Abhängigkeitsreihenfolge (Spec 4.3). */
@Serializable
enum class RecordType(val wire: String) {
    @SerialName("ingredient") INGREDIENT("ingredient"),
    @SerialName("recipe") RECIPE("recipe"),
    @SerialName("meal_slot") MEAL_SLOT("meal_slot"),
    @SerialName("pantry_item") PANTRY_ITEM("pantry_item"),
    @SerialName("shopping_list") SHOPPING_LIST("shopping_list"),
    @SerialName("shopping_item") SHOPPING_ITEM("shopping_item"),
    /** Seit App-DB v8; der Name ist der Tabellenname, den die Sync-Trigger in die Outbox schreiben. */
    @SerialName("tagebuch_eintrag") TAGEBUCH_EINTRAG("tagebuch_eintrag"),
}

/**
 * Ein synchronisierter Datensatz. [payload] ist genau dann vorhanden, wenn [deleted] `false` ist.
 * [baseRev] sendet der Client beim Push (letzter bekannter Server-Stand), [rev] setzt der Server.
 */
@Serializable
data class SyncRecord(
    val id: String,
    val type: RecordType,
    val deleted: Boolean = false,
    val updatedAt: Long,
    val baseRev: Long? = null,
    val rev: Long? = null,
    val payload: JsonObject? = null,
)

@Serializable
data class PushRequest(val records: List<SyncRecord>)

@Serializable
enum class PushStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("merged") MERGED,
    @SerialName("rejected") REJECTED,
}

/**
 * Ergebnis je gepushtem Datensatz. Bei [PushStatus.MERGED] nennt [canonicalId] den Datensatz, in den zusammengeführt wurde;
 * [current] trägt bei Merge/Ablehnung durch eine Konfliktregel den Server-Stand.
 */
@Serializable
data class PushResult(
    val id: String,
    val type: RecordType,
    val status: PushStatus,
    val rev: Long? = null,
    val canonicalId: String? = null,
    val code: ErrorCode? = null,
    val current: SyncRecord? = null,
)

@Serializable
data class PushResponse(val results: List<PushResult>)

@Serializable
data class PullResponse(val records: List<SyncRecord>, val nextCursor: Long, val hasMore: Boolean)
