package org.nighthawklabs.homescanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReceiptDraftEntity::class,
        ReceiptPageEntity::class,
        ReceiptPageDebugEntity::class,
        ReceiptCorrectionEventEntity::class,
        MerchantLearningEntity::class,
        InventoryItemEntity::class,
        InventoryEventEntity::class,
        ItemCategoryLearningEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun receiptDraftDao(): ReceiptDraftDao
    abstract fun receiptPageDao(): ReceiptPageDao
    abstract fun receiptPageDebugDao(): ReceiptPageDebugDao
    abstract fun receiptCorrectionDao(): ReceiptCorrectionDao
    abstract fun merchantLearningDao(): MerchantLearningDao
    abstract fun inventoryDao(): InventoryDao
}
