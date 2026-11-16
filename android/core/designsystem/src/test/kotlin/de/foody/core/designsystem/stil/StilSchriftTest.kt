package de.foody.core.designsystem.stil

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.sage.Sage
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Die Schriften der Stile trennen deutsch wie das Theme: Ohne Silbentrennung brachen lange Wörter in schmalen Kacheln
 * der Szenen ohne Strich mitten im Wort um („Kokosmilc|h“ in der Woche von Anime, Gerätetest 2026-11-16).
 */
class StilSchriftTest {
    private val schriften: Map<String, TextStyle> = mapOf(
        "Anime.titel" to Anime.titel(20f),
        "Anime.text" to Anime.text(14f),
        "Bayrisch.titel" to Bayrisch.titel(20f),
        "Bayrisch.fraktur" to Bayrisch.fraktur(30f),
        "Bayrisch.text" to Bayrisch.text(14f),
        "Edo.titel" to Edo.titel(20f),
        "Edo.text" to Edo.text(14f),
        "Edo.kopf" to Edo.kopf(),
        "Grauen.titel" to Grauen.titel(20f),
        "Grauen.name" to Grauen.name(20f),
        "Grauen.text" to Grauen.text(14f),
        "Grauen.maschine" to Grauen.maschine(14f),
        "Kreuzer.titel" to Kreuzer.titel(20f),
        "Kreuzer.mono" to Kreuzer.mono(12f),
        "Sage.titel" to Sage.titel(20f, 0.1f),
        "Sage.text" to Sage.text(14f),
    )

    @Test
    fun alleSchriftenTrennenDeutsch() {
        schriften.forEach { (name, stil) ->
            assertEquals(Hyphens.Auto, stil.hyphens, "$name ohne Silbentrennung")
            assertEquals(LocaleList("de-DE"), stil.localeList, "$name ohne deutsche Sprache")
        }
    }
}
