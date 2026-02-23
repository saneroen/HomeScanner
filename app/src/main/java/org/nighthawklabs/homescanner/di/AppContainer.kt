package org.nighthawklabs.homescanner.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import org.nighthawklabs.homescanner.data.db.AppDb
import org.nighthawklabs.homescanner.data.db.InventoryDao
import org.nighthawklabs.homescanner.data.db.MerchantLearningDao
import org.nighthawklabs.homescanner.data.db.ReceiptCorrectionDao
import org.nighthawklabs.homescanner.data.db.ReceiptPageDao
import org.nighthawklabs.homescanner.data.db.ReceiptDraftDao
import org.nighthawklabs.homescanner.data.files.FileStore
import org.nighthawklabs.homescanner.data.parser.MlKitReceiptParser
import org.nighthawklabs.homescanner.data.repo.ReceiptRepository
import org.nighthawklabs.homescanner.data.inventory.CategoryClassifier
import org.nighthawklabs.homescanner.domain.inventory.InventoryIngestionService
import org.nighthawklabs.homescanner.domain.usecase.ReceiptProcessingCoordinator
import org.nighthawklabs.homescanner.ui.crop.CropViewModel
import org.nighthawklabs.homescanner.ui.home.HomeViewModel
import org.nighthawklabs.homescanner.ui.inventory.InventoryItemDetailsViewModel
import org.nighthawklabs.homescanner.ui.inventory.InventoryViewModel
import org.nighthawklabs.homescanner.ui.receipt.ReceiptDetailsViewModel
import org.nighthawklabs.homescanner.ui.scan.ScanViewModel
import org.nighthawklabs.homescanner.work.ReceiptProcessOrchestrator

class AppContainer(private val context: Context) {

    private val db: AppDb by lazy {
        Room.databaseBuilder(context, AppDb::class.java, "receipt_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val dao: ReceiptDraftDao by lazy { db.receiptDraftDao() }
    val receiptPageDao: ReceiptPageDao by lazy { db.receiptPageDao() }
    val receiptPageDebugDao: org.nighthawklabs.homescanner.data.db.ReceiptPageDebugDao by lazy { db.receiptPageDebugDao() }
    val correctionDao: ReceiptCorrectionDao by lazy { db.receiptCorrectionDao() }
    val merchantLearningDao: MerchantLearningDao by lazy { db.merchantLearningDao() }
    val inventoryDao: InventoryDao by lazy { db.inventoryDao() }
    val categoryClassifier: CategoryClassifier by lazy { CategoryClassifier(inventoryDao) }
    val inventoryIngestionService: InventoryIngestionService by lazy {
        InventoryIngestionService(dao, inventoryDao, categoryClassifier)
    }
    val fileStore: FileStore by lazy { FileStore(context) }
    val repository: ReceiptRepository by lazy {
        ReceiptRepository(dao, receiptPageDao, correctionDao, fileStore, receiptPageDebugDao)
    }
    val receiptParser: org.nighthawklabs.homescanner.domain.parser.ReceiptParser = MlKitReceiptParser(context)
    val coordinator: ReceiptProcessingCoordinator by lazy {
        ReceiptProcessingCoordinator(repository, fileStore, receiptParser, merchantLearningDao)
    }

    val orchestrator: ReceiptProcessOrchestrator by lazy {
        ReceiptProcessOrchestrator(context)
    }

    fun homeViewModelFactory(): ViewModelProvider.Factory =
        ViewModelFactory { HomeViewModel(repository) }

    fun scanViewModelFactory(): ViewModelProvider.Factory =
        ViewModelFactory { ScanViewModel(repository, fileStore, orchestrator) }

    fun receiptDetailsViewModelFactory(receiptId: String): ViewModelProvider.Factory =
        ViewModelFactory { ReceiptDetailsViewModel(repository, orchestrator, merchantLearningDao, inventoryIngestionService, context, receiptId) }

    fun inventoryViewModelFactory(): ViewModelProvider.Factory =
        ViewModelFactory { InventoryViewModel(inventoryDao) }

    fun inventoryItemDetailsViewModelFactory(itemId: String): ViewModelProvider.Factory =
        ViewModelFactory { InventoryItemDetailsViewModel(inventoryDao, itemId) }

    fun cropViewModelFactory(receiptId: String, pageIndex: Int): ViewModelProvider.Factory =
        ViewModelFactory { CropViewModel(repository, fileStore, orchestrator, receiptId, pageIndex) }
}

private class ViewModelFactory<T : ViewModel>(
    private val create: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
