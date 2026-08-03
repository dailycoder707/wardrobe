package com.wardrobe.app.feature.closet.addwardrobe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** "Add to Wardrobe" — the FAB's bottom sheet, shared by Home and Closet
 * (both already live in this module). "Take Photo" is a plain navigation
 * callback (the camera screen lives in `feature:capture`, an upstream
 * module this one can't depend on — `WardrobeNavHost` wires the actual
 * route); "Choose from Gallery"/"Import Multiple" launch the system Photo
 * Picker directly and hand the result to [AddToWardrobeViewModel]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToWardrobeSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onImportStarted: () -> Unit,
    viewModel: AddToWardrobeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.navigateToQueue.collect {
            onDismiss()
            onImportStarted()
        }
    }

    val singlePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onGalleryImagesPicked(listOf(uri))
        }
    val multiPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) viewModel.onGalleryImagesPicked(uris)
        }
    val visualMediaRequest =
        PickVisualMediaRequest
            .Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .build()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Add to Wardrobe",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            AddToWardrobeSheetRow(
                icon = Icons.Filled.CameraAlt,
                label = "Take Photo",
                onClick = {
                    onDismiss()
                    onTakePhoto()
                },
            )
            AddToWardrobeSheetRow(
                icon = Icons.Filled.PhotoLibrary,
                label = "Choose From Gallery",
                onClick = { singlePickerLauncher.launch(visualMediaRequest) },
            )
            AddToWardrobeSheetRow(
                icon = Icons.Filled.Collections,
                label = "Import Multiple Photos",
                onClick = { multiPickerLauncher.launch(visualMediaRequest) },
            )
        }
    }
}

@Composable
private fun AddToWardrobeSheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
