package de.foody.feature.heute.tagebuch

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import java.io.File
import java.lang.reflect.Proxy

/**
 * Kamera und Galerie im Test: merkt sich die gestarteten Verträge ([gestartet]) und liefert gleich [foto]
 * (`TakePicture`) bzw. [galerie] (`PickVisualMedia`). Mit [halten] bleibt der Rückruf offen, bis [liefern] ihn auslöst
 * (etwa erst, nachdem der Test einen Prozessende-Zustand hergestellt hat). Über `LocalActivityResultRegistryOwner`
 * einsetzen ([besitzer]).
 */
class FesteErgebnisse(
    var foto: Boolean = true,
    var galerie: Uri? = null,
    var halten: Boolean = false,
) : ActivityResultRegistry() {
    /** Gestartete Verträge in der Reihenfolge der Starts. */
    val gestartet = mutableListOf<ActivityResultContract<*, *>>()

    private val offen = mutableListOf<Pair<Int, ActivityResultContract<*, *>>>()

    val besitzer = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = this@FesteErgebnisse
    }

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        gestartet += contract
        if (halten) offen += requestCode to contract else dispatchResult(requestCode, ergebnis(contract))
    }

    /** Löst den ältesten gehaltenen Rückruf mit dem jetzigen [foto] bzw. [galerie] aus. */
    fun liefern() {
        val (code, vertrag) = offen.removeAt(0)
        dispatchResult(code, ergebnis(vertrag))
    }

    private fun ergebnis(vertrag: ActivityResultContract<*, *>): Any? = when (vertrag) {
        is ActivityResultContracts.TakePicture -> foto
        is ActivityResultContracts.PickVisualMedia -> galerie
        else -> error("Unerwarteter Vertrag: $vertrag")
    }
}

/**
 * Content-Uris für Kamerafotos im Test. Der FileProvider vergleicht Pfade mit „/“ und findet unter Windows den
 * Cache-Ordner nie: Dort legt dies für die Kamera-Authority der App eine Pfadregel in seinen statischen Speicher, die
 * jede Datei auf `content://<authority>/test/<name>` abbildet. Sonst (CI unter Linux) bleibt der echte FileProvider; der
 * Eintrag wird nur geleert, weil er sich Ordner aus früheren Tests (anderer Robolectric-Ordner) merkt.
 *
 * Gibt zurück, was die Änderung rückgängig macht (in `@After` aufrufen): Der vorige Eintrag kommt wieder.
 */
fun kameraUrisOhneFileProvider(context: Context): () -> Unit {
    val authority = "${context.packageName}.kamera"

    @Suppress("UNCHECKED_CAST")
    val speicher = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<String, Any>
    val vorher = synchronized(speicher) {
        speicher[authority].also {
            if (System.getProperty("os.name").orEmpty().startsWith("Windows")) speicher[authority] = pfadregel(authority) else speicher.remove(authority)
        }
    }
    return {
        synchronized(speicher) {
            if (vorher != null) speicher[authority] = vorher else speicher.remove(authority)
        }
    }
}

/** Pfadregel (`FileProvider.PathStrategy`), die jede Datei auf `content://<authority>/test/<name>` abbildet. */
private fun pfadregel(authority: String): Any {
    val regel = Class.forName("androidx.core.content.FileProvider\$PathStrategy")
    return Proxy.newProxyInstance(regel.classLoader, arrayOf(regel)) { selbst, methode, args ->
        when (methode.name) {
            "getUriForFile" -> Uri.parse("content://$authority/test/${(args[0] as File).name}")
            "hashCode" -> System.identityHashCode(selbst)
            "equals" -> selbst === args[0]
            "toString" -> "Kamera-Pfadregel (Test)"
            else -> error("Im Test nicht gebraucht: ${methode.name}")
        }
    }
}
