package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange

/**
 * Verbindet ein Feld mit Wert und Rückruf ([rememberFeldBruecke]) mit dem [TextFieldState], den `BasicTextField` seit
 * Compose Foundation 1.13 verlangt (S14). So behalten die Aufrufer ihren Text dort, wo er schon liegt (ViewModel,
 * `SavedStateHandle`, `rememberSaveable`); der [zustand] selbst wird nicht gesichert, nach dem Prozessende füllt ihn der
 * zurückgeholte Wert.
 *
 * Eingaben meldet [eingabe] sofort, noch bevor das Feld sie zeigt. Ein Wert von außen, der keiner gemeldeten Eingabe
 * entspricht (Leeren nach dem Eintragen, Laden, Plus und Minus), ersetzt den Text, die Schreibmarke steht dann am Ende.
 * Kommt ein gemeldeter Wert verspätet zurück (ein `StateFlow` liefert erst im nächsten Frame), während schon
 * weitergetippt wurde, bleibt das Feld, wie es ist. Sobald die letzte Eingabe zurückkam, gilt wieder jeder abweichende
 * Wert als Wert von außen. Aufrufer dürfen Eingaben deshalb nicht verwerfen oder umformen (keiner tut es).
 */
internal class FeldBruecke(start: String) {
    val zustand = TextFieldState(start, TextRange(start.length))

    /** Rückruf für Eingaben; [rememberFeldBruecke] hält ihn aktuell. */
    var onWert: (String) -> Unit = {}

    /** Der Wert des Aufrufers beim letzten Abgleich; solange er bleibt, gibt es nichts abzugleichen. */
    private var letzterWert = start

    /** Gemeldete Eingaben, die als Wert noch nicht zurückkamen, in der Reihenfolge der Eingabe. */
    private val ausstehend = ArrayDeque<String>()

    /** Läuft nur bei Eingaben (Tastatur, Einfügen, TalkBack), nicht wenn [abgleichen] den Text setzt. */
    val eingabe = InputTransformation {
        val neu = asCharSequence().toString()
        ausstehend.addLast(neu)
        onWert(neu)
    }

    /** Übernimmt den [wert] des Aufrufers, wenn er sich geändert hat und nicht nur eine gemeldete Eingabe ist. */
    fun abgleichen(wert: String) {
        // Das Feld komponiert bei jeder Eingabe neu, der Aufrufer liefert dann oft noch den alten Wert
        if (wert == letzterWert) return
        letzterWert = wert
        // Werte kommen in der Reihenfolge der Eingaben zurück, Zwischenstände können fehlen
        val index = ausstehend.indexOf(wert)
        val gleich = zustand.text.contentEquals(wert)
        when {
            gleich && (index < 0 || index == ausstehend.lastIndex) -> ausstehend.clear()
            index >= 0 -> repeat(index + 1) { ausstehend.removeFirst() }
            else -> {
                ausstehend.clear()
                zustand.setTextAndPlaceCursorAtEnd(wert)
            }
        }
    }
}

/** [FeldBruecke] für ein Feld mit [wert] und [onWert]; gleicht nach jeder Komposition mit [wert] ab. */
@Composable
internal fun rememberFeldBruecke(wert: String, onWert: (String) -> Unit): FeldBruecke {
    val bruecke = remember { FeldBruecke(wert) }
    SideEffect {
        bruecke.onWert = onWert
        bruecke.abgleichen(wert)
    }
    return bruecke
}
