package org.nighthawklabs.homescanner.data.db

import org.nighthawklabs.homescanner.domain.model.ReceiptPage

fun ReceiptPageEntity.toDomain(): ReceiptPage = ReceiptPage(
    receiptId = receiptId,
    pageIndex = pageIndex,
    rawImagePath = rawImagePath,
    processedImagePath = processedImagePath,
    status = status,
    createdAt = createdAt
)
