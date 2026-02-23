package org.nighthawklabs.homescanner.ui.crop

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nighthawklabs.homescanner.data.files.FileStore
import org.nighthawklabs.homescanner.data.repo.ReceiptRepository
import org.nighthawklabs.homescanner.domain.model.Receipt
import org.nighthawklabs.homescanner.scanner.BitmapIO
import org.nighthawklabs.homescanner.scanner.CropMath
import org.nighthawklabs.homescanner.scanner.ImageEnhancer
import org.nighthawklabs.homescanner.scanner.PerspectiveTransformer
import org.nighthawklabs.homescanner.scanner.QuadDetector
import org.nighthawklabs.homescanner.work.ReceiptProcessOrchestrator
import java.io.File

class CropViewModel(
    private val repository: ReceiptRepository,
    private val fileStore: FileStore,
    private val orchestrator: ReceiptProcessOrchestrator,
    private val draftId: String,
    private val pageIndex: Int = 0
) : ViewModel() {

    private val _draft = MutableStateFlow<Receipt?>(null)
    val draft: StateFlow<Receipt?> = _draft.asStateFlow()

    private val _uiState = MutableStateFlow(CropUiState())
    val uiState: StateFlow<CropUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeReceipt(draftId).collect { d ->
                _draft.value = d
                if (d != null && _uiState.value.previewBitmap == null) {
                    val rawPath = d.pages.getOrNull(pageIndex)?.rawImagePath
                        ?: if (pageIndex == 0 && d.rawImagePath.isNotEmpty()) d.rawImagePath
                        else ""
                    if (rawPath.isNotEmpty()) loadAndDetect(rawPath)
                }
            }
        }
    }

    private suspend fun loadAndDetect(rawPath: String) = withContext(Dispatchers.Default) {
        val file = File(rawPath)
        if (!file.exists()) {
            _uiState.update { it.copy(errorMessage = "Image file not found") }
            return@withContext
        }
        _uiState.update { it.copy(isDetecting = true, errorMessage = null) }
        runCatching {
            val previewBitmap = BitmapIO.decodeForPreview(file)
            if (previewBitmap == null) {
                _uiState.update {
                    it.copy(
                        isDetecting = false,
                        errorMessage = "Failed to load image"
                    )
                }
                return@withContext
            }
            val quadResult = QuadDetector.detectQuad(previewBitmap)
            val corners = CropMath.reorderCornersTLTRBRBL(quadResult.corners)
            _uiState.update {
                it.copy(
                    previewBitmap = previewBitmap,
                    corners = corners,
                    autoCorners = corners,
                    isDetecting = false,
                    detectionConfidence = quadResult.confidence
                )
            }
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    isDetecting = false,
                    errorMessage = e.message ?: "Detection failed"
                )
            }
        }
    }

    fun updateCorner(index: Int, point: PointF) {
        val bitmap = _uiState.value.previewBitmap ?: return
        val constrained = CropMath.constrainToBounds(
            point,
            bitmap.width.toFloat(),
            bitmap.height.toFloat()
        )
        _uiState.update { state ->
            val newCorners = state.corners.toMutableList()
            if (index in newCorners.indices) {
                newCorners[index] = constrained
                if (!CropMath.isSelfIntersecting(newCorners)) {
                    val reordered = CropMath.reorderCornersTLTRBRBL(newCorners)
                    state.copy(corners = reordered)
                } else {
                    state
                }
            } else {
                state
            }
        }
    }

    fun resetCorners() {
        _uiState.update { it.copy(corners = it.autoCorners) }
    }

    fun setEnhanceEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enhanceEnabled = enabled) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun applyCrop(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val bitmap = state.previewBitmap
            val corners = state.corners
            if (bitmap == null || corners.size != 4) {
                _uiState.update { it.copy(errorMessage = "Invalid crop state") }
                return@launch
            }
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.Default) {
                    var cropped = PerspectiveTransformer.transform(bitmap, corners)
                    if (state.enhanceEnabled) {
                        val enhanced = ImageEnhancer.enhance(
                            cropped,
                            grayscale = false,
                            contrastStretch = true,
                            binarize = false
                        )
                        cropped.recycle()
                        cropped = enhanced
                    }
                    val croppedPath = fileStore.newProcessedReceiptPath(draftId, pageIndex)
                    withContext(Dispatchers.IO) {
                        BitmapIO.saveAsJpeg(cropped, File(croppedPath), 90)
                    }
                    cropped.recycle()
                    repository.setProcessedImageForPage(draftId, pageIndex, croppedPath)
                }
                orchestrator.enqueueReprocess(draftId)
                _uiState.update { it.copy(isApplying = false) }
                onDone()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isApplying = false,
                        errorMessage = e.message ?: "Crop failed"
                    )
                }
            }
        }
    }

    fun markCropped(onDone: () -> Unit) {
        applyCrop(onDone)
    }
}
