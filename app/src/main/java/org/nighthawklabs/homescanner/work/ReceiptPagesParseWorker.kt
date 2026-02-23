package org.nighthawklabs.homescanner.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nighthawklabs.homescanner.App
import org.nighthawklabs.homescanner.data.learning.MerchantKeyNormalizer
import org.nighthawklabs.homescanner.data.learning.MerchantLearningService
import org.nighthawklabs.homescanner.data.parser.MultiPageMerger
import org.nighthawklabs.homescanner.data.parser.ReceiptSchemaBuilder
import org.nighthawklabs.homescanner.data.parser.debug.ParseDebug
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import java.io.File

class ReceiptPagesParseWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as App).container
    private val repository get() = container.repository
    private val parser get() = container.receiptParser
    private val pageDao get() = container.receiptPageDao
    private val merchantLearningDao get() = container.merchantLearningDao

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ReceiptForeground.buildForegroundInfo(
            context = applicationContext,
            workerId = id,
            stage = ReceiptWorkConstants.STAGE_PARSING,
            percent = 50,
            message = "Parsing pages…"
        )

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val receiptId = inputData.getString(ReceiptWorkConstants.KEY_RECEIPT_ID)
            ?: return@withContext Result.failure()
        val pages = pageDao.getPages(receiptId).sortedBy { it.pageIndex }
        val processedPages = pages.filter { it.processedImagePath != null }
        if (processedPages.isEmpty()) {
            repository.markFailed(receiptId, "No processed pages to parse")
            return@withContext Result.failure()
        }
        setForeground(getForegroundInfo())
        repository.setStatus(receiptId, ReceiptStatus.PARSING)
        val total = processedPages.size
        val pageResults = mutableListOf<MultiPageMerger.PageResult>()
        var merchantKey = ""
        for ((idx, page) in processedPages.withIndex()) {
            setProgress(WorkProgress(
                ReceiptWorkConstants.STAGE_PARSING,
                50 + (idx * 45) / total,
                "Parsing page ${idx + 1}/$total…"
            ).toData())
            val file = File(page.processedImagePath!!)
            if (!file.exists()) continue
            val parseResult = runCatching {
                val mlKitParser = parser as? org.nighthawklabs.homescanner.data.parser.MlKitReceiptParser
                if (mlKitParser != null) {
                    withContext(Dispatchers.Default) { mlKitParser.parsePage(receiptId, page.pageIndex, file) }
                } else {
                    withContext(Dispatchers.Default) { parser.parse(file) }
                }
            }
            if (parseResult.isFailure) {
                if (runAttemptCount >= 3) {
                    repository.markFailed(receiptId, parseResult.exceptionOrNull()?.message ?: "Parse failed")
                    return@withContext Result.failure()
                }
                return@withContext Result.retry()
            }
            val parsed = parseResult.getOrThrow()
            parsed.pageParseDebug?.let { pageDebug ->
                container.receiptPageDebugDao.upsert(
                    org.nighthawklabs.homescanner.data.db.ReceiptPageDebugEntity(
                        receiptId = receiptId,
                        pageIndex = page.pageIndex,
                        debugJson = Json.encodeToString(pageDebug),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            ParseDebug.logPerPageParse(
                receiptId = receiptId,
                pageIndex = page.pageIndex,
                itemCount = parsed.items.size,
                topLines = parsed.items.take(5).map { it.rawText },
                skippedReason = if (parsed.items.isEmpty()) "extractor returned 0" else null
            )
            if (merchantKey.isEmpty()) {
                merchantKey = MerchantKeyNormalizer.normalize(parsed.merchantName ?: "")
            }
            pageResults.add(MultiPageMerger.PageResult(page.pageIndex, parsed))
        }
        if (pageResults.isEmpty()) {
            repository.markFailed(receiptId, "All pages failed to parse")
            return@withContext Result.failure()
        }
        setProgress(WorkProgress(ReceiptWorkConstants.STAGE_PARSING, 95, "Merging results…").toData())
        val learning = if (merchantKey.isNotEmpty()) {
            MerchantLearningService.getLearning(merchantLearningDao, merchantKey)
        } else null
        val merged = MultiPageMerger.merge(pageResults, learning)
        ParseDebug.logPostMerge(
            receiptId = receiptId,
            mergedItemCount = merged.items.size,
            perPageCounts = pageResults.map { it.result.items.size },
            topItems = merged.items.take(5).map { it.itemName }
        )
        val json = ReceiptSchemaBuilder.buildReceiptJson(receiptId, merged)
        if (!json.contains("\"items\"")) {
            repository.markFailed(receiptId, "Schema JSON missing items key - parse pipeline bug")
            return@withContext Result.failure()
        }
        repository.setParsedJson(
            id = receiptId,
            json = json,
            merchantName = merged.merchantName,
            purchaseTime = merged.purchaseTime,
            currency = merged.currency,
            subtotal = merged.subtotal,
            tax = merged.tax,
            total = merged.total,
            confidence = merged.confidence,
            warnings = merged.warnings
        )
        Result.success()
    }
}
