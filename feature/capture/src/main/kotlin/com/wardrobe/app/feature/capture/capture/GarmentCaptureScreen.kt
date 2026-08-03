package com.wardrobe.app.feature.capture.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.image.capture.CameraCaptureController
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentCaptureScreen(
    onBack: () -> Unit,
    onQueued: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarmentCaptureViewModel = hiltViewModel(),
) {
    val isQueued by viewModel.isQueued.collectAsStateWithLifecycle()

    LaunchedEffect(isQueued) {
        if (isQueued) onQueued()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Take Photo") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        GarmentCameraCapture(
            isQueuing = isQueued,
            onCaptured = viewModel::onPhotoCaptured,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

@Composable
private fun GarmentCameraCapture(
    isQueuing: Boolean,
    onCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by
        remember {
            mutableIntStateOf(
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    1
                } else {
                    0
                },
            )
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = if (granted) 1 else 0
        }

    if (hasPermission == 0) {
        Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Camera access is needed to photograph a new item.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow Camera") }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { CameraCaptureController(context) }
    val previewView = remember { PreviewView(context) }
    var isCapturing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { controller.bindToLifecycle(lifecycleOwner, previewView) }

    Column(modifier = modifier) {
        AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { previewView })
        Button(
            enabled = !isCapturing && !isQueuing,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            onClick = {
                isCapturing = true
                coroutineScope.launch {
                    val outputFile = File.createTempFile("garment_capture_", ".jpg", context.cacheDir)
                    val captured = controller.capture(outputFile)
                    onCaptured(captured.path)
                }
            },
        ) {
            if (isCapturing || isQueuing) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            Text("Capture")
        }
    }
}
