package de.foody.core.erkennung

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Die beiden Modelldateien in [ordner] (`filesDir/modell/`): Sprachteil und Bildteil. Sie werden einmal importiert
 * (z. B. per SAF aus OpenCloud) und bleiben nur auf diesem Gerät; nichts davon wird gesynct.
 */
class ModellDateien(private val ordner: File) {
    private val sprache = File(ordner, SPRACHE)
    private val bild = File(ordner, BILD)

    sealed interface Stand {
        data object Fehlt : Stand

        data class Bereit(val bytes: Long) : Stand
    }

    sealed interface Import {
        /** [bekannt]: die in Phase 0 gemessenen Dateien (Prüfsumme stimmt). */
        data class Fertig(val bekannt: Boolean) : Import

        /** Mindestens eine Datei ist kein GGUF. */
        data object KeinModell : Import

        /** Nicht genau ein Bildteil (`clip`) und ein Sprachteil. */
        data object FalscheDateien : Import

        /** Lesen oder Schreiben ging schief (z. B. kein Platz). */
        data object Fehler : Import
    }

    fun pfade(): ModellPfade? = if (sprache.isFile && bild.isFile) ModellPfade(sprache.path, bild.path) else null

    fun stand(): Stand = if (pfade() == null) Stand.Fehlt else Stand.Bereit(sprache.length() + bild.length())

    fun entfernen() {
        sprache.delete()
        bild.delete()
    }

    /**
     * Übernimmt zwei GGUF-Dateien in beliebiger Reihenfolge. Welche der Bildteil ist, steht im Kopf der Datei. Erst
     * nach der Prüfung ersetzen sie ein vorhandenes Modell; bei einem Fehler bleibt nichts liegen. Blockiert.
     */
    fun importieren(quellen: List<() -> InputStream>): Import {
        if (quellen.size != 2) return Import.FalscheDateien
        ordner.mkdirs()
        val teile = quellen.indices.map { File(ordner, "import-$it.teil") }
        try {
            val summen = quellen.zip(teile).map { (quelle, ziel) -> kopieren(quelle, ziel) }
            val arten = teile.map { GgufKopf.architektur(it) ?: return Import.KeinModell }
            val bildteil = arten.indexOf(CLIP)
            if (bildteil < 0 || arten.count { it == CLIP } != 1) return Import.FalscheDateien
            val sprachteil = 1 - bildteil
            if (!teile[sprachteil].renameTo(sprache.also { it.delete() }) || !teile[bildteil].renameTo(bild.also { it.delete() })) {
                entfernen()
                return Import.Fehler
            }
            return Import.Fertig(bekannt = summen[sprachteil] == BEKANNT_SPRACHE && summen[bildteil] == BEKANNT_BILD)
        } catch (_: IOException) {
            return Import.Fehler
        } finally {
            teile.forEach { it.delete() }
        }
    }

    /** Kopiert und gibt die SHA-256 (hex) zurück. */
    private fun kopieren(quelle: () -> InputStream, ziel: File): String {
        val summe = MessageDigest.getInstance("SHA-256")
        DigestInputStream(quelle(), summe).use { ein -> ziel.outputStream().use { ein.copyTo(it, 1 shl 20) } }
        return summe.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Dateinamen im Modellordner; nur ihr Vorhandensein entscheidet über [Stand.Bereit]. */
        const val SPRACHE = "sprache.gguf"
        const val BILD = "bild.gguf"

        private const val CLIP = "clip"

        /** Qwen3-VL-2B + „food-analysis“, Sprachteil Q4_K_M und Bildteil Q8_0 aus Phase 0 (ADR 0017). */
        const val BEKANNT_SPRACHE = "c5cac93ab69a95ba9018d259f8667ce83caf17575b247c8549fc1a8ef24869b2"
        const val BEKANNT_BILD = "27be2d65580d71554e8b130266a1efef6d4dc2b752fbe0dddd43ac61078e093d"
    }
}
