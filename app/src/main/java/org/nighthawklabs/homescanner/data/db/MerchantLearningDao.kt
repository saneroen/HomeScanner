package org.nighthawklabs.homescanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantLearningDao {
    @Query("SELECT * FROM merchant_learning WHERE merchantKey = :merchantKey")
    fun observeLearning(merchantKey: String): Flow<MerchantLearningEntity?>

    @Query("SELECT * FROM merchant_learning WHERE merchantKey = :merchantKey")
    suspend fun getLearning(merchantKey: String): MerchantLearningEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MerchantLearningEntity)
}
