package org.nighthawklabs.homescanner.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nighthawklabs.homescanner.data.db.MerchantLearningDao
import org.nighthawklabs.homescanner.data.files.FileStore
import org.nighthawklabs.homescanner.data.learning.MerchantKeyNormalizer
import org.nighthawklabs.homescanner.data.learning.MerchantLearningService
import org.nighthawklabs.homescanner.data.parser.ReceiptSchemaBuilder
import org.nighthawklabs.homescanner.data.repo.ReceiptRepository
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult
import org.nighthawklabs.homescanner.domain.parser.ReceiptParser
import org.nighthawklabs.homescanner.scanner.BitmapIO
import org.nighthawklabs.homescanner.scanner.CropMath
import org.nighthawklabs.homescanner.scanner.ImageEnhancer
import org.nighthawklabs.homescanner.scanner.PerspectiveTransformer
import org.nighthawklabs.homescanner.scanner.QuadDetector
import java.io.File

class ReceiptProcessingCoordinator(
    private val repository: ReceiptRepository,
    private val fileStore: FileStore,
    private val parser: ReceiptParser,
    private val merchantLearningDao: MerchantLearningDao
) {

    suspend fun processAfterCapture(receiptId: String) {
        withContext(Dispatchers.Default) {
            runCatching {
                val receipt = repository.getReceipt(receiptId) ?: return@withContext

                val processedPath = receipt.processedImagePath
                if (processedPath == null) {
                    repository.setStatus(receiptId, ReceiptStatus.PROCESSING_IMAGE)
                    processImage(receiptId, receipt.rawImagePath)
                }
                runParse(receiptId)
            }.onFailure {
                repository.markFailed(receiptId, it.message)
            }
        }
    }

    suspend fun processAfterCrop(receiptId: String) {
        withContext(Dispatchers.Default) {
            runCatching {
                repository.setStatus(receiptId, ReceiptStatus.PARSING)
                runParse(receiptId)
            }.onFailure {
                repository.markFailed(receiptId, it.message)
            }
        }
    }

    private suspend fun processImage(receiptId: String, rawPath: String) {
        val file = File(rawPath)
        if (!file.exists()) {
            repository.markFailed(receiptId, "Raw image not found")
            return
        }
        val bitmap = BitmapIO.decodeForPreview(file) ?: run {
            repository.markFailed(receiptId, "Failed to decode image")
            return
        }
        val quadResult = QuadDetector.detectQuad(bitmap)
        val corners = CropMath.reorderCornersTLTRBRBL(quadResult.corners)
        var cropped = PerspectiveTransformer.transform(bitmap, corners)
        val enhanced = ImageEnhancer.enhance(
            cropped,
            grayscale = false,
            contrastStretch = true,
            binarize = false
        )
        cropped.recycle()
        cropped = enhanced
        val processedPath = fileStore.newCroppedReceiptPath(receiptId)
        withContext(Dispatchers.IO) {
            BitmapIO.saveAsJpeg(cropped, File(processedPath), 90)
        }
        cropped.recycle()
        repository.setProcessedImage(receiptId, processedPath)
    }

    private suspend fun runParse(receiptId: String) {
        repository.setStatus(receiptId, ReceiptStatus.PARSING)

        val receipt = repository.getReceipt(receiptId) ?: run {
            repository.markFailed(receiptId, "Receipt not found")
            return
        }
        val imagePath = receipt.processedImagePath ?: receipt.rawImagePath
        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            repository.markFailed(receiptId, "Image file not found")
            return
        }

        var parsed = withContext(Dispatchers.Default) {
            parser.parse(imageFile)
        }

        val merchantKey = MerchantKeyNormalizer.normalize(parsed.merchantName ?: "")
        if (merchantKey.isNotEmpty()) {
            val learning = MerchantLearningService.getLearning(merchantLearningDao, merchantKey)
            if (learning != null) {
                parsed = applyLearning(parsed, learning)
            }
        }

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
