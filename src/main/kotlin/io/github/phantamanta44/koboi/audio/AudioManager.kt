package io.github.phantamanta44.koboi.audio

import io.github.phantamanta44.koboi.cpu.Timer
import io.github.phantamanta44.koboi.memory.AudioKillSwitchRegister
import io.github.phantamanta44.koboi.memory.Ch3WavePatternMemoryArea
import io.github.phantamanta44.koboi.memory.FreqHighRegister
import io.github.phantamanta44.koboi.memory.FreqLowRegister
import io.github.phantamanta44.koboi.util.toUnsignedInt

class AudioManager(val audio: IAudioInterface, private val clock: Timer,
                   private val mC1FreqLo: FreqLowRegister, private val mC1FreqHi: FreqHighRegister,
                   private val mC2FreqLo: FreqLowRegister, private val mC2FreqHi: FreqHighRegister,
                   private val mC3FreqLo: FreqLowRegister, private val mC3FreqHi: FreqHighRegister, private val mC3Wave: Ch3WavePatternMemoryArea,
                   private val mState: AudioKillSwitchRegister) {

    var enabled: Boolean = true

    val c1ToneSweep: Sweeper = Sweeper(audio.channel1.generator, ::c1Disable)
    val c1LengthCounter: LengthCounter = LengthCounter(::c1Disable)
    val c1VolumeEnv: VolumeEnvelope = VolumeEnvelope(audio.channel1)

    val c2LengthCounter: LengthCounter = LengthCounter(::c2Disable)
    val c2VolumeEnv: VolumeEnvelope = VolumeEnvelope(audio.channel2)

    val c3LengthCounter: LengthCounter = LengthCounter(::c3Disable)

    val c4LengthCounter: LengthCounter = LengthCounter(::c4Disable)
    val c4VolumeEnv: VolumeEnvelope = VolumeEnvelope(audio.channel4)

    private var audioTimer: Long = 0L

    fun cycle() {
        if (enabled && clock.globalTimer % 16384L == 0L) { // 256 Hz
            val d64 = clock.globalTimer % 65536L == 0L // 64 Hz
            if (audio.channel1.enabled) {
                if (clock.globalTimer % 32768L == 0L) c1ToneSweep.cycle() // 128 Hz
                c1LengthCounter.cycle()
                if (d64) c1VolumeEnv.cycle()
            }
            if (audio.channel2.enabled) {
                c2LengthCounter.cycle()
                if (d64) c2VolumeEnv.cycle()
            }
            if (audio.channel3.enabled) {
                c3LengthCounter.cycle()
            }
            if (audio.channel4.enabled) {
                c4LengthCounter.cycle()
                if (d64) c4VolumeEnv.cycle()
            }
            ++audioTimer
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
        audio.channel3.generator.period = 2048 - (mC3FreqLo.value.toUnsignedInt() or (mC3FreqHi.freqBits shl 8))
    }

    fun c1RestartSound() {
        mState.c1Alive = true
        audio.channel1.resetSound()
        c1ToneSweep.reset()
        c1LengthCounter.reset()
        c1VolumeEnv.reset()
        c1UpdateFrequency()
    }

    fun c2RestartSound() {
        mState.c2Alive = true
        audio.channel2.resetSound()
        c2LengthCounter.reset()
        c2VolumeEnv.reset()
    }

    fun c3RestartSound() {
        mState.c3Alive = true
        audio.channel3.resetSound()
        c3LengthCounter.reset()
    }

    fun c4RestartSound() {
        mState.c4Alive = true
        audio.channel4.resetSound()
        c4LengthCounter.reset()
        c4VolumeEnv.reset()
    }

}