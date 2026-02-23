package org.nighthawklabs.homescanner.domain.model

data class ReceiptDraft(
    val id: String,
    val createdAt: Long,
    val rawImagePath: String,
    val croppedImagePath: String?,
    val status: ReceiptDraftStatus
)
