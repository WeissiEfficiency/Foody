package de.foody.core.testing

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle

/**
 * Ein neues SavedStateHandle wie nach einem Prozessende: Gesichert wird, was Android sichern würde, also die Werte samt
 * der über `saveable` angemeldeten Zustände. Das Bundle geht einmal durch ein Parcel; nicht ablegbare Werte fallen so
 * im Test auf statt erst auf dem Gerät. Nur unter Robolectric (Bundle, Parcel).
 *
 * `savedStateProvider()` ist für die Lifecycle-Bibliotheken gedacht; es ist aber genau der Weg, über den Android sichert
 * (ein Bundle mit einem Eintrag je Schlüssel). Wiederhergestellt wird wie in `SavedStateHandle.createHandle`.
 */
@SuppressLint("RestrictedApi", "VisibleForTests")
fun SavedStateHandle.nachProzessende(): SavedStateHandle {
    val zurueck = durchParcel(savedStateProvider().saveState())
    @Suppress("DEPRECATION")
    return SavedStateHandle(zurueck.keySet().associateWith { zurueck.get(it) })
}

private fun durchParcel(b: Bundle): Bundle {
    val p = Parcel.obtain()
    try {
        p.writeBundle(b)
        p.setDataPosition(0)
        return requireNotNull(p.readBundle(SavedStateHandle::class.java.classLoader))
    } finally {
        p.recycle()
    }
}
