package com.cosgame.costrack.touch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Collects touch events during a session and calculates statistics.
 */
class TouchSessionCollector {

    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val _events = MutableStateFlow<List<TouchEvent>>(emptyList())
    val events: StateFlow<List<TouchEvent>> = _events.asStateFlow()

    private val _gestures = MutableStateFlow<List<TouchGesture>>(emptyList())
    val gestures: StateFlow<List<TouchGesture>> = _gestures.asStateFlow()

    private val _stats = MutableStateFlow(TouchSessionStats())
    val stats: StateFlow<TouchSessionStats> = _stats.asStateFlow()

    private var sessionStartTime: Long = 0
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    // Active gestures per finger
    private val activeGestures = mutableMapOf<Int, MutableList<TouchEvent>>()

    /**
     * Set screen dimensions for coordinate normalization.
     */
    fun setScreenDimensions(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    /**
     * Start a new collection session.
     */
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        _events.value = emptyList()
        _gestures.value = emptyList()
        _stats.value = TouchSessionStats()
        activeGestures.clear()
        _isCollecting.value = true
    }

    /**
     * Stop collection and finalize session.
     */
    fun stopSession(): TouchSession? {
        if (!_isCollecting.value) return null

        _isCollecting.value = false

        // Complete any active gestures
        activeGestures.forEach { (fingerId, events) ->
            if (events.isNotEmpty()) {
                val gesture = TouchGesture(events.toList(), fingerId)
                _gestures.value = _gestures.value + gesture
            }
        }
        activeGestures.clear()

        // Calculate final stats
        calculateStats()

        val endTime = System.currentTimeMillis()
        val allGestures = _gestures.value
        val stats = _stats.value

        return TouchSession(
            startTime = sessionStartTime,
            endTime = endTime,
            tapCount = stats.tapCount,
            swipeCount = stats.swipeCount,
            totalEvents = stats.totalEvents,
            avgPressure = stats.avgPressure,
            avgTapDuration = stats.avgTapDuration,
            avgSwipeVelocity = stats.avgSwipeVelocity,
            touchEventsJson = eventsToJson(_events.value)
        )
    }

    /**
     * Add a touch event from raw screen coordinates.
     */
    fun addEvent(
        rawX: Float,
        rawY: Float,
        pressure: Float,
        size: Float,
        eventType: TouchEventType,
        fingerId: Int
    ) {
        if (!_isCollecting.value) return

        val event = TouchEvent.fromRaw(
            rawX = rawX,
            rawY = rawY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pressure = pressure,
            size = size,
            eventType = eventType,
            fingerId = fingerId
        )

        _events.value = _events.value + event

        // Track gestures
        when (eventType) {
            TouchEventType.DOWN -> {
                activeGestures[fingerId] = mutableListOf(event)
            }
            TouchEventType.MOVE -> {
                activeGestures[fingerId]?.add(event)
            }
            TouchEventType.UP -> {
                activeGestures[fingerId]?.add(event)
                activeGestures.remove(fingerId)?.let { gestureEvents ->
                    if (gestureEvents.isNotEmpty()) {
                        val gesture = TouchGesture(gestureEvents, fingerId)
                        _gestures.value = _gestures.value + gesture
                        // Update stats after each gesture
                        calculateStats()
                    }
                }
            }
        }
    }

    /**
     * Add a touch event directly (already normalized).
     */
    fun addNormalizedEvent(event: TouchEvent) {
        if (!_isCollecting.value) return

        _events.value = _events.value + event

        when (event.eventType) {
            TouchEventType.DOWN -> {
                activeGestures[event.fingerId] = mutableListOf(event)
            }
            TouchEventType.MOVE -> {
                activeGestures[event.fingerId]?.add(event)
            }
            TouchEventType.UP -> {
                activeGestures[event.fingerId]?.add(event)
                activeGestures.remove(event.fingerId)?.let { gestureEvents ->
                    if (gestureEvents.isNotEmpty()) {
                        val gesture = TouchGesture(gestureEvents, event.fingerId)
                        _gestures.value = _gestures.value + gesture
                        calculateStats()
                    }
                }
            }
        }
    }

    private fun calculateStats() {
        val allGestures = _gestures.value
        val allEvents = _events.value

        if (allEvents.isEmpty()) {
            _stats.value = TouchSessionStats()
            return
        }

        val taps = allGestures.filter { it.isTap }
        val swipes = allGestures.filter { it.isSwipe }

        val avgPressure = allEvents.map { it.pressure }.average().toFloat()
        val avgTapDuration = if (taps.isNotEmpty()) {
            taps.map { it.duration.toFloat() }.average().toFloat()
        } else 0f
        val avgSwipeVelocity = if (swipes.isNotEmpty()) {
            swipes.map { it.velocity }.average().toFloat()
        } else 0f

        // Calculate tap rate (per minute)
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        val tapRate = if (sessionDuration > 0) {
            taps.size * 60000f / sessionDuration
        } else 0f

        // Zone distribution (3x3)
        val zoneDistribution = FloatArray(9)
        val downEvents = allEvents.filter { it.eventType == TouchEventType.DOWN }
        if (downEvents.isNotEmpty()) {
            downEvents.forEach { event ->
                val zone = event.getZone3x3()
                zoneDistribution[zone]++
            }
            // Normalize
            val total = zoneDistribution.sum()
            if (total > 0) {
                for (i in zoneDistribution.indices) {
                    zoneDistribution[i] /= total
                }
            }
        }

        _stats.value = TouchSessionStats(
            tapCount = taps.size,
            swipeCount = swipes.size,
            totalEvents = allEvents.size,
            avgPressure = avgPressure,
            avgTapDuration = avgTapDuration,
            avgSwipeVelocity = avgSwipeVelocity,
            tapRate = tapRate,
            zoneDistribution = zoneDistribution
        )
    }

    /**
     * Clear all data without stopping session.
     */
    fun clear() {
        _events.value = emptyList()
        _gestures.value = emptyList()
        _stats.value = TouchSessionStats()
        activeGestures.clear()
    }

    /**
     * Convert events to JSON string for storage.
     */
    private fun eventsToJson(events: List<TouchEvent>): String {
        val jsonArray = JSONArray()
        events.forEach { event ->
            val obj = JSONObject().apply {
                put("x", event.x)
                put("y", event.y)
                put("p", event.pressure)
                put("s", event.size)
                put("t", event.timestamp)
                put("e", event.eventType.ordinal)
                put("f", event.fingerId)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    companion object {
        /**
         * Parse events from JSON string.
         */
        fun eventsFromJson(json: String): List<TouchEvent> {
            if (json.isEmpty()) return emptyList()

            return try {
                val jsonArray = JSONArray(json)
                (0 until jsonArray.length()).map { i ->
                    val obj = jsonArray.getJSONObject(i)
                    TouchEvent(
                        x = obj.getDouble("x").toFloat(),
                        y = obj.getDouble("y").toFloat(),
                        pressure = obj.getDouble("p").toFloat(),
                        size = obj.getDouble("s").toFloat(),
                        timestamp = obj.getLong("t"),
                        eventType = TouchEventType.entries[obj.getInt("e")],
                        fingerId = obj.getInt("f")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
