package org.nighthawklabs.homescanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY displayName ASC")
    fun observeAllItems(): Flow<List<InventoryItemEntity>>

    @Query("""
        SELECT * FROM inventory_items
        WHERE (:category = 'ALL' OR category = :category)
        AND (displayName LIKE :queryPattern OR normalizedKey LIKE :queryPattern OR :queryBlank = 1)
        ORDER BY displayName ASC
    """)
    fun observeItemsFiltered(
        category: String,
        queryPattern: String,
        queryBlank: Int
    ): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getItem(id: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE normalizedKey = :normalizedKey")
    suspend fun getItemByNormalizedKey(normalizedKey: String): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(entity: InventoryItemEntity)

    @Query("""
        UPDATE inventory_items SET
            displayName = :displayName,
            category = :category,
            subcategory = :subcategory,
            updatedAt = :updatedAt,
            lastPurchasedAt = :lastPurchasedAt,
            lastPurchasePrice = :lastPurchasePrice,
            purchaseCount = :purchaseCount
        WHERE id = :id
    """)
    suspend fun updateItem(
        id: String,
        displayName: String,
        category: String,
        subcategory: String?,
        updatedAt: Long,
        lastPurchasedAt: Long?,
        lastPurchasePrice: Double?,
        purchaseCount: Int
    )

    @Query("SELECT * FROM inventory_events WHERE receiptId = :receiptId AND receiptLineId = :receiptLineId")
    suspend fun getEventByReceiptLine(receiptId: String, receiptLineId: String): InventoryEventEntity?

    @Query("SELECT * FROM inventory_events WHERE itemId = :itemId ORDER BY occurredAt DESC")
    fun observeEventsForItem(itemId: String): Flow<List<InventoryEventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(entity: InventoryEventEntity): Long

    @Query("SELECT * FROM item_category_learning WHERE key = :key")
    suspend fun getLearningForKey(key: String): ItemCategoryLearningEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearning(entity: ItemCategoryLearningEntity)
}
