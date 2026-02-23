package org.nighthawklabs.homescanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptPageDebugDao {

    @Query("SELECT * FROM receipt_page_debug WHERE receiptId = :receiptId AND pageIndex = :pageIndex")
    fun observeDebug(receiptId: String, pageIndex: Int): Flow<ReceiptPageDebugEntity?>

    @Query("SELECT * FROM receipt_page_debug WHERE receiptId = :receiptId AND pageIndex = :pageIndex")
    suspend fun getDebug(receiptId: String, pageIndex: Int): ReceiptPageDebugEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReceiptPageDebugEntity)
}
