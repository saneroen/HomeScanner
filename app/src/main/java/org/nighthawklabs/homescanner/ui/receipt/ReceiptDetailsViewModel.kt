package org.nighthawklabs.homescanner.ui.receipt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nighthawklabs.homescanner.domain.model.CorrectionPayload
import org.nighthawklabs.homescanner.domain.model.ReceiptSummaryEditableDto
import org.nighthawklabs.homescanner.data.db.MerchantLearningDao
import org.nighthawklabs.homescanner.data.parser.ReceiptParsedSchema
import org.nighthawklabs.homescanner.data.parser.ReceiptSchemaBuilder
import org.nighthawklabs.homescanner.data.parser.ReceiptWorkingSetConverter
import org.nighthawklabs.homescanner.data.learning.MerchantKeyNormalizer
import org.nighthawklabs.homescanner.data.learning.MerchantLearningService
import org.nighthawklabs.homescanner.data.repo.ReceiptRepository
import org.nighthawklabs.homescanner.domain.model.CorrectionEventType
import org.nighthawklabs.homescanner.domain.model.ItemKind
import org.nighthawklabs.homescanner.domain.model.ReceiptItemEditable
import org.nighthawklabs.homescanner.domain.model.ReceiptWorkingSet
import org.nighthawklabs.homescanner.domain.model.Receipt

import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import org.nighthawklabs.homescanner.domain.inventory.IngestionResult
import org.nighthawklabs.homescanner.domain.inventory.InventoryIngestionService
import org.nighthawklabs.homescanner.work.ReceiptProcessOrchestrator

