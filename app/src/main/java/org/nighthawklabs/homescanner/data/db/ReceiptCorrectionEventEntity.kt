package org.nighthawklabs.homescanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipt_corrections")
data class ReceiptCorrectionEventEntity(
    @PrimaryKey val id: String,
    val receiptId: String,
    val createdAt: Long,
    val type: String,
    val payloadJson: String
)
