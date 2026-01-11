package com.cosgame.costrack.ui.sensors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosgame.costrack.sensor.SensorReading

/**
 * Sensors screen showing sensor toggles and data.
 * Adapts UI based on User/Developer mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensors") },
                actions = {
                    if (uiState.isDevMode) {
                        TextButton(onClick = { /* Mode indicator */ }) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick actions
            QuickActionsCard(
                allActive = uiState.allSensorsActive,
                anyActive = uiState.anySensorActive,
                onStartAll = viewModel::startAllSensors,
                onStopAll = viewModel::stopAllSensors
            )

            // Accelerometer card
            SensorCard(
                title = "Accelerometer",
                subtitle = "Linear acceleration (m/s²)",
                enabled = uiState.accelerometerEnabled,
                onToggle = viewModel::toggleAccelerometer,
                reading = uiState.accelerometerReading,
                isDevMode = uiState.isDevMode,
                bufferFill = uiState.accelerometerBufferFill,
                sampleCount = uiState.accelerometerSampleCount,
                samplingFrequency = viewModel.getSamplingFrequency()
            )

            // Gyroscope card
            SensorCard(
                title = "Gyroscope",
                subtitle = "Angular velocity (rad/s)",
                enabled = uiState.gyroscopeEnabled,
                onToggle = viewModel::toggleGyroscope,
                reading = uiState.gyroscopeReading,
                isDevMode = uiState.isDevMode,
                bufferFill = uiState.gyroscopeBufferFill,
                sampleCount = uiState.gyroscopeSampleCount,
                samplingFrequency = viewModel.getSamplingFrequency()
            )

            // Developer mode info
            AnimatedVisibility(
                visible = uiState.isDevMode,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                DevInfoCard(viewModel)
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    allActive: Boolean,
    anyActive: Boolean,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (anyActive) "Sensors Active" else "Sensors Inactive",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onStopAll,
                    enabled = anyActive
                ) {
                    Text("Stop All")
                }
                Button(
                    onClick = onStartAll,
                    enabled = !allActive
                ) {
                    Text("Start All")
                }
            }
        }
    }
}

@Composable
private fun SensorCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    reading: SensorReading?,
    isDevMode: Boolean,
    bufferFill: Float,
    sampleCount: Long,
    samplingFrequency: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle() }
                )
            }

            // User mode: Simple magnitude display
            if (enabled && reading != null) {
                UserModeDisplay(reading)
            }

            // Developer mode: Detailed data
            AnimatedVisibility(
                visible = isDevMode && enabled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                if (reading != null) {
                    DevModeDisplay(
                        reading = reading,
                        bufferFill = bufferFill,
                        sampleCount = sampleCount,
                        samplingFrequency = samplingFrequency
                    )
                }
            }

            // Buffer progress (always visible when active)
            if (enabled) {
                BufferProgressBar(bufferFill)
            }
        }
    }
}

@Composable
private fun UserModeDisplay(reading: SensorReading) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MagnitudeDisplay(
                label = "Magnitude",
                value = reading.magnitude
            )
        }
    }
}

@Composable
private fun DevModeDisplay(
    reading: SensorReading,
    bufferFill: Float,
    sampleCount: Long,
    samplingFrequency: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Raw values
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Raw Values",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AxisValue("X", reading.x)
                    AxisValue("Y", reading.y)
                    AxisValue("Z", reading.z)
                }
            }
        }

        // Statistics
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("Samples", sampleCount.toString())
                    StatItem("Rate", "${samplingFrequency} Hz")
                    StatItem("Buffer", "${(bufferFill * 100).toInt()}%")
                }
            }
        }
    }
}

@Composable
private fun AxisValue(axis: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = axis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format("%+.3f", value),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MagnitudeDisplay(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = String.format("%.2f", value),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun BufferProgressBar(fill: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Buffer",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "${(fill * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
        }
        LinearProgressIndicator(
            progress = fill,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = if (fill >= 1f)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DevInfoCard(viewModel: SensorsViewModel) {
    val accelInfo = viewModel.getAccelerometerInfo()
    val gyroInfo = viewModel.getGyroscopeInfo()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sensor Hardware Info",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            if (accelInfo != null) {
                Text(
                    text = "Accel: ${accelInfo.name}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "  Max: ${accelInfo.maxFrequencyHz} Hz, Range: ±${String.format("%.1f", accelInfo.maxRange)} m/s²",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (gyroInfo != null) {
                Text(
                    text = "Gyro: ${gyroInfo.name}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "  Max: ${gyroInfo.maxFrequencyHz} Hz, Range: ±${String.format("%.0f", gyroInfo.maxRangeDegPerSec)}°/s",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
