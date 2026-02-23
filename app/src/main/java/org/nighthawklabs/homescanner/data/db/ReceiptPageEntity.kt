package org.nighthawklabs.homescanner.data.db

import org.nighthawklabs.homescanner.domain.model.ReceiptPage
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipt_pages",
    primaryKeys = ["receiptId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ReceiptDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receiptId")]
)
data class ReceiptPageEntity(
    val receiptId: String,
    val pageIndex: Int,
    val rawImagePath: String,
    val processedImagePath: String? = null,
    val status: String,
    val createdAt: Long
) {
    fun toDomain(): ReceiptPage = ReceiptPage(
        receiptId = receiptId,
        pageIndex = pageIndex,
        rawImagePath = rawImagePath,
        processedImagePath = processedImagePath,
        status = status,
        createdAt = createdAt
    )
}
