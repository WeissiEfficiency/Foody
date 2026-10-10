package de.foody.app.scan

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Produktsuche bei Open Food Facts. Gesendet wird nur die Produktnummer; der Client kennt bewusst nicht die
 * lokale Zertifizierungsstelle des Sync-Servers (eigener [http]).
 */
class OffProduktSuche(private val http: HttpClient, private val appVersion: String) : ProduktSuche {
    override suspend fun suche(code: String): ProduktSuche.Antwort = try {
        val antwort = http.get("https://world.openfoodfacts.org/api/v2/product/$code.json") {
            header(HttpHeaders.UserAgent, "Foody/$appVersion (privat; Android)")
            parameter("fields", "product_name,product_name_de,nutriments,nutrition_data_per")
        }
        when (antwort.status) {
            HttpStatusCode.OK -> OpenFoodFacts.auswerten(Json.parseToJsonElement(antwort.bodyAsText()).jsonObject, code)
                ?.let { ProduktSuche.Antwort.Gefunden(it) } ?: ProduktSuche.Antwort.Unbekannt
            HttpStatusCode.NotFound -> ProduktSuche.Antwort.Unbekannt
            else -> ProduktSuche.Antwort.Offline
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Kein Netz, Zeitüberschreitung, kaputte Antwort: Foto anbieten
        ProduktSuche.Antwort.Offline
    }
}
