package com.cosgame.costrack.ui.touch

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosgame.costrack.touch.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TouchLabScreen(
    onBack: () -> Unit,
    viewModel: TouchLabViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Touch Lab") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearData() }) {
                        Icon(Icons.Filled.Clear, "Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Touch Canvas
            TouchCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(16.dp),
                drawingPaths = uiState.drawingPaths,
                currentPath = uiState.currentDrawingPath,
                isCollecting = uiState.isCollecting,
                onTouchEvent = { viewModel.onTouchEvent(it) },
                onSizeChanged = { width, height ->
                    viewModel.setScreenDimensions(width, height)
                }
            )

            // Control buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.activeMission != null) {
                    Button(
                        onClick = { viewModel.stopMission() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Close, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop Mission")
                    }
                } else {
                    Button(
                        onClick = {
                            if (uiState.isCollecting) viewModel.stopCollection()
                            else viewModel.startCollection()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (uiState.isCollecting) Icons.Filled.Close
                            else Icons.Filled.PlayArrow,
                            null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (uiState.isCollecting) "Stop" else "Start")
                    }
                }
            }

            // Mission progress
            uiState.missionProgress?.let { progress ->
                MissionProgressCard(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            // Real-time metrics
            MetricsCard(
                metrics = uiState.metrics,
                touchCount = uiState.touchCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // DNA Profile
            DnaProfileCard(
                profile = uiState.dnaProfile,
                intensityLevel = uiState.intensityLevel,
                isStable = uiState.isStable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Heatmap
            HeatmapCard(
                heatmap = uiState.heatmap,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mission selector
            if (uiState.activeMission == null) {
                MissionSelectorCard(
                    missions = uiState.availableMissions,
                    onSelectMission = { viewModel.startMission(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchCanvas(
    modifier: Modifier = Modifier,
    drawingPaths: List<List<Pair<Float, Float>>>,
    currentPath: List<Pair<Float, Float>>,
    isCollecting: Boolean,
    onTouchEvent: (MotionEvent) -> Unit,
    onSizeChanged: (Int, Int) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCollecting) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 2.dp,
                color = if (isCollecting) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .onSizeChanged { size ->
                onSizeChanged(size.width, size.height)
            }
            .pointerInteropFilter { event ->
                if (isCollecting) {
                    onTouchEvent(event)
                    true
                } else false
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw completed paths
            val pathColor = Color(0xFF2196F3)
            drawingPaths.forEach { points ->
                if (points.size > 1) {
                    val path = Path()
                    path.moveTo(points.first().first, points.first().second)
                    points.drop(1).forEach { point ->
                        path.lineTo(point.first, point.second)
                    }
                    drawPath(
                        path = path,
                        color = pathColor.copy(alpha = 0.5f),
                        style = Stroke(width = 4f)
                    )
                }
            }

            // Draw current path
            if (currentPath.size > 1) {
                val path = Path()
                path.moveTo(currentPath.first().first, currentPath.first().second)
                currentPath.drop(1).forEach { point ->
                    path.lineTo(point.first, point.second)
                }
                drawPath(
                    path = path,
                    color = pathColor,
                    style = Stroke(width = 6f)
                )
            }

            // Draw current touch point
            currentPath.lastOrNull()?.let { point ->
                drawCircle(
                    color = Color(0xFFFF5722),
                    radius = 20f,
                    center = Offset(point.first, point.second)
                )
            }
        }

        // Overlay text when not collecting
        if (!isCollecting) {
            Text(
                text = "Tap Start to begin",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun MetricsCard(
    metrics: TouchMetrics,
    touchCount: Long,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Real-time Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("Touches", touchCount.toString())
                MetricItem("TPM", metrics.formattedTpm)
                MetricItem("Pressure", metrics.formattedPressure)
                MetricItem("Velocity", metrics.formattedVelocity)
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
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
private fun DnaProfileCard(
    profile: TouchDnaProfile,
    intensityLevel: IntensityLevel,
    isStable: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DNA.Touch Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isStable) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "STABLE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Intensity bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Intensity", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${profile.intensityFormatted} (${intensityLevel.displayName})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = profile.intensity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Style", style = MaterialTheme.typography.bodyMedium)
                Text(
                    profile.styleDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Consistency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Consistency", style = MaterialTheme.typography.bodyMedium)
                Text(
                    profile.consistencyFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (profile.totalGestures > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Taps: ${profile.tapCount} | Swipes: ${profile.swipeCount} | Draws: ${profile.drawCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeatmapCard(
    heatmap: ScreenHeatmap,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Screen Heatmap",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3x3 grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (col in 0..2) {
                            val region = ScreenRegion.fromPosition(row, col)
                            val intensity = heatmap.getIntensity(region)
                            HeatmapCell(
                                intensity = intensity,
                                isDominant = region == heatmap.dominantRegion,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (heatmap.totalTouches > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Total touches: ${heatmap.totalTouches}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    intensity: Float,
    isDominant: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(
        red = (intensity * 0.8f + 0.1f),
        green = (0.3f - intensity * 0.2f),
        blue = (0.3f - intensity * 0.2f),
        alpha = intensity * 0.8f + 0.1f
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .then(
                if (isDominant) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(4.dp)
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (intensity > 0.01f) {
            Text(
                text = String.format("%.0f%%", intensity * 100),
                style = MaterialTheme.typography.labelSmall,
                color = if (intensity > 0.3f) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MissionProgressCard(
    progress: TouchMissionProgress,
    modifier: Modifier = Modifier
) {
    val isComplete = progress.isComplete
    val isFailed = progress.isFailed

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isComplete -> MaterialTheme.colorScheme.primaryContainer
                isFailed -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${progress.mission.icon} ${progress.mission.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                when {
                    isComplete -> Text("Complete!", color = MaterialTheme.colorScheme.primary)
                    isFailed -> Text("Failed", color = MaterialTheme.colorScheme.error)
                    else -> progress.timeRemaining?.let { time ->
                        Text("${time / 1000}s left")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = progress.mission.description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = progress.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${progress.currentCount} / ${progress.mission.targetCount}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun MissionSelectorCard(
    missions: List<TouchMission>,
    onSelectMission: (TouchMission) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Touch Missions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Group by type
            val grouped = missions.groupBy { it.type }

            grouped.forEach { (type, typeMissions) ->
                Text(
                    text = "${type.icon} ${type.displayName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(typeMissions) { mission ->
                        MissionChip(
                            mission = mission,
                            onClick = { onSelectMission(mission) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun MissionChip(
    mission: TouchMission,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = mission.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "x${mission.targetCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            mission.timeLimit?.let {
                Text(
                    text = "${it}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
