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
 * Content-Uris für Kamerafotos ohne die Pfadregeln des FileProviders: Er vergleicht Pfade mit „/“ und findet unter
 * Windows den Cache-Ordner nie. Legt für die Kamera-Authority der App eine Pfadregel in seinen statischen Speicher, die
 * jede Datei auf `content://<authority>/test/<name>` abbildet.
 */
fun kameraUrisOhneFileProvider(context: Context) {
    val authority = "${context.packageName}.kamera"
    val regel = Class.forName("androidx.core.content.FileProvider\$PathStrategy")
    val stellvertreter = Proxy.newProxyInstance(regel.classLoader, arrayOf(regel)) { selbst, methode, args ->
        when (methode.name) {
            "getUriForFile" -> Uri.parse("content://$authority/test/${(args[0] as File).name}")
            "hashCode" -> System.identityHashCode(selbst)
            "equals" -> selbst === args[0]
            "toString" -> "Kamera-Pfadregel (Test)"
            else -> error("Im Test nicht gebraucht: ${methode.name}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    val speicher = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<String, Any>
    synchronized(speicher) { speicher[authority] = stellvertreter }
}
