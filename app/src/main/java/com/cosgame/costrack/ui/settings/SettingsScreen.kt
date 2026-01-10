package com.cosgame.costrack.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosgame.costrack.state.AppMode

/**
 * Settings screen with basic options and developer options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Reset confirmation dialog
    if (uiState.showResetConfirmation) {
        ResetConfirmationDialog(
            onConfirm = viewModel::resetAllSettings,
            onDismiss = viewModel::hideResetConfirmation
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    if (uiState.isDevMode) {
                        TextButton(onClick = { }) {
                            Text("DEV", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // General Settings
            SettingsSection(title = "General") {
                // Sampling Rate
                SamplingRateSetting(
                    selectedRate = uiState.selectedSamplingRate,
                    onRateSelected = viewModel::setSamplingRate
                )
            }

            // Developer Options (visible when unlocked)
            AnimatedVisibility(
                visible = uiState.canAccessDevOptions,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SettingsSection(title = "Developer Options") {
                    // Dev mode toggle
                    SettingsSwitch(
                        title = "Developer Mode",
                        subtitle = if (uiState.isDevMode)
                            "Showing detailed information"
                        else
                            "Showing simplified UI",
                        checked = uiState.isDevMode,
                        onCheckedChange = { viewModel.toggleDevMode() }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Lock dev mode
                    SettingsButton(
                        title = "Lock Developer Mode",
                        subtitle = "Remove developer access",
                        onClick = {
                            viewModel.lockDevMode()
                            Toast.makeText(
                                context,
                                "Developer mode locked",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Reset all
                    SettingsButton(
                        title = "Reset All Settings",
                        subtitle = "Restore defaults and lock dev mode",
                        onClick = viewModel::showResetConfirmation,
                        isDestructive = true
                    )
                }
            }

            // About Section
            SettingsSection(title = "About") {
                AboutItem(
                    title = "CosGame",
                    subtitle = "Sensor Data Collection App"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Version with tap-to-unlock
                VersionItem(
                    version = "1.0.0",
                    build = "1",
                    devModeUnlocked = uiState.devModeUnlocked,
                    onTap = {
                        when (val result = viewModel.onVersionTap()) {
                            is UnlockResult.JustUnlocked -> {
                                Toast.makeText(
                                    context,
                                    "Developer mode unlocked!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            is UnlockResult.TapsRemaining -> {
                                if (result.count <= 3) {
                                    Toast.makeText(
                                        context,
                                        "${result.count} taps to unlock dev mode",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            is UnlockResult.AlreadyUnlocked -> {
                                // Do nothing
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SamplingRateSetting(
    selectedRate: SamplingRate,
    onRateSelected: (SamplingRate) -> Unit
) {
    Column {
        Text(
            text = "Sampling Rate",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Higher rates use more battery",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(Modifier.selectableGroup()) {
            SamplingRate.values().forEach { rate ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = rate == selectedRate,
                            onClick = { onRateSelected(rate) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = rate == selectedRate,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rate.label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutItem(
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VersionItem(
    version: String,
    build: String,
    devModeUnlocked: Boolean,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Version",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$version (build $build)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (devModeUnlocked) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "DEV",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun ResetConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset All Settings?") },
        text = {
            Text("This will restore all settings to defaults and lock developer mode. This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
