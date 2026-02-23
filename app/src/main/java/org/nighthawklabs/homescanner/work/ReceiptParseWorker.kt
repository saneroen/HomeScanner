package org.nighthawklabs.homescanner.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nighthawklabs.homescanner.App
import org.nighthawklabs.homescanner.data.parser.ReceiptSchemaBuilder
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult
import org.nighthawklabs.homescanner.domain.parser.ReceiptParser
import java.io.File

class ReceiptParseWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as App).container
    private val repository get() = container.repository
    private val parser get() = container.receiptParser
    private val merchantLearningDao get() = container.merchantLearningDao

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ReceiptForeground.buildForegroundInfo(
            context = applicationContext,
            workerId = id,
            stage = ReceiptWorkConstants.STAGE_PARSING,
            percent = 50,
            message = "Extracting text…"
        )

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val receiptId = inputData.getString(ReceiptWorkConstants.KEY_RECEIPT_ID)
            ?: return@withContext Result.failure()
        val processedPath = inputData.getString(ReceiptWorkConstants.KEY_PROCESSED_PATH)
        val receipt = repository.getReceipt(receiptId)
            ?: run {
                repository.markFailed(receiptId, "Receipt not found")
                return@withContext Result.failure()
            }
        val imagePath = processedPath ?: receipt.processedImagePath ?: receipt.rawImagePath
        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            repository.markFailed(receiptId, "Image file not found")
            return@withContext Result.failure()
        }
        setForeground(getForegroundInfo())
        repository.setStatus(receiptId, ReceiptStatus.PARSING)
        setProgress(WorkProgress(ReceiptWorkConstants.STAGE_PARSING, 60, "Running OCR…").toData())
        val parseResult = runCatching {
            withContext(Dispatchers.Default) { parser.parse(imageFile) }
        }
        if (parseResult.isFailure) {
            val maxRetries = 3
            if (runAttemptCount < maxRetries) {
                return@withContext Result.retry()
            }
            repository.markFailed(receiptId, parseResult.exceptionOrNull()?.message ?: "Parse failed")
            return@withContext Result.failure()
        }
        var parsed = parseResult.getOrThrow()
        setProgress(WorkProgress(ReceiptWorkConstants.STAGE_PARSING, 85, "Applying learning…").toData())
        val merchantKey = org.nighthawklabs.homescanner.data.learning.MerchantKeyNormalizer
            .normalize(parsed.merchantName ?: "")
        if (merchantKey.isNotEmpty()) {
            val learning = org.nighthawklabs.homescanner.data.learning.MerchantLearningService
                .getLearning(merchantLearningDao, merchantKey)
            if (learning != null) {
                parsed = applyLearning(parsed, learning)
            }
        }
        setProgress(WorkProgress(ReceiptWorkConstants.STAGE_PARSING, 95, "Building result…").toData())
        val json = ReceiptSchemaBuilder.buildReceiptJson(receiptId, parsed)
        repository.setParsedJson(
            id = receiptId,
            json = json,
            merchantName = parsed.merchantName,
            purchaseTime = parsed.purchaseTime,
            currency = parsed.currency,
            subtotal = parsed.subtotal,
            tax = parsed.tax,
            total = parsed.total,
            confidence = parsed.confidence,
            warnings = parsed.warnings
        )
        Result.success()
    }

    private fun applyLearning(
        parsed: ParsedReceiptResult,
        learning: org.nighthawklabs.homescanner.domain.model.MerchantLearning
    ): ParsedReceiptResult {
        val ignoreRegexes = learning.ignoreRegexes.mapNotNull { runCatching { Regex(it) }.getOrNull() }
        val filtered = parsed.items.filter { item ->
            val rawNorm = item.rawText.trim()
            !ignoreRegexes.any { it.containsMatchIn(rawNorm) }
        }
        val aliasMap = learning.aliasMap
        val normalizedKey: (String) -> String = { s -> s.lowercase().trim().replace(Regex("""\s+"""), " ") }
        val withAliases = filtered.map { item ->
            val key = normalizedKey(item.rawText)
            val mappedName = aliasMap[key]
            if (mappedName != null) {
                item.copy(itemName = mappedName, confidence = (item.confidence + 0.1).coerceAtMost(1.0))
            } else {
                item
            }
        }
        return parsed.copy(items = withAliases)
    }
}
