package org.nighthawklabs.homescanner.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.nighthawklabs.homescanner.scanner.CropMath

private const val HANDLE_RADIUS_DP = 28f
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f
private const val LOUPE_SIZE_DP = 120f
private const val LOUPE_MAGNIFICATION = 3f
private const val MIN_VISIBLE_FRACTION = 0.2f
private const val DOUBLE_TAP_ZOOM = 2.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    viewModel: CropViewModel,
    onBack: () -> Unit,
    onCropped: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        var resetViewCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isDetecting || uiState.previewBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.isDetecting) "Detecting document..." else "Loading...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                if (uiState.detectionConfidence < 0.5f) {
                    Text(
                        text = "Low confidence — adjust corners",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                CropCanvas(
                    bitmap = uiState.previewBitmap!!,
                    corners = uiState.corners,
                    isApplying = uiState.isApplying,
                    onCornerDrag = { index, x, y ->
                        viewModel.updateCorner(index, android.graphics.PointF(x, y))
                    },
                    onResetViewReady = { resetViewCallback = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.resetCorners() },
                    enabled = uiState.corners.isNotEmpty() && !uiState.isApplying
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset corners")
                }
                IconButton(
                    onClick = { resetViewCallback?.invoke() },
                    enabled = !uiState.isApplying
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset view")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enhance", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.enhanceEnabled,
                        onCheckedChange = { viewModel.setEnhanceEnabled(it) },
                        enabled = !uiState.isApplying
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { viewModel.applyCrop(onCropped) },
                    enabled = uiState.corners.size == 4 && !uiState.isApplying
                ) {
                    if (uiState.isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Apply Crop")
                    }
                }
            }
        }
    }
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    corners: List<android.graphics.PointF>,
    isApplying: Boolean,
    onCornerDrag: (index: Int, x: Float, y: Float) -> Unit,
    onResetViewReady: ((() -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { HANDLE_RADIUS_DP.dp.toPx() }
    val loupeSizePx = with(density) { LOUPE_SIZE_DP.dp.toPx() }

    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    var activeHandle by remember { mutableStateOf<Int?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }

    val fit = remember(bitmap.width, bitmap.height, layoutSize) {
        if (layoutSize.width > 0 && layoutSize.height > 0) {
            CropMath.computeFit(
                layoutSize.width.toFloat(),
                layoutSize.height.toFloat(),
                bitmap.width,
                bitmap.height
            )
        } else null
    }

    val transformedRect = remember(fit, userScale, userOffset, layoutSize) {
        fit?.let {
            CropMath.transformedImageRect(
                it, userScale, userOffset,
                layoutSize.width.toFloat(),
                layoutSize.height.toFloat()
            )
        }
    }

    val resetView: () -> Unit = {
        userScale = 1f
        userOffset = Offset.Zero
    }

    LaunchedEffect(Unit) {
        onResetViewReady?.invoke(resetView)
    }

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize = it }
            .pointerInput(
                bitmap, corners, fit, layoutSize, isApplying,
                handleRadiusPx, userScale, userOffset
            ) {
                if (isApplying) return@pointerInput
                awaitPointerEventScope {
                    var lastCentroid = Offset.Zero
                    var lastDist = 0f
                    var initialScale = 1f
                    var initialOffset = Offset.Zero

                    while (true) {
                        val event = awaitPointerEvent()
                        val f = fit ?: continue
                        val changes = event.changes

                        when (event.type) {
                            PointerEventType.Press -> {
                                when (changes.size) {
                                    1 -> {
                                        val pos = changes[0].position
                                        val handleIdx = CropMath.findNearestCornerTransformed(
                                            pos.x, pos.y, corners, f,
                                            userScale, userOffset, handleRadiusPx * 1.5f
                                        )
                                        if (handleIdx != null) {
                                            activeHandle = handleIdx
                                        } else {
                                            val now = System.currentTimeMillis()
                                            if (now - lastTapTime < 300) {
                                                if (userScale > 1.01f) {
                                                    resetView()
                                                } else {
                                                    userScale = DOUBLE_TAP_ZOOM
                                                    val rect = CropMath.transformedImageRect(
                                                        f, 1f, Offset.Zero,
                                                        layoutSize.width.toFloat(),
                                                        layoutSize.height.toFloat()
                                                    )
                                                    userOffset = Offset(
                                                        rect.center.x - pos.x,
                                                        rect.center.y - pos.y
                                                    ) * (1 - 1 / DOUBLE_TAP_ZOOM)
                                                }
                                            }
                                            lastTapTime = now
                                        }
                                    }
                                    2 -> {
                                        activeHandle = null
                                        lastCentroid = Offset(
                                            (changes[0].position.x + changes[1].position.x) / 2,
                                            (changes[0].position.y + changes[1].position.y) / 2
                                        )
                                        lastDist = kotlin.math.hypot(
                                            (changes[1].position.x - changes[0].position.x).toDouble(),
                                            (changes[1].position.y - changes[0].position.y).toDouble()
                                        ).toFloat()
                                        initialScale = userScale
                                        initialOffset = userOffset
                                    }
                                }
                            }
                            PointerEventType.Move -> {
                                when {
                                    activeHandle != null && changes.isNotEmpty() -> {
                                        val pos = changes[0].position
                                        val imgPoint = CropMath.screenToImage(
                                            pos, f, userScale, userOffset
                                        )
                                        onCornerDrag(activeHandle!!, imgPoint.x, imgPoint.y)
                                    }
                                    changes.size >= 2 -> {
                                        val centroid = Offset(
                                            (changes[0].position.x + changes[1].position.x) / 2,
                                            (changes[0].position.y + changes[1].position.y) / 2
                                        )
                                        val dist = kotlin.math.hypot(
                                            (changes[1].position.x - changes[0].position.x).toDouble(),
                                            (changes[1].position.y - changes[0].position.y).toDouble()
                                        ).toFloat()
                                        if (lastDist > 0.1f) {
                                            val scaleFactor = dist / lastDist
                                            userScale = (initialScale * scaleFactor)
                                                .coerceIn(MIN_SCALE, MAX_SCALE)
                                        }
                                        userOffset = initialOffset + (centroid - lastCentroid)
                                        val rect = CropMath.transformedImageRect(
                                            f, userScale, userOffset,
                                            layoutSize.width.toFloat(),
                                            layoutSize.height.toFloat()
                                        )
                                        val minW = layoutSize.width * MIN_VISIBLE_FRACTION
                                        val minH = layoutSize.height * MIN_VISIBLE_FRACTION
                                        userOffset = Offset(
                                            userOffset.x.coerceIn(
                                                layoutSize.width - rect.right - minW,
                                                -rect.left + minW
                                            ),
                                            userOffset.y.coerceIn(
                                                layoutSize.height - rect.bottom - minH,
                                                -rect.top + minH
                                            )
                                        )
                                        lastCentroid = centroid
                                        lastDist = dist
                                    }
                                    changes.size == 1 && activeHandle == null -> {
                                        val delta = changes[0].position - changes[0].previousPosition
                                        val newOffset = userOffset + delta
                                        val rect = CropMath.transformedImageRect(
                                            f, userScale, newOffset,
                                            layoutSize.width.toFloat(),
                                            layoutSize.height.toFloat()
                                        )
                                        val minW = layoutSize.width * MIN_VISIBLE_FRACTION
                                        val minH = layoutSize.height * MIN_VISIBLE_FRACTION
                                        userOffset = Offset(
                                            newOffset.x.coerceIn(
                                                layoutSize.width - rect.right - minW,
                                                -rect.left + minW
                                            ),
                                            newOffset.y.coerceIn(
                                                layoutSize.height - rect.bottom - minH,
                                                -rect.top + minH
                                            )
                                        )
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                if (changes.all { !it.pressed }) {
                                    activeHandle = null
                                }
                            }
                            else -> {}
                        }
                        changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        fit?.let { f ->
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baseRect = f.imageRect
                    val cx = baseRect.centerX()
                    val cy = baseRect.centerY()
                    translate(userOffset.x, userOffset.y) {
                        scale(userScale, pivot = Offset(cx, cy)) {
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(f.offsetX.toInt(), f.offsetY.toInt()),
                                dstSize = IntSize(
                                    (bitmap.width * f.scale).toInt(),
                                    (bitmap.height * f.scale).toInt()
                                )
                            )
                        }
                    }
                }

                val overlayPath = remember(corners, f, userScale, userOffset) {
                    if (corners.size != 4) return@remember null
                    Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, Size(layoutSize.width.toFloat(), layoutSize.height.toFloat())))
                        val quadPath = Path().apply {
                            val pts = corners.map {
                                CropMath.imageToScreen(
                                    android.graphics.PointF(it.x, it.y),
                                    f, userScale, userOffset
                                )
                            }
                            moveTo(pts[0].x, pts[0].y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        addPath(quadPath)
                    }
                }
                if (overlayPath != null) {
                    val path = overlayPath!!
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val surfaceColor = MaterialTheme.colorScheme.surface
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawPath(path, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                        val pts = corners.map {
                            CropMath.imageToScreen(
                                android.graphics.PointF(it.x, it.y),
                                f, userScale, userOffset
                            )
                        }
                        val quadPath = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        drawPath(
                            path = quadPath,
                            color = primaryColor.copy(alpha = 0.5f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                        pts.forEachIndexed { i, pt ->
                            drawCircle(
                                color = primaryColor,
                                radius = handleRadiusPx,
                                center = pt
                            )
                            drawCircle(
                                color = surfaceColor,
                                radius = handleRadiusPx - 2,
                                center = pt
                            )
                        }
                    }
                }

                if (activeHandle != null && corners.size == 4) {
                    val idx = activeHandle!!
                    val handleImg = corners[idx]
                    val handleScreen = CropMath.imageToScreen(
                        android.graphics.PointF(handleImg.x, handleImg.y),
                        f, userScale, userOffset
                    )
                    LoupeOverlay(
                        bitmap = bitmap,
                        imageBitmap = imageBitmap,
                        centerImage = handleImg,
                        screenPosition = handleScreen,
                        loupeSizePx = loupeSizePx,
                        magnification = LOUPE_MAGNIFICATION,
                        layoutWidth = layoutSize.width.toFloat(),
                        layoutHeight = layoutSize.height.toFloat()
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        ) {
            Text(
                text = "Pinch to zoom • Drag corners to adjust",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun LoupeOverlay(
    bitmap: Bitmap,
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    centerImage: android.graphics.PointF,
    screenPosition: Offset,
    loupeSizePx: Float,
    magnification: Float,
    layoutWidth: Float,
    layoutHeight: Float
) {
    val halfExtent = 40
    val srcLeft = (centerImage.x - halfExtent).toInt().coerceIn(0, bitmap.width - 1)
    val srcTop = (centerImage.y - halfExtent).toInt().coerceIn(0, bitmap.height - 1)
    val srcW = (halfExtent * 2).coerceIn(1, bitmap.width - srcLeft)
    val srcH = (halfExtent * 2).coerceIn(1, bitmap.height - srcTop)

    val loupeLeft = screenPosition.x - loupeSizePx / 2
    val loupeTop = screenPosition.y - loupeSizePx - 20
    val clampedLeft = loupeLeft.coerceIn(8f, layoutWidth - loupeSizePx - 8f)
    val clampedTop = loupeTop.coerceIn(8f, layoutHeight - loupeSizePx - 8f)

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .offset { IntOffset(clampedLeft.roundToInt(), clampedTop.roundToInt()) }
            .size(LOUPE_SIZE_DP.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(srcLeft, srcTop),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(loupeSizePx.roundToInt(), loupeSizePx.roundToInt())
            )
            val center = Offset(loupeSizePx / 2, loupeSizePx / 2)
            drawCircle(
                color = primaryColor,
                radius = 3f,
                center = center
            )
            drawCircle(
                color = surfaceColor,
                radius = 1.5f,
                center = center
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.7f),
                start = Offset(loupeSizePx / 2, 0f),
                end = Offset(loupeSizePx / 2, loupeSizePx),
                strokeWidth = 1f
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.7f),
                start = Offset(0f, loupeSizePx / 2),
                end = Offset(loupeSizePx, loupeSizePx / 2),
                strokeWidth = 1f
            )
        }
    }
}
