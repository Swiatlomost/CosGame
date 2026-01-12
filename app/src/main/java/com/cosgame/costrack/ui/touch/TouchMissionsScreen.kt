package com.cosgame.costrack.ui.touch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.cosgame.costrack.touch.TouchSessionStats

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
                title = { Text("Touch Intelligence") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tab Row
            TabRow(
                selectedTabIndex = uiState.currentTab.ordinal
            ) {
                Tab(
                    selected = uiState.currentTab == TouchTab.COLLECT,
                    onClick = { viewModel.selectTab(TouchTab.COLLECT) },
                    text = { Text("Collect") },
                    icon = { Icon(Icons.Filled.Add, null) }
                )
                Tab(
                    selected = uiState.currentTab == TouchTab.TRAIN,
                    onClick = { viewModel.selectTab(TouchTab.TRAIN) },
                    text = { Text("Train") },
                    icon = { Icon(Icons.Filled.Build, null) }
                )
                Tab(
                    selected = uiState.currentTab == TouchTab.TEST,
                    onClick = { viewModel.selectTab(TouchTab.TEST) },
                    text = { Text("Test") },
                    icon = { Icon(Icons.Filled.PlayArrow, null) }
                )
            }

            // Tab Content
            when (uiState.currentTab) {
                TouchTab.COLLECT -> CollectTabContent(uiState, viewModel)
                TouchTab.TRAIN -> TrainTabContent(uiState, viewModel)
                TouchTab.TEST -> TestTabContent(uiState, viewModel)
            }
        }
    }
}

// ==================== COLLECT TAB ====================

