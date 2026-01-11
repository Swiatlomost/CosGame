package com.cosgame.costrack.ui.classifiers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosgame.costrack.classifier.HarActivity

/**
 * Classifiers screen showing HAR classification results.
 * Adapts UI based on User/Developer mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassifiersScreen(
    viewModel: ClassifiersViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Recognition") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Error message
            uiState.error?.let { error ->
                ErrorCard(error = error, onDismiss = viewModel::clearError)
            }

            // Control buttons
            ControlCard(
                isRunning = uiState.isRunning,
                onStart = viewModel::startClassification,
                onStop = viewModel::stopClassification,
                onReset = viewModel::resetAggregator
            )

            // Buffer progress (when collecting data)
            if (uiState.isRunning && uiState.bufferProgress < 1f) {
                BufferCollectingCard(progress = uiState.bufferProgress)
            }

            // Main activity display
            if (uiState.hasResults || uiState.isRunning) {
                ActivityDisplayCard(
                    activity = uiState.currentActivity,
                    confidence = uiState.confidence,
                    isStable = uiState.isStable,
                    isRunning = uiState.isRunning
                )
            }

            // Developer mode: detailed info
            AnimatedVisibility(
                visible = uiState.isDevMode && uiState.hasResults,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Probabilities
                    uiState.aggregatedResult?.let { result ->
                        ProbabilitiesCard(distribution = result.distribution)
                    }

                    // Inference stats
                    uiState.lastClassificationResult?.let { result ->
                        InferenceStatsCard(
                            inferenceTimeMs = result.inferenceTimeMs,
                            classificationCount = uiState.classificationCount,
                            aggregationStrategy = uiState.aggregatedResult?.strategy?.name ?: "N/A",
                            sampleCount = uiState.aggregatedResult?.sampleCount ?: 0
                        )
                    }

                    // Debug logs - Classification
                    DebugLogsCard(
                        title = "Classification Logs",
                        logs = uiState.debugLogs,
                        onCopy = { viewModel.getLogsAsText() },
                        onClear = viewModel::clearLogs
                    )

                    // Debug logs - Raw Sensors
                    DebugLogsCard(
                        title = "Raw Sensor Data",
                        logs = uiState.sensorLogs,
                        onCopy = { viewModel.getSensorLogsAsText() },
                        onClear = viewModel::clearSensorLogs
                    )
                }
            }

            // Placeholder when not running
            if (!uiState.isRunning && !uiState.hasResults) {
                PlaceholderCard()
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
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
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun ControlCard(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (isRunning) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop")
                }
            } else {
                Button(onClick = onStart) {
                    Text("Start Recognition")
                }
            }
            OutlinedButton(onClick = onReset) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun BufferCollectingCard(progress: Float) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Collecting sensor data...",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ActivityDisplayCard(
    activity: HarActivity,
    confidence: Float,
    isStable: Boolean,
    isRunning: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning && !isStable) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isStable)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stability indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isStable) Color(0xFF4CAF50)
                            else Color(0xFFFF9800)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStable) "Stable" else "Detecting...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Activity icon
            Text(
                text = activity.toIcon(),
                fontSize = 64.sp,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Activity name
            Text(
                text = activity.toDisplayName(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Confidence bar
            Column(
                modifier = Modifier.fillMaxWidth(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = confidence,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        confidence >= 0.8f -> Color(0xFF4CAF50)
                        confidence >= 0.6f -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(confidence * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProbabilitiesCard(distribution: Map<String, Float>) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "All Probabilities",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            distribution.entries
                .sortedByDescending { it.value }
                .forEach { (label, prob) ->
                    ProbabilityRow(
                        label = HarActivity.fromLabel(label).toDisplayName(),
                        probability = prob
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
        }
    }
}

@Composable
private fun ProbabilityRow(label: String, probability: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = String.format("%.1f%%", probability * 100),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
        LinearProgressIndicator(
            progress = probability,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
    }
}

@Composable
private fun InferenceStatsCard(
    inferenceTimeMs: Long,
    classificationCount: Long,
    aggregationStrategy: String,
    sampleCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Inference Statistics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("Inference", "${inferenceTimeMs}ms")
                StatColumn("Count", classificationCount.toString())
                StatColumn("Strategy", aggregationStrategy.take(8))
                StatColumn("Samples", sampleCount.toString())
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaceholderCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎯",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ready to recognize your activity",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Press Start to begin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DebugLogsCard(
    title: String = "Debug Logs",
    logs: List<String>,
    onCopy: () -> String,
    onClear: () -> Unit
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$title (${logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    TextButton(onClick = {
                        val logsText = onCopy()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("HAR Logs", logsText))
                        Toast.makeText(context, "Logs copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy")
                    }
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log entries
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No logs yet. Start classification to see results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logs.asReversed().forEach { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
