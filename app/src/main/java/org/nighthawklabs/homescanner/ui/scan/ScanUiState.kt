package org.nighthawklabs.homescanner.ui.scan

import androidx.compose.ui.geometry.Offset

data class ScanUiState(
    val permissionGranted: Boolean = false,
    val isCapturing: Boolean = false,
    val errorMessage: String? = null,
    val previewReady: Boolean = false,
    val autoCaptureEnabled: Boolean = true,
    val overlayQuad: List<Offset> = emptyList(),
    val debugText: String = "Detecting…",
    val previewWidth: Float = 0f,
    val previewHeight: Float = 0f,
    val currentReceiptId: String? = null,
    val pageCount: Int = 0,
    val isSessionActive: Boolean = false,
    val showBottomSheet: Boolean = false,
    val lastCapturedPageIndex: Int? = null,
    val maxPages: Int = 5,
    val uiHint: String = ""
)