@Composable
private fun CollectTabContent(
    uiState: TouchMissionsUiState,
    viewModel: TouchMissionsViewModel
) {
    when (uiState.missionState) {
        MissionState.IDLE -> {
            MissionSelector(
                missions = uiState.missions,
                totalSessions = uiState.totalSessions,
                labelCounts = uiState.labelCounts,
                selectedLabel = uiState.selectedLabel,
                categories = uiState.categories,
                onLabelChange = { viewModel.setLabel(it) },
                onSelectMission = { viewModel.startMission(it) }
            )
        }
        MissionState.COUNTDOWN -> {
            CountdownScreen(
                mission = uiState.currentMission!!,
                countdownValue = uiState.countdownValue,
                onCancel = { viewModel.cancelMission() }
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
                onCancel = { viewModel.cancelMission() }
            )
        }
        MissionState.COMPLETED -> {
            MissionResultScreen(
                result = uiState.result!!,
                onDone = { viewModel.reset() }
            )
        }
        MissionState.CANCELLED -> {
            viewModel.reset()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissionSelector(
    missions: List<TouchMissionType>,
    totalSessions: Int,
    labelCounts: Map<String, Int>,
    selectedLabel: String,
    categories: List<com.cosgame.costrack.learn.Category>,
    onLabelChange: (String) -> Unit,
    onSelectMission: (TouchMissionType) -> Unit
) {
    LazyColumn(
        modifier = Modifier
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

                    if (labelCounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            labelCounts.forEach { (label, count) ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text("$label: $count") }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Label selector
        item {
            LabelSelector(
                selectedLabel = selectedLabel,
                categories = categories,
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
    categories: List<com.cosgame.costrack.learn.Category>,
    onLabelChange: (String) -> Unit
) {
    Column {
        Text(
            text = "Category for training",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (categories.isEmpty()) {
            Text(
                text = "No categories defined. Go to Learn > My Categories to add some.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.take(4).forEach { category ->
                    FilterChip(
                        selected = selectedLabel == category.name,
                        onClick = { onLabelChange(category.name) },
                        label = { Text(category.name.replace("_", " "), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Show more categories if there are more than 4
            if (categories.size > 4) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.drop(4).take(4).forEach { category ->
                        FilterChip(
                            selected = selectedLabel == category.name,
                            onClick = { onLabelChange(category.name) },
                            label = { Text(category.name.replace("_", " "), fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = mission.icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(12.dp))
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
    onCancel: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = mission.icon, fontSize = 64.sp)
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
            Text(text = "Get ready!", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(48.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun MissionRunningScreen(
    mission: TouchMissionType,
    timeRemaining: Int,
    stats: TouchSessionStats,
    drawingPath: List<Pair<Float, Float>>,
    completedPaths: List<List<Pair<Float, Float>>>,
    onTouchStart: (Float, Float, Float) -> Unit,
    onTouchMove: (Float, Float, Float) -> Unit,
    onTouchEnd: (Float, Float) -> Unit,
    onSizeChanged: (Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "$timeRemaining",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Canvas
        TouchCanvas(
            drawingPath = drawingPath,
            completedPaths = completedPaths,
            onTouchStart = onTouchStart,
            onTouchMove = onTouchMove,
            onTouchEnd = onTouchEnd,
            onSizeChanged = onSizeChanged,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp)
        )

        // Stats
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Taps", stats.tapCount.toString())
                StatItem("Swipes", stats.swipeCount.toString())
                StatItem("Pressure", String.format("%.2f", stats.avgPressure))
            }
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
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
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .onSizeChanged { onSizeChanged(it.width, it.height) }
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

            completedPaths.forEach { points ->
                if (points.size > 1) {
                    val path = Path()
                    path.moveTo(points.first().first, points.first().second)
                    points.drop(1).forEach { path.lineTo(it.first, it.second) }
                    drawPath(path, pathColor.copy(alpha = 0.4f), style = Stroke(width = 4f))
                }
            }

            if (drawingPath.size > 1) {
                val path = Path()
                path.moveTo(drawingPath.first().first, drawingPath.first().second)
                drawingPath.drop(1).forEach { path.lineTo(it.first, it.second) }
                drawPath(path, pathColor, style = Stroke(width = 6f))
            }

            drawingPath.lastOrNull()?.let {
                drawCircle(Color(0xFFFF5722), 20f, Offset(it.first, it.second))
            }
        }
    }
}

@Composable
private fun MissionResultScreen(
    result: com.cosgame.costrack.touch.TouchMissionResult,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Mission Complete!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = result.missionType.displayName, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = result.scoreFormatted,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
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
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ==================== TRAIN TAB ====================

@Composable
private fun TrainTabContent(
    uiState: TouchMissionsUiState,
    viewModel: TouchMissionsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Data Overview
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Training Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DataStatItem("Total", uiState.totalSessions.toString())
                        DataStatItem("Labeled", uiState.labeledSessions.toString())
                        DataStatItem("Labels", uiState.labels.size.toString())
                    }

                    if (uiState.labelCounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        uiState.labelCounts.forEach { (label, count) ->
                            LinearProgressIndicator(
                                progress = count.toFloat() / uiState.totalSessions.coerceAtLeast(1),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            )
                            Text(
                                text = "$label: $count samples",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Model Status
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Neural Network",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    uiState.modelInfo?.let { info ->
                        Text(
                            text = "Architecture: ${info.architecture}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Status:")
                            Text(
                                text = if (info.isTrained) "Trained" else "Not trained",
                                color = if (info.isTrained)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (info.isTrained) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Epochs:")
                                Text(text = "${info.epochs}")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Classes:")
                                Text(text = info.classLabels.joinToString(", "))
                            }
                        }
                    }
                }
            }
        }

        // Training Controls
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Train Model",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Epochs slider
                    Text(text = "Epochs: ${uiState.trainingEpochs}")
                    Slider(
                        value = uiState.trainingEpochs.toFloat(),
                        onValueChange = { viewModel.setTrainingEpochs(it.toInt()) },
                        valueRange = 5f..100f,
                        steps = 18
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Training progress
                    when (uiState.trainingState) {
                        TrainingState.TRAINING -> {
                            LinearProgressIndicator(
                                progress = uiState.trainingProgress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.trainingMessage,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (uiState.currentAccuracy > 0) {
                                Text(
                                    text = "Accuracy: ${String.format("%.1f", uiState.currentAccuracy * 100)}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        TrainingState.COMPLETED -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = uiState.trainingMessage)
                                }
                            }
                        }
                        TrainingState.ERROR -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = uiState.trainingMessage)
                                }
                            }
                        }
                        else -> {}
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startTraining() },
                            enabled = uiState.trainingState != TrainingState.TRAINING &&
                                    uiState.labeledSessions >= 10,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Train")
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetModel() },
                            enabled = uiState.modelInfo?.isTrained == true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }

                    if (uiState.labeledSessions < 10) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Need at least 10 labeled sessions to train (have ${uiState.labeledSessions})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
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

// ==================== TEST TAB ====================

@Composable
private fun TestTabContent(
    uiState: TouchMissionsUiState,
    viewModel: TouchMissionsViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Model status
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.modelInfo?.isTrained == true)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (uiState.modelInfo?.isTrained == true)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Filled.Warning,
                    null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.modelInfo?.isTrained == true)
                        "Model ready - ${uiState.modelInfo.classLabels.joinToString(", ")}"
                    else
                        "Model not trained - go to Train tab first"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Instructions
        Text(
            text = if (uiState.isTestRunning)
                "Draw on the canvas, then tap Stop to classify"
            else
                "Tap Start, draw patterns, then Stop to see prediction",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Canvas
        TouchCanvas(
            drawingPath = uiState.testDrawingPath,
            completedPaths = uiState.testCompletedPaths,
            onTouchStart = { x, y, p -> viewModel.onTestTouchStart(x, y, p) },
            onTouchMove = { x, y, p -> viewModel.onTestTouchMove(x, y, p) },
            onTouchEnd = { x, y -> viewModel.onTestTouchEnd(x, y) },
            onSizeChanged = { w, h -> viewModel.setScreenDimensions(w, h) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live stats
        if (uiState.isTestRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Taps", uiState.testStats.tapCount.toString())
                StatItem("Swipes", uiState.testStats.swipeCount.toString())
                StatItem("Events", uiState.testStats.totalEvents.toString())
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Result
        uiState.testResult?.let { result ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Prediction",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.label.replace("_", " ").uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (result.confidence > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Confidence: ${String.format("%.1f", result.confidence * 100)}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (result.probabilities.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        uiState.modelInfo?.classLabels?.forEachIndexed { index, label ->
                            if (index < result.probabilities.size) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.width(80.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    LinearProgressIndicator(
                                        progress = result.probabilities[index],
                                        modifier = Modifier.weight(1f).height(8.dp)
                                    )
                                    Text(
                                        text = "${(result.probabilities[index] * 100).toInt()}%",
                                        modifier = Modifier.width(40.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.End
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.isTestRunning) {
                Button(
                    onClick = { viewModel.stopTest() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Close, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop & Classify")
                }
            } else {
                Button(
                    onClick = { viewModel.startTest() },
                    enabled = uiState.modelInfo?.isTrained == true,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }

                if (uiState.testResult != null) {
                    OutlinedButton(
                        onClick = { viewModel.clearTestResult() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Clear, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }
    }
}
