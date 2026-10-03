package de.foody.app.data.repo

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val recipes: RecipeRepository,
    private val ingredients: IngredientRepository,
    @ApplicationContext private val context: Context,
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
        for (uri in uris) {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } ?: error("Datei nicht lesbar")
                importOne(text, defaultServings, tag, notesTemplate)
            }.onSuccess { outcome ->
                when (outcome) {
                    is Outcome.Imported -> ids += outcome.id
                    Outcome.AlreadyImported -> skipped++
                    Outcome.NotRecognized -> failed++
                }
            }.onFailure { failed++ }
            onProgress(ids.size + failed + skipped, uris.size)
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
        (importOne(text, defaultServings, tag, notesTemplate) as? Outcome.Imported)?.id

    private suspend fun importOne(text: String, defaultServings: Int, tag: String, notesTemplate: (String?) -> String): Outcome {
        val parsed = MarkdownRecipeImporter.parse(text)
        if (parsed.name.isBlank() || parsed.ingredients.isEmpty()) return Outcome.NotRecognized
        // Gleiche Quelle schon vorhanden (auch archiviert) → nicht doppelt anlegen
        if (parsed.sourceUrl != null && recipes.findBySourceUrl(parsed.sourceUrl!!) != null) return Outcome.AlreadyImported
        val lines = parsed.ingredients.map { ing ->
            RecipeDraft.Line(
                ingredientId = ingredients.getOrCreate(ing.name).id,
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
}
