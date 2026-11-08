package de.foody.core.erkennung

import javax.inject.Inject

/** JNI-Gegenstück zu `foody_erkennung.cpp`; die Bibliothek wird erst beim ersten Gebrauch geladen. */
internal object LlamaNativ {
    /** `false`, wenn die Bibliothek fehlt (Emulator x86_64) – dann gibt es keine Erkennung. */
    val geladen: Boolean by lazy { runCatching { System.loadLibrary("foody_erkennung") }.isSuccess }

    external fun neu(): Long

    external fun laden(h: Long, modell: String, bildteil: String, bild: String, threads: Int, maxBildTokens: Int): Boolean

    external fun fragen(h: Long, anweisung: String, maxTokens: Int): ByteArray?

    external fun abbrechen(h: Long)

    external fun freigeben(h: Long)
}

/**
 * Qwen3-VL über llama.cpp auf der CPU (ADR 0017): 6 Threads (die großen Kerne des Snapdragon 8 Gen 3), höchstens
 * [MAX_BILD_TOKENS] Bild-Tokens, gierige Auswahl wie in der Messung.
 */
class LlamaModell @Inject constructor() : BildSprachModell {
    override fun sitzung(): ModellSitzung = if (LlamaNativ.geladen) LlamaSitzung(LlamaNativ.neu()) else OhneLaufzeit

    private class LlamaSitzung(private val h: Long) : ModellSitzung {
        private var offen = true

        override fun laden(pfade: ModellPfade, bildPfad: String) =
            LlamaNativ.laden(h, pfade.sprache, pfade.bild, bildPfad, THREADS, MAX_BILD_TOKENS)

        override fun fragen(anweisung: String) = LlamaNativ.fragen(h, anweisung, MAX_ANTWORT_TOKENS)?.decodeToString()

        override fun abbrechen() = LlamaNativ.abbrechen(h)

        @Synchronized override fun close() {
            if (offen) LlamaNativ.freigeben(h)
            offen = false
        }
    }

    private object OhneLaufzeit : ModellSitzung {
        override fun laden(pfade: ModellPfade, bildPfad: String) = false

        override fun fragen(anweisung: String): String? = null

        override fun abbrechen() {}

        override fun close() {}
    }

    companion object {
        const val THREADS = 6
        const val MAX_BILD_TOKENS = 512

        /** Mit Zutatenliste bis etwa 450 Tokens (Messung 2026-11-08); abgeschnittenes JSON wäre unlesbar. */
        const val MAX_ANTWORT_TOKENS = 600
    }
}
