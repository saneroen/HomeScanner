package org.nighthawklabs.homescanner.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nighthawklabs.homescanner.data.db.InventoryDao
import org.nighthawklabs.homescanner.data.db.InventoryEventEntity
import org.nighthawklabs.homescanner.data.db.InventoryItemEntity

data class InventoryItemDetailsUiState(
    val item: InventoryItemEntity? = null,
    val events: List<InventoryEventEntity> = emptyList()
)

class InventoryItemDetailsViewModel(
    private val inventoryDao: InventoryDao,
    itemId: String
) : ViewModel() {

    private val _itemId = itemId

    private val _uiState = MutableStateFlow(InventoryItemDetailsUiState())
    val uiState: StateFlow<InventoryItemDetailsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            inventoryDao.getItem(_itemId)?.let { item ->
                _uiState.update { it.copy(item = item) }
            }
        }
        viewModelScope.launch {
            inventoryDao.observeEventsForItem(_itemId).collect { events ->
                _uiState.update { it.copy(events = events) }
            }
        }
    }
}
