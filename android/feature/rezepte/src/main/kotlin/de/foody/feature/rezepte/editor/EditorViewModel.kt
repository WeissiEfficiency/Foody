package de.foody.feature.rezepte.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.api.protocol.ZahlGrenzen
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.Gespeichert
import de.foody.core.data.rezepte.RezeptEntwurf
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.SolangeAppOffen
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.ImportedIngredient
import de.foody.domain.IngredientCatalog
import de.foody.domain.Mahlzeit
import de.foody.domain.RezeptEinordnung
import de.foody.domain.ZutatZeile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Eingaben des Editors, Zahlen als Text wie getippt; [mahlzeiten]/[gaenge] `null` = automatisch vermuten. Serializable:
 * Sie liegen im SavedStateHandle und überstehen ein Prozessende.
 */
data class EditorEingaben(
    val name: String = "",
    val portionen: String = "2",
    val vorbereitung: String = "",
    val kochzeit: String = "",
    val tags: String = "",
    val zutatenText: String = "",
    val schritteText: String = "",
    val notizen: String = "",
    val quelle: String = "",
    val mahlzeiten: Set<Mahlzeit>? = null,
    val gaenge: Set<Gang>? = null,
    val bild: String? = null,
) : java.io.Serializable

/** Eine Zutatzeile mit dem, was erkannt wurde; [fehler] sperrt das Speichern. */
data class ZeilenStatus(val zeile: String, val erkannt: ImportedIngredient?, val imKatalog: Boolean, val fehler: String?)

/** Eine geladene Zeile: was gespeichert war und zu welcher Zutat; bleibt, solange ihr Text unverändert ist. */
internal data class Vorlage(val zeile: ImportedIngredient, val zutatId: String?)

data class EditorZustand(
    val geladen: Boolean = false,
    val id: String? = null,
    val eingaben: EditorEingaben = EditorEingaben(),
    val zeilen: List<ZeilenStatus> = emptyList(),
    val einordnung: Einordnung = Einordnung(emptySet(), emptySet(), true, true),
    val hinweis: String? = null,
)

private const val MAX_NAME = 200
private const val MAX_PORTIONEN = 99
private const val MAX_MINUTEN = 1440
private val EXPONENT = Regex("""\d[eE][+-]?\d""")

