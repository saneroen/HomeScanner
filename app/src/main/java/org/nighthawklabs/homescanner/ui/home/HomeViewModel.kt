package org.nighthawklabs.homescanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.nighthawklabs.homescanner.data.repo.ReceiptRepository
import org.nighthawklabs.homescanner.domain.model.Receipt

class HomeViewModel(
    private val repository: ReceiptRepository
) : ViewModel() {

    val receipts: StateFlow<List<Receipt>> = repository.observeReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