class ReceiptDetailsViewModel(
    private val repository: ReceiptRepository,
    private val orchestrator: ReceiptProcessOrchestrator,
    private val merchantLearningDao: MerchantLearningDao,
    private val inventoryIngestionService: InventoryIngestionService,
    private val context: Context,
    private val receiptId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptDetailsUiState())
    val uiState: StateFlow<ReceiptDetailsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var hasTriggeredProcessing = false
    private var originalSchemaForLearning: ReceiptParsedSchema? = null

    init {
        viewModelScope.launch {
            orchestrator.observeWorkProgress(receiptId).collect { progress ->
                _uiState.update { it.copy(workProgress = progress) }
            }
        }
        viewModelScope.launch {
            repository.observeReceipt(receiptId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { receipt ->
                    val effectiveJson = receipt?.effectiveJson
                    val parsed = effectiveJson?.let { json ->
                        runCatching {
                            Json { ignoreUnknownKeys = true }.decodeFromString<ReceiptParsedSchema>(json)
                        }.getOrNull()
                    }
                    if (originalSchemaForLearning == null && receipt?.parseJson != null) {
                        originalSchemaForLearning = runCatching {
                            Json { ignoreUnknownKeys = true }.decodeFromString<ReceiptParsedSchema>(receipt.parseJson)
                        }.getOrNull()
                    }
                    _uiState.update {
                        it.copy(receipt = receipt, parsedSchema = parsed, errorMessage = null)
                    }
                    if (receipt != null && !hasTriggeredProcessing && needsProcessing(receipt)) {
                        hasTriggeredProcessing = true
                        orchestrator.enqueueAutoProcess(receiptId)
                    }
                }
        }
    }

    private fun needsProcessing(receipt: Receipt): Boolean {
        return receipt.parseJson == null && receipt.status in setOf(
            ReceiptStatus.CAPTURED_RAW,
            ReceiptStatus.PROCESSING_IMAGE,
            ReceiptStatus.PARSING
        )
    }

    fun enterFixMode() {
        val schema = _uiState.value.parsedSchema ?: return
        val jsonSource = _uiState.value.receipt?.effectiveJson ?: ""
        val workingSet = ReceiptWorkingSetConverter.fromParsedSchema(schema, jsonSource)
        _uiState.update {
            it.copy(isFixMode = true, workingSet = workingSet)
        }
    }

    fun exitFixMode() {
        _uiState.update {
            it.copy(isFixMode = false, workingSet = null)
        }
    }

    fun updateItem(item: ReceiptItemEditable) {
        _uiState.update { state ->
            val ws = state.workingSet ?: return@update state
            val idx = ws.items.indexOfFirst { it.lineId == item.lineId }
            if (idx >= 0) {
                ws.items[idx] = item
            }
            state.copy(workingSet = ws)
        }
    }

    fun markItemKind(item: ReceiptItemEditable, kind: ItemKind) {
        item.kind = kind
        updateItem(item)
    }

    fun deleteItem(item: ReceiptItemEditable) {
        item.deleted = true
        updateItem(item)
    }

    fun addItem() {
        _uiState.update { state ->
            val ws = state.workingSet ?: return@update state
            val newItem = ReceiptItemEditable(
                lineId = "new_%d".format(System.currentTimeMillis() % 10000),
                rawText = "",
                itemName = "New item",
                qty = 1.0,
                unitPrice = 0.0,
                lineTotal = 0.0,
                paidPrice = 0.0,
                kind = ItemKind.ITEM
            )
            ws.items.add(newItem)
            state.copy(workingSet = ws)
        }
    }

    fun saveFixes() {
        val workingSet = _uiState.value.workingSet ?: return
        viewModelScope.launch {
            doSaveFixes(workingSet)
            _uiState.update { it.copy(isFixMode = false, workingSet = null) }
        }
    }

    private suspend fun doSaveFixes(workingSet: ReceiptWorkingSet) {
        val receipt = _uiState.value.receipt ?: return
        val correctedJson = ReceiptSchemaBuilder.buildFromWorkingSet(receiptId, workingSet)
        val beforeSummary = ReceiptSummaryEditableDto(
            receipt.subtotal ?: 0.0,
            receipt.tax ?: 0.0,
            receipt.total ?: 0.0,
            receipt.total ?: 0.0
        )
        val afterSummary = ReceiptSummaryEditableDto(
            workingSet.computedSubtotal(),
            workingSet.computedTax(),
            workingSet.computedTotal(),
            workingSet.computedTotal()
        )
        repository.addCorrectionEvent(
            receiptId,
            "UPDATE_SUMMARY",
            Json.encodeToString(CorrectionPayload.UpdateSummary(beforeSummary, afterSummary))
        )
        repository.setCorrectedJson(
            id = receiptId,
            json = correctedJson,
            merchantName = workingSet.merchantName,
            subtotal = workingSet.computedSubtotal(),
            tax = workingSet.computedTax(),
            total = workingSet.computedTotal()
        )
        val merchantKey = MerchantKeyNormalizer.normalize(receipt.merchantName ?: "")
        if (merchantKey.isNotEmpty()) {
            val originalItems = originalSchemaForLearning?.items?.map { schemaItem ->
                ReceiptItemEditable(
                    lineId = schemaItem.lineId,
                    rawText = schemaItem.rawText,
                    itemName = schemaItem.itemName,
                    qty = schemaItem.qty,
                    unitPrice = schemaItem.unitPrice,
                    lineTotal = schemaItem.lineTotal,
                    paidPrice = schemaItem.paidPrice,
                    kind = ItemKind.ITEM,
                    deleted = false
                )
            } ?: emptyList()
            val editedItems = workingSet.items.filter { !it.deleted }
            val aliasSignals = MerchantLearningService.extractAliasSignals(originalItems, editedItems)
            val notItemRows = workingSet.items.filter { it.kind == ItemKind.OTHER }
            val ignorePatterns = MerchantLearningService.extractIgnorePatterns(notItemRows)
            if (aliasSignals.isNotEmpty() || ignorePatterns.isNotEmpty()) {
                MerchantLearningService.mergeSignals(merchantLearningDao, merchantKey, aliasSignals, ignorePatterns)
            }
        }
    }

    fun confirmReceipt() {
        val receipt = _uiState.value.receipt ?: return
        val workingSet = _uiState.value.workingSet
        viewModelScope.launch {
            if (workingSet != null) {
                doSaveFixes(workingSet)
            }
            repository.markConfirmed(receipt.id)
            val result = inventoryIngestionService.ingestConfirmedReceipt(receipt.id)
            _uiState.update {
                it.copy(ingestionResult = when (result) {
                    is IngestionResult.Success -> "Added ${result.ingested} items to inventory"
                    is IngestionResult.Error -> "Inventory: ${result.message}"
                })
            }
        }
    }

    fun retryParse() {
        val receipt = _uiState.value.receipt ?: return
        orchestrator.enqueueReprocess(receipt.id)
    }

    fun cancelProcessing() {
        orchestrator.cancel(receiptId)
        viewModelScope.launch {
            repository.markFailed(receiptId, "Cancelled")
        }
    }

    fun copyJsonToClipboard(): Boolean {
        val jsonToCopy = when (_uiState.value.jsonViewMode) {
            JsonViewMode.CORRECTED -> _uiState.value.receipt?.correctedJson
            JsonViewMode.PARSED -> _uiState.value.receipt?.parseJson
            JsonViewMode.EFFECTIVE -> _uiState.value.receipt?.effectiveJson
        } ?: return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("Receipt JSON", jsonToCopy))
        return true
    }

    fun setJsonViewMode(mode: JsonViewMode) {
        _uiState.update { it.copy(jsonViewMode = mode) }
    }

    fun setSelectedPage(index: Int) {
        _uiState.update { it.copy(selectedPageIndex = index) }
    }

    fun loadPageDebug(receiptId: String, pageIndex: Int) {
        viewModelScope.launch {
            val debug = repository.getPageDebug(receiptId, pageIndex)
            _uiState.update { it.copy(pageDebug = debug) }
        }
    }

    fun triggerReparse() {
        orchestrator.enqueueReprocess(receiptId)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearIngestionResult() {
        _uiState.update { it.copy(ingestionResult = null) }
    }
}
