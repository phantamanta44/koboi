package io.github.phantamanta44.koboi.util

import kotlin.concurrent.getOrSet

const val SAMPLE_COUNT: Int = 512

class ThreadThrottlingData {

    companion object {

        val perThread: ThreadLocal<ThreadThrottlingData> = ThreadLocal()

        fun getAverageExecutionTime(): Long {
            val data = perThread.get()
            return data?.averageExecutionTime ?: -1L
        }

    }

    private val samples: LongArray = LongArray(SAMPLE_COUNT)
    private var sampleIndex: Int = 0
    private var lastSample: Long = -1L
    private val averageExecutionTime: Long
        get() = samples.sum() / SAMPLE_COUNT

    fun throttle(periodNanos: Long) {
        val now = System.nanoTime()
        val sleepTime = periodNanos - averageExecutionTime
        if (lastSample != -1L) {
            samples[sampleIndex] = now - lastSample
            sampleIndex = (sampleIndex + 1) % SAMPLE_COUNT
        }
        if (sleepTime > 0) {
            val remainder = (sleepTime % 1000000).toInt()
            Thread.sleep((sleepTime - remainder) / 1000000, remainder)
        }
        lastSample = now
    }

}

fun throttleThread(periodNanos: Long) {
    ThreadThrottlingData.perThread.getOrSet(::ThreadThrottlingData).throttle(periodNanos)
}