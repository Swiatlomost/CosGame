package com.cosgame.costrack.classifier

import org.junit.Assert.*
import org.junit.Test

class ClassificationResultTest {

    @Test
    fun `isHighConfidence returns true above threshold`() {
        val result = ClassificationResult(
            classifierId = "test",
            label = "walking",
            confidence = 0.85f,
            allProbabilities = mapOf("walking" to 0.85f, "sitting" to 0.15f)
        )
        assertTrue(result.isHighConfidence(0.7f))
        assertTrue(result.isHighConfidence(0.85f))
        assertFalse(result.isHighConfidence(0.9f))
    }

    @Test
    fun `isHighConfidence uses default threshold`() {
        val highConf = ClassificationResult(
            classifierId = "test",
            label = "walking",
            confidence = 0.75f,
            allProbabilities = emptyMap()
        )
        val lowConf = ClassificationResult(
            classifierId = "test",
            label = "walking",
            confidence = 0.65f,
            allProbabilities = emptyMap()
        )
        assertTrue(highConf.isHighConfidence()) // default 0.7
        assertFalse(lowConf.isHighConfidence())
    }

    @Test
    fun `topN returns sorted predictions`() {
        val result = ClassificationResult(
            classifierId = "test",
            label = "walking",
            confidence = 0.5f,
            allProbabilities = mapOf(
                "walking" to 0.5f,
                "sitting" to 0.3f,
                "standing" to 0.15f,
                "laying" to 0.05f
            )
        )

        val top2 = result.topN(2)
        assertEquals(2, top2.size)
        assertEquals("walking", top2[0].first)
        assertEquals(0.5f, top2[0].second, 0.001f)
        assertEquals("sitting", top2[1].first)
        assertEquals(0.3f, top2[1].second, 0.001f)
    }

    @Test
    fun `topN handles request larger than available`() {
        val result = ClassificationResult(
            classifierId = "test",
            label = "walking",
            confidence = 0.8f,
            allProbabilities = mapOf("walking" to 0.8f, "sitting" to 0.2f)
        )

        val top5 = result.topN(5)
        assertEquals(2, top5.size) // Only 2 available
    }

    @Test
    fun `timestamp is set automatically`() {
        val before = System.currentTimeMillis()
        val result = ClassificationResult(
            classifierId = "test",
            label = "walking",
            confidence = 0.8f,
            allProbabilities = emptyMap()
        )
        val after = System.currentTimeMillis()

        assertTrue(result.timestamp >= before)
        assertTrue(result.timestamp <= after)
    }

    @Test
    fun `custom timestamp can be provided`() {
        val customTime = 1234567890L
        val result = ClassificationResult(
            classifierId = "test",
            label = "walking",
            confidence = 0.8f,
            allProbabilities = emptyMap(),
            timestamp = customTime
        )
        assertEquals(customTime, result.timestamp)
    }
}

class HarActivityTest {

    @Test
    fun `fromLabel returns correct activity`() {
        assertEquals(HarActivity.WALKING, HarActivity.fromLabel("walking"))
        assertEquals(HarActivity.SITTING, HarActivity.fromLabel("sitting"))
        assertEquals(HarActivity.LAYING, HarActivity.fromLabel("laying"))
    }

    @Test
    fun `fromLabel is case insensitive`() {
        assertEquals(HarActivity.WALKING, HarActivity.fromLabel("WALKING"))
        assertEquals(HarActivity.WALKING, HarActivity.fromLabel("Walking"))
    }

    @Test
    fun `fromLabel returns UNKNOWN for invalid label`() {
        assertEquals(HarActivity.UNKNOWN, HarActivity.fromLabel("running"))
        assertEquals(HarActivity.UNKNOWN, HarActivity.fromLabel(""))
        assertEquals(HarActivity.UNKNOWN, HarActivity.fromLabel("invalid"))
    }

    @Test
    fun `fromIndex returns correct activity`() {
        assertEquals(HarActivity.WALKING, HarActivity.fromIndex(0))
        assertEquals(HarActivity.WALKING_UPSTAIRS, HarActivity.fromIndex(1))
        assertEquals(HarActivity.WALKING_DOWNSTAIRS, HarActivity.fromIndex(2))
        assertEquals(HarActivity.SITTING, HarActivity.fromIndex(3))
        assertEquals(HarActivity.STANDING, HarActivity.fromIndex(4))
        assertEquals(HarActivity.LAYING, HarActivity.fromIndex(5))
    }

    @Test
    fun `fromIndex returns UNKNOWN for invalid index`() {
        assertEquals(HarActivity.UNKNOWN, HarActivity.fromIndex(-1))
        assertEquals(HarActivity.UNKNOWN, HarActivity.fromIndex(100))
    }

    @Test
    fun `standardActivities excludes UNKNOWN`() {
        val activities = HarActivity.standardActivities
        assertEquals(6, activities.size)
        assertFalse(activities.contains(HarActivity.UNKNOWN))
    }

    @Test
    fun `all activities have label and description`() {
        HarActivity.values().forEach { activity ->
            assertTrue(activity.label.isNotEmpty())
            assertTrue(activity.description.isNotEmpty())
        }
    }

    @Test
    fun `activity labels are lowercase`() {
        HarActivity.standardActivities.forEach { activity ->
            assertEquals(activity.label, activity.label.lowercase())
        }
    }
}

class HarClassifierConfigTest {

    @Test
    fun `default config has correct values`() {
        val config = HarClassifierConfig()
        assertEquals("har_model.tflite", config.modelFileName)
        assertEquals(128, config.windowSize)
        assertEquals(0.6f, config.confidenceThreshold, 0.001f)
        assertEquals(500L, config.inferenceIntervalMs)
    }

    @Test
    fun `custom config values work`() {
        val config = HarClassifierConfig(
            modelFileName = "custom_model.tflite",
            windowSize = 64,
            confidenceThreshold = 0.8f,
            inferenceIntervalMs = 1000
        )
        assertEquals("custom_model.tflite", config.modelFileName)
        assertEquals(64, config.windowSize)
        assertEquals(0.8f, config.confidenceThreshold, 0.001f)
        assertEquals(1000L, config.inferenceIntervalMs)
    }
}
