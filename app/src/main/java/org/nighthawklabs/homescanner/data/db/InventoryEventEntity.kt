package org.nighthawklabs.homescanner.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_events",
    indices = [
        Index(value = ["receiptId", "receiptLineId"], unique = true),
        Index(value = ["itemId"]),
        Index(value = ["receiptId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class InventoryEventEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val type: String,
    val receiptId: String,
    val receiptLineId: String,
    val occurredAt: Long,
    val quantity: Double,
    val unit: String? = null,
    val unitPrice: Double? = null,
    val lineTotal: Double,
    val currency: String,
    val rawText: String? = null,
    val createdAt: Long
)
