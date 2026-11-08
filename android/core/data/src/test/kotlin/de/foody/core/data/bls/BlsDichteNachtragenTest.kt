package de.foody.core.data.bls

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class BlsDichteNachtragenTest {
    private lateinit var db: FoodyDatenbank

    private val bls = BlsNachschlag(
        {
            sequenceOf(
                "M111300\tVollmilch frisch, 3,5 % Fett, pasteurisiert\t259\t3.55\t4.03\t4.03\t3.49\t0\t0.087",
                "B723000\tLaugengebäck mit Hagelsalz\t1191\t9.132\t50\t1.4\t4.113\t3.746\t2.81",
            )
        },
        EmptyCoroutineContext,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun zutat(id: String, name: String, quelle: String?, dichte: String? = null) = ZutatEntity(
        id = id, canonicalName = name, nutrientBasis = NutrientBasis.PER_100_G, nutrientSource = quelle,
        densityGPerMl = dichte?.let(::BigDecimal), createdAt = 1, updatedAt = 1,
    )

    @Test
    fun uebernommeneFluessigkeitBekommtIhreDichte() = runTest {
        db.zutatDao().upsert(zutat("z-milch", "Vollmilch", "BLS 4.0 (MRI): Vollmilch frisch, 3,5 % Fett, pasteurisiert"))
        db.zutatDao().upsert(zutat("z-breze", "Breze", "BLS 4.0 (MRI): Laugengebäck mit Hagelsalz"))
        db.zutatDao().upsert(zutat("z-saft", "Saft", "Etikett"))
        db.zutatDao().upsert(zutat("z-eigen", "Milch", "BLS 4.0 (MRI): Vollmilch frisch, 3,5 % Fett, pasteurisiert", dichte = "1.1"))

        assertEquals(1, BlsDichteNachtragen(db, bls) { 50L }.nachtragen())

        val milch = assertNotNull(db.zutatDao().nachId("z-milch"))
        assertEquals(0, BigDecimal("1.03").compareTo(milch.densityGPerMl))
        assertEquals(50L, milch.updatedAt)
        // Festes, fremde Quelle und eine schon gesetzte Dichte bleiben
        assertNull(db.zutatDao().nachId("z-breze")?.densityGPerMl)
        assertNull(db.zutatDao().nachId("z-saft")?.densityGPerMl)
        assertEquals(0, BigDecimal("1.1").compareTo(db.zutatDao().nachId("z-eigen")?.densityGPerMl))
    }

    @Test
    fun zweiterLaufTutNichts() = runTest {
        db.zutatDao().upsert(zutat("z-milch", "Vollmilch", "BLS 4.0 (MRI): Vollmilch frisch, 3,5 % Fett, pasteurisiert"))
        BlsDichteNachtragen(db, bls) { 50L }.nachtragen()
        assertEquals(0, BlsDichteNachtragen(db, bls) { 60L }.nachtragen())
    }
}
