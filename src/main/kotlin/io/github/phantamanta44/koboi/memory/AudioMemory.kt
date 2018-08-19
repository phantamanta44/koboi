package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.GameEngine
import io.github.phantamanta44.koboi.audio.*

typealias ManagerRef<T> = (AudioManager) -> T
typealias ChannelRef<G> = (IAudioInterface) -> IAudioChannel<G>
typealias ManagerFun = AudioManager.() -> Unit

class Ch1SweepRegister(engine: GameEngine) : BiDiBitwiseRegister(readableMask = 0b01111111) {

    private val sweeper: Sweeper by lazy { engine.audio.c1ToneSweep }
    
    private var sweepTime: Int by delegateMaskedInt(0b01110000, 4)

    private var subtract: Boolean by delegateBit(3)

    private var sweepShift: Int by delegateMaskedInt(0b00000111, 0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        val sweepTime0 = sweepTime
        if (sweepTime0 == 0) {
            sweeper.enabled = false
        } else {
            sweeper.enabled = true
            sweeper.period = sweepTime0
            sweeper.factor = 1 shl sweepShift
            sweeper.operation = if (subtract) Operation.SUBTRACT else Operation.ADD
        }
    }

    override fun typeAt(addr: Int): String = "RACh1Sweep"

}

class LengthDutyRegister(engine: GameEngine, channel: ChannelRef<ISquareAudioGenerator>, lengthCounter: ManagerRef<LengthCounter>)
    : BiDiBitwiseRegister(readableMask = 0b11000000) {

    private val generator: ISquareAudioGenerator by lazy { channel(engine.audio.audio).generator }
    private val lengthCounter: LengthCounter by lazy { lengthCounter(engine.audio) }

    private var duty: Int by delegateMaskedInt(0b11000000, 6)

    private var audioLength: Int by delegateMaskedInt(0b00111111, 0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        generator.dutyType = duty
        lengthCounter.length = 64 - audioLength
    }

    override fun typeAt(addr: Int): String = "RALengthDuty"

}

class VolumeEnvelopeRegister(engine: GameEngine, channel: ChannelRef<IAudioGenerator>, envelope: ManagerRef<VolumeEnvelope>)
    : BitwiseRegister() {

    private val channel: IAudioChannel<IAudioGenerator> by lazy { channel(engine.audio.audio) }
    private val envelope: VolumeEnvelope by lazy { envelope(engine.audio) }

    var volume: Int by delegateMaskedInt(0b11110000, 4)

    private var increase: Boolean by delegateBit(3)

    private var sweepShift: Int by delegateMaskedInt(0b00000111, 0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        val sweepShift0 = sweepShift
        channel.volume = volume / 15F
        envelope.initialVolume = volume
        if (sweepShift0 == 0) {
            envelope.enabled = false
        } else {
            envelope.enabled = true
            envelope.period = sweepShift0
            envelope.operation = if (increase) Operation.ADD else Operation.SUBTRACT
        }
    }

    override fun typeAt(addr: Int): String = "RAVolumeEnvelope"

}

class FreqLowRegister(private val engine: GameEngine, private val updateFrequency: ManagerFun) : BiDiBitwiseRegister(readableMask = 0) {

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        engine.audio.updateFrequency()
    }

    override fun typeAt(addr: Int): String = "RAFreqLow"

}

class FreqHighRegister(private val engine: GameEngine,
                       lengthCounter: ManagerRef<LengthCounter>,
                       private val updateFrequency: ManagerFun, private val restartSound: ManagerFun)
    : BiDiBitwiseRegister(readableMask = 0b01000000) {

    private val lengthCounter: LengthCounter by lazy { lengthCounter(engine.audio) }

    private var initial: Boolean by delegateBit(7)

    private var respectLength: Boolean by delegateBit(6)

    var freqBits: Int by delegateMaskedInt(0b00000111, 0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        engine.audio.updateFrequency()
        lengthCounter.enabled = respectLength
        if (initial) engine.audio.restartSound()
    }

    override fun typeAt(addr: Int): String = "RAFreqHigh"

}

class Ch3EnableRegister(engine: GameEngine) : BiDiBitwiseRegister(readableMask = 0b10000000) {

    private val generator: IWavePatternAudioGenerator by lazy { engine.audio.audio.channel3.generator }

    var enabled: Boolean by delegateBit(7)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        generator.enabled = enabled
    }

}

