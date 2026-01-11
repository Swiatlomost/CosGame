package com.cosgame.costrack.ui.touch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosgame.costrack.touch.MissionState
import com.cosgame.costrack.touch.TouchMissionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchMissionsScreen(
    onBack: () -> Unit,
    viewModel: TouchMissionsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Touch Missions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (uiState.missionState) {
            MissionState.IDLE -> {
                MissionSelector(
                    missions = uiState.missions,
                    totalSessions = uiState.totalSessions,
                    selectedLabel = uiState.selectedLabel,
                    onLabelChange = { viewModel.setLabel(it) },
                    onSelectMission = { viewModel.startMission(it) },
                    modifier = Modifier.padding(padding)
                )
            }
            MissionState.COUNTDOWN -> {
                CountdownScreen(
                    mission = uiState.currentMission!!,
                    countdownValue = uiState.countdownValue,
                    onCancel = { viewModel.cancelMission() },
                    modifier = Modifier.padding(padding)
                )
            }
            MissionState.RUNNING -> {
                MissionRunningScreen(
                    mission = uiState.currentMission!!,
                    timeRemaining = uiState.timeRemaining,
                    stats = uiState.currentStats,
                    drawingPath = uiState.drawingPath,
                    completedPaths = uiState.completedPaths,
                    onTouchStart = { x, y, p -> viewModel.onTouchStart(x, y, p) },
                    onTouchMove = { x, y, p -> viewModel.onTouchMove(x, y, p) },
                    onTouchEnd = { x, y -> viewModel.onTouchEnd(x, y) },
                    onSizeChanged = { w, h -> viewModel.setScreenDimensions(w, h) },
                    onCancel = { viewModel.cancelMission() },
                    modifier = Modifier.padding(padding)
                )
            }
            MissionState.COMPLETED -> {
                MissionResultScreen(
                    result = uiState.result!!,
                    onDone = { viewModel.reset() },
                    modifier = Modifier.padding(padding)
                )
            }
            MissionState.CANCELLED -> {
                viewModel.reset()
            }
        }
    }
}

@Composable
private fun MissionSelector(
    missions: List<TouchMissionType>,
    totalSessions: Int,
    selectedLabel: String,
    onLabelChange: (String) -> Unit,
    onSelectMission: (TouchMissionType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stats card
        item {
            Card(
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
                    Column {
                        Text(
                            text = "Touch Sessions",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Collected data for training",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "$totalSessions",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Label selector
        item {
            LabelSelector(
                selectedLabel = selectedLabel,
                onLabelChange = onLabelChange
            )
        }

        // Mission cards
        item {
            Text(
                text = "Select Mission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(missions) { mission ->
            MissionCard(
                mission = mission,
                onClick = { onSelectMission(mission) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelSelector(
    selectedLabel: String,
    onLabelChange: (String) -> Unit
) {
    val labels = listOf("left_hand", "right_hand", "relaxed", "stressed", "default")

    Column {
        Text(
            text = "Label for training",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.take(4).forEach { label ->
                FilterChip(
                    selected = selectedLabel == label,
                    onClick = { onLabelChange(label) },
                    label = { Text(label.replace("_", " ")) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissionCard(
    mission: TouchMissionType,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mission.icon,
                fontSize = 36.sp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mission.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = mission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${mission.durationSeconds}s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CountdownScreen(
    mission: TouchMissionType,
    countdownValue: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = mission.icon,
                fontSize = 64.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = mission.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "$countdownValue",
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Get ready!",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(48.dp))

            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun MissionRunningScreen(
    mission: TouchMissionType,
    timeRemaining: Int,
    stats: com.cosgame.costrack.touch.TouchSessionStats,
    drawingPath: List<Pair<Float, Float>>,
    completedPaths: List<List<Pair<Float, Float>>>,
    onTouchStart: (Float, Float, Float) -> Unit,
    onTouchMove: (Float, Float, Float) -> Unit,
    onTouchEnd: (Float, Float) -> Unit,
    onSizeChanged: (Int, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header with timer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${mission.icon} ${mission.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = mission.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Timer
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "$timeRemaining",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Touch canvas
        TouchCanvas(
            drawingPath = drawingPath,
            completedPaths = completedPaths,
            onTouchStart = onTouchStart,
            onTouchMove = onTouchMove,
            onTouchEnd = onTouchEnd,
            onSizeChanged = onSizeChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        )

        // Live stats
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Taps", stats.tapCount.toString())
                StatItem("Swipes", stats.swipeCount.toString())
                StatItem("Pressure", String.format("%.2f", stats.avgPressure))
            }
        }

        // Cancel button
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        ) {
            Icon(Icons.Filled.Close, null)
            Spacer(Modifier.width(4.dp))
            Text("Cancel")
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TouchCanvas(
    drawingPath: List<Pair<Float, Float>>,
    completedPaths: List<List<Pair<Float, Float>>>,
    onTouchStart: (Float, Float, Float) -> Unit,
    onTouchMove: (Float, Float, Float) -> Unit,
    onTouchEnd: (Float, Float) -> Unit,
    onSizeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            )
            .onSizeChanged { size ->
                onSizeChanged(size.width, size.height)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onTouchStart(down.position.x, down.position.y, down.pressure)

                    var lastPosition = down.position
                    var isPressed = true
                    while (isPressed) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.positionChange() != Offset.Zero) {
                                onTouchMove(change.position.x, change.position.y, change.pressure)
                            }
                            lastPosition = change.position
                            change.consume()
                        }
                        isPressed = event.changes.any { it.pressed }
                    }

                    onTouchEnd(lastPosition.x, lastPosition.y)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pathColor = Color(0xFF2196F3)

            // Draw completed paths
            completedPaths.forEach { points ->
                if (points.size > 1) {
                    val path = Path()
                    path.moveTo(points.first().first, points.first().second)
                    points.drop(1).forEach { point ->
                        path.lineTo(point.first, point.second)
                    }
                    drawPath(
                        path = path,
                        color = pathColor.copy(alpha = 0.4f),
                        style = Stroke(width = 4f)
                    )
                }
            }

            // Draw current path
            if (drawingPath.size > 1) {
                val path = Path()
                path.moveTo(drawingPath.first().first, drawingPath.first().second)
                drawingPath.drop(1).forEach { point ->
                    path.lineTo(point.first, point.second)
                }
                drawPath(
                    path = path,
                    color = pathColor,
                    style = Stroke(width = 6f)
                )
            }

            // Draw touch point
            drawingPath.lastOrNull()?.let { point ->
                drawCircle(
                    color = Color(0xFFFF5722),
                    radius = 20f,
                    center = Offset(point.first, point.second)
                )
            }
        }
    }
}

@Composable
private fun MissionResultScreen(
    result: com.cosgame.costrack.touch.TouchMissionResult,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✅",
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Mission Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = result.missionType.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Score
        Text(
            text = result.scoreFormatted,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Stats
        Card {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                ResultRow("Taps", result.tapCount.toString())
                ResultRow("Swipes", result.swipeCount.toString())
                ResultRow("Avg Pressure", String.format("%.2f", result.avgPressure))
                ResultRow("Duration", "${result.duration / 1000}s")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Data saved for training",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
