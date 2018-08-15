package io.github.phantamanta44.koboi.audio

class Sweeper(private val generator: ISquareAudioGenerator, private val disable: () -> Unit) {

    var enabled: Boolean = false
    var period: Int = 1 // in 128ths of a second
    var factor: Int = 2
    var operation: Operation = Operation.ADD

    var timer: Int = 0

    fun cycle() {
        if (enabled && ++timer > period) {
            timer = 0
            val current = generator.period
            val newPeriod = operation.apply(current, -current / factor)
            if (newPeriod >= 2048 || newPeriod < 0) {
                disable()
            } else {
                generator.period = newPeriod
            }
        }
    }

    fun reset() {
        timer = 0
    }

}

class LengthCounter(private val disable: () -> Unit) {

    var enabled: Boolean = false
    var length: Int = 0

    var counter: Int = 0

    fun cycle() {
        if (enabled && ++counter >= length) disable()
    }

    fun reset() {
        counter = 0
    }

}

class VolumeEnvelope(private val channel: IAudioChannel<IAudioGenerator>) {

    var initialVolume: Int = 0
    var enabled: Boolean = false
    var period: Int = 1 // in 64ths of a second
    var operation : Operation = Operation.SUBTRACT

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

enum class Operation(val apply: (Int, Int) -> Int) {

    ADD(Int::plus),
    SUBTRACT(Int::minus)

}