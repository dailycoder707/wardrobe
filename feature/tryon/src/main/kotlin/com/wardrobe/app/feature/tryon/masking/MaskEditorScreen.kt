package com.wardrobe.app.feature.tryon.masking

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaskEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MaskEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Edit Mask") }) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val bitmap = state.workingBitmap
            if (state.isLoading || bitmap == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                MaskEditorCanvas(
                    bitmap = bitmap,
                    onErase = viewModel::erase,
                    onRestore = viewModel::restore,
                    onSave = {
                        viewModel.save()
                        onDone()
                    },
                )
            }
        }
    }
}

@Composable
internal fun MaskEditorCanvas(
    bitmap: android.graphics.Bitmap,
    onErase: (Int, Int) -> Unit,
    onRestore: (Int, Int) -> Unit,
    onSave: () -> Unit,
) {
    var tool by remember { mutableStateOf(MaskTool.ERASE) }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val boxWidthPx = constraints.maxWidth.toFloat()
            val boxHeightPx = constraints.maxHeight.toFloat()
            val scaleX = bitmap.width / boxWidthPx
            val scaleY = bitmap.height / boxHeightPx

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Garment mask editor canvas",
                contentScale = ContentScale.FillBounds,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tool, bitmap) {
                            detectDragGestures { change, _ ->
                                val bitmapX = (change.position.x * scaleX).toInt()
                                val bitmapY = (change.position.y * scaleY).toInt()
                                when (tool) {
                                    MaskTool.ERASE -> onErase(bitmapX, bitmapY)
                                    MaskTool.RESTORE -> onRestore(bitmapX, bitmapY)
                                }
                            }
                        },
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { tool = MaskTool.ERASE }) { Text("Erase") }
            Button(onClick = { tool = MaskTool.RESTORE }) { Text("Restore") }
            Button(onClick = onSave) { Text("Save") }
        }
    }
}
