package com.wardrobe.app.feature.settings.aiproviders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.ai.AiConnectionTestResult
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiUsageSummary
import com.wardrobe.app.core.model.ai.AiVendor
import com.wardrobe.app.core.ui.components.DropdownField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProvidersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AI Providers") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
            items(state.rows, key = { it.capability }) { row ->
                AiProviderRowCard(row = row, viewModel = viewModel)
            }
            if (state.usage.isNotEmpty()) {
                item { AiUsageSection(state.usage) }
            }
        }
    }
}

@Composable
private fun AiProviderRowCard(
    row: AiProviderRowUiState,
    viewModel: AiProvidersViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.capability.displayName(), style = MaterialTheme.typography.titleMedium)
            ModeSelector(row, viewModel)
            if (row.config.mode == AiProviderMode.CLOUD) {
                CloudFields(row, viewModel)
            }
        }
    }
    if (row.showConsentDialog) {
        ConsentDialog(
            onConfirm = viewModel::onConsentGranted,
            onDismiss = viewModel::onConsentDeclined,
        )
    }
}

@Composable
private fun ModeSelector(
    row: AiProviderRowUiState,
    viewModel: AiProvidersViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AiProviderMode.entries.forEach { mode ->
            TextButton(onClick = { viewModel.onModeSelected(row.capability, mode) }) {
                Text(
                    mode.displayName(),
                    style =
                        if (row.config.mode == mode) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                )
            }
        }
    }
}

@Composable
private fun CloudFields(
    row: AiProviderRowUiState,
    viewModel: AiProvidersViewModel,
) {
    val config = row.config
    var apiKeyDraft by remember(row.capability) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownField(
            label = "Vendor",
            options = AiVendor.entries.map { it to it.displayName() },
            selected = config.vendor,
            onSelect = { vendor -> vendor?.let { viewModel.onVendorSelected(row.capability, it) } },
        )
        OutlinedTextField(
            value = config.baseUrl.orEmpty(),
            onValueChange = { viewModel.onBaseUrlChanged(row.capability, it) },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = config.model.orEmpty(),
            onValueChange = { viewModel.onModelChanged(row.capability, it) },
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = {
                apiKeyDraft = it
                viewModel.onApiKeyChanged(row.capability, it)
            },
            label = { Text(if (row.hasApiKey) "API key (configured)" else "API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = config.costRatePerThousandTokens?.toString().orEmpty(),
            onValueChange = { viewModel.onCostRateChanged(row.capability, it) },
            label = { Text("Cost per 1K tokens (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!config.isCloudReady()) {
            // M24 real-device finding: this used to be tappable with an
            // empty Vendor/Base URL — consent would "succeed" but cloud
            // stayed silently unreachable (isCloudReady still needs a real
            // baseUrl), with nothing telling the user why. Required fields
            // must actually be filled in before consent is even offered.
            val missingRequiredFields = config.vendor == null || config.baseUrl.isNullOrBlank()
            TextButton(
                onClick = { viewModel.onRequestConsent(row.capability) },
                enabled = !missingRequiredFields,
            ) {
                Text(
                    if (missingRequiredFields) {
                        "Choose a vendor and Base URL first"
                    } else {
                        "Grant consent to enable cloud"
                    },
                )
            }
        }
        TestConnectionRow(row, viewModel)
    }
}

@Composable
private fun TestConnectionRow(
    row: AiProviderRowUiState,
    viewModel: AiProvidersViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { viewModel.onTestConnection(row.capability) },
            enabled = row.config.isCloudReady() && row.hasApiKey && row.testState !is AiConnectionTestState.Loading,
        ) {
            Text("Test Connection")
        }
        when (val testState = row.testState) {
            is AiConnectionTestState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            is AiConnectionTestState.Done -> TestResultText(testState.result)
            null -> Unit
        }
    }
}

@Composable
private fun TestResultText(result: AiConnectionTestResult) {
    when (result) {
        is AiConnectionTestResult.Success -> {
            Text("Connected (${result.latencyMs}ms)", color = MaterialTheme.colorScheme.primary)
        }

        is AiConnectionTestResult.Failure -> {
            Text(result.reason, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send photos to the cloud?") },
        text = {
            Text(
                "Enabling this sends garment photos to the vendor and Base URL you configured for processing. " +
                    "You can switch back to on-device at any time.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("I consent") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AiUsageSection(usage: List<AiUsageSummary>) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("AI Usage", style = MaterialTheme.typography.titleMedium)
        usage.forEach { summary ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            AiUsageRow(summary)
        }
    }
}

@Composable
private fun AiUsageRow(summary: AiUsageSummary) {
    Column {
        Text(
            "${summary.capability.displayName()} — ${summary.provider ?: "on-device"}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "${summary.totalCalls} calls · ${summary.cacheHitCount} cache hits · " +
                (summary.averageLatencyMs?.let { "${it}ms avg" } ?: "no real dispatches yet") +
                (summary.estimatedTotalCostMinorUnits?.let { " · ~$it minor units" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
