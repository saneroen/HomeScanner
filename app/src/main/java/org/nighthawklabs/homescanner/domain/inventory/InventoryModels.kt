package org.nighthawklabs.homescanner.domain.inventory

enum class InventoryCategory {
    FOOD,
    HOME,
    SERVICES,
    OTHER
}

data class InventoryItem(
    val id: String,
    val normalizedKey: String,
    val displayName: String,
    val category: InventoryCategory,
    val subcategory: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPurchasedAt: Long?,
    val lastPurchasePrice: Double?,
    val purchaseCount: Int
)

data class InventoryEvent(
    val id: String,
    val itemId: String,
    val type: String,
    val receiptId: String,
    val receiptLineId: String,
    val occurredAt: Long,
    val quantity: Double,
    val unit: String?,
    val unitPrice: Double?,
    val lineTotal: Double,
    val currency: String,
    val rawText: String?,
    val createdAt: Long
)
