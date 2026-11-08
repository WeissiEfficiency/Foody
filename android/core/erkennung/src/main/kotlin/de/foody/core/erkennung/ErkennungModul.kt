package de.foody.core.erkennung

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/** Eigener Thread für die Erkennung: die nativen Aufrufe blockieren bis zum Zeitlimit (90 s). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Erkennen

/** Hilt-Modul der Fotoerkennung (wie `ScanModul`). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ErkennungModul {
    @Binds abstract fun erkenner(impl: ModellErkenner): MahlzeitErkenner

    @Binds abstract fun modell(impl: LlamaModell): BildSprachModell

    @Binds abstract fun bilder(impl: AndroidBildVorbereitung): BildVorbereitung

    @Binds abstract fun speicher(impl: AndroidFreierSpeicher): FreierSpeicher

    companion object {
        @Provides
        @Singleton
        fun dateien(@ApplicationContext context: Context) = ModellDateien(File(context.filesDir, "modell"))

        @Provides
        @Singleton
        @Erkennen
        fun ablauf(): CoroutineContext = Executors.newSingleThreadExecutor { Thread(it, "Fotoerkennung") }.asCoroutineDispatcher()
    }
}

/**
 * Liest das Foto über [ImageDecoder] (dreht nach EXIF), verkleinert auf höchstens [LAENGSTE_SEITE] px und legt es als
 * JPEG in `cache/erkennung/` ab. Mehr braucht das Modell bei 512 Bild-Tokens nicht.
 */
class AndroidBildVorbereitung @Inject constructor(@ApplicationContext private val context: Context) : BildVorbereitung {
    override suspend fun vorbereiten(bildUri: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val quelle = ImageDecoder.createSource(context.contentResolver, bildUri.toUri())
            val bild = ImageDecoder.decodeBitmap(quelle) { decoder, info, _ ->
                val laengste = maxOf(info.size.width, info.size.height)
                if (laengste > LAENGSTE_SEITE) {
                    val faktor = LAENGSTE_SEITE.toFloat() / laengste
                    decoder.setTargetSize((info.size.width * faktor).toInt().coerceAtLeast(1), (info.size.height * faktor).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val ordner = File(context.cacheDir, "erkennung").apply { mkdirs() }
            File.createTempFile("foto", ".jpg", ordner).also { datei ->
                datei.outputStream().use { bild.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bild.recycle()
            }
        }.getOrNull()
    }

    companion object {
        const val LAENGSTE_SEITE = 1024
    }
}

/** Verfügbarer Arbeitsspeicher laut [ActivityManager] (schließt freigebbaren Cache ein). */
class AndroidFreierSpeicher @Inject constructor(@ApplicationContext private val context: Context) : FreierSpeicher {
    override fun bytes(): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return if (info.lowMemory) 0 else info.availMem
    }
}
