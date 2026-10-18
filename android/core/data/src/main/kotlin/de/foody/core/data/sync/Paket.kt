package de.foody.core.data.sync

import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecipePayload
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import de.foody.api.protocol.decode
import java.io.File
import kotlinx.serialization.json.JsonObject

/**
 * Eine Fassung eines Outbox-Eintrags: Typ, ID und `queuedAt` zum Zeitpunkt des Paketbaus. Ein erneutes Vormerken
 * ergibt eine andere Fassung; so lassen sich in einem Lauf schon gesendete Fassungen ausnehmen.
 */
data class Fassung(val typ: RecordType, val id: String, val vorgemerktUm: Long)

/**
 * Ein zu sendender Datensatz aus der Outbox.
 *
 * - [vorgemerktUm]: `queuedAt` des Outbox-Eintrags beim Bauen. Nur wenn der Eintrag nach dem Push noch dieses
 *   `queuedAt` hat, wurde nichts nachträglich geändert und er darf die Outbox verlassen.
 * - [fotoProblem]: Problem-Code, der nach dem Senden am Datensatz vermerkt wird (`photo_unsyncable`: das eigene Foto ist
 *   nicht übertragbar, das Rezept geht ohne Foto hinaus).
 * - [fotoHash]: Hash des Fotos, das im Rezept steht (eigenes Foto oder offener Fotowunsch); `null` ohne Foto.
 * - [fotoDatei]: die eigene Datei zu [fotoHash], die bei Bedarf hochgeladen wird; `null` bei einem Fotowunsch (das Foto
 *   stammt vom Server, er hat es) und ohne Foto.
 */
data class Ausstehend(
    val record: SyncRecord,
    val vorgemerktUm: Long,
    val fotoProblem: String?,
    val fotoHash: String?,
    val fotoDatei: File?,
) {
    /** Diese Fassung des Outbox-Eintrags. */
    val schluessel: Fassung get() = Fassung(record.type, record.id, vorgemerktUm)

    /**
     * Derselbe Datensatz ohne Foto und mit dem Problem `photo_unsyncable` – für ein Foto, das der Server beim Hochladen
     * dauerhaft ablehnt. Würde das Rezept stattdessen warten, lüde jeder Lauf dieselben Bytes erneut hoch.
     */
    fun ohneFoto(): Ausstehend {
        if (record.type != RecordType.RECIPE || record.deleted) return this
        val payload = RecordType.RECIPE.decode(requireNotNull(record.payload)) as RecipePayload
        val neu = Protocol.json.encodeToJsonElement(RecipePayload.serializer(), payload.copy(photo = null)) as JsonObject
        return copy(record = record.copy(payload = neu), fotoProblem = SyncSpeicher.FOTO_NICHT_UEBERTRAGBAR, fotoHash = null, fotoDatei = null)
    }
}
