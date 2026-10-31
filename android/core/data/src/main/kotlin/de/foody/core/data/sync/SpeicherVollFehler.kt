package de.foody.core.data.sync

/**
 * Der Gerätespeicher ist voll, ein Server-Datensatz ließ sich nicht schreiben. [SyncSpeicher.anwenden] bricht dann ab,
 * statt den Datensatz als Problem aufzugeben; der Cursor bleibt stehen, der nächste Lauf holt ihn erneut.
 */
class SpeicherVollFehler(ursache: Throwable) : Exception("speicher_voll", ursache)
