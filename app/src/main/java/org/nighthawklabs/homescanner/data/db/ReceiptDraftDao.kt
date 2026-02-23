package org.nighthawklabs.homescanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDraftDao {
    @Query("SELECT * FROM receipt_drafts ORDER BY createdAt DESC")
    fun observeDrafts(): Flow<List<ReceiptDraftEntity>>

    @Query("SELECT * FROM receipt_drafts WHERE id = :id")
    fun observeDraft(id: String): Flow<ReceiptDraftEntity?>

    @Query("SELECT * FROM receipt_drafts WHERE id = :id")
    suspend fun getDraft(id: String): ReceiptDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReceiptDraftEntity)

    @Query("UPDATE receipt_drafts SET pageCount = :count WHERE id = :id")
    suspend fun setPageCount(id: String, count: Int)

    @Query("UPDATE receipt_drafts SET rawImagePath = :rawPath, croppedImagePath = :croppedPath WHERE id = :id")
    suspend fun updateFirstPagePaths(id: String, rawPath: String, croppedPath: String?)

    @Query("UPDATE receipt_drafts SET croppedImagePath = :processedPath, status = :status WHERE id = :id")
    suspend fun updateProcessed(id: String, processedPath: String, status: String)

    @Query("UPDATE receipt_drafts SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE receipt_drafts SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun setStatusWithError(id: String, status: String, errorMessage: String?)

    @Query("""
        UPDATE receipt_drafts SET
            parseJson = :json,
            merchantName = :merchantName,
            purchaseTime = :purchaseTime,
            currency = :currency,
            subtotal = :subtotal,
            tax = :tax,
            total = :total,
            parseConfidence = :confidence,
            warningsJson = :warningsJson,
            status = :status
        WHERE id = :id
    """)
    suspend fun setParsed(
        id: String,
        json: String,
        merchantName: String?,
        purchaseTime: Long?,
        currency: String?,
        subtotal: Double?,
        tax: Double?,
        total: Double?,
        confidence: Double?,
        warningsJson: String?,
        status: String
    )

    @Query("UPDATE receipt_drafts SET status = :status, errorMessage = :message WHERE id = :id")
    suspend fun markFailed(id: String, status: String, message: String?)

    @Query("""
        UPDATE receipt_drafts SET
            correctedJson = :json,
            correctionStatus = 'EDITED',
            lastEditedAt = :editedAt,
            merchantName = :merchantName,
            subtotal = :subtotal,
            tax = :tax,
            total = :total
        WHERE id = :id
    """)
    suspend fun setCorrected(
        id: String,
        json: String,
        editedAt: Long,
        merchantName: String?,
        subtotal: Double?,
        tax: Double?,
        total: Double?
    )

    @Query("""
        UPDATE receipt_drafts SET
            status = :status,
            confirmedAt = :confirmedAt
        WHERE id = :id
    """)
    suspend fun markConfirmedWithTimestamp(id: String, status: String, confirmedAt: Long)

    @Query("""
        UPDATE receipt_drafts SET
            ingestedAt = :ingestedAt,
            ingestionStatus = :ingestionStatus,
            ingestionError = :ingestionError
        WHERE id = :id
    """)
    suspend fun markIngested(id: String, ingestedAt: Long, ingestionStatus: String, ingestionError: String?)
}
