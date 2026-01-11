package com.cosgame.costrack.touch

/**
 * Generates touch heatmaps from event data.
 * Grid size: 10 columns x 20 rows = 200 cells
 */
class TouchHeatmapGenerator {

    companion object {
        const val GRID_COLS = 10
        const val GRID_ROWS = 20
        const val GRID_SIZE = GRID_COLS * GRID_ROWS  // 200
    }

    /**
     * Generate heatmap from touch events.
     * Returns normalized FloatArray (0-1) of size 200.
     */
    fun generate(events: List<TouchEvent>): FloatArray {
        val counts = IntArray(GRID_SIZE)

        events.forEach { event ->
            val cellIndex = event.getZone10x20()
            if (cellIndex in 0 until GRID_SIZE) {
                counts[cellIndex]++
            }
        }

        // Normalize to 0-1
        val maxCount = counts.maxOrNull() ?: 1
        val normalized = FloatArray(GRID_SIZE)

        if (maxCount > 0) {
            for (i in counts.indices) {
                normalized[i] = counts[i].toFloat() / maxCount
            }
        }

        return normalized
    }

    /**
     * Generate heatmap from TouchSession.
     */
    fun generate(session: TouchSession): FloatArray {
        val events = TouchSessionCollector.eventsFromJson(session.touchEventsJson)
        return generate(events)
    }

    /**
     * Generate weighted heatmap (weights by pressure).
     */
    fun generateWeighted(events: List<TouchEvent>): FloatArray {
        val weights = FloatArray(GRID_SIZE)

        events.forEach { event ->
            val cellIndex = event.getZone10x20()
            if (cellIndex in 0 until GRID_SIZE) {
                weights[cellIndex] += event.pressure
            }
        }

        // Normalize to 0-1
        val maxWeight = weights.maxOrNull() ?: 1f
        val normalized = FloatArray(GRID_SIZE)

        if (maxWeight > 0) {
            for (i in weights.indices) {
                normalized[i] = weights[i] / maxWeight
            }
        }

        return normalized
    }

    /**
     * Generate heatmap for specific event types only.
     */
    fun generateForType(events: List<TouchEvent>, eventType: TouchEventType): FloatArray {
        val filtered = events.filter { it.eventType == eventType }
        return generate(filtered)
    }

    /**
     * Get value at specific grid position.
     */
    fun getValue(heatmap: FloatArray, row: Int, col: Int): Float {
        val index = row * GRID_COLS + col
        return if (index in heatmap.indices) heatmap[index] else 0f
    }

    /**
     * Get row for a cell index.
     */
    fun getRow(index: Int): Int = index / GRID_COLS

    /**
     * Get column for a cell index.
     */
    fun getCol(index: Int): Int = index % GRID_COLS

    /**
     * Find hotspot (cell with max activity).
     */
    fun findHotspot(heatmap: FloatArray): Pair<Int, Int>? {
        val maxIndex = heatmap.indices.maxByOrNull { heatmap[it] } ?: return null
        return Pair(getRow(maxIndex), getCol(maxIndex))
    }

    /**
     * Calculate heatmap statistics.
     */
    fun getStats(heatmap: FloatArray): HeatmapStats {
        val nonZeroCells = heatmap.count { it > 0 }
        val coverage = nonZeroCells.toFloat() / GRID_SIZE
        val mean = if (nonZeroCells > 0) heatmap.sum() / nonZeroCells else 0f
        val max = heatmap.maxOrNull() ?: 0f
        val hotspot = findHotspot(heatmap)

        // Determine dominant region (top/middle/bottom, left/center/right)
        val topSum = (0 until 7).sumOf { row ->
            (0 until GRID_COLS).sumOf { col -> getValue(heatmap, row, col).toDouble() }
        }.toFloat()
        val midSum = (7 until 14).sumOf { row ->
            (0 until GRID_COLS).sumOf { col -> getValue(heatmap, row, col).toDouble() }
        }.toFloat()
        val botSum = (14 until 20).sumOf { row ->
            (0 until GRID_COLS).sumOf { col -> getValue(heatmap, row, col).toDouble() }
        }.toFloat()

        val leftSum = (0 until GRID_ROWS).sumOf { row ->
            (0 until 3).sumOf { col -> getValue(heatmap, row, col).toDouble() }
        }.toFloat()
        val centerSum = (0 until GRID_ROWS).sumOf { row ->
            (3 until 7).sumOf { col -> getValue(heatmap, row, col).toDouble() }
        }.toFloat()
        val rightSum = (0 until GRID_ROWS).sumOf { row ->
            (7 until 10).sumOf { col -> getValue(heatmap, row, col).toDouble() }
        }.toFloat()

        val verticalRegion = when {
            topSum >= midSum && topSum >= botSum -> "top"
            botSum >= midSum -> "bottom"
            else -> "middle"
        }

        val horizontalRegion = when {
            leftSum >= centerSum && leftSum >= rightSum -> "left"
            rightSum >= centerSum -> "right"
            else -> "center"
        }

        return HeatmapStats(
            coverage = coverage,
            meanIntensity = mean,
            maxIntensity = max,
            hotspotRow = hotspot?.first ?: -1,
            hotspotCol = hotspot?.second ?: -1,
            dominantVertical = verticalRegion,
            dominantHorizontal = horizontalRegion
        )
    }

    /**
     * Combine multiple heatmaps (average).
     */
    fun combine(heatmaps: List<FloatArray>): FloatArray {
        if (heatmaps.isEmpty()) return FloatArray(GRID_SIZE)

        val combined = FloatArray(GRID_SIZE)
        heatmaps.forEach { heatmap ->
            for (i in combined.indices) {
                if (i < heatmap.size) {
                    combined[i] += heatmap[i]
                }
            }
        }

        val count = heatmaps.size.toFloat()
        for (i in combined.indices) {
            combined[i] /= count
        }

        return combined
    }
}

/**
 * Heatmap statistics.
 */
data class HeatmapStats(
    val coverage: Float,           // 0-1, percentage of cells with activity
    val meanIntensity: Float,      // Average intensity of active cells
    val maxIntensity: Float,       // Maximum intensity
    val hotspotRow: Int,           // Row of hotspot (-1 if none)
    val hotspotCol: Int,           // Column of hotspot (-1 if none)
    val dominantVertical: String,  // "top", "middle", "bottom"
    val dominantHorizontal: String // "left", "center", "right"
) {
    val dominantRegion: String get() = "$dominantVertical-$dominantHorizontal"

    val coveragePercent: String get() = String.format("%.1f%%", coverage * 100)
}
