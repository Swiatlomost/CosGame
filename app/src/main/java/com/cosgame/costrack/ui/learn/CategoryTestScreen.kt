package com.cosgame.costrack.ui.learn

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosgame.costrack.learn.FusionMethod

/**
 * Screen for testing trained category models in real-time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTestScreen(
    viewModel: CategoryTestViewModel = viewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test Categories") },
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
            // Message card
            if (uiState.message.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { viewModel.dismissMessage() }) {
                            Text("OK")
                        }
                    }
                }
            }

            // Model status
            ModelStatusRow(
                hasTouchModel = uiState.hasTouchModel,
                hasMovementModel = uiState.hasMovementModel
            )

            // Main prediction card
            PredictionCard(
                prediction = uiState.combinedPrediction,
                confidence = uiState.combinedConfidence,
                isTesting = uiState.testState == CategoryTestState.TESTING,
                categories = uiState.categories
            )

            // Control buttons
            ControlButtons(
                testState = uiState.testState,
                hasModel = uiState.hasMovementModel || uiState.hasTouchModel,
                onStart = { viewModel.startTesting() },
                onStop = { viewModel.stopTesting() },
                onPause = { viewModel.pauseTesting() },
                onResume = { viewModel.resumeTesting() }
            )

            // Stats card
            if (uiState.testCount > 0) {
                StatsCard(
                    testCount = uiState.testCount,
                    correctCount = uiState.correctCount,
                    onMarkCorrect = { viewModel.markCorrect() },
                    onReset = { viewModel.resetStats() }
                )
            }

            // Fusion method selector
            if (uiState.hasTouchModel && uiState.hasMovementModel) {
                FusionMethodCard(
                    currentMethod = uiState.fusionMethod,
                    onMethodSelected = { viewModel.setFusionMethod(it) }
                )
            }

            // Probabilities card
            uiState.movementPrediction?.let { pred ->
                ProbabilitiesCard(
                    title = "Movement Probabilities",
                    probabilities = pred.probabilities
                )
            }

            uiState.touchPrediction?.let { pred ->
                ProbabilitiesCard(
                    title = "Touch Probabilities",
                    probabilities = pred.probabilities
                )
            }
        }
    }
}

@Composable
private fun ModelStatusRow(
    hasTouchModel: Boolean,
    hasMovementModel: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModelChip(
            label = "Touch",
            icon = "👆",
            hasModel = hasTouchModel,
            modifier = Modifier.weight(1f)
        )
        ModelChip(
            label = "Movement",
            icon = "🏃",
            hasModel = hasMovementModel,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModelChip(
    label: String,
    icon: String,
    hasModel: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (hasModel)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (hasModel) "✓" else "✗",
                color = if (hasModel) Color(0xFF4CAF50) else Color(0xFFE57373)
            )
        }
    }
}

@Composable
private fun PredictionCard(
    prediction: String,
    confidence: Float,
    isTesting: Boolean,
    categories: List<String>
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Predicted Category",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isTesting && prediction.isNotEmpty()) {
                Text(
                    text = prediction.replace("_", " ").uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.scale(scale)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Confidence bar
                LinearProgressIndicator(
                    progress = confidence,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Confidence: ${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (!isTesting) {
                Text(
                    text = "?",
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (categories.isEmpty())
                        "No model trained"
                    else
                        "Press Start to begin testing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else {
                CircularProgressIndicator()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Collecting data...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ControlButtons(
    testState: CategoryTestState,
    hasModel: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (testState) {
            CategoryTestState.IDLE -> {
                Button(
                    onClick = onStart,
                    enabled = hasModel,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Testing")
                }
            }
            CategoryTestState.TESTING -> {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pause")
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
            CategoryTestState.PAUSED -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume")
                }
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    testCount: Int,
    correctCount: Int,
    onMarkCorrect: () -> Unit,
    onReset: () -> Unit
) {
    val accuracy = if (testCount > 0) correctCount.toFloat() / testCount else 0f

    Card {
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
                    text = "Testing Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = testCount.toString(), label = "Tests")
                StatItem(value = correctCount.toString(), label = "Correct")
                StatItem(
                    value = "${(accuracy * 100).toInt()}%",
                    label = "Accuracy"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onMarkCorrect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark Last as Correct")
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FusionMethodCard(
    currentMethod: FusionMethod,
    onMethodSelected: (FusionMethod) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Fusion Method",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FusionMethod.entries.forEach { method ->
                    FilterChip(
                        selected = currentMethod == method,
                        onClick = { onMethodSelected(method) },
                        label = { Text(method.name.replace("_", " "), fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilitiesCard(
    title: String,
    probabilities: Map<String, Float>
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            probabilities.entries.sortedByDescending { it.value }.forEach { (label, prob) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label.replace("_", " "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    LinearProgressIndicator(
                        progress = prob,
                        modifier = Modifier
                            .weight(2f)
                            .height(8.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${(prob * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