@HiltViewModel
class EditorViewModel @Inject constructor(
    /** Eingaben überstehen damit ein Prozessende (etwa während das Kamerafoto entsteht). */
    private val gespeichert: SavedStateHandle,
    private val repo: RezeptRepository,
    db: FoodyDatenbank,
    private val ablage: FotoAblage,
) : ViewModel() {
    private val eingaben = gespeichert.getMutableStateFlow(EINGABEN, EditorEingaben())
    private val original = MutableStateFlow(EditorEingaben())
    private val kopf = MutableStateFlow(Triple(false, null as String?, null as String?)) // geladen, id, hinweis
    private val gesperrt = MutableStateFlow(false)
    private val vorlagen = MutableStateFlow(emptyMap<String, Vorlage>())
    private var geladenFuer: String? = KEINS

    private val katalog = db.zutatDao().alleFlow().map { z -> z.map { it.canonicalName.lowercase() }.toSet() }

    val zustand: StateFlow<EditorZustand> = combine(eingaben, katalog, kopf, vorlagen) { e, namen, (geladen, id, hinweis), v ->
        EditorZustand(geladen, id, e, zeilenStatus(e.zutatenText, namen, v), einordnung(e), hinweis)
    }.stateIn(viewModelScope, SolangeAppOffen, EditorZustand())

    val speichernMoeglich: StateFlow<Boolean> = combine(zustand, gesperrt) { z, sperre -> !sperre && z.geladen && gueltig(z) }
        .stateIn(viewModelScope, SolangeAppOffen, false)

    val geaendert: StateFlow<Boolean> = combine(eingaben, original) { e, o -> e != o }
        .stateIn(viewModelScope, SolangeAppOffen, false)

    /** Die Eingaben, wie sie geladen (oder zuletzt gespeichert) wurden. */
    val ursprung: StateFlow<EditorEingaben> = original.asStateFlow()

    /**
     * Lädt [id] (oder ein leeres Rezept); derselbe Aufruf nach einem Neuaufbau lässt die Eingaben stehen. Nach einem
     * Prozessende kommen Ursprung und Zeilen aus der Datenbank, die Eingaben aus dem SavedStateHandle.
     *
     * [auftrag]: Auf dem Tablet dient ein ViewModel allen Editor-Aufträgen; ein neuer Auftrag beginnt frisch, auch mit
     * derselben [id] (ein zweites „Neues Rezept“ ist leer). Er steht im SavedStateHandle, damit ein Prozessende die
     * Eingaben des laufenden Auftrags nicht verwirft.
     */
    suspend fun laden(id: String?, auftrag: Int = 0) {
        if (gespeichert.get<Int>(AUFTRAG) != auftrag) {
            if (gespeichert.contains(AUFTRAG)) gespeichert.remove<String>(EINGABEN_FUER)
            gespeichert[AUFTRAG] = auftrag
            geladenFuer = KEINS
            kopf.value = Triple(false, null, null)
        }
        if (geladenFuer == id) return
        geladenFuer = id
        val gerettet = eingaben.value.takeIf { gespeichert.get<String>(EINGABEN_FUER) == (id ?: NEU) }
        gespeichert[EINGABEN_FUER] = id ?: NEU
        val e = id?.let { repo.entwurf(it) }
        // Unveränderte Zeilen behalten Zutat und Hinweis: Katalognamen wie „Reis (roh)“ lassen sich nicht verlustfrei
        // neu zerlegen
        val zeilen = e?.zeilen.orEmpty()
        val texte = zeilen.map { ZutatZeile.schreiben(it.amount, it.unit, it.name, it.note, it.optional) }
        vorlagen.value = texte.withIndex().associate { (i, text) -> schluessel(text) to Vorlage(zeilen[i], e?.zutatIds?.getOrNull(i)) }
        val start = if (e == null) EditorEingaben() else EditorEingaben(
            name = e.name, portionen = e.portionen.toString(), vorbereitung = e.vorbereitung?.toString().orEmpty(),
            kochzeit = e.kochzeit?.toString().orEmpty(), tags = e.tags,
            zutatenText = texte.joinToString("\n"),
            schritteText = e.schritte.joinToString("\n"), notizen = e.notizen.orEmpty(), quelle = e.quelle.orEmpty(),
            mahlzeiten = e.mahlzeiten, gaenge = e.gaenge, bild = e.bild,
        )
        eingaben.value = gerettet ?: start
        original.value = start
        gesperrt.value = false
        kopf.value = Triple(true, id, if (id != null && e == null) GELOESCHT else null)
    }

    fun aendern(f: (EditorEingaben) -> EditorEingaben) = eingaben.update(f)

    fun mahlzeitUmschalten(m: Mahlzeit) = eingaben.update { e ->
        val jetzt = e.mahlzeiten ?: zustand.value.einordnung.mahlzeiten
        e.copy(mahlzeiten = if (m in jetzt) jetzt - m else jetzt + m)
    }

    fun gangUmschalten(g: Gang) = eingaben.update { e ->
        val jetzt = e.gaenge ?: zustand.value.einordnung.gaenge
        e.copy(gaenge = if (g in jetzt) jetzt - g else jetzt + g)
    }

    fun automatisch() = eingaben.update { it.copy(mahlzeiten = null, gaenge = null) }

    /** Übernimmt ein Foto (Photo-Picker oder Kamera) verkleinert in die Fotoablage; scheitert es, bleibt das alte. */
    suspend fun fotoUebernehmen(quelle: Uri) {
        try {
            val link = ablage.speichern(quelle)
            eingaben.update { it.copy(bild = link) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            hinweis(FOTO_FEHLER)
        }
    }

    fun fotoEntfernen() = eingaben.update { it.copy(bild = null) }

    fun hinweis(text: String?) = kopf.update { it.copy(third = text) }

    /**
     * Speichert; `null` bei ungültiger Eingabe oder laufendem/abgeschlossenem Speichern (Doppeltipp). Das Schreiben läuft
     * zu Ende, auch wenn der Bildschirm mittendrin gedreht wird; nur ein Fehler gibt „Speichern“ wieder frei.
     */
    suspend fun speichern(): Gespeichert? {
        val z = zustand.value
        if (!z.geladen || !gueltig(z) || !gesperrt.compareAndSet(expect = false, update = true)) return null
        var fertig = false
        try {
            return withContext(NonCancellable) { schreiben(z).also { fertig = true } }
        } finally {
            if (!fertig) gesperrt.value = false
        }
    }

    private suspend fun schreiben(z: EditorZustand): Gespeichert {
        val e = z.eingaben
        val zeilen = z.zeilen.filter { it.erkannt != null }
        val ergebnis = repo.speichern(
            RezeptEntwurf(
                id = z.id, name = e.name.trim(), portionen = e.portionen.trim().toInt(),
                vorbereitung = e.vorbereitung.trim().toIntOrNull(), kochzeit = e.kochzeit.trim().toIntOrNull(), tags = e.tags,
                zeilen = zeilen.mapNotNull { it.erkannt }, schritte = e.schritteText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                notizen = e.notizen, quelle = e.quelle, mahlzeiten = e.mahlzeiten, gaenge = e.gaenge, bild = e.bild,
                zutatIds = zeilen.map { vorlagen.value[schluessel(it.zeile)]?.zutatId },
                bildBehalten = z.id != null && e.bild == original.value.bild,
            ),
        )
        when (ergebnis) {
            is Gespeichert.Ok -> original.value = e
            Gespeichert.Geloescht -> {
                hinweis(GELOESCHT)
                gesperrt.value = false
            }
        }
        return ergebnis
    }

    private fun einordnung(e: EditorEingaben) =
        RezeptEinordnung.einordnen(e.name, e.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, e.mahlzeiten, e.gaenge)

    companion object {
        const val GELOESCHT = "Rezept wurde inzwischen gelöscht"
        const val FOTO_FEHLER = "Foto konnte nicht übernommen werden"
        private const val KEINS = "\u0000"
        private const val EINGABEN = "rezeptEingaben"
        private const val EINGABEN_FUER = "rezeptEingabenFuer"
        private const val AUFTRAG = "rezeptAuftrag"
        private const val NEU = "neu"
    }
}

/** Vergleichsform einer Zeile: ohne Rand, Leerraum zusammengefasst. */
internal fun schluessel(zeile: String) = zeile.trim().replace(LEERRAUM, " ")

private val LEERRAUM = Regex("\\s+")

/**
 * Je nicht-leerer Zeile: erkannte Zutat, Katalogtreffer (Name oder vereinheitlichter Name), Fehler. Eine Zeile, deren
 * Text einer geladenen Zeile aus [vorlagen] gleicht, gilt als diese – unverändert.
 */
internal fun zeilenStatus(text: String, katalog: Set<String>, vorlagen: Map<String, Vorlage> = emptyMap()): List<ZeilenStatus> =
    text.lines().filter { it.isNotBlank() }.map { zeile ->
        vorlagen[schluessel(zeile)]?.let { return@map ZeilenStatus(zeile.trim(), it.zeile, it.zutatId != null, null) }
        val erkannt = ZutatZeile.lesen(zeile)
        val fehler = when {
            EXPONENT.containsMatchIn(zeile) -> "Ungültige Zahl"
            erkannt == null -> "Keine Zutat erkannt"
            erkannt.amount?.let { it.signum() < 0 || !ZahlGrenzen.imRahmen(it) } == true -> "Menge außerhalb der Grenzen"
            erkannt.name.length > MAX_NAME -> "Name zu lang"
            else -> null
        }
        val imKatalog = erkannt != null &&
            (erkannt.name.lowercase() in katalog || IngredientCatalog.canonicalName(erkannt.name).lowercase() in katalog)
        ZeilenStatus(zeile.trim(), erkannt, imKatalog, fehler)
    }

private fun minuten(text: String) = text.isBlank() || text.trim().toIntOrNull()?.let { it in 0..MAX_MINUTEN } == true

/** Name 1–200 Zeichen, Portionen 1–99, Zeiten leer oder 0–1440, jede Zutatzeile ohne Fehler. */
internal fun gueltig(z: EditorZustand): Boolean {
    val e = z.eingaben
    return e.name.trim().length in 1..MAX_NAME &&
        e.portionen.trim().toIntOrNull()?.let { it in 1..MAX_PORTIONEN } == true &&
        minuten(e.vorbereitung) && minuten(e.kochzeit) &&
        z.zeilen.all { it.fehler == null }
}