class Ch3LengthRegister(engine: GameEngine) : SingleByteMemoryArea() {

    private val lengthCounter: LengthCounter by lazy { engine.audio.c3LengthCounter }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        lengthCounter.length = 256 - value.toInt()
    }

    override fun read(addr: Int, direct: Boolean): Byte = if (direct) super.read(addr, direct) else 0xFF.toByte()

    override fun typeAt(addr: Int): String = "RACh3Length"

}

class Ch3WavePatternMemoryArea(engine: GameEngine, private val enableRegister: Ch3EnableRegister) : SimpleMemoryArea(16) {

    private val generator: IWavePatternAudioGenerator by lazy { engine.audio.audio.channel3.generator }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        if (direct || !enableRegister.enabled) {
            super.write(addr, *values, start = start, length = length, direct = direct)
            System.arraycopy(memory, 0, generator.waveform, 0, 16)
            directObserver.onMemMutate(addr, length)
        }
    }

    // TODO weird reading-while-enabled issue

    override fun typeAt(addr: Int): String = "RACh3WaveRAM"

}

class Ch4LengthRegister(engine: GameEngine) : SingleByteMemoryArea() {

    private val lengthCounter: LengthCounter by lazy { engine.audio.c4LengthCounter }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        lengthCounter.length = 64 - value.toInt() and 0b00111111
    }

    override fun read(addr: Int, direct: Boolean): Byte = if (direct) super.read(addr, direct) else 0xFF.toByte()

    override fun typeAt(addr: Int): String = "RACh4Length"

}

class Ch4PolyCounterRegister(engine: GameEngine) : BitwiseRegister() {

    private val generator: ILfsrAudioGenerator by lazy { engine.audio.audio.channel4.generator }

    private var freqShift: Int by delegateMaskedInt(0b11110000, 4)

    private var lfsr7BitMode: Boolean by delegateBit(3)

    private var dividingRatio: Int by delegateMaskedInt(0b00000111, 0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        generator.period = dividingRatio * (2 shl freqShift)
        generator.mode7Bit = lfsr7BitMode
    }

    override fun typeAt(addr: Int): String = "RACh4PolyCounter"

}

class Ch4ControlRegister(private val engine: GameEngine) : BiDiBitwiseRegister(readableMask = 0b01000000) {

    private val lengthCounter: LengthCounter by lazy { engine.audio.c4LengthCounter }

    private var initial: Boolean by delegateBit(7)

    private var respectLength: Boolean by delegateBit(6)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        lengthCounter.enabled = respectLength
        if (initial) engine.audio.c4RestartSound()
    }

    override fun typeAt(addr: Int): String = "RACh4Control"

}

class VInRegister : BitwiseRegister() {

    // NO-OP

    override fun typeAt(addr: Int): String = "RAVIn"

}

class ChannelVolumeRegister(private val engine: GameEngine) : BitwiseRegister() {

    private val audio: IAudioInterface by lazy { engine.audio.audio }

    private var c4Left: Boolean by delegateBit(7)
    private var c3Left: Boolean by delegateBit(6)
    private var c2Left: Boolean by delegateBit(5)
    private var c1Left: Boolean by delegateBit(4)

    private var c4Right: Boolean by delegateBit(3)
    private var c3Right: Boolean by delegateBit(2)
    private var c2Right: Boolean by delegateBit(1)
    private var c1Right: Boolean by delegateBit(0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        with (audio.channel1) {
            outputLeft = c1Left
            outputRight = c1Right
        }
        with (audio.channel2) {
            outputLeft = c2Left
            outputRight = c2Right
        }
        with (audio.channel3) {
            outputLeft = c3Left
            outputRight = c3Right
        }
        with (audio.channel4) {
            outputLeft = c4Left
            outputRight = c4Right
        }
    }

    override fun typeAt(addr: Int): String = "RAChannelVolume"

}

class AudioKillSwitchRegister(private val engine: GameEngine) : BiDiBitwiseRegister(0b10000000, 0b10001111) {

    private var enableAudio: Boolean by delegateBit(7)

    var c4Alive: Boolean by delegateBit(3)
    var c3Alive: Boolean by delegateBit(2)
    var c2Alive: Boolean by delegateBit(1)
    var c1Alive: Boolean by delegateBit(0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        engine.audio.enabled = enableAudio
    }

    override fun typeAt(addr: Int): String = "RAKillSwitch"

}