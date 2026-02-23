package org.nighthawklabs.homescanner.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nighthawklabs.homescanner.data.db.InventoryDao
import org.nighthawklabs.homescanner.data.db.InventoryItemEntity

data class InventoryHomeUiState(
    val selectedCategory: String = "ALL",
    val searchQuery: String = "",
    val items: List<InventoryItemEntity> = emptyList()
)

class InventoryViewModel(
    private val inventoryDao: InventoryDao
) : ViewModel() {

    private val _filter = MutableStateFlow(Pair("ALL", ""))
    private val _uiState = MutableStateFlow(InventoryHomeUiState())
    val uiState: StateFlow<InventoryHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _filter.flatMapLatest { (category, query) ->
                val pattern = if (query.isBlank()) "%" else "%$query%"
                val queryBlank = if (query.isBlank()) 1 else 0
                inventoryDao.observeItemsFiltered(category, pattern, queryBlank)
            }.collect { items ->
                val (category, query) = _filter.value
                _uiState.update { it.copy(items = items, selectedCategory = category, searchQuery = query) }
            }
        }
    }

    fun setCategory(category: String) {
        _filter.update { (_, q) -> Pair(category, q) }
    }

    fun setSearchQuery(query: String) {
        _filter.update { (c, _) -> Pair(c, query) }
    }
}
