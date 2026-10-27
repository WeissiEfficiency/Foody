package de.foody.core.scan

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.core.data.Einstellungsschluessel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Einstellung „Online-Produktsuche“ (Open Food Facts); standardmäßig an, gesendet wird nur die Produktnummer. Liest und
 * schreibt den Schlüssel der v1 ([Einstellungsschluessel.ONLINE_SUCHE] in [Einstellungsschluessel.PREFS]), damit die
 * Wahl das Update überlebt.
 */
@Singleton
class ScanEinstellung @Inject constructor(@ApplicationContext kontext: Context) : OnlineSucheErlaubt {
    private val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
    private val _onlineSuche = MutableStateFlow(prefs.getBoolean(Einstellungsschluessel.ONLINE_SUCHE, true))
    val onlineSuche: StateFlow<Boolean> = _onlineSuche.asStateFlow()

    fun setzen(an: Boolean) {
        prefs.edit { putBoolean(Einstellungsschluessel.ONLINE_SUCHE, an) }
        _onlineSuche.value = an
    }

    override fun erlaubt(): Boolean = _onlineSuche.value
}
