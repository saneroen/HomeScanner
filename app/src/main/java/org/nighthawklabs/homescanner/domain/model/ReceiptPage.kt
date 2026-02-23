package org.nighthawklabs.homescanner.domain.model

data class ReceiptPage(
    val receiptId: String,
    val pageIndex: Int,
    val rawImagePath: String,
    val processedImagePath: String?,
    val status: String,
    val createdAt: Long
) {
    val displayImagePath: String get() = processedImagePath ?: rawImagePath
}
