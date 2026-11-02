package de.foody.core.erkennung

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Baut den Kopf einer GGUF-Datei (v3) für Tests: einige Einträge vor `general.architecture`, dann Füllbytes. */
internal fun ggufKopf(architektur: String?, rest: Int = 64): ByteArray {
    val aus = ByteArrayOutputStream()
    fun bytes(n: Int, f: ByteBuffer.() -> Unit) = aus.write(ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).apply(f).array())
    fun u32(v: Int) = bytes(4) { putInt(v) }
    fun u64(v: Long) = bytes(8) { putLong(v) }
    fun text(s: String) = s.encodeToByteArray().let {
        u64(it.size.toLong())
        aus.write(it)
    }
    aus.write("GGUF".encodeToByteArray())
    u32(3)
    u64(0)
    u64(if (architektur == null) 4 else 5)
    // u32, Text, Liste von Zahlen, Liste von Texten – alle muss der Leser überspringen
    text("general.alignment")
    u32(4)
    u32(32)
    text("general.name")
    u32(8)
    text("Testmodell")
    text("tokenizer.ggml.scores")
    u32(9)
    u32(6)
    u64(3)
    repeat(3) { bytes(4) { putFloat(0.5f) } }
    text("tokenizer.ggml.tokens")
    u32(9)
    u32(8)
    u64(2)
    text("<|im_start|>")
    text("Ä")
    if (architektur != null) {
        text("general.architecture")
        u32(8)
        text(architektur)
    }
    aus.write(ByteArray(rest))
    return aus.toByteArray()
}
