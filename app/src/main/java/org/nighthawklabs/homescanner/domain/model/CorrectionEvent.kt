package org.nighthawklabs.homescanner.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class CorrectionEventType {
    EDIT_ITEM,
    DELETE_ITEM,
    ADD_ITEM,
    MARK_FEE,
    MARK_DISCOUNT,
    MARK_TAX,
    MARK_NOT_ITEM,
    UPDATE_SUMMARY
}

@Serializable
sealed class CorrectionPayload {
    @Serializable
    @SerialName("edit_item")
    data class EditItem(val lineId: String, val before: ReceiptItemEditableDto, val after: ReceiptItemEditableDto) : CorrectionPayload()

    @Serializable
    @SerialName("delete_item")
    data class DeleteItem(val lineId: String, val rawText: String) : CorrectionPayload()

    @Serializable
    @SerialName("add_item")
    data class AddItem(val item: ReceiptItemEditableDto) : CorrectionPayload()

    @Serializable
    @SerialName("mark_kind")
    data class MarkKind(val lineId: String, val fromKind: String, val toKind: String) : CorrectionPayload()

    @Serializable
    @SerialName("update_summary")
    data class UpdateSummary(val before: ReceiptSummaryEditableDto, val after: ReceiptSummaryEditableDto) : CorrectionPayload()
}

@Serializable
data class ReceiptItemEditableDto(
    val lineId: String = "",
    val rawText: String = "",
    val itemName: String = "",
    val qty: Double = 1.0,
    val unitPrice: Double? = null,
    val lineTotal: Double = 0.0,
    val paidPrice: Double = 0.0,
    val kind: String = "ITEM"
)

@Serializable
data class ReceiptSummaryEditableDto(
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val paidTotal: Double = 0.0
)
