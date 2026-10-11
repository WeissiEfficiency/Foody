package de.foody.app.ui.pantry

import androidx.compose.foundation.lazy.items
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.PantryRepository
import de.foody.domain.MeasureUnit
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PantryRow(val item: PantryItemEntity, val name: String)
data class PantryUiState(val rows: List<PantryRow> = emptyList(), val ingredients: List<IngredientEntity> = emptyList())

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val pantry: PantryRepository,
    ingredients: IngredientRepository,
) : ViewModel() {
    val state = combine(pantry.observeAll(), ingredients.observeAll()) { items, ings ->
        val names = ings.associate { it.id to it.canonicalName }
        PantryUiState(items.map { PantryRow(it, names[it.ingredientId].orEmpty()) }.sortedBy { it.name.lowercase() }, ings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    fun save(id: String?, ingredientId: String, amount: java.math.BigDecimal, unit: MeasureUnit, bestBefore: LocalDate?) =
        viewModelScope.launch { pantry.save(id, ingredientId, amount, unit, bestBefore) }
    fun delete(id: String) = viewModelScope.launch { pantry.delete(id) }
}
