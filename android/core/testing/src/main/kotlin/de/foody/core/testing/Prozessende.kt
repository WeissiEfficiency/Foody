package de.foody.core.testing

import androidx.lifecycle.SavedStateHandle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Ein neues SavedStateHandle wie nach einem Prozessende: Jeder Wert geht durch die Java-Serialisierung, wie beim
 * Ablegen im Bundle. Nicht serialisierbare Werte fallen so im Test auf statt erst auf dem Gerät. Werte, die über
 * `saveable` (SavedStateProvider) liegen, kommen nicht mit.
 */
fun SavedStateHandle.nachProzessende(): SavedStateHandle = SavedStateHandle(
    keys().associateWith { schluessel ->
        val bytes = ByteArrayOutputStream().also { aus -> ObjectOutputStream(aus).use { it.writeObject(get<Any?>(schluessel)) } }
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    },
)
