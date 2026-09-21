package com.callguard.audio

/** Fixed-size in-memory ring of the most recent PCM16 samples (never persisted). */
class PcmRingBuffer(private val capacity: Int) {
    private val data = ShortArray(capacity)
    private var writePos = 0
    var size = 0
        private set
    var totalWritten = 0L
        private set

    @Synchronized fun write(src: ShortArray, len: Int = src.size) {
        for (i in 0 until len) {
            data[writePos] = src[i]
            writePos = (writePos + 1) % capacity
        }
        size = minOf(capacity, size + len)
        totalWritten += len
    }

    /** Oldest-to-newest copy of the buffered samples. */
    @Synchronized fun snapshot(): ShortArray {
        val out = ShortArray(size)
        val start = (writePos - size + capacity) % capacity
        for (i in 0 until size) out[i] = data[(start + i) % capacity]
        return out
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        fun ofSeconds(seconds: Int) = PcmRingBuffer(SAMPLE_RATE * seconds)
    }
}
