package de.foody.server.sync

import de.foody.server.db.Database
import java.time.Clock
import java.time.Duration

/**
 * Entfernt Löschmarkierungen, die älter als [retention] sind. Je Haushalt wird `compacted_before_rev` auf die größte
 * entfernte Revision angehoben; ältere Pull-Cursor werden danach mit `410 cursor_expired` abgewiesen.
 */
class Compactor(private val db: Database, private val clock: Clock, private val retention: Duration = Duration.ofDays(90)) {

    /** Führt die Kompaktierung in einer Transaktion aus und liefert die Zahl entfernter Löschmarkierungen. */
    fun run(): Int = db.tx { c ->
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
