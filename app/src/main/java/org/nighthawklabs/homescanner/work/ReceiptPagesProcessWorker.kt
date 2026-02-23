package org.nighthawklabs.homescanner.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nighthawklabs.homescanner.App
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import org.nighthawklabs.homescanner.scanner.BitmapIO
import org.nighthawklabs.homescanner.scanner.CropMath
import org.nighthawklabs.homescanner.scanner.ImageEnhancer
import org.nighthawklabs.homescanner.scanner.PerspectiveTransformer
import org.nighthawklabs.homescanner.scanner.QuadDetector
import java.io.File

class ReceiptPagesProcessWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as App).container
    private val repository get() = container.repository
    private val fileStore get() = container.fileStore
    private val pageDao get() = container.receiptPageDao

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ReceiptForeground.buildForegroundInfo(
            context = applicationContext,
            workerId = id,
            stage = ReceiptWorkConstants.STAGE_PROCESSING_IMAGE,
            percent = 0,
            message = "Processing page 1…"
        )

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val receiptId = inputData.getString(ReceiptWorkConstants.KEY_RECEIPT_ID)
            ?: return@withContext Result.failure()
        val pages = pageDao.getPages(receiptId)
        if (pages.isEmpty()) {
            repository.markFailed(receiptId, "No pages to process")
            return@withContext Result.failure()
        }
        setForeground(getForegroundInfo())
        repository.setStatus(receiptId, ReceiptStatus.PROCESSING_IMAGE)
        val total = pages.size
        var processed = 0
        for ((idx, page) in pages.sortedBy { it.pageIndex }.withIndex()) {
            setProgress(WorkProgress(
                ReceiptWorkConstants.STAGE_PROCESSING_IMAGE,
                (idx * 100) / total,
                "Processing page ${idx + 1}/$total…"
            ).toData())
            val existing = page.processedImagePath
            if (existing != null) {
                val f = File(existing)
                if (f.exists() && f.length() > 0) {
                    processed++
                    continue
                }
            }
            val rawFile = File(page.rawImagePath)
            if (!rawFile.exists()) {
                pageDao.updatePageStatus(receiptId, page.pageIndex, "FAILED")
                continue
            }
            runCatching {
                val bitmap = BitmapIO.decodeForPreview(rawFile)
                    ?: throw IllegalArgumentException("Failed to decode image")
                val quadResult = QuadDetector.detectQuad(bitmap)
                val corners = CropMath.reorderCornersTLTRBRBL(quadResult.corners)
                var cropped = PerspectiveTransformer.transform(bitmap, corners)
                bitmap.recycle()
                val enhanced = ImageEnhancer.enhance(
                    cropped,
                    grayscale = false,
                    contrastStretch = true,
                    binarize = false
                )
                cropped.recycle()
                cropped = enhanced
                val processedPath = fileStore.newProcessedReceiptPath(receiptId, page.pageIndex)
                withContext(Dispatchers.IO) {
                    BitmapIO.saveAsJpeg(cropped, File(processedPath), 90)
                }
                cropped.recycle()
                repository.setProcessedImageForPage(receiptId, page.pageIndex, processedPath)
                processed++
            }.onFailure { e ->
                val maxRetries = 3
                val cause = e.cause ?: e
                val isRetriable = (cause is OutOfMemoryError || cause is java.io.IOException) && runAttemptCount < maxRetries
                if (!isRetriable) {
                    repository.markFailed(receiptId, e.message ?: "Image processing failed")
                    return@withContext Result.failure()
                }
                return@withContext Result.retry()
            }
        }
        Result.success(Data.Builder().putInt(ReceiptWorkConstants.KEY_PAGE_COUNT, total).build())
    }
}
