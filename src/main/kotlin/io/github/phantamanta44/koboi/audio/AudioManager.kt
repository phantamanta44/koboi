package io.github.phantamanta44.koboi.audio

import io.github.phantamanta44.koboi.memory.AudioChannelStateRegister
import io.github.phantamanta44.koboi.memory.FreqHighRegister
import io.github.phantamanta44.koboi.memory.FreqLowRegister
import io.github.phantamanta44.koboi.util.toUnsignedInt

class AudioManager(val audio: IAudioInterface,
                   private val mC1FreqLo: FreqLowRegister, private val mC1FreqHi: FreqHighRegister,
                   private val mC2FreqLo: FreqLowRegister, private val mC2FreqHi: FreqHighRegister,
                   private val mC3FreqLo: FreqLowRegister, private val mC3FreqHi: FreqHighRegister,
                   private val mState: AudioChannelStateRegister) {

    companion object {

        private val sequencerFrames: Array<SequencerFrame> = arrayOf(
                SequencerFrame(true, false, false),
                SequencerFrame(false, false, false),
                SequencerFrame(true, true, false),
                SequencerFrame(false, false, false),
                SequencerFrame(true, false, false),
                SequencerFrame(false, false, false),
                SequencerFrame(true, true, false),
                SequencerFrame(false, false, true)
        )

    }

    var enabled: Boolean = true

    val c1Dac: AudioDac = AudioDac(::c1Disable)
    val c1ToneSweep: Sweeper = Sweeper(mC1FreqLo, mC1FreqHi, audio.channel1.generator, ::c1Disable)
    val c1LengthCounter: LengthCounter = LengthCounter(64, ::c1Disable)
    val c1VolumeEnv: VolumeEnvelope = VolumeEnvelope(audio.channel1)

    val c2Dac: AudioDac = AudioDac(::c2Disable)
    val c2LengthCounter: LengthCounter = LengthCounter(64, ::c2Disable)
    val c2VolumeEnv: VolumeEnvelope = VolumeEnvelope(audio.channel2)

    val c3Dac: AudioDac = AudioDac(::c3Disable)
    val c3LengthCounter: LengthCounter = LengthCounter(256, ::c3Disable)
    val c3WaveIndexer: WaveIndexer = WaveIndexer(audio.channel3.generator)

    val c4Dac: AudioDac = AudioDac(::c4Disable)
    val c4LengthCounter: LengthCounter = LengthCounter(64, ::c4Disable)
    val c4VolumeEnv: VolumeEnvelope = VolumeEnvelope(audio.channel4)

    private var sequencerIndex: Int = 0
    val frame: SequencerFrame
        get() = sequencerFrames[sequencerIndex]
    private var divider: Int = 0

    fun powerUp() {
        enabled = true
        sequencerIndex = 7
    }

    fun cycle() {
        if (audio.channel3.enabled) {
            c3WaveIndexer.cycle()
        }
        if (++divider == 8192) {
            divider = 0
            sequencerIndex = (sequencerIndex + 1) % 8
            if (frame.clock64) {
                if (audio.channel1.enabled) {
                    c1VolumeEnv.cycle()
                }
                if (audio.channel2.enabled) {
                    c2VolumeEnv.cycle()
                }
                if (audio.channel4.enabled) {
                    c4VolumeEnv.cycle()
                }
            }
            if (frame.clock128) {
                if (audio.channel1.enabled) {
                    c1ToneSweep.cycle()
                }
            }
            if (frame.clock256) {
                c1LengthCounter.cycle()
                c2LengthCounter.cycle()
                c3LengthCounter.cycle()
                c4LengthCounter.cycle()
            }
        }
    }

    fun kill() {
        audio.kill()
    }

    private fun c1Disable() {
        audio.channel1.enabled = false
        mState.c1Alive = false
    }

    private fun c2Disable() {
        audio.channel2.enabled = false
        mState.c2Alive = false
    }

    private fun c3Disable() {
        audio.channel3.enabled = false
        mState.c3Alive = false
    }

    private fun c4Disable() {
        audio.channel4.enabled = false
        mState.c4Alive = false
    }

    fun c1UpdateFrequency() {
        audio.channel1.generator.period = 2048 - (mC1FreqLo.value.toUnsignedInt() or (mC1FreqHi.freqBits shl 8))
    }

    fun c2UpdateFrequency() {
        audio.channel2.generator.period = 2048 - (mC2FreqLo.value.toUnsignedInt() or (mC2FreqHi.freqBits shl 8))
    }

    fun c3UpdateFrequency() {
        c3WaveIndexer.period = 2048 - (mC3FreqLo.value.toUnsignedInt() or (mC3FreqHi.freqBits shl 8))
    }

    fun c1RestartSound() {
        if (c1Dac.enabled) {
            mState.c1Alive = true
            audio.channel1.enabled = true
        }
        audio.channel1.resetSound()
        val freqRegVal = mC1FreqLo.value.toUnsignedInt() or (mC1FreqHi.freqBits shl 8)
        c1ToneSweep.freqRegVal = freqRegVal
        c1ToneSweep.reset()
        c1VolumeEnv.reset()
        audio.channel1.generator.period = 2048 - freqRegVal
    }

    fun c2RestartSound() {
        if (c2Dac.enabled) {
            mState.c2Alive = true
            audio.channel2.enabled = true
        }
        audio.channel2.resetSound()
        c2VolumeEnv.reset()
    }

    fun c3RestartSound() {
        if (c3Dac.enabled) {
            mState.c3Alive = true
            audio.channel3.enabled = true
        }
        audio.channel3.resetSound()
        c3WaveIndexer.reset()
    }

    fun c4RestartSound() {
        if (c4Dac.enabled) {
            mState.c4Alive = true
            audio.channel4.enabled = true
        }
        audio.channel4.resetSound()
        c4VolumeEnv.reset()
    }

}

class SequencerFrame(val clock256: Boolean, val clock128: Boolean, val clock64: Boolean)