package de.foody.app.ui.ingredients

import androidx.compose.material3.Text
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.util.runSuspendCatching
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class IngredientsViewModel @Inject constructor(private val repo: IngredientRepository) : ViewModel() {
    val ingredients = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Einmalige Meldung (Text-Ressource + Zahl für Platzhalter); wird von der UI nach Anzeige quittiert. */
    private val _message = MutableStateFlow<Pair<Int, Int>?>(null)
    val message = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    fun save(e: IngredientEntity) = viewModelScope.launch {
        // Eindeutiger Name (Unique-Index) – Kollision als Meldung statt Absturz.
        runSuspendCatching { repo.save(e) }.onFailure { _message.value = R.string.ingredient_name_taken to 0 }
    }
    fun delete(id: String) = viewModelScope.launch {
        if (!repo.delete(id)) _message.value = R.string.ingredient_in_use to 0
    }
    fun merge(fromId: String, intoId: String) = viewModelScope.launch {
        repo.merge(fromId, intoId)
        _message.value = R.string.ingredient_merged to 1
    }
    fun harmonize() = viewModelScope.launch {
        _message.value = R.plurals.ingredients_harmonized to repo.harmonizeNames()
    }
}
