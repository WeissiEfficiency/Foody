package de.foody.core.data.sicherung

import android.content.Context
import android.database.SQLException
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import de.foody.api.protocol.ZahlGrenzen
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.sicherung.SicherungDaten.Companion.DATEN_EINTRAG
import de.foody.core.data.sicherung.SicherungDaten.Companion.FORMAT
import de.foody.core.data.sicherung.SicherungDaten.Companion.FORMAT_V1
import de.foody.core.data.sicherung.SicherungDaten.Companion.FOTO_NAME
import de.foody.core.data.sicherung.SicherungDaten.Companion.FOTO_ORDNER
import de.foody.core.data.sicherung.SicherungDaten.Companion.FOTO_SCHEMA
import de.foody.core.data.sicherung.SicherungDaten.Companion.MAX_FOTO_BYTES
import de.foody.core.data.sicherung.SicherungDaten.Companion.MAX_GESAMT_BYTES
import de.foody.core.data.sicherung.SicherungDaten.Companion.MAX_JSON_BYTES
import de.foody.core.data.startdaten.StartdatenLader
import de.foody.core.data.sync.SyncSpeicher
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.TagebuchArt
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Ergebnis von [Sicherung.lesen]. */
sealed interface ImportErgebnis {
    /** Alle Daten wurden ersetzt; [vorratUebersprungen] zählt Vorratseinträge einer v1-Sicherung, die entfallen. */
    data class Fertig(val rezepte: Int, val zutaten: Int, val fotos: Int, val vorratUebersprungen: Int) : ImportErgebnis

    /** Import gesperrt, solange der Sync verbunden ist (er würde die Daten auf dem Server ersetzen). Nichts geändert. */
    data object SyncAktiv : ImportErgebnis

    /** Datei unbrauchbar (unbekanntes Format, beschädigt, zu groß, unsichere Pfade). Nichts geändert. [grund] ist deutsch. */
    data class Fehler(val grund: String) : ImportErgebnis
}

/** Ergebnis von [Sicherung.allesLoeschen]. */
sealed interface LoeschErgebnis {
    data object Fertig : LoeschErgebnis

    /** Gesperrt, solange der Sync verbunden ist (er würde die Löschung auf den Server tragen). Nichts geändert. */
    data object SyncAktiv : LoeschErgebnis
}

/**
 * Sicherung aller Daten als ZIP – wie in der v1 mit `backup.json` und den Rezeptfotos unter `photos/<n>.jpg`
 * (Aufbau in [SicherungDaten]). Geschrieben wird Format 3 (ohne Vorrat); gelesen werden Format 3 und Sicherungen der
 * v1 (auch die älteren reinen JSON-Dateien ohne ZIP), deren Vorrat übersprungen wird.
 *
 * Der Import **ersetzt** alle Daten (wie v1) und ist nur bei getrenntem Sync erlaubt. Er ist atomar: Fotos werden
 * zuerst als neue eigene Dateien abgelegt (byte-gleich, damit ihr Hash stabil bleibt), dann folgt die Datenbank in
 * einer Transaktion; scheitert etwas, werden die abgelegten Fotos wieder entfernt und die Daten bleiben unverändert.
 * Bei inaktivem Sync merken die Trigger nichts in der Outbox vor; ein späteres Verbinden mit „Zusammenführen“ lädt die
 * importierten Daten hoch.
 */
