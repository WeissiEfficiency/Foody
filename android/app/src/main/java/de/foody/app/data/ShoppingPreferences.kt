package de.foody.app.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Ansicht der Einkaufsliste: Kacheln (Standard) oder Liste – wie in gängigen Einkaufs-Apps wählbar. */
@Singleton
class ShoppingPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("foody", Context.MODE_PRIVATE)

    private val _listView = MutableStateFlow(prefs.getBoolean(KEY, false))
    val listView = _listView.asStateFlow()

    fun setListView(enabled: Boolean) {
        prefs.edit { putBoolean(KEY, enabled) }
        _listView.value = enabled
    }

    private companion object {
        const val KEY = "shoppingListView"
    }
}
