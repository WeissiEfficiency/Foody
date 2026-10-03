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

data class ImportResult(val importedIds: List<String>, val failed: Int)

/** Importiert Rezepte aus Markdown-Dateien (z. B. Web-Clipper-Export von Rezeptseiten). */
@Singleton
class RecipeImportRepository @Inject constructor(
    private val recipes: RecipeRepository,
    private val ingredients: IngredientRepository,
    @ApplicationContext private val context: Context,
) {
    suspend fun import(uris: List<Uri>, defaultServings: Int, tag: String, notesTemplate: (String?) -> String): ImportResult {
        val ids = mutableListOf<String>()
        var failed = 0
        for (uri in uris) {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } ?: error("Datei nicht lesbar")
                importText(text, defaultServings, tag, notesTemplate)
            }.onSuccess { it?.let(ids::add) ?: failed++ }.onFailure { failed++ }
        }
        return ImportResult(ids, failed)
    }

    /**
     * Importiert alle Markdown-Dateien eines per Ordnerauswahl freigegebenen Verzeichnisses
     * (z. B. eine Sammlung von Rezeptideen). Unterordner werden nicht durchsucht.
     */
    suspend fun importFolder(treeUri: Uri, defaultServings: Int, tag: String, notesTemplate: (String?) -> String): ImportResult {
        val files = withContext(Dispatchers.IO) { markdownFilesIn(treeUri) }
        return import(files, defaultServings, tag, notesTemplate)
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
    suspend fun importText(text: String, defaultServings: Int, tag: String, notesTemplate: (String?) -> String): String? {
        val parsed = MarkdownRecipeImporter.parse(text)
        if (parsed.name.isBlank() || parsed.ingredients.isEmpty()) return null
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
            ),
        )
    }
}
