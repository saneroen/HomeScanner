package org.nighthawklabs.homescanner.ui.crop

import android.graphics.Bitmap
import android.graphics.PointF

data class CropUiState(
    val previewBitmap: Bitmap? = null,
    val corners: List<PointF> = emptyList(),
    val autoCorners: List<PointF> = emptyList(),
    val enhanceEnabled: Boolean = false,
    val isDetecting: Boolean = false,
    val isApplying: Boolean = false,
    val errorMessage: String? = null,
    val detectionConfidence: Float = 0f
)
