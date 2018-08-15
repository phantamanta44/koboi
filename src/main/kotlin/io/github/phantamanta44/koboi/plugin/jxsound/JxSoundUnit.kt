package io.github.phantamanta44.koboi.plugin.jxsound

import io.github.phantamanta44.koboi.audio.IAudioGenerator
import io.github.phantamanta44.koboi.audio.ILfsrAudioGenerator
import io.github.phantamanta44.koboi.audio.ISquareAudioGenerator
import io.github.phantamanta44.koboi.audio.IWavePatternAudioGenerator
import io.github.phantamanta44.koboi.util.PropDel

abstract class JxAudioProducer : IAudioGenerator {

    private var counter: Int = 0
    protected abstract val counterBoundary: Int

    open fun cycle(): Byte {
        val clock = if (++counter >= counterBoundary) {
            counter = 0
            true
        } else {
            false
        }
        return generate(clock)
    }

    open fun resetSound() {
        counter = 0
    }

    abstract fun generate(clock: Boolean): Byte

}


class SquareWaveProducer : JxAudioProducer(), ISquareAudioGenerator {

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

class ArbitraryWaveAudioProducer : JxAudioProducer(), IWavePatternAudioGenerator {

    companion object {

        private val INDICES: List<Pair<Int, (Int) -> Int>> = (0..15)
                .flatMap { listOf(it to { n: Int -> (n and 0xF0) ushr 4 }, it to { n: Int -> n and 0x0F }) }

    }

    override val counterBoundary: Int by PropDel.r(::period)
    override var period: Int = 1

    private var index: Int = 0

    override fun cycle(): Byte = if (enabled) super.cycle() else 0

    override fun generate(clock: Boolean): Byte {
        val coords = INDICES[index]
        if (clock) index = (index + 1) % 32
        return Math.round((255F * coords.second(waveform[coords.first].toInt()) / 15F) - 128F).toByte()
    }

    override fun resetSound() {
        super.resetSound()
        index = 0
    }

    override var enabled: Boolean = false
    override val waveform: ByteArray = ByteArray(16)

}

class NoiseAudioProducer : JxAudioProducer(), ILfsrAudioGenerator {

    override val counterBoundary: Int by PropDel.r(::period)
    override var period: Int = 1
    override var mode7Bit: Boolean = false

    private var state: Int = 0x7F

    override fun generate(clock: Boolean): Byte {
        if (clock) {
            val newBit = (state and 0x01) xor ((state and 0x02) ushr 1)
            state = (state ushr 1) or (newBit shl 14)
            if (mode7Bit) state = state or (newBit shl 6)
        }
        return if (state and 0 == 0) 127 else -128
    }

    override fun resetSound() {
        super.resetSound()
        state = 0x7F
    }

}