package com.cosgame.costrack.ui.missions

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

/**
 * Screen for executing an active mission with countdown and data collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveMissionScreen(
    missionId: String,
    viewModel: ActiveMissionViewModel = viewModel(),
    onMissionComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initialize mission only if not already set (survives rotation)
    LaunchedEffect(missionId) {
        if (viewModel.uiState.value.mission == null) {
            val mission = Missions.getById(missionId)
            if (mission != null) {
                viewModel.setMission(mission)
            }
        }
    }

    // Handle completion/cancellation
    LaunchedEffect(uiState.phase) {
        when (uiState.phase) {
            MissionPhase.COMPLETE -> {
                // Stay on screen briefly to show completion
            }
            MissionPhase.CANCELLED -> onCancel()
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.mission?.name ?: "Mission") },
                navigationIcon = {
                    if (uiState.phase != MissionPhase.COLLECTING) {
                        IconButton(onClick = {
                            viewModel.cancelMission()
                            onCancel()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (uiState.phase) {
                MissionPhase.READY -> ReadyPhase(
                    mission = uiState.mission,
                    onStart = { viewModel.startMission() }
                )
                MissionPhase.COUNTDOWN -> CountdownPhase(
                    countdownValue = uiState.countdownValue
                )
                MissionPhase.COLLECTING -> CollectingPhase(
                    mission = uiState.mission,
                    remainingSeconds = uiState.remainingSeconds,
                    samplesCollected = uiState.samplesCollected,
                    progress = uiState.progress,
                    onCancel = { viewModel.cancelMission() }
                )
                MissionPhase.COMPLETE -> CompletePhase(
                    samplesCollected = uiState.samplesCollected,
                    onDone = {
                        viewModel.reset()
                        onMissionComplete()
                    }
                )
                MissionPhase.CANCELLED -> {
                    // Will navigate away
                }
            }
        }
    }
}

@Composable
private fun ReadyPhase(
    mission: Mission?,
    onStart: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = mission?.icon ?: "?",
            fontSize = 80.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = mission?.name ?: "Unknown",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = mission?.description ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Duration: ${mission?.displayDuration}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Instructions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = getInstructions(mission),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Start Mission",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun CountdownPhase(countdownValue: Int) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "countdown_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Get Ready!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(scale)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = countdownValue.toString(),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun CollectingPhase(
    mission: Mission?,
    remainingSeconds: Int,
    samplesCollected: Int,
    progress: Float,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = mission?.icon ?: "?",
            fontSize = 80.sp,
            modifier = Modifier.scale(pulseScale)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = getActivityVerb(mission),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Timer
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = remainingSeconds.toString(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "$samplesCollected samples collected",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Cancel Mission")
        }
    }
}

@Composable
private fun CompletePhase(
    samplesCollected: Int,
    onDone: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "🎉",
            fontSize = 80.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Mission Complete!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = samplesCollected.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "samples collected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun getInstructions(mission: Mission?): String {
    return when (mission?.activityType) {
        com.cosgame.costrack.training.ActivityType.WALKING ->
            "Walk at your normal pace around the room or outside. Keep your phone in your pocket or hand as you normally would."
        com.cosgame.costrack.training.ActivityType.RUNNING ->
            "Jog or run in place, or move around if you have space. Keep your phone secure."
        com.cosgame.costrack.training.ActivityType.SITTING ->
            "Sit comfortably on a chair. You can move naturally but stay seated."
        com.cosgame.costrack.training.ActivityType.STANDING ->
            "Stand still in a relaxed position. Small movements are okay."
        com.cosgame.costrack.training.ActivityType.LAYING ->
            "Lie down comfortably on a bed or couch. Place your phone beside you or on your body."
        else -> "Follow the activity instructions."
    }
}

private fun getActivityVerb(mission: Mission?): String {
    return when (mission?.activityType) {
        com.cosgame.costrack.training.ActivityType.WALKING -> "Keep Walking!"
        com.cosgame.costrack.training.ActivityType.RUNNING -> "Keep Running!"
        com.cosgame.costrack.training.ActivityType.SITTING -> "Stay Seated!"
        com.cosgame.costrack.training.ActivityType.STANDING -> "Stay Standing!"
        com.cosgame.costrack.training.ActivityType.LAYING -> "Stay Laying!"
        else -> "Continue!"
    }
}
