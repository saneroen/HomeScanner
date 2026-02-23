package org.nighthawklabs.homescanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptPageDao {

    @Query("SELECT * FROM receipt_pages WHERE receiptId = :receiptId ORDER BY pageIndex ASC")
    fun observePages(receiptId: String): Flow<List<ReceiptPageEntity>>

    @Query("SELECT * FROM receipt_pages WHERE receiptId = :receiptId ORDER BY pageIndex ASC")
    suspend fun getPages(receiptId: String): List<ReceiptPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: ReceiptPageEntity)

    @Query("UPDATE receipt_pages SET processedImagePath = :processedPath, status = :status WHERE receiptId = :receiptId AND pageIndex = :pageIndex")
    suspend fun updateProcessedPath(receiptId: String, pageIndex: Int, processedPath: String, status: String)

    @Query("UPDATE receipt_pages SET status = :status WHERE receiptId = :receiptId AND pageIndex = :pageIndex")
    suspend fun updatePageStatus(receiptId: String, pageIndex: Int, status: String)

    @Query("DELETE FROM receipt_pages WHERE receiptId = :receiptId AND pageIndex = :pageIndex")
    suspend fun deletePage(receiptId: String, pageIndex: Int)

    @Query("DELETE FROM receipt_pages WHERE receiptId = :receiptId AND pageIndex >= :fromIndex")
    suspend fun deletePagesFrom(receiptId: String, fromIndex: Int)
}
