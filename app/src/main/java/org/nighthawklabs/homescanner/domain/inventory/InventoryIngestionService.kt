package org.nighthawklabs.homescanner.domain.inventory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.nighthawklabs.homescanner.data.db.InventoryDao
import org.nighthawklabs.homescanner.data.db.InventoryEventEntity
import org.nighthawklabs.homescanner.data.db.InventoryItemEntity
import org.nighthawklabs.homescanner.data.db.ReceiptDraftDao
import org.nighthawklabs.homescanner.data.inventory.CategoryClassifier
import org.nighthawklabs.homescanner.data.inventory.Normalization
import org.nighthawklabs.homescanner.data.parser.ReceiptParsedSchema
import org.nighthawklabs.homescanner.data.parser.ReceiptItemSchema
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import java.util.UUID

class InventoryIngestionService(
    private val receiptDraftDao: ReceiptDraftDao,
    private val inventoryDao: InventoryDao,
    private val classifier: CategoryClassifier
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ingestConfirmedReceipt(receiptId: String): IngestionResult = withContext(Dispatchers.IO) {
        val draft = receiptDraftDao.getDraft(receiptId) ?: return@withContext IngestionResult.Error("Receipt not found")
        if (draft.status != ReceiptStatus.CONFIRMED.name) {
            return@withContext IngestionResult.Error("Receipt is not confirmed")
        }
        val effectiveJson = draft.correctedJson ?: draft.parseJson
        if (effectiveJson.isNullOrBlank()) {
            receiptDraftDao.markIngested(receiptId, System.currentTimeMillis(), "FAILED", "No JSON data")
            return@withContext IngestionResult.Error("No JSON data")
        }
        val schema = runCatching {
            json.decodeFromString<ReceiptParsedSchema>(effectiveJson)
        }.getOrElse { e ->
            receiptDraftDao.markIngested(receiptId, System.currentTimeMillis(), "FAILED", e.message)
            return@withContext IngestionResult.Error("Parse error: ${e.message}")
        }
        val currency = schema.currency.ifBlank { "USD" }
        val purchaseTime = parsePurchaseTime(schema.purchaseTime) ?: draft.confirmedAt ?: draft.createdAt
        var ingested = 0
        var skipped = 0
        for (item in schema.items) {
            val lineId = item.lineId
            val existing = inventoryDao.getEventByReceiptLine(receiptId, lineId)
            if (existing != null) {
                skipped++
                continue
            }
            val lineTotal = item.lineTotal
            if (lineTotal <= 0) {
                skipped++
                continue
            }
            val displayName = item.itemName.ifBlank { item.rawText }.ifBlank { "Unknown" }
            val normalizedKey = Normalization.normalizeKey(displayName)
            if (normalizedKey.isBlank()) {
                skipped++
                continue
            }
            val classification = classifier.classify(
                itemName = displayName,
                rawText = item.rawText,
                merchantName = draft.merchantName
            )
            var itemEntity = inventoryDao.getItemByNormalizedKey(normalizedKey)
            val now = System.currentTimeMillis()
            if (itemEntity == null) {
                itemEntity = InventoryItemEntity(
                    id = UUID.randomUUID().toString(),
                    normalizedKey = normalizedKey,
                    displayName = displayName,
                    category = classification.category.name,
                    subcategory = null,
                    createdAt = now,
                    updatedAt = now,
                    lastPurchasedAt = purchaseTime,
                    lastPurchasePrice = item.unitPrice.takeIf { it > 0 } ?: lineTotal,
                    purchaseCount = 1
                )
                inventoryDao.insertItem(itemEntity)
            } else {
                inventoryDao.updateItem(
                    id = itemEntity.id,
                    displayName = itemEntity.displayName,
                    category = itemEntity.category,
                    subcategory = itemEntity.subcategory,
                    updatedAt = now,
                    lastPurchasedAt = purchaseTime,
                    lastPurchasePrice = item.unitPrice.takeIf { it > 0 } ?: lineTotal,
                    purchaseCount = itemEntity.purchaseCount + 1
                )
                itemEntity = itemEntity.copy(
                    updatedAt = now,
                    lastPurchasedAt = purchaseTime,
                    lastPurchasePrice = item.unitPrice.takeIf { it > 0 } ?: lineTotal,
                    purchaseCount = itemEntity.purchaseCount + 1
                )
            }
            val qty = if (item.qty > 0) item.qty else 1.0
            val unitPrice = item.unitPrice.takeIf { it > 0 }
            val event = InventoryEventEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemEntity.id,
                type = "PURCHASE",
                receiptId = receiptId,
                receiptLineId = lineId,
                occurredAt = purchaseTime,
                quantity = qty,
                unit = null,
                unitPrice = unitPrice,
                lineTotal = lineTotal,
                currency = currency,
                rawText = item.rawText.takeIf { it.isNotBlank() },
                createdAt = now
            )
            val inserted = inventoryDao.insertEvent(event)
            if (inserted > 0) ingested++
        }
        receiptDraftDao.markIngested(receiptId, System.currentTimeMillis(), "INGESTED", null)
        IngestionResult.Success(ingested, skipped)
    }

    private fun parsePurchaseTime(purchaseTime: String?): Long? {
        if (purchaseTime.isNullOrBlank()) return null
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(purchaseTime)?.time
        }.getOrNull() ?: runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(purchaseTime)?.time
        }.getOrNull()
    }
}

sealed class IngestionResult {
    data class Success(val ingested: Int, val skipped: Int) : IngestionResult()
    data class Error(val message: String) : IngestionResult()
}
