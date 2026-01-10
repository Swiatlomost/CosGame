package com.cosgame.costrack.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosgame.costrack.state.AppMode

/**
 * Home screen showing app overview and quick actions.
 * Adapts UI based on User/Developer mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToSensors: () -> Unit,
    onNavigateToClassifiers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CosGame") },
                actions = {
                    // Mode indicator
                    Surface(
                        color = if (uiState.isDevMode)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (uiState.isDevMode) "Developer" else "User",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
            // Welcome card
            WelcomeCard(isDevMode = uiState.isDevMode)

            // Quick actions grid
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Sensors",
                    icon = "📡",
                    subtitle = "Monitor data",
                    onClick = onNavigateToSensors,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Activity",
                    icon = "🎯",
                    subtitle = "Recognition",
                    onClick = onNavigateToClassifiers,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Settings",
                    icon = "⚙️",
                    subtitle = "Configure",
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f)
                )
                // Placeholder for balance
                Box(modifier = Modifier.weight(1f))
            }

            // Status overview (Developer mode shows more)
            StatusOverviewCard(
                isDevMode = uiState.isDevMode,
                sensorsActive = uiState.sensorsActive,
                classifierReady = uiState.classifierReady,
                currentActivity = uiState.currentActivity
            )

            // Developer mode: additional info
            if (uiState.isDevMode) {
                DevInfoCard()
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WelcomeCard(isDevMode: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎮",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Welcome to CosGame",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isDevMode)
                    "Developer mode active - full access enabled"
                else
                    "Track your activities with sensor data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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
private fun StatusOverviewCard(
    isDevMode: Boolean,
    sensorsActive: Boolean,
    classifierReady: Boolean,
    currentActivity: String?
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            StatusRow(
                label = "Sensors",
                status = if (sensorsActive) "Active" else "Inactive",
                isActive = sensorsActive
            )

            if (isDevMode) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Classifier",
                    status = if (classifierReady) "Ready" else "Not loaded",
                    isActive = classifierReady
                )
            }

            if (currentActivity != null) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Activity",
                    status = currentActivity,
                    isActive = true
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    status: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DevInfoCard() {
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
                text = "Developer Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• HAR Classifier: TFLite model",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Sensors: Accelerometer + Gyroscope",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Buffer: 128 samples @ 50Hz",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Aggregator: DNA with temporal smoothing",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
