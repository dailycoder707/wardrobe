package com.wardrobe.app.feature.settings.sync

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.sync.pairing.PairingQrCodec
import kotlinx.coroutines.launch

private const val TAB_SHOW = 0
private const val TAB_SCAN = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(TAB_SHOW) }

    DisposableEffect(Unit) { onDispose { viewModel.cancel() } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Connect Phone") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == TAB_SHOW,
                    onClick = { selectedTab = TAB_SHOW },
                    text = { Text("Show QR") },
                )
                Tab(
                    selected = selectedTab == TAB_SCAN,
                    onClick = { selectedTab = TAB_SCAN },
                    text = { Text("Scan QR") },
                )
            }
            when (selectedTab) {
                TAB_SHOW -> ShowQrTab(state, onGenerate = viewModel::generateQr)
                else -> ScanQrTab(state, onScanned = viewModel::onQrScanned)
            }
        }
    }
}

@Composable
private fun ShowQrTab(
    state: PairingUiState,
    onGenerate: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            is PairingUiState.ShowingQr -> {
                val bitmap =
                    remember(state.pngBytes) { BitmapFactory.decodeByteArray(state.pngBytes, 0, state.pngBytes.size) }
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code — scan with your other device to pair",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text(
                    "Open Wardrobe Sync on your other device and scan this code",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is PairingUiState.Completed -> {
                Text("Paired with ${state.peerDisplayName}", style = MaterialTheme.typography.titleMedium)
            }

            is PairingUiState.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onGenerate) { Text("Try Again") }
            }

            PairingUiState.Idle -> {
                Button(onClick = onGenerate) { Text("Generate QR Code") }
            }
        }
    }
}

@Composable
private fun ScanQrTab(
    state: PairingUiState,
    onScanned: (String) -> Unit,
) {
    when (state) {
        is PairingUiState.Completed -> {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text("Paired with ${state.peerDisplayName}", style = MaterialTheme.typography.titleMedium)
            }
        }

        else -> {
            CameraQrScanner(onScanned = onScanned)
        }
    }
}

@Composable
private fun CameraQrScanner(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    var hasPermission by
        remember {
            mutableIntStateOf(
                if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
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
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Camera access is needed to scan the QR code.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow Camera") }
        }
        return
    }

    CameraPreviewAnalyzer(onScanned)
}

@Composable
private fun CameraPreviewAnalyzer(onScanned: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    // A plain AtomicBoolean, not Compose state: the analyzer callback below
    // runs on a background executor thread, and this flag is purely an
    // internal one-shot gate (never read for recomposition), so it must
    // not go through Compose's main-thread-oriented snapshot state.
    val hasScanned =
        remember {
            java.util.concurrent.atomic
                .AtomicBoolean(false)
        }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis =
                        ImageAnalysis.Builder().build().also { analysisUseCase ->
                            analysisUseCase.setAnalyzer(
                                java.util.concurrent.Executors
                                    .newSingleThreadExecutor(),
                            ) { imageProxy ->
                                if (!hasScanned.get()) {
                                    val payload = decodeQrFromImageProxy(imageProxy)
                                    if (payload != null && hasScanned.compareAndSet(false, true)) {
                                        coroutineScope.launch { onScanned(payload) }
                                    }
                                }
                                imageProxy.close()
                            }
                        }
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                },
                androidx.core.content.ContextCompat
                    .getMainExecutor(ctx),
            )
            previewView
        },
    )
}

private fun decodeQrFromImageProxy(imageProxy: androidx.camera.core.ImageProxy): String? {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source =
        com.google.zxing.PlanarYUVLuminanceSource(
            bytes,
            imageProxy.width,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false,
        )
    val offer = PairingQrCodec.decode(source) ?: return null
    return kotlinx.serialization.json.Json.encodeToString(
        com.wardrobe.app.core.sync.pairing.PairingOfferPayload
            .serializer(),
        offer,
    )
}
