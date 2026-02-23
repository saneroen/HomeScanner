package org.nighthawklabs.homescanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.nighthawklabs.homescanner.domain.model.Receipt
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus

@Entity(tableName = "receipt_drafts")
data class ReceiptDraftEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val rawImagePath: String,
    val croppedImagePath: String?,
    val pageCount: Int = 1,
    val status: String,
    val parseJson: String? = null,
    val correctedJson: String? = null,
    val correctionStatus: String? = null,
    val confirmedAt: Long? = null,
    val lastEditedAt: Long? = null,
    val merchantName: String? = null,
    val purchaseTime: Long? = null,
    val currency: String? = null,
    val subtotal: Double? = null,
    val tax: Double? = null,
    val total: Double? = null,
    val parseConfidence: Double? = null,
    val warningsJson: String? = null,
    val errorMessage: String? = null,
    val ingestedAt: Long? = null,
    val ingestionStatus: String? = null,
    val ingestionError: String? = null
) {
    fun toDomain(pages: List<org.nighthawklabs.homescanner.domain.model.ReceiptPage> = emptyList()): Receipt {
        val firstPage = pages.firstOrNull()
        val rawPath = firstPage?.rawImagePath ?: rawImagePath
        val processedPath = firstPage?.processedImagePath ?: croppedImagePath
        return Receipt(
        id = id,
        createdAt = createdAt,
        rawImagePath = rawPath,
        processedImagePath = processedPath,
        pageCount = pageCount,
        pages = pages,
        status = ReceiptStatus.valueOf(status),
        parseJson = parseJson,
        correctedJson = correctedJson,
        correctionStatus = correctionStatus,
        confirmedAt = confirmedAt,
        lastEditedAt = lastEditedAt,
        merchantName = merchantName,
        purchaseTime = purchaseTime,
        currency = currency,
        subtotal = subtotal,
        tax = tax,
        total = total,
        parseConfidence = parseConfidence,
        warningsJson = warningsJson,
        errorMessage = errorMessage,
        ingestedAt = ingestedAt,
        ingestionStatus = ingestionStatus,
        ingestionError = ingestionError
    )
    }
}
