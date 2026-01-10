package com.cosgame.costrack.data

/**
 * A fixed-size circular buffer optimized for sensor data windowing.
 * Used by classifiers to maintain a sliding window of recent sensor readings.
 *
 * @param T the type of elements stored in the buffer
 * @param capacity the maximum number of elements the buffer can hold
 */
class RingBuffer<T>(val capacity: Int) {

    init {
        require(capacity > 0) { "Capacity must be positive" }
    }

    private val buffer: Array<Any?> = arrayOfNulls(capacity)
    private var writeIndex = 0
    private var _size = 0

    /**
     * Current number of elements in the buffer.
     */
    val size: Int get() = _size

    /**
     * Whether the buffer is empty.
     */
    val isEmpty: Boolean get() = _size == 0

    /**
     * Whether the buffer is full.
     */
    val isFull: Boolean get() = _size == capacity

    /**
     * Adds an element to the buffer. If the buffer is full,
     * the oldest element is overwritten.
     *
     * @param element the element to add
     */
    fun push(element: T) {
        buffer[writeIndex] = element
        writeIndex = (writeIndex + 1) % capacity
        if (_size < capacity) {
            _size++
        }
    }

    /**
     * Returns the element at the given index, where 0 is the oldest element.
     *
     * @param index the index of the element to retrieve
     * @return the element at the given index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        if (index < 0 || index >= _size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $_size")
        }
        val actualIndex = if (_size < capacity) {
            index
        } else {
            (writeIndex + index) % capacity
        }
        return buffer[actualIndex] as T
    }

    /**
     * Returns the most recently added element.
     *
     * @return the newest element
     * @throws NoSuchElementException if buffer is empty
     */
    @Suppress("UNCHECKED_CAST")
    fun peek(): T {
        if (isEmpty) {
            throw NoSuchElementException("Buffer is empty")
        }
        val lastIndex = if (writeIndex == 0) capacity - 1 else writeIndex - 1
        return buffer[lastIndex] as T
    }

    /**
     * Returns the oldest element in the buffer.
     *
     * @return the oldest element
     * @throws NoSuchElementException if buffer is empty
     */
    @Suppress("UNCHECKED_CAST")
    fun peekOldest(): T {
        if (isEmpty) {
            throw NoSuchElementException("Buffer is empty")
        }
        return this[0]
    }

    /**
     * Clears all elements from the buffer.
     */
    fun clear() {
        for (i in 0 until capacity) {
            buffer[i] = null
        }
        writeIndex = 0
        _size = 0
    }

    /**
     * Returns all elements as a list, ordered from oldest to newest.
     *
     * @return list of all elements
     */
    @Suppress("UNCHECKED_CAST")
    fun toList(): List<T> {
        if (isEmpty) return emptyList()
        return (0 until _size).map { this[it] }
    }

    /**
     * Returns the elements as a FloatArray. Useful for ML model input.
     * Only works when T is Number.
     *
     * @return FloatArray of all elements
     */
    @Suppress("UNCHECKED_CAST")
    fun toFloatArray(): FloatArray {
        return FloatArray(_size) { index ->
            (this[index] as Number).toFloat()
        }
    }

    /**
     * Copies a window of data to the destination array.
     * Optimized for classifier input preparation.
     *
     * @param destination the array to copy to
     * @param destOffset starting position in destination
     * @param startIndex starting index in buffer (0 = oldest)
     * @param count number of elements to copy
     */
    @Suppress("UNCHECKED_CAST")
    fun copyToArray(
        destination: FloatArray,
        destOffset: Int = 0,
        startIndex: Int = 0,
        count: Int = _size
    ) {
        require(startIndex >= 0 && startIndex < _size) { "Invalid startIndex" }
        require(count >= 0 && startIndex + count <= _size) { "Invalid count" }
        require(destOffset >= 0 && destOffset + count <= destination.size) { "Destination overflow" }

        for (i in 0 until count) {
            destination[destOffset + i] = (this[startIndex + i] as Number).toFloat()
        }
    }
}

/**
 * Specialized ring buffer for 3-axis sensor data (x, y, z).
 * Stores data as interleaved [x0, y0, z0, x1, y1, z1, ...] for efficient ML input.
 */
class SensorRingBuffer(val windowSize: Int) {

    init {
        require(windowSize > 0) { "Window size must be positive" }
    }

    private val xBuffer = RingBuffer<Float>(windowSize)
    private val yBuffer = RingBuffer<Float>(windowSize)
    private val zBuffer = RingBuffer<Float>(windowSize)

    val size: Int get() = xBuffer.size
    val isFull: Boolean get() = xBuffer.isFull
    val isEmpty: Boolean get() = xBuffer.isEmpty

    /**
     * Adds a 3-axis sensor reading.
     */
    fun push(x: Float, y: Float, z: Float) {
        xBuffer.push(x)
        yBuffer.push(y)
        zBuffer.push(z)
    }

    /**
     * Returns interleaved data [x0,y0,z0,x1,y1,z1,...] for ML model input.
     */
    fun toInterleavedArray(): FloatArray {
        val result = FloatArray(size * 3)
        for (i in 0 until size) {
            result[i * 3] = xBuffer[i]
            result[i * 3 + 1] = yBuffer[i]
            result[i * 3 + 2] = zBuffer[i]
        }
        return result
    }

    /**
     * Returns separate channel arrays [xArray, yArray, zArray] for models
     * that expect channel-separated input.
     */
    fun toChannelArrays(): Triple<FloatArray, FloatArray, FloatArray> {
        return Triple(
            xBuffer.toFloatArray(),
            yBuffer.toFloatArray(),
            zBuffer.toFloatArray()
        )
    }

    /**
     * Clears all buffers.
     */
    fun clear() {
        xBuffer.clear()
        yBuffer.clear()
        zBuffer.clear()
    }
}
