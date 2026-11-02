package de.foody.core.erkennung

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream

/**
 * Liest aus dem Kopf einer GGUF-Datei `general.architecture` (Bildteil: `clip`, Sprachteil z. B. `qwen3vl`). Mehr
 * braucht der Import nicht; `null`, wenn es keine GGUF-Datei ist oder der Schlüssel fehlt.
 */
internal object GgufKopf {
    private const val MAGIE = 0x46554747 // "GGUF", little endian
    private const val SCHLUESSEL = "general.architecture"

    /** Bis hierhin sucht der Leser; Schlüssel und Texte im Kopf sind kurz. */
    private const val MAX_TEXT = 1 shl 20

    fun architektur(datei: File): String? = runCatching { datei.inputStream().buffered().use(::lesen) }.getOrNull()

    fun architektur(eingabe: InputStream): String? = runCatching { lesen(eingabe) }.getOrNull()

    private fun lesen(eingabe: InputStream): String? {
        val d = Leser(DataInputStream(eingabe))
        if (d.u32() != MAGIE) return null
        if (d.u32() !in 2..3) return null
        d.u64() // Anzahl Tensoren
        val eintraege = d.u64()
        repeat(minOf(eintraege, 10_000L).toInt()) {
            val schluessel = d.text()
            val typ = d.u32()
            if (schluessel == SCHLUESSEL && typ == TEXT) return d.text()
            d.ueberspringen(typ)
        }
        return null
    }

    private const val TEXT = 8
    private const val LISTE = 9

    private class Leser(private val d: DataInputStream) {
        fun u32(): Int = Integer.reverseBytes(d.readInt())

        fun u64(): Long = java.lang.Long.reverseBytes(d.readLong())

        fun text(): String {
            val laenge = u64()
            if (laenge !in 0..MAX_TEXT) throw EOFException("Text zu lang")
            return ByteArray(laenge.toInt()).also(d::readFully).decodeToString()
        }

        fun ueberspringen(typ: Int) {
            when (typ) {
                TEXT -> text()
                LISTE -> {
                    val inneres = u32()
                    val anzahl = u64()
                    if (anzahl !in 0..100_000_000) throw EOFException("Liste zu lang")
                    val breite = breite(inneres)
                    if (breite != null) {
                        d.skipNBytes(anzahl * breite)
                    } else {
                        repeat(anzahl.toInt()) { ueberspringen(inneres) }
                    }
                }
                else -> d.skipNBytes(breite(typ)?.toLong() ?: throw EOFException("Typ $typ"))
            }
        }

        /** Bytes eines Werts fester Größe; `null` für Text und Liste. */
        private fun breite(typ: Int): Int? = when (typ) {
            0, 1, 7 -> 1 // u8, i8, bool
            2, 3 -> 2 // u16, i16
            4, 5, 6 -> 4 // u32, i32, f32
            10, 11, 12 -> 8 // u64, i64, f64
            else -> null
        }
    }
}
