package org.nighthawklabs.homescanner.ui.receipt

import org.nighthawklabs.homescanner.data.parser.ReceiptParsedSchema
import org.nighthawklabs.homescanner.domain.model.Receipt
import org.nighthawklabs.homescanner.domain.model.ReceiptWorkingSet
import org.nighthawklabs.homescanner.work.WorkProgress

data class ReceiptDetailsUiState(
    val receipt: Receipt? = null,
    val parsedSchema: ReceiptParsedSchema? = null,
    val errorMessage: String? = null,
    val ingestionResult: String? = null,
    val isFixMode: Boolean = false,
    val workingSet: ReceiptWorkingSet? = null,
    val jsonViewMode: JsonViewMode = JsonViewMode.EFFECTIVE,
    val workProgress: WorkProgress? = null,
    val selectedPageIndex: Int = 0,
    val pageDebug: org.nighthawklabs.homescanner.data.parser.debug.PageParseDebug? = null
)

enum class JsonViewMode { CORRECTED, PARSED, EFFECTIVE }
