package com.wardrobe.app.core.tryon.capture

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [com.wardrobe.app.core.image.capture.CameraCaptureController]'s front-camera
 * sibling — guided body-profile capture is the first place this app's camera
 * pipeline ever binds [CameraSelector.DEFAULT_FRONT_CAMERA] rather than the
 * back camera. Kept as its own class rather than a parameter added to the
 * existing controller: garment capture and body-profile capture share no
 * state, only the same CameraX plumbing shape. Requires a real camera; only
 * exercisable via an instrumented test on a device/emulator, same
 * unautomatable-here tier `CameraCaptureController` is already in.
 */
class BodyCaptureController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var imageCapture: ImageCapture? = null

        suspend fun bindToLifecycle(
            lifecycleOwner: LifecycleOwner,
            previewView: PreviewView,
        ) {
            val provider = awaitCameraProvider()
            val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder().build()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
            imageCapture = capture
        }

        suspend fun capture(outputFile: File): File =
            suspendCancellableCoroutine { continuation ->
                val capture = imageCapture
                if (capture == null) {
                    continuation.resumeWithException(
                        IllegalStateException("Camera not bound — call bindToLifecycle first"),
                    )
                    return@suspendCancellableCoroutine
                }
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            continuation.resume(outputFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resumeWithException(exception)
                        }
                    },
                )
            }

        private suspend fun awaitCameraProvider(): ProcessCameraProvider =
            suspendCancellableCoroutine { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    { continuation.resume(future.get()) },
                    ContextCompat.getMainExecutor(context),
                )
            }
    }
