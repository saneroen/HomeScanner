package org.nighthawklabs.homescanner.work

import android.content.Context
import androidx.lifecycle.Observer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ReceiptProcessOrchestrator(private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    private val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    fun enqueueAutoProcess(receiptId: String) {
        val workName = ReceiptWorkConstants.workName(receiptId)
        val imageWork = OneTimeWorkRequestBuilder<ReceiptPagesProcessWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ReceiptWorkConstants.KEY_RECEIPT_ID to receiptId))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(ReceiptWorkConstants.TAG_RECEIPT)
            .addTag(ReceiptWorkConstants.tagReceipt(receiptId))
            .build()
        val parseWork = OneTimeWorkRequestBuilder<ReceiptPagesParseWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ReceiptWorkConstants.KEY_RECEIPT_ID to receiptId))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(ReceiptWorkConstants.TAG_RECEIPT)
            .addTag(ReceiptWorkConstants.tagReceipt(receiptId))
            .build()
        workManager.beginUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            imageWork
        ).then(parseWork).enqueue()
    }

    fun enqueueReprocess(receiptId: String) {
        val workName = ReceiptWorkConstants.workName(receiptId)
        val imageWork = OneTimeWorkRequestBuilder<ReceiptPagesProcessWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ReceiptWorkConstants.KEY_RECEIPT_ID to receiptId))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(ReceiptWorkConstants.TAG_RECEIPT)
            .addTag(ReceiptWorkConstants.tagReceipt(receiptId))
            .build()
        val parseWork = OneTimeWorkRequestBuilder<ReceiptPagesParseWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ReceiptWorkConstants.KEY_RECEIPT_ID to receiptId))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(ReceiptWorkConstants.TAG_RECEIPT)
            .addTag(ReceiptWorkConstants.tagReceipt(receiptId))
            .build()
        workManager.beginUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            imageWork
        ).then(parseWork).enqueue()
    }

    fun enqueueParseOnly(receiptId: String, processedPath: String?) {
        val workName = ReceiptWorkConstants.workName(receiptId)
        val parseWork = OneTimeWorkRequestBuilder<ReceiptPagesParseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    ReceiptWorkConstants.KEY_RECEIPT_ID to receiptId,
                    ReceiptWorkConstants.KEY_PROCESSED_PATH to (processedPath ?: "")
                )
            )
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(ReceiptWorkConstants.TAG_RECEIPT)
            .addTag(ReceiptWorkConstants.tagReceipt(receiptId))
            .build()
        workManager.beginUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            parseWork
        ).enqueue()
    }

    fun cancel(receiptId: String) {
        workManager.cancelUniqueWork(ReceiptWorkConstants.workName(receiptId))
    }

    fun observeWorkProgress(receiptId: String): Flow<WorkProgress?> = callbackFlow {
        val liveData = workManager.getWorkInfosForUniqueWorkLiveData(ReceiptWorkConstants.workName(receiptId))
        val observer = Observer<List<androidx.work.WorkInfo>> { workInfos ->
            val running = workInfos?.find { it.state == androidx.work.WorkInfo.State.RUNNING }
            val progress = running?.progress?.let { WorkProgress.from(it) }
            trySend(progress)
        }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }
}
