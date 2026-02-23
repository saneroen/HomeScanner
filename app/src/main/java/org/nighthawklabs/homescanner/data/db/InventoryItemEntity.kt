package org.nighthawklabs.homescanner.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["normalizedKey"], unique = true),
        Index(value = ["category"])
    ]
)
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val normalizedKey: String,
    val displayName: String,
    val category: String,
    val subcategory: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPurchasedAt: Long? = null,
    val lastPurchasePrice: Double? = null,
    val purchaseCount: Int
)
