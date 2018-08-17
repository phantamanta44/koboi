package io.github.phantamanta44.koboi.plugin.jxsound

import io.github.phantamanta44.koboi.audio.IAudioChannel
import io.github.phantamanta44.koboi.audio.IAudioInterface
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

class JxSoundAudioInterface : IAudioInterface {

    private val alive: AtomicBoolean = AtomicBoolean(true)

    override val channel1: JxChannel<SquareWaveProducer> = JxChannel(1048576, SquareWaveProducer())
    override val channel2: JxChannel<SquareWaveProducer> = JxChannel(1048576, SquareWaveProducer())
    override val channel3: JxChannel<ArbitraryWaveAudioProducer> = JxChannel(2097152, ArbitraryWaveAudioProducer())
    override val channel4: JxChannel<NoiseAudioProducer> = JxChannel(524288, NoiseAudioProducer())

    init {
        beginThread("1", channel1)
        beginThread("2", channel2)
        beginThread("3", channel3)
        beginThread("4", channel4)
    }

    private fun beginThread(number: String, channel: JxChannel<JxAudioProducer>) {
        thread(isDaemon = true, name = "JxSound channel $number thread") {
            while (alive.get()) channel.execute()
            channel.kill()
        }
    }

    override fun kill() {
        alive.set(false)
    }

}

class JxChannel<out G : JxAudioProducer>(freq: Int, override val generator: G) : IAudioChannel<G> {

    override var enabled: Boolean = false

    override var volume: Float = 1F
    override var outputLeft: Boolean = true
    override var outputRight: Boolean = true

    private val audioFormat: AudioFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 65536F, 8, 2, 2, 65536F, false)
    private val audioSink: SourceDataLine = AudioSystem.getSourceDataLine(audioFormat)

    private var bufLeft: Byte = 0
    private var bufRight: Byte = 0
    private val writeBuffer: ByteArray
    private var writeIndex: Int = 0
    private val audioBufferBoundary: Int

    private val cyclesPerSample: Int = 4194304 / freq
    private var sampleCounter: Int = 0
    private var outputCounter: Int = 0

    init {
        audioSink.open(audioFormat, 4096)
        writeBuffer = ByteArray(audioSink.bufferSize)
        audioBufferBoundary = writeBuffer.size / 2
        audioSink.start()
    }

    fun execute() {
        if (enabled) {
            if (++sampleCounter == cyclesPerSample) {
                sampleCounter = 0
                if (volume > 0) {
                    val sample = Math.round(generator.cycle().toInt() * volume * 0.15F).toByte()
                    bufLeft = if (outputLeft) sample else 0
                    bufRight = if (outputRight) sample else 0
                } else {
                    generator.cycle()
                    bufLeft = 0
                    bufRight = 0
                }
            }
        }
        if (++outputCounter == 64) {
            outputCounter = 0
            writeBuffer[writeIndex++] = bufLeft
            writeBuffer[writeIndex++] = bufRight
            if (writeIndex >= audioBufferBoundary) {
                audioSink.write(writeBuffer, 0, writeIndex)
                writeIndex = 0
            }
        }
    }

    override fun resetSound() {
        enabled = true
        generator.resetSound()
    }

    fun kill() {
        audioSink.drain()
        audioSink.stop()
        audioSink.close()
    }

}