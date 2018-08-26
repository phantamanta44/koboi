package io.github.phantamanta44.koboi.audio

import io.github.phantamanta44.koboi.cpu.Timer
import io.github.phantamanta44.koboi.memory.AudioChannelStateRegister
import io.github.phantamanta44.koboi.memory.FreqHighRegister
import io.github.phantamanta44.koboi.memory.FreqLowRegister
import io.github.phantamanta44.koboi.util.toUnsignedInt

class AudioManager(val audio: IAudioInterface, private val clock: Timer,
                   private val mC1FreqLo: FreqLowRegister, private val mC1FreqHi: FreqHighRegister,
                   private val mC2FreqLo: FreqLowRegister, private val mC2FreqHi: FreqHighRegister,
                   private val mC3FreqLo: FreqLowRegister, private val mC3FreqHi: FreqHighRegister,
                   private val mState: AudioChannelStateRegister) {

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

    val c4Dac: AudioDac = AudioDac(::c4Disable)
    val c4LengthCounter: LengthCounter = LengthCounter(64, ::c4Disable)
    val c4VolumeEnv: VolumeEnvelope = VolumeEnvelope(audio.channel4)

    private var audioTimer: Long = 0

    fun testAudioTimer(bit: Long): Boolean = audioTimer and bit != 0L

    private fun testAudioTimerEdge(bit: Long): Boolean = (audioTimer - 1) and bit > audioTimer and bit

    fun powerUp() {
        enabled = true
        audioTimer = (audioTimer + 1) % 512
    }

    fun cycle() {
        if (testAudioTimerEdge(0x8000)) { // 64 Hz
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
        if (testAudioTimerEdge(0x4000)) { // 128 Hz
            if (audio.channel1.enabled) {
                c1ToneSweep.cycle()
            }
        }
        if (enabled && testAudioTimerEdge(0x2000)) { // 256 Hz
            c1LengthCounter.cycle()
            c2LengthCounter.cycle()
            c3LengthCounter.cycle()
            c4LengthCounter.cycle()
        }
        ++audioTimer
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
        audio.channel3.generator.period = 2048 - (mC3FreqLo.value.toUnsignedInt() or (mC3FreqHi.freqBits shl 8))
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