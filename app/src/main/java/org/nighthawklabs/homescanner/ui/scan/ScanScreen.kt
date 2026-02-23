package org.nighthawklabs.homescanner.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.nighthawklabs.homescanner.scanner.CameraXController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onCaptureComplete: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setPermissionGranted(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.setPermissionGranted(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan") },
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
        if (!uiState.permissionGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Camera permission is required to scan receipts.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    androidx.compose.material3.Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    ) {
                        Text("Grant Camera Permission")
                    }
                }
            }
        } else {
            var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
            val cameraController = remember { CameraXController(context) }

            DisposableEffect(Unit) {
                onDispose { cameraController.unbind() }
            }

            LaunchedEffect(uiState.permissionGranted, previewViewRef) {
                val pv = previewViewRef
                if (uiState.permissionGranted && pv != null) {
                    cameraController.onFrameAnalyzed = { frame ->
                        viewModel.onFrameResult(frame, cameraController, onCaptureComplete)
                    }
                    cameraController.bindCamera(pv, lifecycleOwner)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onSizeChanged { size ->
                        viewModel.setPreviewSize(size.width.toFloat(), size.height.toFloat())
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            previewViewRef = this
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (uiState.overlayQuad.size == 4) {
                    val overlayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            moveTo(uiState.overlayQuad[0].x, uiState.overlayQuad[0].y)
                            for (i in 1..3) {
                                lineTo(uiState.overlayQuad[i].x, uiState.overlayQuad[i].y)
                            }
                            close()
                        }
                        drawPath(
                            path = path,
                            color = overlayColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = uiState.debugText,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (uiState.isSessionActive && uiState.pageCount > 0) {
                            Text(
                                text = "Pages: ${uiState.pageCount}/${uiState.maxPages}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto Capture",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = uiState.autoCaptureEnabled,
                            onCheckedChange = { viewModel.setAutoCaptureEnabled(it) },
                            enabled = !uiState.isCapturing
                        )
                    }
                }

                FloatingActionButton(
                    onClick = {
                        if (!uiState.isCapturing && !uiState.showBottomSheet) {
                            viewModel.captureReceipt(cameraController, onCaptureComplete)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (uiState.isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Capture"
                        )
                    }
                }
            }

            if (uiState.showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.addAnother() },
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = uiState.uiHint.ifEmpty { "Page ${uiState.pageCount} captured ✓" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.finishSession(onCaptureComplete) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Finish")
                            }
                            if (uiState.pageCount > 1) {
                                Button(
                                    onClick = { viewModel.retakeLast() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retake Last")
                                }
                            }
                            if (uiState.pageCount < uiState.maxPages) {
                                Button(
                                    onClick = { viewModel.addAnother() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Add Another")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
