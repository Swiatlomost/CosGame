package com.cosgame.costrack.ui.learn

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Screen for training SensorClassifiers on user categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTrainingScreen(
    viewModel: CategoryTrainingViewModel = viewModel(),
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
                title = { Text("Train Category Models") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                MessageCard(
                    message = uiState.message,
                    isError = uiState.trainingState == CategoryTrainingState.ERROR,
                    onDismiss = { viewModel.dismissMessage() }
                )
            }

            // Training progress
            if (uiState.trainingState == CategoryTrainingState.TRAINING ||
                uiState.trainingState == CategoryTrainingState.LOADING) {
                TrainingProgressCard(
                    progress = uiState.progress,
                    currentEpoch = uiState.currentEpoch,
                    totalEpochs = uiState.totalEpochs,
                    message = uiState.message
                )
            }

            // Categories overview
            CategoriesOverviewCard(categories = uiState.categories)

            // Touch Model Card
            SensorModelCard(
                title = "Touch Model",
                icon = "👆",
                hasModel = uiState.hasTouchModel,
                accuracy = uiState.touchAccuracy,
                isReady = uiState.touchSamplesReady,
                isTraining = uiState.trainingState == CategoryTrainingState.TRAINING,
                onTrain = { viewModel.trainTouchClassifier() },
                readinessMessage = if (uiState.touchSamplesReady) "Ready to train" else "Collect more touch data (10+ per category)"
            )

            // Movement Model Card
            SensorModelCard(
                title = "Movement Model",
                icon = "🏃",
                hasModel = uiState.hasMovementModel,
                accuracy = uiState.movementAccuracy,
                isReady = uiState.movementSamplesReady,
                isTraining = uiState.trainingState == CategoryTrainingState.TRAINING,
                onTrain = { viewModel.trainMovementClassifier() },
                readinessMessage = if (uiState.movementSamplesReady) "Ready to train" else "Collect more movement data (100+ samples per category)"
            )

            // Delete Models
            if (uiState.hasTouchModel || uiState.hasMovementModel) {
                var showDeleteDialog by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All Models")
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete Models?") },
                        text = { Text("This will delete all trained category models. You'll need to retrain them.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.deleteModels()
                                    showDeleteDialog = false
                                }
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer
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
                text = message,
                color = if (isError)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun TrainingProgressCard(
    progress: Float,
    currentEpoch: Int,
    totalEpochs: Int,
    message: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
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
            Text(
                text = "🧠",
                fontSize = 48.sp,
                modifier = Modifier.rotate(rotation)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Training in Progress...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Epoch $currentEpoch / $totalEpochs (${(progress * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CategoriesOverviewCard(categories: List<com.cosgame.costrack.learn.Category>) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (categories.isEmpty()) {
                Text(
                    text = "No categories defined. Go to My Categories to add some.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (categories.size < 2) {
                Text(
                    text = "Need at least 2 categories for training.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.name.replace("_", " "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (category.useTouch) {
                                Chip(text = "👆 ${category.touchSamples}")
                            }
                            if (category.useAccelerometer || category.useGyroscope) {
                                Chip(text = "🏃 ${category.movementSamples}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensorModelCard(
    title: String,
    icon: String,
    hasModel: Boolean,
    accuracy: Float,
    isReady: Boolean,
    isTraining: Boolean,
    onTrain: () -> Unit,
    readinessMessage: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hasModel)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = icon,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (hasModel) "Trained - ${(accuracy * 100).toInt()}% accuracy" else "Not trained",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasModel) {
                    Text(
                        text = "✅",
                        fontSize = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = readinessMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (isReady)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onTrain,
                enabled = isReady && !isTraining,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (hasModel) "Retrain" else "Train")
            }
        }
    }
}
