package com.cosgame.costrack.touch

import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Collects and processes raw touch data from MotionEvents.
 * Converts Android touch events into TouchEvent objects.
 */
class TouchDataCollector {

    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val _currentSequence = MutableStateFlow<List<TouchEvent>>(emptyList())
    val currentSequence: StateFlow<List<TouchEvent>> = _currentSequence.asStateFlow()

    private val _completedSequences = MutableStateFlow<List<TouchSequence>>(emptyList())
    val completedSequences: StateFlow<List<TouchSequence>> = _completedSequences.asStateFlow()

    private val _latestEvent = MutableStateFlow<TouchEvent?>(null)
    val latestEvent: StateFlow<TouchEvent?> = _latestEvent.asStateFlow()

    private val _touchCount = MutableStateFlow(0L)
    val touchCount: StateFlow<Long> = _touchCount.asStateFlow()

    // Screen dimensions for normalization
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    // Session tracking
    private var currentSessionId: Long = 0
    private var eventIdCounter: Long = 0

    // Active finger sequences (for multi-touch)
    private val activeSequences = mutableMapOf<Int, MutableList<TouchEvent>>()

    /**
     * Start collecting touch data.
     */
    fun startCollection(sessionId: Long = System.currentTimeMillis()) {
        currentSessionId = sessionId
        eventIdCounter = 0
        _isCollecting.value = true
        _currentSequence.value = emptyList()
        _completedSequences.value = emptyList()
        activeSequences.clear()
    }

    /**
     * Stop collecting touch data.
     */
    fun stopCollection() {
        // Complete any active sequences
        activeSequences.values.forEach { events ->
            if (events.isNotEmpty()) {
                val sequence = TouchSequence(events.toList())
                _completedSequences.value = _completedSequences.value + sequence
            }
        }
        activeSequences.clear()
        _isCollecting.value = false
    }

    /**
     * Set screen dimensions for normalization.
     */
    fun setScreenDimensions(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    /**
     * Process a MotionEvent and extract touch data.
     * Call this from your View's onTouchEvent or touch modifier.
     */
    fun processTouchEvent(event: MotionEvent): Boolean {
        if (!_isCollecting.value) return false

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchAction.DOWN
            MotionEvent.ACTION_MOVE -> TouchAction.MOVE
            MotionEvent.ACTION_UP -> TouchAction.UP
            MotionEvent.ACTION_POINTER_DOWN -> TouchAction.POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> TouchAction.POINTER_UP
            MotionEvent.ACTION_CANCEL -> TouchAction.CANCEL
            else -> return false
        }

        // Process all active pointers for MOVE events
        if (action == TouchAction.MOVE) {
            for (i in 0 until event.pointerCount) {
                val pointerId = event.getPointerId(i)
                val touchEvent = createTouchEvent(event, i, action, pointerId)
                addEventToSequence(pointerId, touchEvent)
            }
        } else {
            // For other actions, process the specific pointer
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)
            val touchEvent = createTouchEvent(event, pointerIndex, action, pointerId)

            when (action) {
                TouchAction.DOWN, TouchAction.POINTER_DOWN -> {
                    // Start new sequence for this finger
                    activeSequences[pointerId] = mutableListOf(touchEvent)
                    _touchCount.value++
                }
                TouchAction.UP, TouchAction.POINTER_UP, TouchAction.CANCEL -> {
                    // Complete sequence for this finger
                    addEventToSequence(pointerId, touchEvent)
                    completeSequence(pointerId)
                }
                else -> {
                    addEventToSequence(pointerId, touchEvent)
                }
            }
        }

        // Update current sequence (flatten all active sequences)
        _currentSequence.value = activeSequences.values.flatten()

        return true
    }

    private fun createTouchEvent(
        event: MotionEvent,
        pointerIndex: Int,
        action: TouchAction,
        fingerId: Int
    ): TouchEvent {
        return TouchEvent(
            id = eventIdCounter++,
            sessionId = currentSessionId,
            timestamp = event.eventTime,
            x = event.getX(pointerIndex),
            y = event.getY(pointerIndex),
            pressure = event.getPressure(pointerIndex),
            size = event.getSize(pointerIndex),
            action = action,
            fingerId = fingerId,
            fingerCount = event.pointerCount,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        ).also {
            _latestEvent.value = it
        }
    }

    private fun addEventToSequence(fingerId: Int, event: TouchEvent) {
        activeSequences.getOrPut(fingerId) { mutableListOf() }.add(event)
    }

    private fun completeSequence(fingerId: Int) {
        activeSequences.remove(fingerId)?.let { events ->
            if (events.size >= 2) {  // Only save sequences with at least 2 events
                val sequence = TouchSequence(events.toList())
                _completedSequences.value = _completedSequences.value + sequence
            }
        }
    }

    /**
     * Get all completed sequences.
     */
    fun getCompletedSequences(): List<TouchSequence> = _completedSequences.value

    /**
     * Get the latest touch sequence.
     */
    fun getLatestSequence(): TouchSequence? = _completedSequences.value.lastOrNull()

    /**
     * Clear all collected data.
     */
    fun clear() {
        _currentSequence.value = emptyList()
        _completedSequences.value = emptyList()
        _latestEvent.value = null
        _touchCount.value = 0
        activeSequences.clear()
        eventIdCounter = 0
    }

    /**
     * Get statistics about collected touches.
     */
    fun getStats(): TouchCollectionStats {
        val sequences = _completedSequences.value
        return TouchCollectionStats(
            totalTouches = _touchCount.value,
            totalSequences = sequences.size,
            totalEvents = sequences.sumOf { it.eventCount },
            averageSequenceDuration = if (sequences.isNotEmpty())
                sequences.map { it.duration }.average().toLong() else 0,
            averageVelocity = if (sequences.isNotEmpty())
                sequences.map { it.averageVelocity.toDouble() }.average().toFloat() else 0f,
            averagePressure = if (sequences.isNotEmpty())
                sequences.map { it.averagePressure.toDouble() }.average().toFloat() else 0f
        )
    }
}

/**
 * Statistics about touch collection.
 */
data class TouchCollectionStats(
    val totalTouches: Long,
    val totalSequences: Int,
    val totalEvents: Int,
    val averageSequenceDuration: Long,
    val averageVelocity: Float,
    val averagePressure: Float
)
