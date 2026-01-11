package com.cosgame.costrack.ui.missions

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Screen for testing personal vs generic model in real-time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    viewModel: TestViewModel = viewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test Model") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopTesting()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Model selection card
            ModelSelectionCard(
                hasPersonalModel = uiState.hasPersonalModel,
                hasGenericModel = uiState.hasGenericModel,
                usePersonalModel = uiState.usePersonalModel,
                onToggle = { viewModel.setUsePersonalModel(it) },
                enabled = !uiState.isRunning
            )

            // Control buttons
            ControlCard(
                isRunning = uiState.isRunning,
                onStart = { viewModel.startTesting() },
                onStop = { viewModel.stopTesting() }
            )

            // Buffer progress
            if (uiState.isRunning && uiState.bufferProgress < 1f) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Collecting sensor data...")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = uiState.bufferProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Current prediction
            if (uiState.isRunning || uiState.currentActivity != "Unknown") {
                PredictionCard(
                    activity = uiState.currentActivity,
                    icon = uiState.currentActivityIcon,
                    confidence = uiState.confidence,
                    modelUsed = uiState.modelUsed,
                    isRunning = uiState.isRunning
                )
            }

            // Probabilities
            if (uiState.probabilities.isNotEmpty()) {
                ProbabilitiesCard(probabilities = uiState.probabilities)
            }

            // Feedback section
            if (uiState.isRunning) {
                FeedbackCard(
                    correctCount = uiState.correctCount,
                    incorrectCount = uiState.incorrectCount,
                    accuracy = uiState.accuracyFromFeedback,
                    onCorrect = { viewModel.provideFeedback(true) },
                    onIncorrect = { viewModel.provideFeedback(false) },
                    onReset = { viewModel.resetFeedback() }
                )
            }
        }
    }
}

@Composable
private fun ModelSelectionCard(
    hasPersonalModel: Boolean,
    hasGenericModel: Boolean,
    usePersonalModel: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Personal model option
                SelectableModelCard(
                    title = "Personal",
                    subtitle = if (hasPersonalModel) "Trained on your data" else "Not trained",
                    icon = "🧠",
                    selected = usePersonalModel && hasPersonalModel,
                    enabled = hasPersonalModel && enabled,
                    onClick = { onToggle(true) },
                    modifier = Modifier.weight(1f)
                )

                // Generic model option
                SelectableModelCard(
                    title = "Generic",
                    subtitle = if (hasGenericModel) "Pre-trained HAR" else "Not available",
                    icon = "🤖",
                    selected = !usePersonalModel || !hasPersonalModel,
                    enabled = hasGenericModel && enabled,
                    onClick = { onToggle(false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectableModelCard(
    title: String,
    subtitle: String,
    icon: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .alpha(if (enabled) 1f else 0.5f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selected) {
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlCard(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (isRunning) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Testing")
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Testing")
                }
            }
        }
    }
}

@Composable
private fun PredictionCard(
    activity: String,
    icon: String,
    confidence: Float,
    modelUsed: String,
    isRunning: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 0.7f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Model badge
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = modelUsed,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = icon,
                fontSize = 64.sp,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = activity,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

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
private fun ProbabilitiesCard(probabilities: Map<String, Float>) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "All Probabilities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            probabilities.entries
                .sortedByDescending { it.value }
                .forEach { (label, prob) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${(prob * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    LinearProgressIndicator(
                        progress = prob,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .padding(bottom = 8.dp)
                    )
                }
        }
    }
}

@Composable
private fun FeedbackCard(
    correctCount: Int,
    incorrectCount: Int,
    accuracy: Float,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
    onReset: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Is prediction correct?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (correctCount + incorrectCount > 0) {
                    TextButton(onClick = onReset) {
                        Text("Reset")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Correct button
                Button(
                    onClick = onCorrect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Correct")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Yes ($correctCount)")
                }

                // Incorrect button
                Button(
                    onClick = onIncorrect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Incorrect")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("No ($incorrectCount)")
                }
            }

            if (correctCount + incorrectCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "User-reported accuracy: ${(accuracy * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