class Sicherung(
    context: Context,
    private val db: FoodyDatenbank,
    private val ablage: FotoAblage,
    private val sync: SyncSpeicher,
    private val startdaten: StartdatenLader,
) {
    private val context = context.applicationContext ?: context
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ---- Schreiben -------------------------------------------------------------------------------------------

    /**
     * Schreibt alle Daten und die lesbaren Rezeptfotos (eigene Dateien und Galerie-Links) als ZIP nach [ziel]. Jedes
     * Foto steht nur einmal in der ZIP, auch wenn mehrere Rezepte es nutzen. Unlesbare Fotos bleiben als Link stehen.
     * [ziel] wird nicht geschlossen.
     */
    suspend fun schreiben(ziel: OutputStream) = withContext(Dispatchers.IO) {
        val daten = db.withTransaction { ausDatenbank() }
        val zip = ZipOutputStream(ziel.buffered())
        val gepackt = mutableMapOf<String, String>()
        for (bild in daten.recipes.mapNotNull { it.imageUri }.distinct()) {
            val bytes = fotoLesen(bild) ?: continue
            val name = "${gepackt.size + 1}.jpg"
            zip.putNextEntry(ZipEntry(FOTO_ORDNER + name))
            zip.write(bytes)
            zip.closeEntry()
            gepackt[bild] = FOTO_SCHEMA + name
        }
        val mitFotos = daten.copy(recipes = daten.recipes.map { r -> r.copy(imageUri = r.imageUri?.let { gepackt[it] ?: it }) })
        zip.putNextEntry(ZipEntry(DATEN_EINTRAG))
        zip.write(json.encodeToString(SicherungDaten.serializer(), mitFotos).toByteArray())
        zip.closeEntry()
        zip.finish()
        zip.flush()
    }

    /** Inhalt eines eigenen Fotos oder Galerie-Links, `null` wenn nicht lesbar oder zu groß – erst ganz lesen, dann packen. */
    private fun fotoLesen(bild: String): ByteArray? = try {
        val datei = ablage.datei(bild)
        when {
            datei != null -> datei.takeIf { it.isFile && it.length() <= MAX_FOTO_BYTES }?.readBytes()
            bild.startsWith(GALERIE) -> context.contentResolver.openInputStream(Uri.parse(bild))?.use { it.begrenztLesen(MAX_FOTO_BYTES) }
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun ausDatenbank(): SicherungDaten = SicherungDaten(
        format = FORMAT,
        ingredients = db.zutatDao().alle().map {
            SicherungDaten.Zutat(
                id = it.id, name = it.canonicalName, category = it.category, density = it.densityGPerMl.text(),
                pieceWeight = it.pieceWeightG.text(), basis = it.nutrientBasis?.name, energyKj = it.energyKj.text(),
                protein = it.protein.text(), carbs = it.carbs.text(), fat = it.fat.text(), fiber = it.fiber.text(),
                sugar = it.sugar.text(), salt = it.salt.text(), source = it.nutrientSource, createdAt = it.createdAt,
                updatedAt = it.updatedAt, barcode = it.barcode,
            )
        },
        recipes = db.rezeptDao().alle().map {
            SicherungDaten.Rezept(
                id = it.id, name = it.name, servings = it.defaultServings, prep = it.prepMinutes, cook = it.cookMinutes,
                imageUri = it.imageUri, notes = it.notes, tags = it.tags, archivedAt = it.archivedAt, createdAt = it.createdAt,
                updatedAt = it.updatedAt, favorite = it.favorite, sourceUrl = it.sourceUrl, rating = it.rating,
                mahlzeiten = it.mahlzeiten, gaenge = it.gaenge,
            )
        },
        recipeIngredients = db.rezeptZutatDao().alle().map {
            SicherungDaten.RezeptZutat(
                it.id, it.recipeId, it.ingredientId, it.amount.toPlainString(), it.unit.name, it.sortOrder, it.preparationNote,
                it.optional,
            )
        },
        steps = db.arbeitsschrittDao().alle().map { SicherungDaten.Schritt(it.id, it.recipeId, it.position, it.text) },
        mealSlots = db.planEintragDao().alle().map {
            SicherungDaten.PlanEintrag(it.id, it.date.toString(), it.slotType, it.recipeId, it.servings, it.cookedAt, it.createdAt, it.updatedAt)
        },
        shoppingLists = db.einkaufslisteDao().alle().map {
            SicherungDaten.Einkaufsliste(
                it.id, it.name, it.rangeStart?.toString(), it.rangeEnd?.toString(), it.generationVersion, it.createdAt, it.updatedAt,
            )
        },
        shoppingItems = db.einkaufsartikelDao().alle().map {
            SicherungDaten.Einkaufsartikel(
                id = it.id, listId = it.listId, ingredientId = it.ingredientId, name = it.name, amount = it.amount.text(),
                unit = it.unit?.name, checked = it.checked, manual = it.manual, category = it.category, sortOrder = it.sortOrder,
                note = it.note,
            )
        },
        shoppingSources = db.einkaufsartikelQuelleDao().alle().map {
            SicherungDaten.Quelle(
                it.id, it.shoppingItemId, it.mealSlotId, it.recipeIngredientId, it.recipeName, it.date.toString(),
                it.contributedAmount.toPlainString(), it.unit.name,
            )
        },
        tagebuch = db.tagebuchDao().alle().map {
            SicherungDaten.Tagebuch(
                id = it.id, datum = it.datum.toString(), mahlzeit = it.mahlzeit, art = it.art.name, name = it.name,
                rezeptId = it.rezeptId, planEintragId = it.planEintragId, portionen = it.portionen.text(), zutatId = it.zutatId,
                menge = it.menge.text(), einheit = it.einheit?.name, energieKj = it.energieKj.text(), eiweiss = it.eiweiss.text(),
                kohlenhydrate = it.kohlenhydrate.text(), fett = it.fett.text(), vollstaendig = it.vollstaendig,
                createdAt = it.createdAt, updatedAt = it.updatedAt,
            )
        },
    )

    // ---- Lesen -----------------------------------------------------------------------------------------------

    /**
     * Ersetzt alle Daten durch den Inhalt von [quelle] (ZIP oder ältere reine JSON-Sicherung). [quelle] wird nicht
     * geschlossen. Gesperrt bei aktivem Sync; bei jedem Fehler bleiben Daten und Fotoordner unverändert.
     *
     * Abbruch: Die Transaktion läuft nicht abbrechbar; ein Abbruch greift davor (nichts geändert, abgelegte Fotos werden
     * entfernt) oder danach (Daten übernommen, ihre Fotos bleiben; die Nachbereitung entfällt dann).
     *
     * Der Aufrufer muss das Bearbeiten von Rezeptfotos während des Imports sperren: nach der Übernahme werden alle
     * eigenen Fotos ohne Rezept gelöscht, auch ein gerade gespeichertes, noch nicht verknüpftes.
     */
    suspend fun lesen(quelle: InputStream): ImportErgebnis = withContext(Dispatchers.IO) {
        if (sync.zustand().aktiv) return@withContext ImportErgebnis.SyncAktiv
        val abgelegt = mutableMapOf<String, String>()
        var uebernommen = false
        try {
            val daten = entschluesseln(einlesen(quelle, abgelegt))
            val eintraege = abbilden(daten, abgelegt)
            // NonCancellable: ein Abbruch darf die Rückkehr aus der schon festgeschriebenen Transaktion nicht in einen
            // Fehlschlag verwandeln; `uebernommen` wird noch im selben, nicht abbrechbaren Block gesetzt.
            val gesperrt = withContext(NonCancellable) {
                val gesperrt = db.withTransaction {
                    // Erneut in der Transaktion: der Sync könnte inzwischen verbunden worden sein.
                    if (db.syncDao().zustand()?.active == true) return@withTransaction true
                    ersetzen(eintraege)
                    false
                }
                uebernommen = !gesperrt
                gesperrt
            }
            if (gesperrt) return@withContext ImportErgebnis.SyncAktiv
            nachbereiten(daten, eintraege, abgelegt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportErgebnis.Fehler(grund(e))
        } finally {
            // Auch bei Abbruch und Error: Fotos eines nicht übernommenen Imports wieder entfernen.
            if (!uebernommen) verwerfen(abgelegt)
        }
    }

    /**
     * Nach dem Import: nicht mehr benutzte eigene Fotos entfernen, importierte zu große Fotos verkleinern (wie v1). Die
     * Daten sind schon übernommen; Fehler hier werden nur protokolliert, damit der Import nicht als Fehlschlag gilt.
     */
    private suspend fun nachbereiten(daten: SicherungDaten, e: Eintraege, abgelegt: Map<String, String>): ImportErgebnis.Fertig {
        val importiert = abgelegt.values.toSet().intersect(e.rezepte.mapNotNull { it.imageUri }.toSet())
        try {
            val benutzt = db.rezeptDao().alle().mapNotNull { it.imageUri }
            for (link in ablage.unbenutzteEntfernen(benutzt)) db.syncDao().loeschenFotoLokal(link)
            // Nur Fotos über den Grenzen werden neu kodiert; alle anderen bleiben byte-gleich.
            importiert.forEach { link -> ablage.datei(link)?.let(ablage::verkleinern) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.w(TAG, "Nachbereitung nach dem Import fehlgeschlagen", ex)
        }
        return ImportErgebnis.Fertig(
            rezepte = daten.recipes.size,
            zutaten = daten.ingredients.size,
            fotos = importiert.size,
            vorratUebersprungen = daten.pantry.size,
        )
    }

    private fun verwerfen(abgelegt: Map<String, String>) {
        abgelegt.values.forEach { link -> ablage.datei(link)?.delete() }
    }

    /** Text von `backup.json`; Fotos aus einer ZIP landen dabei schon als eigene Dateien in [abgelegt] (Name → Link). */
    private suspend fun einlesen(quelle: InputStream, abgelegt: MutableMap<String, String>): String {
        val ein = BufferedInputStream(quelle)
        ein.mark(2)
        val zip = ein.read() == 'P'.code && ein.read() == 'K'.code
        ein.reset()
        return if (zip) zipLesen(ein, abgelegt) else ein.begrenztLesen(MAX_JSON_BYTES).decodeToString()
    }

    /**
     * Liest `backup.json` und legt die Fotos unter `photos/` ab. Ein Eintrag mit unsicherem Pfad (`..`, absolut)
     * verwirft die ganze Datei (Zip-Slip); andere unbekannte Einträge werden übergangen. Größen sind begrenzt.
     */
    private suspend fun zipLesen(ein: InputStream, abgelegt: MutableMap<String, String>): String {
        var text: String? = null
        var gesamt = 0L
        val zip = ZipInputStream(ein)
        while (true) {
            val eintrag = zip.nextEntry ?: break
            val name = eintrag.name
            if (unsicher(name)) throw SicherungsFehler(UNSICHERER_PFAD)
            val foto = name.removePrefix(FOTO_ORDNER)
            when {
                name == DATEN_EINTRAG -> text = zip.begrenztLesen(MAX_JSON_BYTES).decodeToString()
                name.startsWith(FOTO_ORDNER) && FOTO_NAME.matches(foto) && foto !in abgelegt -> {
                    val bytes = zip.begrenztLesen(MAX_FOTO_BYTES)
                    gesamt += bytes.size
                    if (gesamt > MAX_GESAMT_BYTES) throw SicherungsFehler(ZU_GROSS)
                    abgelegt[foto] = ablage.ablegen(bytes)
                }
            }
        }
        return text ?: throw SicherungsFehler("Keine $DATEN_EINTRAG in der Sicherung")
    }

    private fun unsicher(name: String): Boolean =
        name.startsWith("/") || name.startsWith("\\") || name.contains(':') || name.split('/', '\\').any { it == ".." }

    /** Prüft die Formatnummer vor dem eigentlichen Lesen: ein unbekanntes Format soll so heißen, nicht „beschädigt“. */
    private fun entschluesseln(text: String): SicherungDaten {
        val baum = json.parseToJsonElement(text) as? JsonObject ?: throw SicherungsFehler(BESCHAEDIGT)
        fun nummer(schluessel: String): Int? {
            val wert = baum[schluessel] ?: return null
            return (wert as? JsonPrimitive)?.intOrNull ?: throw SicherungsFehler("Unbekanntes Format")
        }
        val format = nummer("format")
        if (format != null) {
            if (format != FORMAT) throw SicherungsFehler("Unbekanntes Format $format")
        } else {
            val alt = nummer("formatVersion") ?: FORMAT_V1
            if (alt != FORMAT_V1) throw SicherungsFehler("Unbekanntes Format $alt")
        }
        return json.decodeFromJsonElement(SicherungDaten.serializer(), baum)
    }

    /** Alle Zeilen der Sicherung als Entities – vollständig geprüft, bevor die Datenbank angefasst wird. */
    private class Eintraege(
        val zutaten: List<ZutatEntity>,
        val rezepte: List<RezeptEntity>,
        val rezeptZutaten: List<RezeptZutatEntity>,
        val schritte: List<ArbeitsschrittEntity>,
        val plan: List<PlanEintragEntity>,
        val listen: List<EinkaufslisteEntity>,
        val artikel: List<EinkaufsartikelEntity>,
        val quellen: List<EinkaufsartikelQuelleEntity>,
        val tagebuch: List<TagebuchEintragEntity>,
    )

    private fun abbilden(d: SicherungDaten, abgelegt: Map<String, String>) = Eintraege(
        zutaten = d.ingredients.map {
            ZutatEntity(
                id = it.id, canonicalName = it.name, category = it.category, densityGPerMl = it.density.zahlOderNull(),
                pieceWeightG = it.pieceWeight.zahlOderNull(), nutrientBasis = it.basis?.let(NutrientBasis::valueOf),
                energyKj = it.energyKj.zahlOderNull(), protein = it.protein.zahlOderNull(), carbs = it.carbs.zahlOderNull(),
                fat = it.fat.zahlOderNull(), fiber = it.fiber.zahlOderNull(), sugar = it.sugar.zahlOderNull(),
                salt = it.salt.zahlOderNull(), nutrientSource = it.source, createdAt = it.createdAt, updatedAt = it.updatedAt,
                barcode = it.barcode,
            )
        },
        rezepte = d.recipes.map {
            RezeptEntity(
                id = it.id, name = it.name, defaultServings = it.servings, prepMinutes = it.prep, cookMinutes = it.cook,
                imageUri = foto(it.imageUri, abgelegt), notes = it.notes, tags = it.tags, archivedAt = it.archivedAt,
                createdAt = it.createdAt, updatedAt = it.updatedAt, favorite = it.favorite, sourceUrl = it.sourceUrl,
                rating = it.rating, mahlzeiten = it.mahlzeiten, gaenge = it.gaenge,
            )
        },
        rezeptZutaten = d.recipeIngredients.map {
            RezeptZutatEntity(
                it.id, it.recipeId, it.ingredientId, zahl(it.amount), MeasureUnit.valueOf(it.unit), it.sortOrder, it.note, it.optional,
            )
        },
        schritte = d.steps.map { ArbeitsschrittEntity(it.id, it.recipeId, it.position, it.text) },
        plan = d.mealSlots.map {
            PlanEintragEntity(
                id = it.id, date = LocalDate.parse(it.date), slotType = mahlzeit(it.slotType), recipeId = it.recipeId,
                servings = it.servings, cookedAt = it.cookedAt, createdAt = it.createdAt, updatedAt = it.updatedAt,
            )
        },
        listen = d.shoppingLists.map {
            EinkaufslisteEntity(
                id = it.id, name = it.name, rangeStart = it.start?.let(LocalDate::parse), rangeEnd = it.end?.let(LocalDate::parse),
                generationVersion = it.version, createdAt = it.createdAt, updatedAt = it.updatedAt,
            )
        },
        artikel = d.shoppingItems.map {
            EinkaufsartikelEntity(
                id = it.id, listId = it.listId, ingredientId = it.ingredientId, name = it.name, amount = it.amount.zahlOderNull(),
                unit = it.unit?.let(MeasureUnit::valueOf), checked = it.checked, manual = it.manual, category = it.category,
                sortOrder = it.sortOrder, note = it.note,
            )
        },
        quellen = d.shoppingSources.map {
            EinkaufsartikelQuelleEntity(
                it.id, it.itemId, it.mealSlotId, it.recipeIngredientId, it.recipeName, LocalDate.parse(it.date), zahl(it.amount),
                MeasureUnit.valueOf(it.unit),
            )
        },
        tagebuch = d.tagebuch.map {
            TagebuchEintragEntity(
                id = it.id, datum = LocalDate.parse(it.datum), mahlzeit = mahlzeit(it.mahlzeit), art = TagebuchArt.valueOf(it.art),
                name = it.name, rezeptId = it.rezeptId, planEintragId = it.planEintragId, portionen = it.portionen.zahlOderNull(),
                zutatId = it.zutatId, menge = it.menge.zahlOderNull(), einheit = it.einheit?.let(MeasureUnit::valueOf),
                energieKj = it.energieKj.zahlOderNull(), eiweiss = it.eiweiss.zahlOderNull(),
                kohlenhydrate = it.kohlenhydrate.zahlOderNull(), fett = it.fett.zahlOderNull(), vollstaendig = it.vollstaendig,
                createdAt = it.createdAt, updatedAt = it.updatedAt,
            )
        },
    )

    /**
     * Fotoverweis einer Sicherung (wie v1): Foto aus der ZIP → Link der neu abgelegten Datei (fehlt es, kein Foto);
     * Galerie-Links und eigene Fotolinks bleiben; alles andere – etwa `file:`-Pfade in den privaten Speicher aus einer
     * manipulierten Datei – wird verworfen, sonst läse Foody sie mit eigenen Rechten und packte sie in die nächste Sicherung.
     */
    private fun foto(bild: String?, abgelegt: Map<String, String>): String? = when {
        bild == null -> null
        bild.startsWith(FOTO_SCHEMA) -> abgelegt[bild.removePrefix(FOTO_SCHEMA)]
        bild.startsWith(GALERIE) || ablage.istEigeneDatei(bild) -> bild
        else -> null
    }

    /** Alter Freitext („Frühstück“) wird dem Enum-Namen zugeordnet (wie v1); unbekannte Texte bleiben. */
    private fun mahlzeit(text: String): String = Mahlzeit.ausText(text)?.name ?: text

    // ---- Alles löschen ---------------------------------------------------------------------------------------

    /**
     * Löscht alle Fachdaten dieses Geräts und eigene Fotos, dann den Startkatalog neu einspielen. Nur bei getrenntem Sync
     * (auch in der Transaktion erneut geprüft); Sync-Zustand, Outbox und Einstellungen bleiben. Die Startdaten-Version
     * wird vor dem Einspielen zurückgesetzt: scheitert es, holt der nächste App-Start den Katalog nach.
     */
    suspend fun allesLoeschen(): LoeschErgebnis = withContext(Dispatchers.IO) {
        // Nicht abbrechbar: Wer die Seite verlässt, während gelöscht wird, soll kein halbes Ergebnis bekommen (Daten
        // weg, aber Fotos noch da und Katalog leer). Das Ganze ist kurz.
        withContext(NonCancellable) {
            // Vor dem Leeren: Stirbt der Prozess danach, holt der nächste Start den Startkatalog nach
            val vorher = startdaten.versionZuruecksetzen()
            val gesperrt = db.withTransaction {
                // Die Sperre wird hier geprüft, in der Transaktion: der Sync könnte gerade erst verbunden worden sein
                if (db.syncDao().zustand()?.active == true) return@withTransaction true
                leeren()
                false
            }
            if (gesperrt) {
                startdaten.versionWiederherstellen(vorher)
                return@withContext LoeschErgebnis.SyncAktiv
            }
            try {
                for (link in ablage.unbenutzteEntfernen(emptyList())) db.syncDao().loeschenFotoLokal(link)
                startdaten.ladenWennNoetig()
            } catch (e: Exception) {
                Log.w(TAG, "Aufräumen nach dem Löschen fehlgeschlagen", e)
            }
            LoeschErgebnis.Fertig
        }
    }

    /** Leert alle Fachtabellen, Kinder zuerst; nur in einer Transaktion aufrufen. */
    private suspend fun leeren() {
        db.tagebuchDao().alleLoeschen()
        db.einkaufsartikelQuelleDao().alleLoeschen()
        db.einkaufsartikelDao().alleLoeschen()
        db.einkaufslisteDao().alleLoeschen()
        db.planEintragDao().alleLoeschen()
        db.arbeitsschrittDao().alleLoeschen()
        db.rezeptZutatDao().alleLoeschen()
        db.rezeptDao().alleLoeschen()
        db.zutatDao().alleLoeschen()
    }

    /** Leert alle Fachtabellen und schreibt die Sicherung hinein; nur in einer Transaktion aufrufen. */
    private suspend fun ersetzen(e: Eintraege) {
        leeren()
        e.zutaten.forEach { db.zutatDao().upsert(it) }
        e.rezepte.forEach { db.rezeptDao().upsert(it) }
        e.rezeptZutaten.forEach { db.rezeptZutatDao().upsert(it) }
        e.schritte.forEach { db.arbeitsschrittDao().upsert(it) }
        e.plan.forEach { db.planEintragDao().upsert(it) }
        e.listen.forEach { db.einkaufslisteDao().upsert(it) }
        e.artikel.forEach { db.einkaufsartikelDao().upsert(it) }
        e.quellen.forEach { db.einkaufsartikelQuelleDao().upsert(it) }
        e.tagebuch.forEach { db.tagebuchDao().upsert(it) }
    }

    /** Feste deutsche Meldung je Fehlerart; nie Dateiinhalte. */
    private fun grund(e: Exception): String = when (e) {
        is SicherungsFehler -> e.message
        is SQLException -> "Die Sicherung enthält ungültige Verweise"
        else -> BESCHAEDIGT
    }

    /** Erwarteter, benannter Fehler beim Lesen einer Sicherung. */
    private class SicherungsFehler(override val message: String) : Exception(message)

    private companion object {
        const val TAG = "Sicherung"
        const val GALERIE = "content://"
        const val BESCHAEDIGT = "Die Sicherung ist beschädigt oder unvollständig"
        const val ZU_GROSS = "Sicherung zu groß"
        const val UNSICHERER_PFAD = "Die Sicherung enthält unzulässige Dateipfade"

        fun BigDecimal?.text(): String? = this?.toPlainString()

        /** Zahl aus einer Sicherung, mit den Grenzen aus [ZahlGrenzen]. */
        fun zahl(text: String): BigDecimal = ZahlGrenzen.lesen(text) ?: throw SicherungsFehler("Ungültige Zahl in der Sicherung")

        fun String?.zahlOderNull(): BigDecimal? = this?.let(::zahl)

        /** Liest höchstens [grenze] Bytes; mehr gilt als zu große oder bösartige Datei. */
        fun InputStream.begrenztLesen(grenze: Long): ByteArray {
            val aus = ByteArrayOutputStream()
            val puffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var gelesen = 0L
            while (true) {
                val n = read(puffer)
                if (n < 0) return aus.toByteArray()
                gelesen += n
                if (gelesen > grenze) throw SicherungsFehler(ZU_GROSS)
                aus.write(puffer, 0, n)
            }
        }
    }
}
