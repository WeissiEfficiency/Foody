package de.foody.feature.rezepte.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.api.protocol.ZahlGrenzen
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.Gespeichert
import de.foody.core.data.rezepte.RezeptEntwurf
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.ImportedIngredient
import de.foody.domain.IngredientCatalog
import de.foody.domain.Mahlzeit
import de.foody.domain.RezeptEinordnung
import de.foody.domain.ZutatZeile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Eingaben des Editors, Zahlen als Text wie getippt; [mahlzeiten]/[gaenge] `null` = automatisch vermuten. */
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
)

/** Eine Zutatzeile mit dem, was erkannt wurde; [fehler] sperrt das Speichern. */
data class ZeilenStatus(val zeile: String, val erkannt: ImportedIngredient?, val imKatalog: Boolean, val fehler: String?)

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
    private val repo: RezeptRepository,
    db: FoodyDatenbank,
    private val ablage: FotoAblage,
) : ViewModel() {
    private val eingaben = MutableStateFlow(EditorEingaben())
    private val original = MutableStateFlow(EditorEingaben())
    private val kopf = MutableStateFlow(Triple(false, null as String?, null as String?)) // geladen, id, hinweis
    private val gesperrt = MutableStateFlow(false)
    private var geladenFuer: String? = KEINS

    private val katalog = db.zutatDao().alleFlow().map { z -> z.map { it.canonicalName.lowercase() }.toSet() }

    val zustand: StateFlow<EditorZustand> = combine(eingaben, katalog, kopf) { e, namen, (geladen, id, hinweis) ->
        EditorZustand(geladen, id, e, zeilenStatus(e.zutatenText, namen), einordnung(e), hinweis)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EditorZustand())

    val speichernMoeglich: StateFlow<Boolean> = combine(zustand, gesperrt) { z, sperre -> !sperre && z.geladen && gueltig(z) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val geaendert: StateFlow<Boolean> = combine(eingaben, original) { e, o -> e != o }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Lädt [id] (oder ein leeres Rezept); derselbe Aufruf nach einem Neuaufbau lässt die Eingaben stehen. */
    suspend fun laden(id: String?) {
        if (geladenFuer == id) return
        geladenFuer = id
        val e = id?.let { repo.entwurf(it) }
        val start = if (e == null) EditorEingaben() else EditorEingaben(
            name = e.name, portionen = e.portionen.toString(), vorbereitung = e.vorbereitung?.toString().orEmpty(),
            kochzeit = e.kochzeit?.toString().orEmpty(), tags = e.tags,
            zutatenText = e.zeilen.joinToString("\n") { ZutatZeile.schreiben(it.amount, it.unit, it.name, it.note, it.optional) },
            schritteText = e.schritte.joinToString("\n"), notizen = e.notizen.orEmpty(), quelle = e.quelle.orEmpty(),
            mahlzeiten = e.mahlzeiten, gaenge = e.gaenge, bild = e.bild,
        )
        eingaben.value = start
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

    /** Speichert; `null` bei ungültiger Eingabe oder laufendem/abgeschlossenem Speichern (Doppeltipp). */
    suspend fun speichern(): Gespeichert? {
        val z = zustand.value
        if (!z.geladen || !gueltig(z) || !gesperrt.compareAndSet(expect = false, update = true)) return null
        val e = z.eingaben
        val ergebnis = repo.speichern(
            RezeptEntwurf(
                id = z.id, name = e.name.trim(), portionen = e.portionen.trim().toInt(),
                vorbereitung = e.vorbereitung.trim().toIntOrNull(), kochzeit = e.kochzeit.trim().toIntOrNull(), tags = e.tags,
                zeilen = z.zeilen.mapNotNull { it.erkannt }, schritte = e.schritteText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                notizen = e.notizen, quelle = e.quelle, mahlzeiten = e.mahlzeiten, gaenge = e.gaenge, bild = e.bild,
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
    }
}

/** Je nicht-leerer Zeile: erkannte Zutat, Katalogtreffer (Name oder vereinheitlichter Name), Fehler. */
internal fun zeilenStatus(text: String, katalog: Set<String>): List<ZeilenStatus> =
    text.lines().filter { it.isNotBlank() }.map { zeile ->
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
