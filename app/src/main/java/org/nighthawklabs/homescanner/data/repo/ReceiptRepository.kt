package org.nighthawklabs.homescanner.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nighthawklabs.homescanner.data.db.ReceiptCorrectionDao
import org.nighthawklabs.homescanner.data.db.ReceiptCorrectionEventEntity
import org.nighthawklabs.homescanner.data.db.ReceiptDraftEntity
import org.nighthawklabs.homescanner.data.db.ReceiptDraftDao
import org.nighthawklabs.homescanner.data.db.ReceiptPageDao
import org.nighthawklabs.homescanner.data.db.ReceiptPageEntity
import org.nighthawklabs.homescanner.data.files.FileStore
import org.nighthawklabs.homescanner.domain.model.Receipt
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import java.util.UUID

class ReceiptRepository(
    private val dao: ReceiptDraftDao,
    private val pageDao: ReceiptPageDao,
    private val correctionDao: ReceiptCorrectionDao,
    private val fileStore: FileStore,
    private val pageDebugDao: org.nighthawklabs.homescanner.data.db.ReceiptPageDebugDao? = null
) {
    fun observeReceipts(): Flow<List<Receipt>> =
        dao.observeDrafts().map { drafts ->
            drafts
                .filter { it.pageCount > 0 }
                .map { draft -> draft.toDomain(emptyList()) }
        }

    fun observeReceipt(id: String): Flow<Receipt?> =
        combine(dao.observeDraft(id), pageDao.observePages(id)) { draft, pages ->
            draft?.toDomain(pages.map { it.toDomain() })
        }

    fun observePages(id: String): Flow<List<org.nighthawklabs.homescanner.domain.model.ReceiptPage>> =
        pageDao.observePages(id).map { entities -> entities.map { it.toDomain() } }

    fun observeDraft(id: String): Flow<Receipt?> = observeReceipt(id)

    suspend fun getReceipt(id: String): Receipt? {
        val draft = dao.getDraft(id) ?: return null
        val pages = pageDao.getPages(id)
        return draft.toDomain(pages.map { it.toDomain() })
    }

    suspend fun getPages(id: String): List<org.nighthawklabs.homescanner.domain.model.ReceiptPage> =
        pageDao.getPages(id).map { it.toDomain() }

    suspend fun createReceiptSession(): String {
        val id = UUID.randomUUID().toString()
        val entity = ReceiptDraftEntity(
            id = id,
            createdAt = System.currentTimeMillis(),
            rawImagePath = "",
            croppedImagePath = null,
            pageCount = 0,
            status = ReceiptStatus.CAPTURING.name
        )
        dao.insert(entity)
        return id
    }

    suspend fun addPageToSession(receiptId: String, pageIndex: Int, rawPath: String) {
        val page = ReceiptPageEntity(
            receiptId = receiptId,
            pageIndex = pageIndex,
            rawImagePath = rawPath,
            processedImagePath = null,
            status = "CAPTURED_RAW",
            createdAt = System.currentTimeMillis()
        )
        pageDao.insertPage(page)
        dao.setPageCount(receiptId, pageIndex + 1)
        if (pageIndex == 0) {
            dao.updateFirstPagePaths(receiptId, rawPath, null)
        }
        dao.setStatusWithError(receiptId, ReceiptStatus.CAPTURED_RAW.name, null)
    }

    suspend fun deleteLastPage(receiptId: String) {
        val pages = pageDao.getPages(receiptId)
        if (pages.isEmpty()) return
        val lastIndex = pages.maxOf { it.pageIndex }
        pageDao.deletePage(receiptId, lastIndex)
        val newCount = pages.size - 1
        dao.setPageCount(receiptId, newCount)
        if (newCount > 0) {
            val first = pageDao.getPages(receiptId).minByOrNull { it.pageIndex }!!
            dao.updateFirstPagePaths(receiptId, first.rawImagePath, first.processedImagePath)
        } else {
            dao.updateFirstPagePaths(receiptId, "", null)
            dao.setStatusWithError(receiptId, ReceiptStatus.CAPTURING.name, null)
        }
    }

    suspend fun createDraftCaptured(): Receipt = createReceiptAfterCapture()

    suspend fun createReceiptAfterCapture(): Receipt {
        val id = createReceiptSession()
        val rawPath = fileStore.newRawReceiptPath(id, 0)
        addPageToSession(id, 0, rawPath)
        return getReceipt(id)!!
    }

    suspend fun setStatus(id: String, status: ReceiptStatus, errorMessage: String? = null) {
        val msg = when (status) {
            ReceiptStatus.FAILED -> errorMessage
            else -> null
        }
        dao.setStatusWithError(id, status.name, msg)
    }

    suspend fun setProcessedImage(id: String, processedPath: String) {
        dao.updateProcessed(id, processedPath, ReceiptStatus.IMAGE_READY.name)
    }

    suspend fun setParsedJson(
        id: String,
        json: String,
        merchantName: String?,
        purchaseTime: Long?,
        currency: String?,
        subtotal: Double?,
        tax: Double?,
        total: Double?,
        confidence: Double?,
        warnings: List<String>
    ) {
        val warningsJson = if (warnings.isEmpty()) null else Json.encodeToString(warnings)
        dao.setParsed(
            id = id,
            json = json,
            merchantName = merchantName,
            purchaseTime = purchaseTime,
            currency = currency,
            subtotal = subtotal,
            tax = tax,
            total = total,
            confidence = confidence,
            warningsJson = warningsJson,
            status = ReceiptStatus.NEEDS_REVIEW.name
        )
    }

    suspend fun markConfirmed(id: String) {
        val now = System.currentTimeMillis()
        dao.markConfirmedWithTimestamp(id, ReceiptStatus.CONFIRMED.name, now)
    }

    suspend fun setCorrectedJson(
        id: String,
        json: String,
        merchantName: String?,
        subtotal: Double?,
        tax: Double?,
        total: Double?
    ) {
        dao.setCorrected(
            id = id,
            json = json,
            editedAt = System.currentTimeMillis(),
            merchantName = merchantName,
            subtotal = subtotal,
            tax = tax,
            total = total
        )
    }

    suspend fun addCorrectionEvent(receiptId: String, type: String, payloadJson: String) {
        correctionDao.insert(
            ReceiptCorrectionEventEntity(
                id = UUID.randomUUID().toString(),
                receiptId = receiptId,
                createdAt = System.currentTimeMillis(),
                type = type,
                payloadJson = payloadJson
            )
        )
    }

    fun getEffectiveJson(receipt: Receipt): String? = receipt.effectiveJson

    suspend fun markFailed(id: String, message: String?) {
        dao.markFailed(id, ReceiptStatus.FAILED.name, message)
    }

    suspend fun markCropped(id: String, croppedPath: String) {
        setProcessedImage(id, croppedPath)
    }

    suspend fun getPageDebug(receiptId: String, pageIndex: Int): org.nighthawklabs.homescanner.data.parser.debug.PageParseDebug? {
        val entity = pageDebugDao?.getDebug(receiptId, pageIndex) ?: return null
        return runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<org.nighthawklabs.homescanner.data.parser.debug.PageParseDebug>(entity.debugJson)
        }.getOrNull()
    }

    suspend fun setProcessedImageForPage(receiptId: String, pageIndex: Int, processedPath: String) {
        pageDao.updateProcessedPath(receiptId, pageIndex, processedPath, "IMAGE_READY")
        val pages = pageDao.getPages(receiptId)
        val first = pages.minByOrNull { it.pageIndex }
        if (first?.pageIndex == pageIndex) {
            dao.updateFirstPagePaths(receiptId, first.rawImagePath, processedPath)
        }
    }
}
