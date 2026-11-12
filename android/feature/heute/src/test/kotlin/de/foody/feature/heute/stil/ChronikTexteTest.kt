package de.foody.feature.heute.stil

import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.TagebuchArt
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChronikTexteTest {
    private val heute = LocalDate.of(2026, 10, 24)

    @Test
    fun zusatzWieImEntwurf() {
        assertEquals("der heutige Tag", tagZusatz(heute, heute))
        assertEquals("gestern", tagZusatz(heute.minusDays(1), heute))
        assertEquals("vor zwei Tagen", tagZusatz(heute.minusDays(2), heute))
        assertEquals("vor sechs Tagen", tagZusatz(heute.minusDays(6), heute))
        assertEquals("morgen", tagZusatz(heute.plusDays(1), heute))
        assertNull(tagZusatz(heute.minusDays(7), heute))
        assertNull(tagZusatz(heute.plusDays(2), heute))
    }

    @Test
    fun gruppennamen() {
        assertEquals(listOf("DES MORGENS", "ZU MITTAG", "ZWISCHENMAHL", "DES ABENDS"), listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.SNACK, Mahlzeit.ABENDESSEN).map { it.chronikName() })
    }

    @Test
    fun mengen() {
        assertEquals("1 Port.", eintragMenge(eintrag(TagebuchArt.REZEPT, portionen = BigDecimal.ONE)))
        assertEquals("½ Port.", eintragMenge(eintrag(TagebuchArt.REZEPT, portionen = BigDecimal("0.5"))))
        assertEquals("1½ Port.", eintragMenge(eintrag(TagebuchArt.REZEPT, portionen = BigDecimal("1.5"))))
        assertEquals("150 g", eintragMenge(eintrag(TagebuchArt.ZUTAT, menge = BigDecimal("150"), einheit = MeasureUnit.GRAM)))
        assertNull(eintragMenge(eintrag(TagebuchArt.FREI)))
    }

    private fun eintrag(art: TagebuchArt, portionen: BigDecimal? = null, menge: BigDecimal? = null, einheit: MeasureUnit? = null) = TagebuchEintragEntity(
        id = "e", datum = heute, mahlzeit = Mahlzeit.SNACK.name, art = art, name = "X", portionen = portionen, menge = menge, einheit = einheit,
        createdAt = 1, updatedAt = 1,
    )
}
