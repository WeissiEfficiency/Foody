package de.foody.app.ui.shopping

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import de.foody.domain.ExportResult
import de.foody.domain.ShoppingListExporter
import de.foody.domain.ShoppingListSnapshot
import de.foody.domain.ShoppingListTextFormatter

/**
 * Export als Klartext über das Android-Sharesheet (z. B. an Bring!, Messenger, Notizen).
 * Verändert die interne Liste nie.
 */
class AndroidShareExporter(private val context: Context, private val chooserTitle: String) : ShoppingListExporter {
    override suspend fun export(snapshot: ShoppingListSnapshot): ExportResult = try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, snapshot.name)
            putExtra(Intent.EXTRA_TEXT, ShoppingListTextFormatter.format(snapshot))
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ExportResult.Success
    } catch (e: ActivityNotFoundException) {
        ExportResult.Failure(e.message ?: "")
    }
}
