package com.wardrobe.app.feature.settings.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.ui.components.SectionHeader

private val AVATAR_SIZE = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenWardrobePreferences: () -> Unit,
    onOpenAiProviders: () -> Unit,
    onOpenWardrobeSync: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickAvatarLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::onAvatarPicked)
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ProfileSection(
                state = state,
                onNameDraftChanged = viewModel::onNameDraftChanged,
                onSaveName = viewModel::onSaveName,
                onChangeAvatar = {
                    pickAvatarLauncher.launch(
                        PickVisualMediaRequest
                            .Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            .build(),
                    )
                },
            )
            WardrobeSection(onOpenWardrobePreferences)
            AiSection(state, onOpenAiProviders)
            AppSection(state, onOpenWardrobeSync)
        }
    }
}

@Composable
private fun ProfileSection(
    state: ProfileUiState,
    onNameDraftChanged: (String) -> Unit,
    onSaveName: () -> Unit,
    onChangeAvatar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Profile")
        AvatarRow(state = state, onChangeAvatar = onChangeAvatar)
        OutlinedTextField(
            value = state.nameDraft,
            onValueChange = onNameDraftChanged,
            label = { Text(if (state.savedName == null) "What's your name?" else "Name") },
            singleLine = true,
            isError = state.nameError != null,
            supportingText = { state.nameError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onSaveName,
            enabled = !state.isSavingName && state.nameDraft.trim() != state.savedName.orEmpty(),
        ) {
            Text(if (state.isSavingName) "Saving…" else "Save")
        }
    }
}

@Composable
private fun AvatarRow(
    state: ProfileUiState,
    onChangeAvatar: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onChangeAvatar)
                .semantics { contentDescription = "Change profile photo" },
        contentAlignment = Alignment.Center,
    ) {
        if (state.avatarImageUri != null) {
            AsyncImage(
                model = state.avatarImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(40.dp))
        }
        if (state.isSavingAvatar) {
            CircularProgressIndicator(modifier = Modifier.size(AVATAR_SIZE))
        }
    }
}

@Composable
private fun WardrobeSection(onOpenWardrobePreferences: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Wardrobe")
        SettingsLinkCard(
            title = "Style Preferences",
            subtitle = "Occupation, sizing, preferred brands and budget",
            onClick = onOpenWardrobePreferences,
        )
    }
}

@Composable
private fun AiSection(
    state: ProfileUiState,
    onOpenAiProviders: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "AI")
        SettingsLinkCard(
            title = "AI Providers",
            subtitle = "${state.cloudConfiguredCapabilityCount} of ${state.totalCapabilityCount} using Cloud AI",
            onClick = onOpenAiProviders,
        )
    }
}

@Composable
private fun AppSection(
    state: ProfileUiState,
    onOpenWardrobeSync: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "App")
        SettingsLinkCard(
            title = "Wardrobe Sync",
            subtitle = syncSubtitle(state),
            onClick = onOpenWardrobeSync,
        )
        Text(
            text = "Version ${state.appVersion}",
            style = MaterialTheme.typography.labelMedium,
            color = WardrobeTheme.extendedColors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private fun syncSubtitle(state: ProfileUiState): String =
    when {
        state.connectedDeviceName == null -> "No device connected"
        state.lastSyncAtLabel == null -> "Connected to ${state.connectedDeviceName}"
        else -> "${state.connectedDeviceName} · last synced ${state.lastSyncAtLabel}"
    }

@Composable
private fun SettingsLinkCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
    }
}
