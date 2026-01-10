package com.cosgame.costrack.data

import org.junit.Assert.*
import org.junit.Test

class RingBufferTest {

    @Test
    fun `new buffer is empty`() {
        val buffer = RingBuffer<Int>(5)
        assertTrue(buffer.isEmpty)
        assertFalse(buffer.isFull)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `push increases size`() {
        val buffer = RingBuffer<Int>(5)
        buffer.push(1)
        assertEquals(1, buffer.size)
        buffer.push(2)
        assertEquals(2, buffer.size)
    }

    @Test
    fun `buffer becomes full at capacity`() {
        val buffer = RingBuffer<Int>(3)
        buffer.push(1)
        buffer.push(2)
        assertFalse(buffer.isFull)
        buffer.push(3)
        assertTrue(buffer.isFull)
        assertEquals(3, buffer.size)
    }

    @Test
    fun `push overwrites oldest when full`() {
        val buffer = RingBuffer<Int>(3)
        buffer.push(1)
        buffer.push(2)
        buffer.push(3)
        buffer.push(4) // overwrites 1

        assertEquals(3, buffer.size)
        assertEquals(2, buffer[0]) // oldest is now 2
        assertEquals(3, buffer[1])
        assertEquals(4, buffer[2]) // newest
    }

    @Test
    fun `get returns elements in correct order`() {
        val buffer = RingBuffer<String>(5)
        buffer.push("a")
        buffer.push("b")
        buffer.push("c")

        assertEquals("a", buffer[0])
        assertEquals("b", buffer[1])
        assertEquals("c", buffer[2])
    }

    @Test
    fun `peek returns newest element`() {
        val buffer = RingBuffer<Int>(5)
        buffer.push(10)
        assertEquals(10, buffer.peek())
        buffer.push(20)
        assertEquals(20, buffer.peek())
    }

    @Test
    fun `peekOldest returns oldest element`() {
        val buffer = RingBuffer<Int>(3)
        buffer.push(1)
        buffer.push(2)
        buffer.push(3)
        assertEquals(1, buffer.peekOldest())

        buffer.push(4) // overwrites 1
        assertEquals(2, buffer.peekOldest())
    }

    @Test(expected = NoSuchElementException::class)
    fun `peek throws on empty buffer`() {
        val buffer = RingBuffer<Int>(5)
        buffer.peek()
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `get throws on invalid index`() {
        val buffer = RingBuffer<Int>(5)
        buffer.push(1)
        buffer[5]
    }

    @Test
    fun `clear resets buffer`() {
        val buffer = RingBuffer<Int>(5)
        buffer.push(1)
        buffer.push(2)
        buffer.clear()

        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `toList returns all elements in order`() {
        val buffer = RingBuffer<Int>(5)
        buffer.push(1)
        buffer.push(2)
        buffer.push(3)

        assertEquals(listOf(1, 2, 3), buffer.toList())
    }

    @Test
    fun `toList works after wraparound`() {
        val buffer = RingBuffer<Int>(3)
        buffer.push(1)
        buffer.push(2)
        buffer.push(3)
        buffer.push(4)
        buffer.push(5)

        assertEquals(listOf(3, 4, 5), buffer.toList())
    }

    @Test
    fun `toFloatArray converts numbers`() {
        val buffer = RingBuffer<Int>(5)
        buffer.push(1)
        buffer.push(2)
        buffer.push(3)

        assertArrayEquals(floatArrayOf(1f, 2f, 3f), buffer.toFloatArray(), 0.001f)
    }

    @Test
    fun `copyToArray copies correct window`() {
        val buffer = RingBuffer<Float>(5)
        buffer.push(1f)
        buffer.push(2f)
        buffer.push(3f)
        buffer.push(4f)
        buffer.push(5f)

        val dest = FloatArray(3)
        buffer.copyToArray(dest, destOffset = 0, startIndex = 1, count = 3)

        assertArrayEquals(floatArrayOf(2f, 3f, 4f), dest, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `capacity must be positive`() {
        RingBuffer<Int>(0)
    }

    @Test
    fun `multiple wraparounds work correctly`() {
        val buffer = RingBuffer<Int>(3)
        // Fill and wrap multiple times
        for (i in 1..10) {
            buffer.push(i)
        }
        // Should contain 8, 9, 10
        assertEquals(listOf(8, 9, 10), buffer.toList())
    }
}

class SensorRingBufferTest {

    @Test
    fun `new sensor buffer is empty`() {
        val buffer = SensorRingBuffer(100)
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `push adds 3-axis data`() {
        val buffer = SensorRingBuffer(100)
        buffer.push(1f, 2f, 3f)
        assertEquals(1, buffer.size)
    }

    @Test
    fun `toInterleavedArray returns correct format`() {
        val buffer = SensorRingBuffer(100)
        buffer.push(1f, 2f, 3f)
        buffer.push(4f, 5f, 6f)

        val result = buffer.toInterleavedArray()
        assertArrayEquals(
            floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
            result,
            0.001f
        )
    }

    @Test
    fun `toChannelArrays returns separate channels`() {
        val buffer = SensorRingBuffer(100)
        buffer.push(1f, 2f, 3f)
        buffer.push(4f, 5f, 6f)

        val (x, y, z) = buffer.toChannelArrays()
        assertArrayEquals(floatArrayOf(1f, 4f), x, 0.001f)
        assertArrayEquals(floatArrayOf(2f, 5f), y, 0.001f)
        assertArrayEquals(floatArrayOf(3f, 6f), z, 0.001f)
    }

    @Test
    fun `buffer wraps correctly for sensor data`() {
        val buffer = SensorRingBuffer(2)
        buffer.push(1f, 1f, 1f)
        buffer.push(2f, 2f, 2f)
        buffer.push(3f, 3f, 3f) // overwrites first

        assertTrue(buffer.isFull)
        val result = buffer.toInterleavedArray()
        assertArrayEquals(
            floatArrayOf(2f, 2f, 2f, 3f, 3f, 3f),
            result,
            0.001f
        )
    }

    @Test
    fun `clear resets sensor buffer`() {
        val buffer = SensorRingBuffer(100)
        buffer.push(1f, 2f, 3f)
        buffer.clear()

        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `typical HAR window size works`() {
        val buffer = SensorRingBuffer(128) // typical HAR window

        // Simulate filling with accelerometer data
        for (i in 0 until 128) {
            buffer.push(i.toFloat(), (i * 2).toFloat(), (i * 3).toFloat())
        }

        assertTrue(buffer.isFull)
        assertEquals(128, buffer.size)

        val interleaved = buffer.toInterleavedArray()
        assertEquals(128 * 3, interleaved.size)
    }
}
