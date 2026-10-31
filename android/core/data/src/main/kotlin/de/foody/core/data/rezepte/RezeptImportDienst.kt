package de.foody.core.data.rezepte

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.core.data.alsIngredient
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.rezeptimport.NachtragErgebnis
import de.foody.core.data.rezeptimport.PortionenNachtrag
import de.foody.domain.ImportedIngredient
import de.foody.domain.ImportedRecipe
import de.foody.domain.MarkdownRecipeImporter
import de.foody.domain.RezeptImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/** [fehler]: Namen der Dateien, die nicht lesbar waren oder kein Rezept enthielten. */
data class ImportErgebnis(val importiert: List<String>, val uebersprungen: Int, val fehler: List<String>)

/** Markdown-Import; die Oberfläche kennt nur diese Schnittstelle (Tests setzen einen Ersatz ein). */
interface RezeptImporter {
    suspend fun dateien(uris: List<Uri>, onFortschritt: (Int, Int) -> Unit = { _, _ -> }): ImportErgebnis

    suspend fun ordner(baum: Uri, onFortschritt: (Int, Int) -> Unit = { _, _ -> }): ImportErgebnis

    /** Portionenzahl der Markdown-Dateien im Ordner bei früher importierten Rezepten nachtragen (R10). */
    suspend fun portionenNachtragen(baum: Uri): NachtragErgebnis
}

/**
 * Importiert Rezepte aus Markdown-Dateien wie die v1 (`RecipeImportRepository`): Vorgabe [VORGABE_PORTIONEN] Portionen,
 * Notiz mit Quelle und Portionen-Hinweis, gleiche Quelle (auch archiviert) wird übersprungen, höchstens [MAX_BYTES] je
 * Datei. Dateien wählen → Tag „Import“, Ordner → Tag „Ideen“.
 */
class RezeptImportDienst @Inject constructor(
    @ApplicationContext private val kontext: Context,
    private val db: FoodyDatenbank,
    private val rezepte: RezeptRepository,
    private val nachtrag: PortionenNachtrag,
) : RezeptImporter {
    override suspend fun dateien(uris: List<Uri>, onFortschritt: (Int, Int) -> Unit): ImportErgebnis =
        importieren(uris, TAG_DATEIEN, onFortschritt)

    /** Alle Markdown-Dateien des freigegebenen Ordners (ohne Unterordner), nach Namen sortiert. */
    override suspend fun ordner(baum: Uri, onFortschritt: (Int, Int) -> Unit): ImportErgebnis =
        importieren(withContext(Dispatchers.IO) { markdownIn(baum) }, TAG_ORDNER, onFortschritt)

    /** Liest alle Markdown-Dateien des Ordners (wie [ordner]); unlesbare fallen weg. Geändert werden nur Portionen und Notiz. */
    override suspend fun portionenNachtragen(baum: Uri): NachtragErgebnis {
        val dateien = withContext(Dispatchers.IO) { markdownIn(baum).mapNotNull { uri -> text(uri)?.let { dateiname(uri) to it } } }
        return nachtrag.anwenden(dateien)
    }

    private suspend fun importieren(uris: List<Uri>, tag: String, onFortschritt: (Int, Int) -> Unit): ImportErgebnis {
        val importiert = mutableListOf<String>()
        val fehler = mutableListOf<String>()
        var uebersprungen = 0
        // Blöcke statt einer Transaktion je Rezept: die Rezeptliste lädt wenige Male statt einmal pro Rezept neu
        for (block in uris.chunked(BLOCK)) {
            val gelesen = withContext(Dispatchers.IO) { block.map { it to lesen(it) } }
            db.withTransaction {
                for ((uri, rezept) in gelesen) {
                    when (val id = rezept?.let { anlegen(it, tag) }) {
                        null -> fehler += dateiname(uri)
                        UEBERSPRUNGEN -> uebersprungen++
                        else -> importiert += id
                    }
                    onFortschritt(importiert.size + uebersprungen + fehler.size, uris.size)
                }
            }
        }
        return ImportErgebnis(importiert, uebersprungen, fehler)
    }

    /** Neue ID; [UEBERSPRUNGEN] bei schon vorhandener Quelle; `null` ohne Titel oder Zutaten. */
    private suspend fun anlegen(r: ImportedRecipe, tag: String): String? {
        if (r.name.isBlank() || r.ingredients.isEmpty()) return null
        val quelle = r.sourceUrl
        if (quelle != null && db.rezeptDao().nachQuelle(quelle).isNotEmpty()) return UEBERSPRUNGEN
        val zutaten = r.ingredients.map { ing ->
            val id = rezepte.zutatId(ing.name)
            db.zutatDao().nachId(id)!!.alsIngredient()
        }
        val e = RezeptImport.vorbereiten(r, zutaten, VORGABE_PORTIONEN)
        val zeilen = e.zeilen.zip(zutaten) { z, zutat ->
            ImportedIngredient(zutat.name, z.menge, z.einheit, z.hinweis, group = null, optional = z.optional)
        }
        val entwurf = RezeptEntwurf(
            id = null, name = r.name, portionen = e.portionen, vorbereitung = null, kochzeit = null, tags = tag,
            zeilen = zeilen, schritte = r.steps,
            notizen = RezeptImport.hinweis(r.sourceUrl, e.portionen, e.geschaetzt, e.ausDatei).ifBlank { null },
            quelle = r.sourceUrl, mahlzeiten = null, gaenge = null, bild = null,
        )
        return (rezepte.speichern(entwurf) as? Gespeichert.Ok)?.id
    }

    /** Text der Datei als Rezept; `null`, wenn unlesbar, zu groß oder nicht zu zerlegen. */
    private fun lesen(uri: Uri): ImportedRecipe? = try {
        text(uri)?.let(MarkdownRecipeImporter::parse)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Inhalt der Datei; `null`, wenn unlesbar oder größer als [MAX_BYTES]. */
    private fun text(uri: Uri): String? = try {
        kontext.contentResolver.openInputStream(uri)?.use { it.textBis(MAX_BYTES) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun dateiname(uri: Uri): String {
        if (uri.scheme == "file") return uri.path?.let { File(it).name }.orEmpty()
        return runCatching {
            kontext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }

    private fun markdownIn(baum: Uri): List<Uri> {
        val kinder = DocumentsContract.buildChildDocumentsUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum))
        val spalten = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val treffer = mutableListOf<Pair<String, Uri>>()
        kontext.contentResolver.query(kinder, spalten, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.substringAfterLast('.').lowercase() !in ENDUNGEN) continue
                treffer += name to DocumentsContract.buildDocumentUriUsingTree(baum, c.getString(0))
            }
        }
        return treffer.sortedBy { it.first }.map { it.second }
    }

    private companion object {
        const val VORGABE_PORTIONEN = 4
        const val TAG_DATEIEN = "Import"
        const val TAG_ORDNER = "Ideen"
        const val BLOCK = 25
        const val MAX_BYTES = 1 shl 20
        const val UEBERSPRUNGEN = "\u0000uebersprungen"
        val ENDUNGEN = setOf("md", "markdown", "txt")
    }
}

/** Höchstens [grenze] Bytes als Text; mehr gilt als Fehler. */
private fun InputStream.textBis(grenze: Int): String {
    val aus = ByteArrayOutputStream()
    val puffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val n = read(puffer)
        if (n < 0) return aus.toByteArray().decodeToString()
        check(aus.size() + n <= grenze) { "Datei zu groß" }
        aus.write(puffer, 0, n)
    }
}
