package de.foody.server.photos

import de.foody.server.db.Database
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Zieht die Fotoordner nach der Migration „Konto statt Haushalt“ von `<root>/<household_id>/` nach
 * `<root>/<konto_id>/` um. Grundlage ist die Tabelle `haushalt_umzug`; ein Eintrag wird erst gelöscht,
 * wenn sein Ordner umgezogen ist. Scheitert ein Eintrag, bleibt er für den nächsten Start erhalten.
 */
class FotoUmzug(private val db: Database, private val root: Path) {
    private val log = LoggerFactory.getLogger("de.foody.server.photos")

    /** Gibt die Anzahl erledigter Einträge zurück. */
    fun ausfuehren(): Int {
        val eintraege = db.tx { c ->
            c.createStatement().use { s ->
                s.executeQuery("SELECT household_id, konto_id FROM haushalt_umzug").use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) }
                }
            }
        }
        var erledigt = 0
        for ((haushalt, konto) in eintraege) {
            try {
                // Gleiche Absicherung gegen Pfad-Tricks wie im PhotoStore.
                require(NAME_REGEX.matches(haushalt) && NAME_REGEX.matches(konto)) { "ungültiger Ordnername" }
                umziehen(root.resolve(haushalt), root.resolve(konto), haushalt)
                db.tx { c ->
                    c.prepareStatement("DELETE FROM haushalt_umzug WHERE household_id = ?").use {
                        it.setString(1, haushalt); it.executeUpdate()
                    }
                }
                erledigt++
            } catch (e: Exception) {
                log.error("Fotoumzug {} -> {} fehlgeschlagen", haushalt, konto, e)
            }
        }
        return erledigt
    }

    private fun umziehen(alt: Path, neu: Path, haushalt: String) {
        if (!Files.isDirectory(alt)) {
            log.warn("Fotoordner {} fehlt", haushalt)
            return
        }
        if (!Files.exists(neu)) {
            Files.move(alt, neu)
            return
        }
        check(Files.isDirectory(neu)) { "Ziel $neu ist kein Ordner" }
        // Inhaltsadressiert: gleicher Name heißt gleicher Inhalt, die Quelle kann verworfen werden.
        Files.list(alt).use { it.toList() }.forEach { datei ->
            val ziel = neu.resolve(datei.fileName.toString())
            if (Files.exists(ziel)) Files.delete(datei) else Files.move(datei, ziel)
        }
        Files.delete(alt)
    }

    private companion object {
        val NAME_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}
