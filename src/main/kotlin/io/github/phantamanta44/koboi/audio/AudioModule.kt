package io.github.phantamanta44.koboi.audio

import io.github.phantamanta44.koboi.memory.FreqHighRegister
import io.github.phantamanta44.koboi.memory.FreqLowRegister

class AudioDac(private val disable: () -> Unit) {

    private var _enabled: Boolean = false

    var enabled: Boolean
        get() = _enabled
        set(value) {
            _enabled = value
            if (!value) disable()
        }

}

class Sweeper(private val lo: FreqLowRegister, private val hi: FreqHighRegister,
              private val generator: ISquareAudioGenerator, private val disable: () -> Unit) {

    var period: Int = 0 // in 128ths of a second
    var shift: Int = 0
    private var _operation: Operation = Operation.ADD
    var operation: Operation
        get() = _operation
        set(value) {
            if (subtracted && value == Operation.ADD) {
                overflow()
            }
            _operation = value
        }

    private val effectivePeriod: Int
        get() = if (period == 0) 8 else period
    var enabled: Boolean = false
    var freqRegVal: Int = 0
    private var timer: Int = 0
    private var subtracted: Boolean = false

    fun cycle() {
        if (enabled && --timer == 0) {
            timer = effectivePeriod
            if (period != 0) {
                calculate()?.let {
                    if (shift != 0) {
                        freqRegVal = it
                        generator.period = 2048 - it
                        lo.value = (it and 0xFF).toByte()
                        hi.freqBits = (it and 0x700) ushr 8
                        calculate()
                    }
                }
            }
        }
    }

    fun reset() {
        subtracted = false
        timer = effectivePeriod
        if (shift == 0) {
            enabled = period != 0
        } else {
            enabled = true
            calculate()
        }
    }

    private fun calculate(): Int? {
        val newPeriod = operation.apply(freqRegVal, freqRegVal ushr shift)
        if (operation == Operation.SUBTRACT) subtracted = true
        return if (newPeriod >= 2048) {
            overflow()
            null
        } else {
            newPeriod and 0x7FF
        }
    }

    private fun overflow() {
        disable()
    }

}

class LengthCounter(private val counterMax: Int, private val disable: () -> Unit) {

    var enabled: Boolean = false

    var counter: Int = 0

    fun cycle() {
        if (enabled && counter > 0 && --counter == 0) disable()
    }

    fun reset(): Boolean {
        return if (counter == 0) {
            counter = counterMax
            true
        } else {
            false
        }
    }

    fun resetAndEnable() {
        if (counter <= 1) counter = counterMax - 1
    }

}

class VolumeEnvelope(private val channel: IAudioChannel<IAudioGenerator>) {

    var initialVolume: Int = 0
    var enabled: Boolean = false
    var period: Int = 1 // in 64ths of a second
    var operation: Operation = Operation.SUBTRACT

    var volume: Int = 0
    var timer: Int = 0

    fun cycle() {
        if (enabled && ++timer > period) {
            timer = 0
            val newVolume = operation.apply(volume, 1)
            if (newVolume in 0..15) {
                volume = newVolume
                channel.volume = newVolume / 15F
                channel.volume = newVolume / 15F
            }
        }
    }

    fun reset() {
        volume = initialVolume
        timer = 0
    }

}

class WaveIndexer(private val generator: IWavePatternAudioGenerator) {

    companion object {

        private val INDICES: List<Pair<Int, Boolean>> = (0..15)
                .flatMap { listOf(it to true, it to false) }

        private const val DIVIDER_BOUND: Int = 2

    }

    var period: Int = 0

    private var divider: Int = 0
    private var counter: Int = 0
    private var index: Int = 0

    fun cycle() {
        if (++divider == DIVIDER_BOUND) {
            divider = 0
            if (--counter == 0) {
                counter = period
                index = (index + 1) % 32
                uploadIndex()
            }
        }
    }

    fun reset() {
        divider = -6
        counter = period
        index = 0
        uploadIndex()
    }

    private fun uploadIndex() {
        INDICES[index].let {
            generator.activeByte = it.first
            generator.highNibble = it.second
        }
    }

}

enum class Operation(val apply: (Int, Int) -> Int) {

    ADD(Int::plus),
    SUBTRACT(Int::minus)

}