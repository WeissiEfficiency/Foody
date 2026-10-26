package de.foody.core.data.zutaten

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale
import java.util.UUID

/**
 * Zutatenkatalog (Phase 6e2): lesen, anlegen, bearbeiten, löschen, zusammenführen und Namen vereinheitlichen. Jede
 * schreibende Methode ist eine Transaktion; die Sync-Trigger legen alles in die Outbox.
 */
class ZutatenRepository(private val db: FoodyDatenbank, private val uhr: () -> Long) {
    private val dao get() = db.zutatDao()

    /** Alle Zutaten nach deutschem Alphabet (ohne Groß/klein, „Äpfel“ neben „Apfel“), Gleichstand nach ID. */
    fun liste(): Flow<List<ZutatKurz>> = dao.mitVerwendungFlow().map { zeilen ->
        val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }
        zeilen.map { it.zutat.kurz(it.rezepte) }
            .sortedWith(Comparator<ZutatKurz> { a, b -> collator.compare(a.name, b.name) }.thenBy { it.id })
    }

    fun anzahl(): Flow<Int> = dao.anzahlFlow()

    fun zutat(id: String): Flow<ZutatEntity?> = dao.nachIdFlow(id)

    /** Namen der Rezepte, die die Zutat verwenden. */
    fun verwendetIn(id: String): Flow<List<String>> = dao.rezeptNamenFlow(id)

    /** Bekannte Schreibweisen aus dem Katalog, ohne den Namen selbst. */
    fun synonyme(name: String): List<String> =
        IngredientCatalog.synonymsOf(name).filterNot { it.equals(name, ignoreCase = true) }

    /**
     * Neue eigene Zutat; die Kategorie rät der Katalog. Mit [packung] in derselben Transaktion deren Basis (sonst je
     * 100 g), Werte und Quelle sowie der Strichcode, wenn ihn keine andere Zutat trägt.
     */
    suspend fun anlegen(name: String, packung: Packungswerte? = null): ZutatErgebnis = namensschutz(name) {
        val n = nameBereinigt(name)
        val fehler = nameFehler(n)
        if (fehler != null) return@namensschutz ZutatErgebnis.Ungueltig(mapOf(ZutatFeld.NAME to fehler))
        db.withTransaction {
            andereMitNamen(n, ausser = null)?.let { return@withTransaction ZutatErgebnis.NameVergeben(it.id, it.canonicalName) }
            val jetzt = uhr()
            val id = UUID.randomUUID().toString()
            val neu = ZutatEntity(id = id, canonicalName = n, category = IngredientCatalog.guessCategory(n), createdAt = jetzt, updatedAt = jetzt)
            dao.upsert(
                if (packung == null) {
                    neu
                } else {
                    neu.mitPackungswerten(packung).copy(barcode = packung.strichcode?.takeIf { dao.mitStrichcode(it) == null })
                },
            )
            ZutatErgebnis.Gespeichert(id)
        }
    }

    /**
     * Schreibt die gegenüber [vorher] geänderten Felder von [nachher] auf den aktuellen Stand der Zutat. [scan]: die im
     * Editor übernommene Packung (genaue kJ, Quelle bleibt; siehe [mitAenderungen]).
     */
    suspend fun speichern(
        id: String,
        vorher: ZutatEntwurf,
        nachher: ZutatEntwurf,
        scan: Packungswerte? = null,
    ): ZutatErgebnis = namensschutz(nachher.name) {
        val fehler = pruefen(vorher, nachher)
        if (fehler.isNotEmpty()) return@namensschutz ZutatErgebnis.Ungueltig(fehler)
        db.withTransaction {
            val aktuell = dao.nachId(id) ?: return@withTransaction ZutatErgebnis.NichtGefunden
            val neu = aktuell.mitAenderungen(vorher, nachher, scan = scan)
            // Nur ein geänderter Name kann kollidieren; eine alte Dublette („Äpfel“/„äpfel“) blockiert sonst nichts
            if (neu.canonicalName != aktuell.canonicalName) {
                andereMitNamen(neu.canonicalName, ausser = id)?.let {
                    return@withTransaction ZutatErgebnis.NameVergeben(it.id, it.canonicalName)
                }
            }
            neu.barcode?.takeIf { it != aktuell.barcode }?.let { code ->
                dao.nachStrichcode(code, ausser = id)?.let { return@withTransaction ZutatErgebnis.StrichcodeVergeben(it.id, it.canonicalName) }
            }
            if (neu != aktuell) dao.upsert(neu.copy(updatedAt = uhr()))
            ZutatErgebnis.Gespeichert(id)
        }
    }

    /** Nur unbenutzte Zutaten; Einkaufsartikel und Tagebucheinträge behalten Name und Werte. */
    suspend fun loeschen(id: String): ZutatErgebnis = db.withTransaction {
        dao.nachId(id) ?: return@withTransaction ZutatErgebnis.NichtGefunden
        val rezepte = dao.rezeptAnzahl(id)
        if (rezepte > 0) return@withTransaction ZutatErgebnis.InVerwendung(rezepte)
        dao.loeschen(id)
        ZutatErgebnis.Geloescht
    }

    /**
     * Führt [vonId] in [nachId] zusammen. Mit [entwurf] (vorher → nachher) wandern die Eingaben des Editors (ohne Namen)
     * mit: Sie gelten für die Quelle, deren Angaben dann Lücken des Ziels füllen.
     */
    suspend fun zusammenfuehren(vonId: String, nachId: String, entwurf: Pair<ZutatEntwurf, ZutatEntwurf>? = null): ZutatErgebnis {
        if (vonId == nachId) return ZutatErgebnis.NichtGefunden
        if (entwurf != null) {
            val fehler = pruefen(entwurf.first, entwurf.second) - ZutatFeld.NAME
            if (fehler.isNotEmpty()) return ZutatErgebnis.Ungueltig(fehler)
        }
        return db.withTransaction {
            val quelle = dao.nachId(vonId) ?: return@withTransaction ZutatErgebnis.NichtGefunden
            val ziel = dao.nachId(nachId) ?: return@withTransaction ZutatErgebnis.NichtGefunden
            val mitEingaben = entwurf?.let { (vorher, nachher) -> quelle.mitAenderungen(vorher, nachher, mitName = false) } ?: quelle
            mitEingaben.barcode?.takeIf { it != quelle.barcode }?.let { code ->
                dao.nachStrichcode(code, ausser = vonId)?.takeIf { it.id != nachId }
                    ?.let { return@withTransaction ZutatErgebnis.StrichcodeVergeben(it.id, it.canonicalName) }
            }
            zusammenfuehrenSchritte(db, mitEingaben, ziel, uhr())
            ZutatErgebnis.Zusammengefuehrt(ziel.id, ziel.canonicalName)
        }
    }

    /** Was „Namen vereinheitlichen“ täte (für die Rückfrage). */
    suspend fun vereinheitlichenVorschau(): List<Angleichung> = angleichungen(dao.alle())

    /**
     * Gleicht alle Namen an den Katalog an – in einer Transaktion und auf dem Stand von jetzt (nicht dem der Vorschau).
     * Gibt die Zahl der angepassten Zutaten zurück ([angepassteZutaten]).
     */
    suspend fun vereinheitlichen(): Int = db.withTransaction {
        val schritte = angleichungen(dao.alle())
        val jetzt = uhr()
        for (s in schritte) {
            when (s) {
                is Angleichung.Umbenennen -> dao.nachId(s.id)?.let { dao.upsert(it.copy(canonicalName = s.neu, updatedAt = jetzt)) }
                is Angleichung.KategorieErgaenzen -> dao.nachId(s.id)?.takeIf { it.category == null }
                    ?.let { dao.upsert(it.copy(category = s.kategorie, updatedAt = jetzt)) }
                is Angleichung.Zusammenfuehren -> {
                    val quelle = dao.nachId(s.vonId)
                    val ziel = dao.nachId(s.nachId)
                    if (quelle != null && ziel != null) zusammenfuehrenSchritte(db, quelle, ziel, jetzt)
                }
            }
        }
        schritte.angepassteZutaten()
    }

    /** Andere Zutat mit diesem Namen ohne Groß/klein – auch bei Umlauten, anders als `COLLATE NOCASE`. */
    private suspend fun andereMitNamen(name: String, ausser: String?): ZutatEntity? =
        dao.alle().firstOrNull { it.id != ausser && it.canonicalName.equals(name, ignoreCase = true) }

    private fun nameFehler(name: String): String? = when {
        name.isEmpty() -> "Name fehlt"
        name.length > NAME_MAX -> "Höchstens 200 Zeichen"
        else -> null
    }

    /** Gleichzeitig (etwa per Sync) angelegter gleicher Name: Der eindeutige Index schlägt an → `NameVergeben`. */
    private suspend fun namensschutz(name: String, block: suspend () -> ZutatErgebnis): ZutatErgebnis = try {
        block()
    } catch (e: SQLiteConstraintException) {
        val andere = dao.nachNameOhneGross(nameBereinigt(name)) ?: throw e
        ZutatErgebnis.NameVergeben(andere.id, andere.canonicalName)
    }
}
