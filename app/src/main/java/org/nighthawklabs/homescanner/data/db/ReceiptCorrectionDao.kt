package org.nighthawklabs.homescanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptCorrectionDao {
    @Query("SELECT * FROM receipt_corrections WHERE receiptId = :receiptId ORDER BY createdAt ASC")
    fun observeCorrections(receiptId: String): Flow<List<ReceiptCorrectionEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReceiptCorrectionEventEntity)
}
