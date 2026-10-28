package de.foody.core.scan

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.IOException

/**
 * Produktsuche bei Open Food Facts. Gesendet wird nur die Produktnummer; der Client kennt bewusst nicht die
 * lokale Zertifizierungsstelle des Sync-Servers (eigener [http]).
 */
class OffProduktSuche(private val http: HttpClient, private val appVersion: String) : ProduktSuche {
    override suspend fun suche(code: String): ProduktSuche.Antwort = try {
        // prepareGet/execute: Der Body wird gezählt gelesen statt ungeprüft in den Speicher
        http.prepareGet("https://world.openfoodfacts.org/api/v2/product/$code.json") {
            header(HttpHeaders.UserAgent, "Foody/$appVersion (privat; Android)")
            parameter("fields", "product_name,product_name_de,nutriments,nutrition_data_per")
        }.execute { antwort ->
            when (antwort.status) {
                HttpStatusCode.OK -> OpenFoodFacts.auswerten(Json.parseToJsonElement(antwort.begrenztLesen()).jsonObject, code)
                    ?.let { ProduktSuche.Antwort.Gefunden(it) } ?: ProduktSuche.Antwort.Unbekannt
                HttpStatusCode.NotFound -> ProduktSuche.Antwort.Unbekannt
                else -> ProduktSuche.Antwort.Offline
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Kein Netz, Zeitüberschreitung, kaputte Antwort: Foto anbieten
        ProduktSuche.Antwort.Offline
    }

    /** Body als Text, höchstens [MAX_ANTWORT_BYTES]; mehr gilt als kaputte Antwort (dann Foto anbieten). */
    private suspend fun HttpResponse.begrenztLesen(): String {
        if ((contentLength() ?: 0) > MAX_ANTWORT_BYTES) throw IOException(ZU_GROSS)
        val bytes = bodyAsChannel().readRemaining(MAX_ANTWORT_BYTES + 1).readByteArray()
        if (bytes.size > MAX_ANTWORT_BYTES) throw IOException(ZU_GROSS)
        return bytes.decodeToString()
    }

    companion object {
        /** Eine Produktantwort mit den angefragten Feldern hat wenige kB; 256 kB lassen viel Luft. */
        const val MAX_ANTWORT_BYTES = 256L * 1024
        private const val ZU_GROSS = "Antwort von Open Food Facts zu groß"
    }
}
