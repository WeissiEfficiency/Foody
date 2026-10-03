package de.foody.app.data.repo

import java.io.InputStream
import java.io.ByteArrayOutputStream
import de.foody.app.util.runSuspendCatching
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.app.data.db.FoodyDatabase
import de.foody.domain.ImportedRecipe
import de.foody.domain.IngredientCatalog
import de.foody.domain.MarkdownRecipeImporter
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** [skipped]: Dateien, deren Quelle bereits importiert wurde. */
data class ImportResult(val importedIds: List<String>, val failed: Int, val skipped: Int = 0)

/** Importiert Rezepte aus Markdown-Dateien (z. B. Web-Clipper-Export von Rezeptseiten). */
@Singleton
class RecipeImportRepository @Inject constructor(
    private val db: FoodyDatabase,
    private val recipes: RecipeRepository,
    private val ingredients: IngredientRepository,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun import(
        uris: List<Uri>,
        defaultServings: Int,
        tag: String,
        notesTemplate: (String?) -> String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        val ids = mutableListOf<String>()
        var failed = 0
        var skipped = 0
        val ingredientIds = mutableMapOf<String, String>()
        // Blöcke statt einer Transaktion pro Rezept: Room benachrichtigt beobachtende Abfragen (Rezeptliste)
        // nach jeder Transaktion – so lädt die Liste wenige Male statt einmal pro Rezept neu.
        for (chunk in uris.chunked(CHUNK_SIZE)) {
            // Lesen und Parsen außerhalb der Transaktion: Eine unlesbare oder kaputte Datei zählt als fehlgeschlagen.
            val parsed = withContext(Dispatchers.IO) {
                chunk.map { uri ->
                    runSuspendCatching { context.contentResolver.openInputStream(uri)?.use { it.readTextCapped(MAX_FILE_BYTES) } }
                        .getOrNull()
                        ?.let { text -> runCatching { MarkdownRecipeImporter.parse(text) }.getOrNull() }
                }
            }
            // Datenbankfehler werden bewusst nicht abgefangen: Die Transaktion rollt den ganzen Block zurück,
            // statt ein halb geschriebenes Rezept zu speichern.
            db.withTransaction {
                for (recipe in parsed) {
                    when (val outcome = recipe?.let { importParsed(it, defaultServings, tag, notesTemplate, ingredientIds) }) {
                        is Outcome.Imported -> ids += outcome.id
                        Outcome.AlreadyImported -> skipped++
                        Outcome.NotRecognized, null -> failed++
                    }
                    onProgress(ids.size + failed + skipped, uris.size)
                }
            }
        }
        return ImportResult(ids, failed, skipped)
    }

    /**
     * Importiert alle Markdown-Dateien eines per Ordnerauswahl freigegebenen Verzeichnisses
     * (z. B. eine Sammlung von Rezeptideen). Unterordner werden nicht durchsucht.
     */
    suspend fun importFolder(
        treeUri: Uri,
        defaultServings: Int,
        tag: String,
        notesTemplate: (String?) -> String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        val files = withContext(Dispatchers.IO) { markdownFilesIn(treeUri) }
        return import(files, defaultServings, tag, notesTemplate, onProgress)
    }

    private fun markdownFilesIn(treeUri: Uri): List<Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val out = mutableListOf<Pair<String, Uri>>()
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.substringAfterLast('.').lowercase() !in setOf("md", "markdown", "txt")) continue
                out += name to DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
            }
        }
        return out.sortedBy { it.first }.map { it.second }
    }

    /** null, wenn der Text kein erkennbares Rezept enthält. */
    private sealed interface Outcome {
        data class Imported(val id: String) : Outcome
        data object AlreadyImported : Outcome
        data object NotRecognized : Outcome
    }

    /** ID des neuen Rezepts; null, wenn der Text kein Rezept enthält oder die Quelle schon importiert wurde. */
    suspend fun importText(text: String, defaultServings: Int, tag: String, notesTemplate: (String?) -> String): String? =
        (importParsed(MarkdownRecipeImporter.parse(text), defaultServings, tag, notesTemplate, mutableMapOf()) as? Outcome.Imported)?.id

    /** [ingredientIds]: Zwischenspeicher kanonischer Name → Zutaten-ID für die Dauer eines Imports. */
    private suspend fun importParsed(
        parsed: ImportedRecipe,
        defaultServings: Int,
        tag: String,
        notesTemplate: (String?) -> String,
        ingredientIds: MutableMap<String, String>,
    ): Outcome {
        if (parsed.name.isBlank() || parsed.ingredients.isEmpty()) return Outcome.NotRecognized
        // Gleiche Quelle schon vorhanden (auch archiviert) → nicht doppelt anlegen
        val sourceUrl = parsed.sourceUrl
        if (sourceUrl != null && recipes.findBySourceUrl(sourceUrl) != null) return Outcome.AlreadyImported
        val lines = parsed.ingredients.map { ing ->
            RecipeDraft.Line(
                ingredientId = ingredientIds.getOrPut(IngredientCatalog.canonicalName(ing.name).lowercase()) {
                    ingredients.getOrCreate(ing.name).id
                },
                // Ohne Zahl → 0 = „nach Bedarf“
                amount = ing.amount ?: BigDecimal.ZERO,
                unit = ing.unit ?: MeasureUnit.PIECE,
                note = listOfNotNull(ing.group, ing.note).joinToString(" · ").ifBlank { null },
                optional = ing.optional,
            )
        }
        return recipes.save(
            RecipeDraft(
                id = null,
                name = parsed.name,
                defaultServings = defaultServings,
                prepMinutes = null,
                cookMinutes = null,
                imageUri = null,
                notes = notesTemplate(parsed.sourceUrl),
                tags = tag,
                ingredients = lines,
                steps = parsed.steps,
                sourceUrl = parsed.sourceUrl,
            ),
        ).let(Outcome::Imported)
    }

    private companion object {
        /** Rezepte pro Transaktion – groß genug für wenige Listen-Aktualisierungen, klein genug für flüssigen Fortschritt. */
        const val CHUNK_SIZE = 25
        /** Rezepttexte sind wenige KB groß; größere Dateien (versehentlich gewählt) zählen als fehlgeschlagen statt den Speicher zu füllen. */
        const val MAX_FILE_BYTES = 1 shl 20
    }
}

/** Liest höchstens [limit] Bytes als Text; mehr gilt als Fehler. */
private fun InputStream.readTextCapped(limit: Int): String {
    // Eigene Schleife statt readNBytes (erst ab Android 13)
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val n = read(buffer)
        if (n < 0) return out.toByteArray().decodeToString()
        check(out.size() + n <= limit) { "Datei zu groß" }
        out.write(buffer, 0, n)
    }
}
