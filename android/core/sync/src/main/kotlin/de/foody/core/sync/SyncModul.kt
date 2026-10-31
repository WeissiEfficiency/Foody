package de.foody.core.sync

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.foody.core.data.Rechnen
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.sync.SyncSpeicher
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

/**
 * Einstellungen des Sync-Clients, die die App liefert (Phase 5: `@Provides` in der App). [lokalesHttpErlaubt]: Debug-Build,
 * erlaubt zusätzlich `http://10.0.2.2` und `http://localhost` ([ServerAdresse.pruefen]). Die Bibliothek rät das nicht
 * aus einer eigenen BuildConfig.
 */
data class SyncKonfiguration(val lokalesHttpErlaubt: Boolean)

/** Baut den [ServerZugang] für eine geprüfte Server-Adresse (Token je Anfrage aus dem [TokenSpeicher]). */
fun interface ZugangFabrik {
    fun fuer(adresse: String): ServerZugang
}

/** Langlebiger Bereich des Sync-Clients (Outbox-Beobachtung, Auslöser); lebt so lange wie die App. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncBereich

/**
 * Server-Zugang für den [SyncLauf]: `null`, wenn der Sync inaktiv ist, keine Adresse gespeichert ist oder kein (lesbares)
 * Token vorliegt. Das Token liest der Zugang je Anfrage neu.
 */
internal fun zugangFuerLauf(speicher: SyncSpeicher, token: TokenSpeicher, fabrik: ZugangFabrik): suspend () -> ServerZugang? = {
    val zustand = speicher.zustand()
    val adresse = zustand.serverUrl
    if (!zustand.aktiv || adresse == null || token.laden() == null) null else fabrik.fuer(adresse)
}

/** Hilt-Modul des Sync-Clients. [SyncKonfiguration] liefert die App. */
@Module
@InstallIn(SingletonComponent::class)
object SyncModul {
    /** Ein gemeinsamer HTTP-Client (Verbindungspool) für alle Zugänge; angelegt bei der ersten Anfrage. */
    @Provides
    @Singleton
    fun zugangFabrik(token: TokenSpeicher): ZugangFabrik {
        val client by lazy { standardHttpClient() }
        return ZugangFabrik { adresse -> KtorServerZugang(adresse, client) { token.laden() } }
    }

    @Provides
    @Singleton
    fun syncLauf(speicher: SyncSpeicher, token: TokenSpeicher, fabrik: ZugangFabrik): SyncLauf =
        SyncLauf(speicher, zugangFuerLauf(speicher, token, fabrik))

    @Provides
    @Singleton
    @SyncBereich
    fun bereich(@Rechnen rechnen: CoroutineContext): CoroutineScope = CoroutineScope(SupervisorJob() + rechnen)

    /** „Läuft“ = der einmalige Auftrag [SyncAusloeser.AUFTRAG] ist gerade in Arbeit. */
    @Provides
    @Singleton
    fun syncLageQuelle(@ApplicationContext context: Context, db: FoodyDatenbank): SyncLageQuelle = SyncLageQuelle {
        val laeuft = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SyncAusloeser.AUFTRAG)
            .map { auftraege -> auftraege.any { it.state == WorkInfo.State.RUNNING } }
        syncLage(db.syncDao().zustandFlow(), laeuft)
    }
}
