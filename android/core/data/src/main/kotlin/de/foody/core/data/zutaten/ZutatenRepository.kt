package de.foody.core.data.zutaten

import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale

/**
 * Zutatenkatalog (Phase 6e2): lesen, anlegen, bearbeiten, löschen, zusammenführen und Namen vereinheitlichen. Jede
 * schreibende Methode ist eine Transaktion; die Sync-Trigger legen alles in die Outbox.
 */
class ZutatenRepository(private val db: FoodyDatenbank, private val uhr: () -> Long) {
    private val dao get() = db.zutatDao()

    /** Alle Zutaten nach deutschem Alphabet (ohne Groß/klein, „Äpfel“ neben „Apfel“), Gleichstand nach ID. */
    fun liste(): Flow<List<ZutatKurz>> = dao.mitVerwendungFlow().map { zeilen ->
        val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }
        zeilen.map { it.zutat.kurz(it.rezepte) }
            .sortedWith(Comparator<ZutatKurz> { a, b -> collator.compare(a.name, b.name) }.thenBy { it.id })
    }

    fun anzahl(): Flow<Int> = dao.anzahlFlow()

    fun zutat(id: String): Flow<ZutatEntity?> = dao.nachIdFlow(id)

    /** Namen der Rezepte, die die Zutat verwenden. */
    fun verwendetIn(id: String): Flow<List<String>> = dao.rezeptNamenFlow(id)

    /** Bekannte Schreibweisen aus dem Katalog, ohne den Namen selbst. */
    fun synonyme(name: String): List<String> =
        IngredientCatalog.synonymsOf(name).filterNot { it.equals(name, ignoreCase = true) }
}
