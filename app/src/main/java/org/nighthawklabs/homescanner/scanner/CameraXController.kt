package org.nighthawklabs.homescanner.scanner

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class CameraXController(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val cameraExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    var onFrameAnalyzed: ((FrameResult) -> Unit)? = null

    suspend fun bindCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner): Result<Unit> =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                ProcessCameraProvider.getInstance(context).apply {
                    addListener({
                        try {
                            val provider = get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()

                            imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                .build()
                                .also { analysis ->
                                    val processor = FrameProcessor()
                                    val callback = onFrameAnalyzed
                                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        try {
                                            val rotation = imageProxy.imageInfo.rotationDegrees
                                            val result = processor.process(imageProxy, rotation)
                                            if (result != null && callback != null) {
                                                cameraExecutor.execute { callback(result) }
                                            }
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                            cont.resume(Result.success(Unit))
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera bind failed", e)
                            cont.resume(Result.failure(e))
                        }
                    }, cameraExecutor)
                }
            }
        }

    suspend fun takePhoto(outputFile: File): Result<Unit> = withContext(Dispatchers.Main) {
        val capture = imageCapture
        if (capture == null) {
            return@withContext Result.failure(IllegalStateException("Camera not bound"))
        }
        suspendCancellableCoroutine { cont ->
            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            capture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        cont.resume(Result.success(Unit))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Capture failed", exception)
                        cont.resume(Result.failure(exception))
                    }
                }
            )
        }
    }

    fun unbind() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        imageAnalysis = null
    }

    companion object {
        private const val TAG = "CameraXController"
    }
}
