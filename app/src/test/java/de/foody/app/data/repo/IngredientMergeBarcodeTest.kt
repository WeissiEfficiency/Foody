package de.foody.app.data.repo

import de.foody.app.data.db.IngredientEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientMergeBarcodeTest {
    private fun z(id: String, code: String?) = IngredientEntity(id = id, canonicalName = id, barcode = code, createdAt = 1, updatedAt = 1)

    @Test fun zielUebernimmtStrichcodeNurWennEsKeinenHat() {
        assertEquals("111", z("ziel", null).fillFrom(z("quelle", "111")).barcode)
        assertEquals("222", z("ziel", "222").fillFrom(z("quelle", "111")).barcode)
    }
}
