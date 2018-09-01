package io.github.phantamanta44.koboi.plugin.jxsound

import io.github.phantamanta44.koboi.audio.IAudioGenerator
import io.github.phantamanta44.koboi.audio.ILfsrAudioGenerator
import io.github.phantamanta44.koboi.audio.ISquareAudioGenerator
import io.github.phantamanta44.koboi.audio.IWavePatternAudioGenerator
import io.github.phantamanta44.koboi.util.PropDel
import java.util.concurrent.locks.ReentrantLock

interface IJxAudioProducer : IAudioGenerator {

    fun cycle(): Byte

    fun resetSound()

}

abstract class JxFreqAudioProducer : IJxAudioProducer {

    private var counter: Int = 0
    protected abstract val counterBoundary: Int

    override fun cycle(): Byte {
        val clock = if (++counter >= counterBoundary) {
            counter = 0
            true
        } else {
            false
        }
        return generate(clock)
    }

    override fun resetSound() {
        counter = 0
    }

    abstract fun generate(clock: Boolean): Byte

}


class SquareWaveProducer : JxFreqAudioProducer(), ISquareAudioGenerator {

    override val counterBoundary: Int
        get() = period
    private var duty: DutyType = DutyType.DUTY_4_8
    override var dutyType: Int
        get() = duty.ordinal
        set(value) {
            duty = DutyType.values()[value]
        }
    override var period: Int = 1

    private var waveformIndex: Int = 0

    override fun generate(clock: Boolean): Byte {
        if (clock) waveformIndex = (waveformIndex + 1) % 8
        return duty.pattern[waveformIndex]
    }

    override fun resetSound() {
        super.resetSound()
        waveformIndex = 0
    }

    enum class DutyType(val pattern: ByteArray) {

        DUTY_1_8(byteArrayOf(-128, -128, -128, -128, -128, -128, -128, 127)),
        DUTY_2_8(byteArrayOf(127, -128, -128, -128, -128, -128, -128, 127)),
        DUTY_4_8(byteArrayOf(127, -128, -128, -128, -128, 127, 127, 127)),
        DUTY_6_8(byteArrayOf(-128, 127, 127, 127, 127, 127, 127, -128))

    }

}

class ArbitraryWaveAudioProducer : JxFreqAudioProducer(), IWavePatternAudioGenerator {

    override val counterBoundary: Int by PropDel.r(::period)

    override var period: Int = 0
    override var activeByte: Int = 0
    override var highNibble: Boolean by PropDel.observe(true) {
        bufLock.lockInterruptibly()
        try {
            if (bufferPointer < buffer.size) {
                val sample = if (it) {
                    (waveform[activeByte].toInt() and 0xF0) ushr 4
                } else {
                    waveform[activeByte].toInt() and 0x0F
                }
                buffer[bufferPointer] = Math.round((255F * sample / 15F) - 128F).toByte()
                ++bufferPointer
            }
        } finally {
            bufLock.unlock()
        }
    }

    private val buffer: ByteArray = ByteArray(32)
    private var bufferPointer: Int = 0
    private var playbackPointer: Int = 0
    private val bufLock: ReentrantLock = ReentrantLock(true)

    override fun generate(clock: Boolean): Byte {
        bufLock.lockInterruptibly()
        try {
            return buffer[playbackPointer].also { if (clock) playbackPointer = (playbackPointer + 1) % buffer.size }
        } finally {
            bufLock.unlock()
        }
    }

    override fun resetSound() {
        bufLock.lockInterruptibly()
        try {
            bufferPointer = 0
            playbackPointer = 0
        } finally {
            bufLock.unlock()
        }
    }

    override val waveform: ByteArray = ByteArray(16)

}

class NoiseAudioProducer : JxFreqAudioProducer(), ILfsrAudioGenerator {

    override val counterBoundary: Int by PropDel.r(::period)
    override var period: Int = 1
    override var mode7Bit: Boolean = false

    private var state: Int = 0x7FFF

    override fun generate(clock: Boolean): Byte {
        if (clock) {
            val newBit = (state and 0x01) xor ((state and 0x02) ushr 1)
            state = (state ushr 1) or (newBit shl 14)
            if (mode7Bit) state = state or (newBit shl 6)
        }
        return if (state and 1 == 0) 127 else -128
    }

}