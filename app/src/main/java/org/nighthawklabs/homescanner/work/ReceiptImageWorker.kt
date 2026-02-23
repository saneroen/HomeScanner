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

class ReceiptImageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as App).container
    private val repository get() = container.repository
    private val fileStore get() = container.fileStore

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ReceiptForeground.buildForegroundInfo(
            context = applicationContext,
            workerId = id,
            stage = ReceiptWorkConstants.STAGE_PROCESSING_IMAGE,
            percent = 0,
            message = "Processing image…"
        )

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val receiptId = inputData.getString(ReceiptWorkConstants.KEY_RECEIPT_ID)
            ?: return@withContext Result.failure()
        val receipt = repository.getReceipt(receiptId)
            ?: return@withContext Result.failure()
        val rawPath = receipt.rawImagePath
        if (rawPath.isBlank()) return@withContext Result.failure()
        val rawFile = File(rawPath)
        if (!rawFile.exists()) {
            repository.markFailed(receiptId, "Raw image not found")
            return@withContext Result.failure()
        }
        val existingProcessed = receipt.processedImagePath
        if (existingProcessed != null) {
            val existingFile = File(existingProcessed)
            if (existingFile.exists() && existingFile.length() > 0) {
                return@withContext Result.success(
                    Data.Builder().putString(ReceiptWorkConstants.KEY_PROCESSED_PATH, existingProcessed).build()
                )
            }
        }
        setForeground(getForegroundInfo())
        runCatching {
            repository.setStatus(receiptId, ReceiptStatus.PROCESSING_IMAGE)
            setProgress(WorkProgress(ReceiptWorkConstants.STAGE_PROCESSING_IMAGE, 20, "Detecting edges…").toData())
            val bitmap = BitmapIO.decodeForPreview(rawFile)
                ?: throw IllegalArgumentException("Failed to decode image")
            val quadResult = QuadDetector.detectQuad(bitmap)
            val corners = CropMath.reorderCornersTLTRBRBL(quadResult.corners)
            setProgress(WorkProgress(ReceiptWorkConstants.STAGE_PROCESSING_IMAGE, 40, "Cropping…").toData())
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
            setProgress(WorkProgress(ReceiptWorkConstants.STAGE_PROCESSING_IMAGE, 80, "Saving…").toData())
            val processedPath = fileStore.newProcessedReceiptPath(receiptId, 0)
            withContext(Dispatchers.IO) {
                BitmapIO.saveAsJpeg(cropped, File(processedPath), 90)
            }
            cropped.recycle()
            repository.setProcessedImage(receiptId, processedPath)
            Result.success(Data.Builder().putString(ReceiptWorkConstants.KEY_PROCESSED_PATH, processedPath).build())
        }.getOrElse { e ->
            val maxRetries = 3
            val cause = e.cause ?: e
            val isRetriable = (cause is OutOfMemoryError || cause is java.io.IOException) && runAttemptCount < maxRetries
            if (isRetriable) {
                Result.retry()
            } else {
                repository.markFailed(receiptId, e.message ?: "Image processing failed")
                Result.failure()
            }
        }
    }
}
