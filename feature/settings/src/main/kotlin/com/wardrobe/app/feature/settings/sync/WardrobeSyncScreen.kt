package com.wardrobe.app.feature.settings.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.sync.ConflictResolution
import com.wardrobe.app.core.model.sync.SyncConflict
import com.wardrobe.app.core.model.sync.SyncHistoryEntry
import com.wardrobe.app.core.model.sync.SyncOutcome
import com.wardrobe.app.core.model.sync.SyncPreferences
import com.wardrobe.app.core.model.sync.SyncState
import com.wardrobe.app.core.model.sync.SyncStatusSnapshot
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeSyncScreen(
    onBack: () -> Unit,
    onConnectPhone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri?.let { viewModel.exportBackup(it.toString()) }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.restoreBackup(it.toString()) }
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Wardrobe Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        WardrobeSyncContent(
            innerPadding = innerPadding,
            status = status,
            conflicts = conflicts,
            preferences = preferences,
            history = history,
            onConnectPhone = onConnectPhone,
            onManualSync = viewModel::manualSync,
            onResolveConflict = viewModel::resolveConflict,
            onUpdatePreferences = viewModel::updatePreferences,
            onExport = { exportLauncher.launch("wardrobe-backup.zip") },
            onRestore = { restoreLauncher.launch(arrayOf("application/zip")) },
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun WardrobeSyncContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    status: SyncStatusSnapshot,
    conflicts: List<SyncConflict>,
    preferences: SyncPreferences,
    history: List<SyncHistoryEntry>,
    onConnectPhone: () -> Unit,
    onManualSync: () -> Unit,
    onResolveConflict: (Long, ConflictResolution) -> Unit,
    onUpdatePreferences: ((SyncPreferences) -> SyncPreferences) -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 16.dp),
    ) {
        item { StatusCard(status, onConnectPhone, onManualSync = onManualSync) }
        if (conflicts.isNotEmpty()) {
            item { Text("Needs your input", style = MaterialTheme.typography.titleMedium) }
            items(conflicts, key = { it.id }) { conflict ->
                ConflictCard(conflict, onResolve = { resolution -> onResolveConflict(conflict.id, resolution) })
            }
        }
        item {
            PreferencesCard(
                preferences = preferences,
                onAutoSyncChanged = { enabled -> onUpdatePreferences { it.copy(autoSyncEnabled = enabled) } },
                onWifiOnlyChanged = { enabled -> onUpdatePreferences { it.copy(wifiOnly = enabled) } },
                onChargingOnlyChanged = { enabled -> onUpdatePreferences { it.copy(chargingOnly = enabled) } },
            )
        }
        item { BackupCard(onExport = onExport, onRestore = onRestore) }
        if (history.isNotEmpty()) {
            item { Text("Sync History", style = MaterialTheme.typography.titleMedium) }
            items(history, key = { it.id }) { entry -> HistoryRow(entry) }
        }
    }
}

@Composable
internal fun StatusCard(
    status: SyncStatusSnapshot,
    onConnectPhone: () -> Unit,
    onManualSync: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val deviceName = status.connectedDevice?.displayName
            Text(
                text = deviceName ?: "No device paired yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(lastSyncLabel(status), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Pending changes: ${status.pendingChangeCount}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Storage used: ${formatBytes(status.storageUsedBytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (status.state == SyncState.ERROR && status.lastError != null) {
                Text(
                    "Last sync didn't finish — it'll retry automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.connectedDevice == null) {
                    Button(onClick = onConnectPhone, modifier = Modifier.fillMaxWidth()) { Text("Connect Phone") }
                } else {
                    OutlinedButton(
                        onClick = onManualSync,
                        enabled = status.state != SyncState.SYNCING,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (status.state == SyncState.SYNCING) "Syncing…" else "Manual Sync")
                    }
                }
            }
        }
    }
}

private fun lastSyncLabel(status: SyncStatusSnapshot): String {
    val lastSync = status.lastSyncAt ?: return "Last sync: never"
    val formatted =
        DateTimeFormatter
            .ofPattern("MMM d, h:mm a")
            .withZone(ZoneId.systemDefault())
            .format(lastSync)
    return "Last sync: $formatted"
}

private fun formatBytes(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes < 1) "<1 MB" else "%.0f MB".format(megabytes)
}

@Composable
internal fun ConflictCard(
    conflict: SyncConflict,
    onResolve: (ConflictResolution) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Sync conflict needs your choice" },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This item changed on both devices", style = MaterialTheme.typography.titleSmall)
            Text("On this device: ${conflict.localSummary}", style = MaterialTheme.typography.bodyMedium)
            Text("On the other device: ${conflict.remoteSummary}", style = MaterialTheme.typography.bodyMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onResolve(ConflictResolution.KEEP_LOCAL) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Keep this device's version") }
                OutlinedButton(
                    onClick = { onResolve(ConflictResolution.KEEP_REMOTE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Keep the other device's version") }
            }
        }
    }
}

@Composable
private fun PreferencesCard(
    preferences: SyncPreferences,
    onAutoSyncChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onChargingOnlyChanged: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PreferenceRow("Sync automatically", preferences.autoSyncEnabled, onAutoSyncChanged)
            PreferenceRow("Wi-Fi only", preferences.wifiOnly, onWifiOnlyChanged)
            PreferenceRow("Only while charging", preferences.chargingOnly, onChargingOnlyChanged)
        }
    }
}

@Composable
private fun PreferenceRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BackupCard(
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export Backup") }
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("Restore Backup") }
        }
    }
}

@Composable
private fun HistoryRow(entry: SyncHistoryEntry) {
    val formatted =
        DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault()).format(entry.startedAt)
    val outcomeLabel = if (entry.outcome == SyncOutcome.SUCCESS) "Success" else "Didn't finish"
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(formatted, style = MaterialTheme.typography.bodyMedium)
        Text(outcomeLabel, style = MaterialTheme.typography.bodyMedium)
    }
}
