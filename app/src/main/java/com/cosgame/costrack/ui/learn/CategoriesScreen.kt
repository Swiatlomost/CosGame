package com.cosgame.costrack.ui.learn

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosgame.costrack.learn.Category
import com.cosgame.costrack.learn.CategoryRepository
import com.cosgame.costrack.training.TrainingDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for CategoriesScreen.
 */
data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val editingCategory: Category? = null
)

/**
 * ViewModel for CategoriesScreen.
 */
class CategoriesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TrainingDatabase.getInstance(application)
    private val repository = CategoryRepository(database.categoryDao())

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.ensureDefaults()
            val categories = repository.getAllCategories()
            _uiState.value = _uiState.value.copy(
                categories = categories,
                isLoading = false
            )
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun showEditDialog(category: Category) {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            editingCategory = category
        )
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(
            showEditDialog = false,
            editingCategory = null
        )
    }

    fun addCategory(name: String, description: String, useTouch: Boolean, useAccel: Boolean, useGyro: Boolean) {
        viewModelScope.launch {
            repository.insert(Category(
                name = name.lowercase().replace(" ", "_"),
                description = description,
                useTouch = useTouch,
                useAccelerometer = useAccel,
                useGyroscope = useGyro
            ))
            hideAddDialog()
            loadCategories()
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.update(category)
            hideEditDialog()
            loadCategories()
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.delete(category)
            hideEditDialog()
            loadCategories()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() }
            ) {
                Icon(Icons.Filled.Add, "Add Category")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Categories are labels for training data. Each category can use different sensors.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                items(uiState.categories) { category ->
                    CategoryCard(
                        category = category,
                        onClick = { viewModel.showEditDialog(category) }
                    )
                }

                if (uiState.categories.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No categories yet. Tap + to add one.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Add Dialog
        if (uiState.showAddDialog) {
            CategoryDialog(
                title = "Add Category",
                category = null,
                onDismiss = { viewModel.hideAddDialog() },
                onSave = { name, desc, touch, accel, gyro ->
                    viewModel.addCategory(name, desc, touch, accel, gyro)
                },
                onDelete = null
            )
        }

        // Edit Dialog
        if (uiState.showEditDialog && uiState.editingCategory != null) {
            CategoryDialog(
                title = "Edit Category",
                category = uiState.editingCategory,
                onDismiss = { viewModel.hideEditDialog() },
                onSave = { name, desc, touch, accel, gyro ->
                    viewModel.updateCategory(
                        uiState.editingCategory!!.copy(
                            name = name.lowercase().replace(" ", "_"),
                            description = desc,
                            useTouch = touch,
                            useAccelerometer = accel,
                            useGyroscope = gyro
                        )
                    )
                },
                onDelete = { viewModel.deleteCategory(uiState.editingCategory!!) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryCard(
    category: Category,
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
            // Color indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(parseColor(category.color)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.name.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (category.description.isNotEmpty()) {
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    category.sensorList.forEach { sensor ->
                        AssistChip(
                            onClick = { },
                            label = { Text(sensor, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${category.totalSamples}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "samples",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
    title: String,
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Boolean, Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var description by remember { mutableStateOf(category?.description ?: "") }
    var useTouch by remember { mutableStateOf(category?.useTouch ?: true) }
    var useAccel by remember { mutableStateOf(category?.useAccelerometer ?: false) }
    var useGyro by remember { mutableStateOf(category?.useGyroscope ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Sensors to use:",
                    style = MaterialTheme.typography.labelMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = useTouch,
                        onClick = { useTouch = !useTouch },
                        label = { Text("Touch") }
                    )
                    FilterChip(
                        selected = useAccel,
                        onClick = { useAccel = !useAccel },
                        label = { Text("Accel") }
                    )
                    FilterChip(
                        selected = useGyro,
                        onClick = { useGyro = !useGyro },
                        label = { Text("Gyro") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description, useTouch, useAccel, useGyro) },
                enabled = name.isNotBlank() && (useTouch || useAccel || useGyro)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF2196F3)
    }
}
