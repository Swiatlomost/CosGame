package com.cosgame.costrack.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.learn.*
import com.cosgame.costrack.touch.TouchFeatureExtractor
import com.cosgame.costrack.touch.TouchRepository
import com.cosgame.costrack.touch.TouchSessionCollector
import com.cosgame.costrack.training.TrainingDatabase
import com.cosgame.costrack.training.TrainingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Training state enum.
 */
enum class CategoryTrainingState {
    IDLE,
    LOADING,
    TRAINING,
    COMPLETED,
    ERROR
}

/**
 * UI State for category training.
 */
data class CategoryTrainingUiState(
    val categories: List<Category> = emptyList(),
    val trainingState: CategoryTrainingState = CategoryTrainingState.IDLE,
    val progress: Float = 0f,
    val currentEpoch: Int = 0,
    val totalEpochs: Int = 50,
    val touchAccuracy: Float = 0f,
    val movementAccuracy: Float = 0f,
    val message: String = "",
    val hasTouchModel: Boolean = false,
    val hasMovementModel: Boolean = false,
    val touchSamplesReady: Boolean = false,
    val movementSamplesReady: Boolean = false
)

/**
 * ViewModel for training SensorClassifiers on user categories.
 */
class CategoryTrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TrainingDatabase.getInstance(application)
    private val categoryRepository = CategoryRepository(database.categoryDao())
    private val touchRepository = TouchRepository(database.touchSessionDao())
    private val trainingRepository = TrainingRepository.getInstance(application)

    // Classifiers
    private var touchClassifier: TouchSensorClassifier? = null
    private var accelClassifier: AccelerometerSensorClassifier? = null
    private var gyroClassifier: GyroscopeSensorClassifier? = null
    private var metaClassifier: MetaClassifier? = null

    private val featureExtractor = TouchFeatureExtractor()

    private val _uiState = MutableStateFlow(CategoryTrainingUiState())
    val uiState: StateFlow<CategoryTrainingUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        checkModelsExist()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.ensureDefaults()
            val categories = categoryRepository.getAllCategories()
            _uiState.value = _uiState.value.copy(categories = categories)
            checkDataReadiness()
        }
    }

    private fun checkModelsExist() {
        val context = getApplication<Application>()
        touchClassifier = TouchSensorClassifier(context)
        accelClassifier = AccelerometerSensorClassifier(context)
        gyroClassifier = GyroscopeSensorClassifier(context)

        _uiState.value = _uiState.value.copy(
            hasTouchModel = touchClassifier?.hasTrainedModel() ?: false,
            hasMovementModel = accelClassifier?.hasTrainedModel() ?: false
        )
    }

    private fun checkDataReadiness() {
        viewModelScope.launch {
            val categories = _uiState.value.categories
            if (categories.size < 2) {
                _uiState.value = _uiState.value.copy(
                    touchSamplesReady = false,
                    movementSamplesReady = false
                )
                return@launch
            }

            // Check touch samples - need at least 10 per category
            var touchReady = true
            var movementReady = true

            for (category in categories) {
                if (category.useTouch && category.touchSamples < 10) {
                    touchReady = false
                }
                if ((category.useAccelerometer || category.useGyroscope) && category.movementSamples < 10) {
                    movementReady = false
                }
            }

            _uiState.value = _uiState.value.copy(
                touchSamplesReady = touchReady,
                movementSamplesReady = movementReady
            )
        }
    }

    fun trainTouchClassifier() {
        if (_uiState.value.trainingState == CategoryTrainingState.TRAINING) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                trainingState = CategoryTrainingState.LOADING,
                message = "Loading touch data..."
            )

            try {
                val result = withContext(Dispatchers.Default) {
                    trainTouchModel()
                }

                _uiState.value = _uiState.value.copy(
                    trainingState = if (result.first) CategoryTrainingState.COMPLETED else CategoryTrainingState.ERROR,
                    touchAccuracy = result.second,
                    hasTouchModel = result.first,
                    message = if (result.first) "Touch model trained! Accuracy: ${(result.second * 100).toInt()}%" else result.third
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    trainingState = CategoryTrainingState.ERROR,
                    message = "Error: ${e.message}"
                )
            }
        }
    }

    private suspend fun trainTouchModel(): Triple<Boolean, Float, String> {
        val categories = categoryRepository.getAllCategories()
        if (categories.size < 2) {
            return Triple(false, 0f, "Need at least 2 categories")
        }

        val categoryNames = categories.map { it.name }

        // Load touch sessions
        val sessions = touchRepository.getLabeledSessionsForTraining()
        val validSessions = sessions.filter { it.label in categoryNames }

        if (validSessions.size < 20) {
            return Triple(false, 0f, "Need at least 20 labeled touch sessions (have ${validSessions.size})")
        }

        // Extract features
        val samples = mutableListOf<FloatArray>()
        val labels = mutableListOf<Int>()

        for (session in validSessions) {
            val labelIndex = categoryNames.indexOf(session.label)
            if (labelIndex < 0) continue

            val events = TouchSessionCollector.eventsFromJson(session.touchEventsJson)
            val features = featureExtractor.extractFeatures(events, session.duration)

            if (features.size == 24) {
                samples.add(features)
                labels.add(labelIndex)
            }
        }

        if (samples.isEmpty()) {
            return Triple(false, 0f, "No valid touch samples found")
        }

        // Create and train classifier
        val context = getApplication<Application>()
        val classifier = TouchSensorClassifier(context)
        classifier.setClassLabels(categoryNames)

        val epochs = _uiState.value.totalEpochs
        var finalAccuracy = 0f

        _uiState.value = _uiState.value.copy(
            trainingState = CategoryTrainingState.TRAINING,
            message = "Training touch model..."
        )

        for (epoch in 1..epochs) {
            // Shuffle data
            val indices = samples.indices.shuffled()
            val shuffledSamples = indices.map { samples[it] }
            val shuffledLabels = indices.map { labels[it] }

            classifier.trainBatch(shuffledSamples, shuffledLabels)

            // Calculate accuracy
            var correct = 0
            for (i in samples.indices) {
                val result = classifier.predict(samples[i])
                if (result.classIndex == labels[i]) correct++
            }
            finalAccuracy = correct.toFloat() / samples.size

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    progress = epoch.toFloat() / epochs,
                    currentEpoch = epoch,
                    touchAccuracy = finalAccuracy,
                    message = "Epoch $epoch/$epochs - Accuracy: ${(finalAccuracy * 100).toInt()}%"
                )
            }
        }

        classifier.saveModel()
        touchClassifier = classifier

        return Triple(true, finalAccuracy, "Success")
    }

    fun trainMovementClassifier() {
        if (_uiState.value.trainingState == CategoryTrainingState.TRAINING) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                trainingState = CategoryTrainingState.LOADING,
                message = "Loading movement data..."
            )

            try {
                val result = withContext(Dispatchers.Default) {
                    trainMovementModel()
                }

                _uiState.value = _uiState.value.copy(
                    trainingState = if (result.first) CategoryTrainingState.COMPLETED else CategoryTrainingState.ERROR,
                    movementAccuracy = result.second,
                    hasMovementModel = result.first,
                    message = if (result.first) "Movement model trained! Accuracy: ${(result.second * 100).toInt()}%" else result.third
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    trainingState = CategoryTrainingState.ERROR,
                    message = "Error: ${e.message}"
                )
            }
        }
    }

    private suspend fun trainMovementModel(): Triple<Boolean, Float, String> {
        val categories = categoryRepository.getAllCategories()
        if (categories.size < 2) {
            return Triple(false, 0f, "Need at least 2 categories")
        }

        val categoryNames = categories.map { it.name }

        // Load movement samples from TrainingRepository
        val samples = trainingRepository.getAllSamplesWithCategory()
        val validSamples = samples.filter { it.category in categoryNames }

        if (validSamples.size < 100) {
            return Triple(false, 0f, "Need at least 100 labeled movement samples (have ${validSamples.size})")
        }

        // Group by category for feature extraction
        val features = mutableListOf<FloatArray>()
        val labels = mutableListOf<Int>()

        // Create windows from samples (50 samples per window = 1 second at 50Hz)
        val windowSize = 50
        val windowsByCat = categoryNames.associateWith { mutableListOf<List<com.cosgame.costrack.training.TrainingSample>>() }

        val samplesByCategory = validSamples.groupBy { it.category }
        for ((catName, catSamples) in samplesByCategory) {
            // Create windows
            for (i in 0 until (catSamples.size - windowSize) step (windowSize / 2)) {
                val window = catSamples.subList(i, i + windowSize)
                windowsByCat[catName]?.add(window)
            }
        }

        // Extract features from windows
        for ((catName, windows) in windowsByCat) {
            val labelIndex = categoryNames.indexOf(catName)
            if (labelIndex < 0) continue

            for (window in windows) {
                val windowFeatures = extractMovementFeatures(window)
                features.add(windowFeatures)
                labels.add(labelIndex)
            }
        }

        if (features.isEmpty()) {
            return Triple(false, 0f, "No valid movement windows found")
        }

        // Train accelerometer classifier
        val context = getApplication<Application>()
        val classifier = AccelerometerSensorClassifier(context)
        classifier.setClassLabels(categoryNames)

        val epochs = _uiState.value.totalEpochs
        var finalAccuracy = 0f

        _uiState.value = _uiState.value.copy(
            trainingState = CategoryTrainingState.TRAINING,
            message = "Training movement model..."
        )

        for (epoch in 1..epochs) {
            val indices = features.indices.shuffled()
            val shuffledFeatures = indices.map { features[it] }
            val shuffledLabels = indices.map { labels[it] }

            classifier.trainBatch(shuffledFeatures, shuffledLabels)

            var correct = 0
            for (i in features.indices) {
                val result = classifier.predict(features[i])
                if (result.classIndex == labels[i]) correct++
            }
            finalAccuracy = correct.toFloat() / features.size

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    progress = epoch.toFloat() / epochs,
                    currentEpoch = epoch,
                    movementAccuracy = finalAccuracy,
                    message = "Epoch $epoch/$epochs - Accuracy: ${(finalAccuracy * 100).toInt()}%"
                )
            }
        }

        classifier.saveModel()
        accelClassifier = classifier

        return Triple(true, finalAccuracy, "Success")
    }

    private fun extractMovementFeatures(samples: List<com.cosgame.costrack.training.TrainingSample>): FloatArray {
        // Extract 18 features for accelerometer
        val accX = samples.map { it.accX }
        val accY = samples.map { it.accY }
        val accZ = samples.map { it.accZ }

        return floatArrayOf(
            // Mean
            accX.average().toFloat(),
            accY.average().toFloat(),
            accZ.average().toFloat(),
            // Std
            accX.std(),
            accY.std(),
            accZ.std(),
            // Min
            accX.minOrNull() ?: 0f,
            accY.minOrNull() ?: 0f,
            accZ.minOrNull() ?: 0f,
            // Max
            accX.maxOrNull() ?: 0f,
            accY.maxOrNull() ?: 0f,
            accZ.maxOrNull() ?: 0f,
            // Range
            (accX.maxOrNull() ?: 0f) - (accX.minOrNull() ?: 0f),
            (accY.maxOrNull() ?: 0f) - (accY.minOrNull() ?: 0f),
            (accZ.maxOrNull() ?: 0f) - (accZ.minOrNull() ?: 0f),
            // Energy
            accX.map { it * it }.average().toFloat(),
            accY.map { it * it }.average().toFloat(),
            accZ.map { it * it }.average().toFloat()
        )
    }

    private fun List<Float>.std(): Float {
        val mean = this.average()
        val variance = this.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }

    fun deleteModels() {
        touchClassifier?.reset()
        accelClassifier?.reset()
        gyroClassifier?.reset()

        _uiState.value = _uiState.value.copy(
            hasTouchModel = false,
            hasMovementModel = false,
            touchAccuracy = 0f,
            movementAccuracy = 0f,
            trainingState = CategoryTrainingState.IDLE,
            message = "Models deleted"
        )
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(
            trainingState = CategoryTrainingState.IDLE,
            message = ""
        )
    }

    fun refresh() {
        loadCategories()
        checkModelsExist()
    }
}
