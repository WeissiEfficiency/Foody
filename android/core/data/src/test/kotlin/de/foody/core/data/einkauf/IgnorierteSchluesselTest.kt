package de.foody.core.data.einkauf

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class IgnorierteSchluesselTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("einkauf", Context.MODE_PRIVATE)
    private lateinit var speicher: IgnorierteSchluessel

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        speicher = IgnorierteSchluessel(prefs)
    }

    @Test
    fun hinzufuegenUndLesen() {
        speicher.hinzufuegen("l1", listOf("z-reis|MASS", "z-milch|VOLUME"))
        speicher.hinzufuegen("l1", listOf("z-ei|COUNT"))
        assertEquals(setOf("z-reis|MASS", "z-milch|VOLUME", "z-ei|COUNT"), speicher.lesen("l1"))
    }

    @Test
    fun entfernen() {
        speicher.hinzufuegen("l1", listOf("a|MASS", "b|MASS"))
        speicher.entfernen("l1", "a|MASS")
        assertEquals(setOf("b|MASS"), speicher.lesen("l1"))
    }

    @Test
    fun vergessen() {
        speicher.hinzufuegen("l1", listOf("a|MASS"))
        speicher.vergessen("l1")
        assertEquals(emptySet(), speicher.lesen("l1"))
    }

    @Test
    fun flussMeldetAenderung() = runTest {
        assertEquals(emptySet(), speicher.fluss("l1").first())
        speicher.hinzufuegen("l1", listOf("z|MASS"))
        assertEquals(setOf("z|MASS"), speicher.fluss("l1").first())
    }

    @Test
    fun listenGetrennt() {
        speicher.hinzufuegen("l1", listOf("a|MASS"))
        assertEquals(emptySet(), speicher.lesen("l2"))
    }
}
