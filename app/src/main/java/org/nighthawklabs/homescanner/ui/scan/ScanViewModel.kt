package org.nighthawklabs.homescanner.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nighthawklabs.homescanner.data.files.FileStore
import org.nighthawklabs.homescanner.data.repo.ReceiptRepository
import org.nighthawklabs.homescanner.scanner.AutoCaptureController
import org.nighthawklabs.homescanner.scanner.CameraXController
import org.nighthawklabs.homescanner.scanner.FrameResult
import org.nighthawklabs.homescanner.scanner.OverlayMapper
import org.nighthawklabs.homescanner.work.ReceiptProcessOrchestrator
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ScanViewModel(
    private val repository: ReceiptRepository,
    private val fileStore: FileStore,
    private val orchestrator: ReceiptProcessOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val autoCaptureController = AutoCaptureController()
    private val capturingGuard = AtomicBoolean(false)
    private var captureJob: Job? = null

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted, errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setAutoCaptureEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoCaptureEnabled = enabled) }
        if (!enabled) autoCaptureController.reset()
    }

    fun setPreviewSize(width: Float, height: Float) {
        _uiState.update { it.copy(previewWidth = width, previewHeight = height) }
    }

    fun onFrameResult(
        frame: FrameResult,
        cameraController: CameraXController,
        onFinish: (String) -> Unit
    ) {
        val state = _uiState.value
        if (state.previewWidth <= 0 || state.previewHeight <= 0) return
        if (state.showBottomSheet) return

        val overlayQuad = OverlayMapper.analysisToPreview(
            corners = frame.corners,
            analysisWidth = frame.analysisWidth,
            analysisHeight = frame.analysisHeight,
            previewWidth = state.previewWidth,
            previewHeight = state.previewHeight,
            rotationDegrees = frame.rotationDegrees
        )

        val decision = autoCaptureController.onFrame(frame)

        _uiState.update {
            it.copy(
                overlayQuad = overlayQuad,
                debugText = decision.reason
            )
        }

        if (decision.shouldCapture && state.autoCaptureEnabled && !state.isCapturing) {
            if (state.pageCount >= state.maxPages) return
            triggerCapture(cameraController, onFinish)
        }
    }

    private fun triggerCapture(
        cameraController: CameraXController,
        onFinish: (String) -> Unit
    ) {
        if (!capturingGuard.compareAndSet(false, true)) return
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, errorMessage = null) }
            runCatching {
                var receiptId = _uiState.value.currentReceiptId
                if (receiptId == null) {
                    receiptId = repository.createReceiptSession()
                    _uiState.update { it.copy(currentReceiptId = receiptId, isSessionActive = true) }
                }
                val pageIndex = _uiState.value.pageCount
                val path = fileStore.newRawReceiptPath(receiptId, pageIndex)
                val outputFile = File(path)
                cameraController.takePhoto(outputFile).getOrThrow()
                if (!outputFile.exists() || outputFile.length() == 0L) {
                    throw IllegalStateException("Capture file empty or missing")
                }
                repository.addPageToSession(receiptId, pageIndex, path)
                autoCaptureController.markCaptured()
                receiptId
            }.onSuccess { receiptId ->
                val newCount = _uiState.value.pageCount + 1
                if (newCount >= _uiState.value.maxPages) {
                    _uiState.update {
                        it.copy(isCapturing = false, pageCount = newCount)
                    }
                    doFinishSession(receiptId, onFinish)
                } else {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            pageCount = newCount,
                            lastCapturedPageIndex = newCount - 1,
                            showBottomSheet = true,
                            debugText = "Page $newCount captured",
                            uiHint = "Page $newCount captured ✓"
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        errorMessage = e.message ?: "Capture failed"
                    )
                }
            }
            capturingGuard.set(false)
        }
    }

    fun captureReceipt(
        cameraController: CameraXController,
        onFinish: (String) -> Unit
    ) {
        if (_uiState.value.showBottomSheet) return
        if (_uiState.value.pageCount >= _uiState.value.maxPages) return
        triggerCapture(cameraController, onFinish)
    }

    fun finishSession(onFinish: (String) -> Unit) {
        val receiptId = _uiState.value.currentReceiptId ?: return
        if (_uiState.value.pageCount == 0) return
        viewModelScope.launch {
            doFinishSession(receiptId, onFinish)
        }
    }

    private suspend fun doFinishSession(receiptId: String, onFinish: (String) -> Unit) {
        repository.setStatus(receiptId, org.nighthawklabs.homescanner.domain.model.ReceiptStatus.CAPTURED_RAW)
        orchestrator.enqueueAutoProcess(receiptId)
        _uiState.update {
            it.copy(
                showBottomSheet = false,
                isSessionActive = false,
                currentReceiptId = null,
                pageCount = 0,
                lastCapturedPageIndex = null
            )
        }
        onFinish(receiptId)
    }

    fun retakeLast() {
        val receiptId = _uiState.value.currentReceiptId ?: return
        if (_uiState.value.pageCount <= 0) return
        viewModelScope.launch {
            val pages = repository.getPages(receiptId)
            val lastPage = pages.maxByOrNull { it.pageIndex }
            if (lastPage != null) {
                java.io.File(lastPage.rawImagePath).takeIf { it.exists() }?.delete()
                lastPage.processedImagePath?.let { java.io.File(it).takeIf { f -> f.exists() }?.delete() }
            }
            repository.deleteLastPage(receiptId)
            val newCount = _uiState.value.pageCount - 1
            _uiState.update {
                it.copy(
                    pageCount = newCount,
                    showBottomSheet = newCount > 0,
                    lastCapturedPageIndex = (newCount - 1).takeIf { it >= 0 },
                    uiHint = if (newCount > 0) "Page $newCount" else "Retake complete"
                )
            }
        }
    }

    fun addAnother() {
        _uiState.update { it.copy(showBottomSheet = false) }
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
        autoCaptureController.reset()
    }
}
