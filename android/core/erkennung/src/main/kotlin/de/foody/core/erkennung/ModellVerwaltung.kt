package de.foody.core.erkennung

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Modell installieren und entfernen für die Einstellungen (Phase 4): Import zweier Dateien über ihre Uris (SAF, z. B.
 * aus OpenCloud), [stand] für Größe und „installiert“. Liegt nur auf diesem Gerät.
 */
@Singleton
class ModellVerwaltung internal constructor(
    private val dateien: ModellDateien,
    private val oeffnen: (String) -> InputStream,
    private val io: CoroutineContext,
) {
    @Inject constructor(dateien: ModellDateien, @ApplicationContext context: Context) : this(
        dateien,
        { uri -> context.contentResolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException(uri) },
        Dispatchers.IO,
    )

    private val _stand = MutableStateFlow(dateien.stand())
    val stand: StateFlow<ModellDateien.Stand> = _stand.asStateFlow()

    /** Kopiert die beiden Dateien ([uris] in beliebiger Reihenfolge); dauert bei 1,5 GB eine Weile. */
    suspend fun importieren(uris: List<String>): ModellDateien.Import = withContext(io) {
        dateien.importieren(uris.map { uri -> { oeffnen(uri) } }).also { _stand.value = dateien.stand() }
    }

    suspend fun entfernen() = withContext(io) {
        dateien.entfernen()
        _stand.value = dateien.stand()
    }
}

/**
 * Schalter „Fotoerkennung“ in den Einstellungen, getrennt je Gerät (eigene Prefs `fotoerkennung`, nicht gesynct).
 * Standard an; der Knopf erscheint nur, wenn zusätzlich ein Modell installiert ist ([sichtbar]).
 */
@Singleton
class ErkennungEinstellung internal constructor(private val prefs: SharedPreferences, verwaltung: ModellVerwaltung) {
    @Inject constructor(@ApplicationContext context: Context, verwaltung: ModellVerwaltung) :
        this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), verwaltung)

    private val _an = MutableStateFlow(prefs.getBoolean(AN, true))
    val an: StateFlow<Boolean> = _an.asStateFlow()

    /** Foto-Knopf zeigen: eingeschaltet und Modell installiert (auf dem Tablet ohne Modell nie). */
    val sichtbar = combine(an, verwaltung.stand) { an, stand -> an && stand is ModellDateien.Stand.Bereit }

    fun setzen(an: Boolean) {
        prefs.edit { putBoolean(AN, an) }
        _an.value = an
    }

    private companion object {
        const val PREFS = "fotoerkennung"
        const val AN = "an"
    }
}
