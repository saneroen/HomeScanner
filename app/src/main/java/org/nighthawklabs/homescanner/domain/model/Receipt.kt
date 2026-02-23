package org.nighthawklabs.homescanner.domain.model

data class Receipt(
    val id: String,
    val createdAt: Long,
    val rawImagePath: String,
    val processedImagePath: String?,
    val pageCount: Int = 1,
    val pages: List<ReceiptPage> = emptyList(),
    val status: ReceiptStatus,
    val parseJson: String?,
    val correctedJson: String? = null,
    val correctionStatus: String? = null,
    val confirmedAt: Long? = null,
    val lastEditedAt: Long? = null,
    val merchantName: String?,
    val purchaseTime: Long?,
    val currency: String?,
    val subtotal: Double?,
    val tax: Double?,
    val total: Double?,
    val parseConfidence: Double?,
    val warningsJson: String?,
    val errorMessage: String?,
    val ingestedAt: Long? = null,
    val ingestionStatus: String? = null,
    val ingestionError: String? = null
) {
    val displayTitle: String get() = merchantName ?: "Receipt"
    val effectiveJson: String? get() = correctedJson ?: parseJson
    val isEdited: Boolean get() = correctionStatus == "EDITED"
}
