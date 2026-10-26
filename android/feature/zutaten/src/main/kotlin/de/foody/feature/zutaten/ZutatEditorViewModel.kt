package de.foody.feature.zutaten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.zutaten.ZutatEntwurf
import de.foody.core.data.zutaten.ZutatErgebnis
import de.foody.core.data.zutaten.ZutatFeld
import de.foody.core.data.zutaten.ZutatKurz
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.data.zutaten.pruefen
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Zustand des Editors. [vorher] ist der Stand beim Öffnen (gespeichert werden nur Abweichungen davon); [fehler] zeigt
 * nur Felder, die der Nutzer verlassen hat, nach „Speichern“ alle; [ziele] = andere Zutaten fürs Zusammenführen.
 */
data class EditorZustand(
    val geladen: Boolean = false,
    val vorher: ZutatEntwurf? = null,
    val entwurf: ZutatEntwurf? = null,
    val fehler: Map<ZutatFeld, String> = emptyMap(),
    val synonyme: List<String> = emptyList(),
    val verwendetIn: List<String> = emptyList(),
    val ziele: List<ZutatKurz> = emptyList(),
    val laeuft: Boolean = false,
) {
    val geaendert: Boolean get() = entwurf != vorher
}

/** Einmalige Ereignisse: [Fertig] schließt den Editor, die übrigen öffnen Dialoge oder zeigen eine Meldung. */
sealed interface EditorEreignis {
    data object Fertig : EditorEreignis

    /** Der neue Name gehört einer anderen Zutat: Zusammenführen anbieten. */
    data class NameVergeben(val id: String, val name: String) : EditorEreignis

    data class InVerwendung(val rezepte: Int) : EditorEreignis

    data class Meldung(val text: String) : EditorEreignis
}

/** Editor einer Zutat: Entwurf bearbeiten, speichern, löschen, zusammenführen. Meldungen beim Schließen gehen an die Liste. */
@HiltViewModel
class ZutatEditorViewModel @Inject constructor(
    private val repo: ZutatenRepository,
    private val meldungen: ZutatenMeldungen,
) : ViewModel() {
    private val stand = MutableStateFlow(EditorZustand())
    val zustand: StateFlow<EditorZustand> = stand.asStateFlow()

    private val kanal = Channel<EditorEreignis>(Channel.BUFFERED)
    val ereignisse: Flow<EditorEreignis> = kanal.receiveAsFlow()

    private var id: String? = null
    private var sichtbar = emptySet<ZutatFeld>()
    private var zusatz = emptyMap<ZutatFeld, String>()

    /** Der Editor ist (durch eigene Aktion oder weil die Zutat verschwand) fertig: weitere Meldungen unterdrücken. */
    private var fertig = false

    fun laden(id: String) {
        if (this.id != null) return
        this.id = id
        viewModelScope.launch {
            repo.zutat(id).collect { z ->
                when {
                    z == null -> if (!fertig && !stand.value.laeuft) verschwunden()
                    !stand.value.geladen -> {
                        val e = ZutatEntwurf.aus(z)
                        stand.update { it.copy(geladen = true, vorher = e, entwurf = e, synonyme = repo.synonyme(z.canonicalName)) }
                    }
                }
            }
        }
        viewModelScope.launch { repo.verwendetIn(id).collect { namen -> stand.update { it.copy(verwendetIn = namen) } } }
        viewModelScope.launch { repo.liste().collect { alle -> stand.update { it.copy(ziele = alle.filter { z -> z.id != id }) } } }
    }

    fun aendern(neu: ZutatEntwurf) {
        zusatz = emptyMap()
        stand.update { it.copy(entwurf = neu) }
        fehlerZeigen()
    }

    /** Feld verlassen oder „Fertig“ gedrückt: ab jetzt zeigt es seinen Fehler. */
    fun feldFertig(f: ZutatFeld) {
        sichtbar = sichtbar + f
        fehlerZeigen()
    }

    fun speichern() {
        sichtbar = ZutatFeld.entries.toSet()
        fehlerZeigen()
        if (stand.value.fehler.isNotEmpty()) return
        starten { id, vorher, entwurf ->
            when (val e = repo.speichern(id, vorher, entwurf)) {
                is ZutatErgebnis.Gespeichert -> schliessen(null)
                is ZutatErgebnis.NameVergeben -> kanal.send(EditorEreignis.NameVergeben(e.id, e.name))
                is ZutatErgebnis.StrichcodeVergeben -> {
                    zusatz = mapOf(ZutatFeld.STRICHCODE to "Gehört schon zu „${e.name}“")
                    fehlerZeigen()
                }
                is ZutatErgebnis.NichtGefunden -> verschwunden()
                else -> fehlerZeigen()
            }
        }
    }

    fun loeschen() = starten { id, _, _ ->
        when (val e = repo.loeschen(id)) {
            is ZutatErgebnis.Geloescht -> schliessen("Gelöscht")
            is ZutatErgebnis.InVerwendung -> kanal.send(EditorEreignis.InVerwendung(e.rezepte))
            is ZutatErgebnis.NichtGefunden -> verschwunden()
            else -> Unit
        }
    }

    /** Diese Zutat in [nachId] zusammenführen; ungespeicherte Eingaben wandern mit. */
    fun zusammenfuehren(nachId: String) = starten { id, vorher, entwurf ->
        val mit = if (entwurf != vorher) vorher to entwurf else null
        when (val e = repo.zusammenfuehren(id, nachId, mit)) {
            is ZutatErgebnis.Zusammengefuehrt -> schliessen("Zusammengeführt in „${e.name}“")
            is ZutatErgebnis.NichtGefunden -> kanal.send(EditorEreignis.Meldung("Die Zutat gibt es nicht mehr"))
            is ZutatErgebnis.Ungueltig -> {
                sichtbar = ZutatFeld.entries.toSet()
                fehlerZeigen()
            }
            else -> Unit
        }
    }

    private fun fehlerZeigen() {
        val s = stand.value
        val geprueft = if (s.vorher != null && s.entwurf != null) pruefen(s.vorher, s.entwurf).filterKeys { it in sichtbar } else emptyMap()
        stand.update { it.copy(fehler = geprueft + zusatz) }
    }

    private suspend fun schliessen(meldung: String?) {
        fertig = true
        meldung?.let(meldungen::senden)
        kanal.send(EditorEreignis.Fertig)
    }

    private suspend fun verschwunden() {
        if (fertig) return
        schliessen("Diese Zutat gibt es nicht mehr")
    }

    /** Führt eine Aktion aus, solange keine andere läuft (zweiter Tipp wird ignoriert). */
    private fun starten(arbeit: suspend (id: String, vorher: ZutatEntwurf, entwurf: ZutatEntwurf) -> Unit) {
        val s = stand.value
        val id = id ?: return
        if (fertig || s.laeuft || s.vorher == null || s.entwurf == null) return
        stand.update { it.copy(laeuft = true) }
        viewModelScope.launch {
            try {
                arbeit(id, s.vorher, s.entwurf)
            } finally {
                stand.update { it.copy(laeuft = false) }
            }
        }
    }
}
