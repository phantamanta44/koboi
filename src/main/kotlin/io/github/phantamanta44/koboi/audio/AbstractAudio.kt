package io.github.phantamanta44.koboi.audio

interface IAudioInterface {

    val channel1: IAudioChannel<ISquareAudioGenerator>

    val channel2: IAudioChannel<ISquareAudioGenerator>

    val channel3: IAudioChannel<IWavePatternAudioGenerator>

    val channel4: IAudioChannel<ILfsrAudioGenerator>

    fun kill()

}

interface IAudioChannel<out G : IAudioGenerator> {

    var enabled: Boolean

    val generator: G

    var volume: Float

    var outputLeft: Boolean

    var outputRight: Boolean

    fun resetSound()

}

// audio generators

interface IAudioGenerator

interface ISquareAudioGenerator : IFrequencyAudioGenerator {

    var dutyType: Int

}

interface IWavePatternAudioGenerator : IFrequencyAudioGenerator {

    val waveform: ByteArray

    var activeByte: Int

    var highNibble: Boolean

}

interface ILfsrAudioGenerator : IFrequencyAudioGenerator {

    var mode7Bit: Boolean

}

interface IFrequencyAudioGenerator : IAudioGenerator {

    /**
     * Square wave generator: 131072nds of a second
     * LSFR: 524288ths of a second
     * Wave channel: 65536ths of a second
     */
    var period: Int

}