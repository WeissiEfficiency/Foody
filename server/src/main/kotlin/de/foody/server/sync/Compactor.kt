package de.foody.server.sync

import de.foody.server.db.Database
import de.foody.server.photos.PhotoStore
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * Entfernt Löschmarkierungen, die älter als [retention] sind und räumt unreferenzierte Fotos auf (älter als [photoGrace]). Je Haushalt wird `compacted_before_rev` auf die größte
 * entfernte Revision angehoben; ältere Pull-Cursor werden danach mit `410 cursor_expired` abgewiesen.
 */
class Compactor(
    private val db: Database,
    private val clock: Clock,
    private val photos: PhotoStore? = null,
    private val retention: Duration = Duration.ofDays(90),
    private val photoGrace: Duration = Duration.ofDays(30),
) {
    private val log = LoggerFactory.getLogger(Compactor::class.java)


    /** Führt die Kompaktierung aus und liefert die Zahl entfernter Löschmarkierungen; danach werden Fotos aufgeräumt. */
    fun run(): Int {
        val removed = compactTombstones()
        photos?.let { removeUnreferencedPhotos(it) }
        return removed
    }

    /**
     * Löscht Fotodateien, die in keinem lebenden Rezept des Haushalts mehr stehen und älter als [photoGrace] sind
     * (die Frist deckt Uploads ab, deren Rezept noch nicht gepusht wurde).
     */
    private fun removeUnreferencedPhotos(store: PhotoStore) {
        val cutoff = clock.instant().minus(photoGrace)
        val households = db.tx { c ->
            c.prepareStatement("SELECT id FROM household").use { st ->
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
        }
        for (household in households) {
            val files = store.listHashes(household)
            if (files.isEmpty()) continue
            val referenced = db.tx { c ->
                c.prepareStatement(
                    "SELECT DISTINCT json_extract(payload, '$.photo') FROM sync_record " +
                        "WHERE household_id = ? AND type = 'recipe' AND deleted = 0",
                ).use { st ->
                    st.setString(1, household)
                    st.executeQuery().use { rs -> buildSet { while (rs.next()) rs.getString(1)?.let(::add) } }
                }
            }
            for ((sha, modified) in files) {
                if (sha !in referenced && modified.isBefore(cutoff)) {
                    store.delete(household, sha)
                    log.info("Foto {} des Haushalts {} entfernt (nicht mehr referenziert)", sha, household)
                }
            }
        }
    }

    private fun compactTombstones(): Int = db.tx { c ->
        val cutoff = clock.millis() - retention.toMillis()
        val maxRemoved = c.prepareStatement(
            "SELECT household_id, MAX(rev) FROM sync_record WHERE deleted = 1 AND deleted_at < ? GROUP BY household_id",
        ).use { st ->
            st.setLong(1, cutoff)
            st.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getLong(2)) } }
        }
        for ((household, rev) in maxRemoved) {
            c.prepareStatement("UPDATE household SET compacted_before_rev = MAX(compacted_before_rev, ?) WHERE id = ?").use { st ->
                st.setLong(1, rev)
                st.setString(2, household)
                st.executeUpdate()
            }
        }
        c.prepareStatement("DELETE FROM sync_record WHERE deleted = 1 AND deleted_at < ?").use { st ->
            st.setLong(1, cutoff)
            st.executeUpdate()
        }
    }
}
