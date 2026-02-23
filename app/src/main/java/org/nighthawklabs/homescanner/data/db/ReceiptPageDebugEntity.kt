package org.nighthawklabs.homescanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipt_page_debug",
    primaryKeys = ["receiptId", "pageIndex"]
)
data class ReceiptPageDebugEntity(
    val receiptId: String,
    val pageIndex: Int,
    val debugJson: String,
    val updatedAt: Long
)
