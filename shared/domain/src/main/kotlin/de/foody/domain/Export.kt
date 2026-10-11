package de.foody.domain

data class ShoppingListSnapshot(
    val name: String,
    val items: List<SnapshotItem>,
) {
    data class SnapshotItem(val name: String, val quantity: Quantity?, val checked: Boolean, val category: String?)
}

sealed interface ExportResult {
    data object Success : ExportResult
    data class Failure(val message: String) : ExportResult
}

/** Neutrale Export-Grenze; die Domain kennt weder Intents noch Bring!-Details. */
interface ShoppingListExporter {
    suspend fun export(snapshot: ShoppingListSnapshot): ExportResult
}

object ShoppingListTextFormatter {
    /** Klartext, eine Position je Zeile, nur offene Einträge. */
    fun format(snapshot: ShoppingListSnapshot): String = buildString {
        appendLine(snapshot.name)
        snapshot.items.filter { !it.checked }.forEach { item ->
            append("- ")
            append(item.name)
            item.quantity?.let { append(" (").append(QuantityFormatter.format(it)).append(")") }
            appendLine()
        }
    }.trimEnd()
}
