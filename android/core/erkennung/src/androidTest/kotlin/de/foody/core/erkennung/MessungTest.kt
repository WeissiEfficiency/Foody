package de.foody.core.erkennung

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.foody.domain.FotoKontext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Misst die Erkennung auf dem Handy (Phase 2, Ziel 15–30 s je Foto). Läuft nur mit `-e messung an`; Modell
 * (`sprache.gguf`, `bild.gguf`) und Fotos liegen in `Android/data/de.foody.core.erkennung.test/files/{modell,bilder}`.
 * Ergebnis: `messung.tsv` im selben Ordner und im Logcat (Tag `FoodyMessung`).
 */
@RunWith(AndroidJUnit4::class)
class MessungTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun zeitJeFoto() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("messung") == "an")
        val context = instrumentation.targetContext
        val ordner = context.getExternalFilesDir(null)!!
        // Die Ordner legt die App selbst an: von adb angelegte kann sie nicht lesen
        val bilder = File(ordner, "bilder").apply { mkdirs() }.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
        File(ordner, "modell").mkdirs()
        assumeTrue("Fotos nach ${ordner.path}/bilder kopieren", bilder.isNotEmpty())
        // Gerichte aus dem deutschen Testsatz der Phase 0 als „eigene Rezepte“
        val kontext = FotoKontext(
            rezepte = listOf(
                "Leberkässemmel", "Breze", "Currywurst mit Pommes", "Schweinebraten mit Knödel", "Weißwurst mit Breze",
                "Bratwurst mit Sauerkraut", "Döner Kebab", "Spaghetti Bolognese", "Kartoffelsalat", "Apfelstrudel",
                "Rinderroulade", "Käsespätzle",
            ).mapIndexed { i, name -> FotoKontext.Eintrag("r$i", name) },
            planRezepte = emptySet(),
            zutaten = emptyList(),
        )
        val erkenner = ModellErkenner(
            dateien = ModellDateien(File(ordner, "modell")),
            modell = LlamaModell(),
            bilder = AndroidBildVorbereitung(context),
            speicher = AndroidFreierSpeicher(context),
            ablauf = Dispatchers.IO,
        )
        val frei = AndroidFreierSpeicher(context)
        val zeilen = mutableListOf("bild\tfrei_mb\tms\tergebnis")
        for (bild in bilder) {
            val freiVorher = frei.bytes() / 1_000_000
            val status = runBlocking { erkenner.erkennen(Uri.fromFile(bild).toString(), kontext) }
            val ms = (status as? ErkennungStatus.Erkannt)?.dauerMs ?: -1L
            zeilen += "${bild.name}\t$freiVorher\t$ms\t$status"
            Log.i("FoodyMessung", zeilen.last())
        }
        File(ordner, "messung.tsv").writeText(zeilen.joinToString("\n"))
    }
}
