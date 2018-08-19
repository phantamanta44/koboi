package io.github.phantamanta44.koboi

import io.github.phantamanta44.koboi.audio.AudioManager
import io.github.phantamanta44.koboi.audio.IAudioInterface
import io.github.phantamanta44.koboi.cpu.*
import io.github.phantamanta44.koboi.debug.DebugTarget
import io.github.phantamanta44.koboi.debug.IDebugProvider
import io.github.phantamanta44.koboi.debug.IDebugSession
import io.github.phantamanta44.koboi.graphics.DisplayController
import io.github.phantamanta44.koboi.graphics.GbRenderer
import io.github.phantamanta44.koboi.graphics.GbcRenderer
import io.github.phantamanta44.koboi.input.InputManager
import io.github.phantamanta44.koboi.memory.*
import io.github.phantamanta44.koboi.plugin.artemis.ArtemisDebugger
import io.github.phantamanta44.koboi.plugin.glfrontend.GlDisplay
import io.github.phantamanta44.koboi.plugin.jinput.JInputInputProvider
import io.github.phantamanta44.koboi.plugin.jxsound.JxSoundAudioInterface
import io.github.phantamanta44.koboi.util.GameboyType
import io.github.phantamanta44.koboi.util.PropDel
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt
import java.io.File

class GameEngine(rom: ByteArray) : IDirectMemoryObserver {

    companion object {

        fun tryInit(romFile: File) {
            Loggr.info("Attempting to read ROM file at ${romFile.absolutePath}...")
            var time = -System.nanoTime()
            val rom = romFile.readBytes()
            time += System.nanoTime()
            Loggr.info("Read ROM file in ${time / 1000000F} ms.")

            Loggr.info("Attempting to initialize game engine...")
            time = -System.nanoTime()
            val engine = GameEngine(rom)
            time += System.nanoTime()
            Loggr.info("Initialized game engine in ${time / 1000000F} ms.")

            Loggr.info("Beginning emulation...")
            engine.begin()
        }

    }

    val memory: DirectObservableMemoryArea
    val cpu: Cpu
    val dma: DmaTransferHandler
    val ppu: DisplayController
    val audio: AudioManager
    val clock: Timer
    val input: InputManager

    private val debug: IDebugProvider
    private var debugTarget: DebugTarget? = null
    private var _debugSession: IDebugSession? = null

    val debugSession: IDebugSession? by PropDel.r(::_debugSession)

    var gameTick: Long = 0L
    var testOutput: String = ""

    init { // TODO distinguish gb, cgb, whatever else
        // load boot rom
        val gbType = if (rom[0x0143] == 0.toByte()) GameboyType.GAMEBOY else GameboyType.GAMEBOY_COLOUR
        Loggr.debug("Detected ${gbType.typeName} rom.")
        val bootrom = javaClass.getResource("/boot/boot_${gbType.typeName}.bin").readBytes()

        // init cart rom
        val mbc = IMemoryBankController.createController(rom) // 0000-7FFF cart rom
        Loggr.debug("Using MBC of type ${mbc.javaClass.simpleName}")

        // init wram
        val wramSwitcher = MemoryBankSwitcher(gbType.wramBankCount, 1, { SimpleMemoryArea(4096) })
        val memWram = MappedMemoryArea(wramSwitcher.banks[0], wramSwitcher.memoryArea) // C000-DFFF wram
        val memWramControl = ObservableRegister { wramSwitcher.active = Math.max(1, it.toInt() and 7) } // FF70 wram bank switch

        // init interrupt flags
        val memIntReq = InterruptRegister() // FF0F interrupt request
        val memIntEnable = InterruptRegister() // FFFF interrupt enable

        // init display and associated memory
        val display = GlDisplay()
        val vramSwitcher = MemoryBankSwitcher(2, 0, { SimpleMemoryArea(8192) }) // 8000-9FFF vram
        val memVramControl = ObservableRegister { vramSwitcher.active = it.toInt() and 1 } // FF4F vram bank switch
        val memLcdControl = LcdControlRegister() // FF40 lcd control
        val memLcdStatus = LcdStatusRegister() // FF41 lcd status
        val memDisplayScroll = SimpleMemoryArea(2) // FF42-FF43 display scroll y, x
        val memScanLine = ResettableRegister() // FF44 scan line y
        val memLyCompare = SingleByteMemoryArea() // FF45 scan line comparison
        // TODO maybe verify dma addresses? but that would be slow
        dma = DmaTransferHandler(this)
        val memDmaOam = ControlMemoryArea(1) { // FF46 oam dma
            dma.performDmaTransfer(DmaTransferMode.OAM, it.toUnsignedInt() shl 8, 160, 0xFE00)
        }
        val memMonoPaletteBg = MonoPaletteRegister() // FF47 monochrome background palette
        val memMonoPaletteSprite0 = MonoPaletteRegister() // FF48 monochrome sprite palette 0
        val memMonoPaletteSprite1 = MonoPaletteRegister() // FF49 monochrome sprite palette
        val memWindowPosition = SimpleMemoryArea(2) // FF4A-FF4B window y, (x-7)
        val memDmaVramAddresses = SimpleMemoryArea(4) // FF51-FF54 vram dma source, destination
        val dmaVramTriggerBacking = BitwiseRegister()
        val memDmaVramTrigger = DisjointMemoryArea(dmaVramTriggerBacking, ControlMemoryArea(1) { // FF55 vram dma trigger
            if (dma.isDmaTransferActive() && it.toInt() and 0x80 == 0) {
                dma.cancelTransfer()
            } else {
                dmaVramTriggerBacking.value = it
                dma.performDmaTransfer(
                        if (dmaVramTriggerBacking.readBit(7)) DmaTransferMode.VRAM_ATOMIC else DmaTransferMode.VRAM_H_BLANK,
                        memDmaVramAddresses.readShort(0).toInt() and 0b1111111111110000,
                        it.toInt() and 0b01111111,
                        memDmaVramAddresses.readShort(2).toInt() and 0b0001111111110000)
            }
        })
        val cgbPaletteBg = ColourPaletteSwicher() // FF68-FF69 background colour palettes
        val cgbPaletteSprite = ColourPaletteSwicher() // FF6A-FF6B sprite colour palettes

        // init timer and associated memory
        val memDivider = TimerDividerRegister(this) // FF04 clock divider
        val memTimerCounter = TimerCounterRegister(this) // FF05 timer counter
        val memTimerModulo = TimerModuloRegister(this, memTimerCounter) // FF06 timer modulo
        clock = Timer(memDivider, memTimerCounter, memTimerModulo, memIntReq, this)
        val memTimerControl = TimerControlRegister(clock) // FF07 timer control

        // init audio and associated memory
        val audioIface = JxSoundAudioInterface()

        val memAudio1Sweep = Ch1SweepRegister(this) // FF10 NR10 channel 1 sweep register
        val memAudio1LengthDuty = LengthDutyRegister(this, IAudioInterface::channel1, AudioManager::c1LengthCounter) // FF11 NR11 channel 1 length/wave pattern duty
        val memAudio1Volume = VolumeEnvelopeRegister(this, IAudioInterface::channel1, AudioManager::c1VolumeEnv) // FF12 NR12 channel 1 volume envelope
        val memAudio1FreqLo = FreqLowRegister(this, AudioManager::c1UpdateFrequency) // FF13 NR13 channel 1 frequency low
        val memAudio1FreqHi = FreqHighRegister(this, AudioManager::c1LengthCounter, AudioManager::c1UpdateFrequency, AudioManager::c1RestartSound) // FF14 NR14 channel 1 frequency high

        val memAudio2LengthDuty = LengthDutyRegister(this, IAudioInterface::channel2, AudioManager::c2LengthCounter) // FF16 NR21 channel 2 length/wave pattern duty
        val memAudio2Volume = VolumeEnvelopeRegister(this, IAudioInterface::channel2, AudioManager::c2VolumeEnv) // FF17 NR22 channel 2 volume envelope
        val memAudio2FreqLo = FreqLowRegister(this, AudioManager::c2UpdateFrequency) // FF18 NR23 channel 2 frequency low
        val memAudio2FreqHi = FreqHighRegister(this, AudioManager::c2LengthCounter, AudioManager::c2UpdateFrequency, AudioManager::c2RestartSound) // FF19 NR24 channel 2 frequency high

        val memAudio3Enable = Ch3EnableRegister(this) // FF1A NR30 channel 3 on/off
        val memAudio3Length = Ch3LengthRegister(this) // FF1B NR31 channel 3 sound length
        val memAudio3Volume = MaskedObservableRegister(0b01100000) { audioIface.channel3.volume = when (it.toInt() and 0b01100000) {
            0b00000000 -> 0F
            0b00100000 -> 1F
            0b01000000 -> 0.5F
            0b01100000 -> 0.25F
            else -> throw IllegalStateException(it.toString())
        } } // FF1C NR32 channel 3 output level
        val memAudio3FreqLo = FreqLowRegister(this, AudioManager::c3UpdateFrequency) // FF1D NR33 channel 3 frequency low
        val memAudio3FreqHi = FreqHighRegister(this, AudioManager::c3LengthCounter, AudioManager::c3UpdateFrequency, AudioManager::c3RestartSound) // FF1E NR34 channel 3 frequency high
        val memAudio3WavePattern = Ch3WavePatternMemoryArea(this, memAudio3Enable) // FF30-FF3F channel 3 wave pattern data

        val memAudio4Length = Ch4LengthRegister(this) // FF20 NR41 channel 4 sound length
        val memAudio4Volume = VolumeEnvelopeRegister(this, IAudioInterface::channel4, AudioManager::c4VolumeEnv) // FF21 NR42 channel 4 volume envelope
        val memAudio4PolyCounter = Ch4PolyCounterRegister(this) // FF22 NR43 channel 4 polynomial counter
        val memAudio4Control = Ch4ControlRegister(this) // FF23 NR44 channel 4 control

        val memAudioMasterVol = VInRegister() // FF24 NR50 VIn control
        val memAudioChannelVol = ChannelVolumeRegister(this) // FF25 NR51 per-channel output
        val memAudioKillSwitch = AudioKillSwitchRegister(this) // FF26 NR52 audio kill switch

        audio = AudioManager(audioIface, clock,
                memAudio1FreqLo, memAudio1FreqHi,
                memAudio2FreqLo, memAudio2FreqHi,
                memAudio3FreqLo, memAudio3FreqHi,
                memAudioKillSwitch)

        // init input and associated memory
        val memInput = JoypadRegister(memIntReq) // FF00 input register
        input = InputManager(memInput, JInputInputProvider())

        // init serial io and associated memory
        val memSioData = SingleByteMemoryArea() // FF01 serial transfer data
        val memSioControl = if (KoboiConfig.blarggMode) {
            ControlMemoryArea(1) {
                if (memSioData.value in 0x20..0x7E || memSioData.value == 0x0A.toByte()) {
                    val char = memSioData.value.toChar()
                    print(char)
                    testOutput += char
                    if (testOutput.endsWith("Failed")) throw IllegalStateException("Blargg!")
                } else {
                    throw IllegalArgumentException("${memSioData.value.toUnsignedHex()} ain't ascii!")
                }
            }
        } else {
            UnusableMemoryArea(1)
        } // FF02 serial transfer control

        // other random registers
        val memClockSpeed = ClockSpeedRegister() // FF4D clock speed control

        // build main memory region
        memory = gbType.wrapMemory(MappedMemoryArea(
                mbc.romArea, // 0000-7FFF cart rom

                vramSwitcher.memoryArea, // 8000-9FFF vram

                mbc.ramArea, // A000-BFFF cart ram

                // wram stuff
                memWram, // C000-DFFF wram
                EchoMemoryArea(memWram, 7680), // E000-FDFF echo of C000-DDFF

                // oam stuff
                SimpleMemoryArea(160), // FE00-FE9F oam

                UnusableMemoryArea(96), // FEA0-FEFF unusable

                memInput, // FF00 joypad register

                memSioData, // FF01-FF02 serial transfer data // TODO serial io
                memSioControl, // FF02 serial transfer control

                SimpleMemoryArea(1), // FF03 unused

                // timer stuff
                memDivider, // FF04 clock divider
                memTimerCounter, // FF05 timer counter
                memTimerModulo, // FF06 timer modulo
                memTimerControl, // FF07 time control

                SimpleMemoryArea(7), // FF08-FF0E unused

                memIntReq, // FF0F interrupt request

                // sound stuff
                memAudio1Sweep, // FF10 NR10 channel 1 sweep register
                memAudio1LengthDuty, // FF11 NR11 channel 1 length/wave pattern duty
                memAudio1Volume, // FF12 NR12 channel 1 volume envelope
                memAudio1FreqLo, // FF13 NR13 channel 1 frequency low
                memAudio1FreqHi, // FF14 NR14 channel 1 frequency high
                UnusableMemoryArea(1), // FF15 unused
                memAudio2LengthDuty, // FF16 NR21 channel 2 length/wave pattern duty
                memAudio2Volume, // FF17 NR22 channel 2 volume envelope
                memAudio2FreqLo, // FF18 NR23 channel 2 frequency low
                memAudio2FreqHi, // FF19 NR24 channel 2 frequency high
                memAudio3Enable, // FF1A NR30 channel 3 on/off
                memAudio3Length, // FF1B NR31 channel 3 sound length
                memAudio3Volume, // FF1C NR32 channel 3 output level
                memAudio3FreqLo, // FF1D NR33 channel 3 frequency low
                memAudio3FreqHi, // FF1E NR34 channel 3 frequency high
                UnusableMemoryArea(1), // FF1F unused
                memAudio4Length, // FF20 NR41 channel 4 sound length
                memAudio4Volume, // FF21 NR42 channel 4 volume envelope
                memAudio4PolyCounter, // FF22 NR43 channel 4 polynomial counter
                memAudio4Control, // FF23 NR44 channel 4 control
                memAudioMasterVol, // FF24 NR50 VIn control
                memAudioChannelVol, // FF25 NR51 per-channel output
                memAudioKillSwitch, // FF26 NR52 audio kill switch
                UnusableMemoryArea(9), // FF27-FF2F unused
                memAudio3WavePattern, // FF30-FF3F channel 3 wave pattern data

                // display stuff
                memLcdControl, // FF40 lcd control
                memLcdStatus, // FF41 lcd status
                memDisplayScroll, // FF42-FF43 display scroll y, x
                memScanLine, // FF44 scan line y
                memLyCompare, // FF45 scan line comparison
                memDmaOam, // FF46 oam dma
                memMonoPaletteBg, // FF47 monochrome background palette
                memMonoPaletteSprite0, // FF48 monochrome sprite palette 0
                memMonoPaletteSprite1, // FF49 monochrome sprite palette 1
                memWindowPosition, // FF4A-FF4B window y, (x-7)

                SingleByteMemoryArea(), // FF4C is-cgb-cart flag

                memClockSpeed, // FF4D clock speed

                SimpleMemoryArea(1), // FF4E unused

                memVramControl, // FF4F vram bank switch

                SingleByteMemoryArea(), // FF50 disable boot rom

                memDmaVramAddresses, // FF51-FF54 vram dma source, destination
                memDmaVramTrigger, // FF55 vram dma trigger

                SingleByteMemoryArea(), // FF56 cgb ir comms // TODO ir comms

                SimpleMemoryArea(17), // FF57-FF67 unused

                // cgb palette stuff
                cgbPaletteBg.controlMemory, // FF68 cgb bg palette control
                cgbPaletteBg.accessMemory, // FF69 cgb bg palette access
                cgbPaletteSprite.controlMemory, // FF6A cgb sprite palette control
                cgbPaletteSprite.accessMemory, // FF6B cgb sprite palette access

                SingleByteMemoryArea(), // FF6C dmg-cart-on-cgb-hardware flag

                SimpleMemoryArea(3), // FF6D-FF6F unused

                memWramControl, // FF70 wram bank switch

                // area 51
                SimpleMemoryArea(1), // FF71 unused
                SimpleMemoryArea(7), // FF71-FF77 some unknown stuff
                SimpleMemoryArea(7), // FF78-FF7F unused

                SimpleMemoryArea(127), // FF80-FFFE hram

                memIntEnable // FFFF interrupt enable
        ), bootrom, ::bootromUnmapped)
        memory.directObserver = this

        // create cpu
        cpu = Cpu(this, memIntReq, memIntEnable, memClockSpeed, memLcdControl)
        if (KoboiConfig.traceBootrom) cpu.backtrace.enabled = true

        // create ppu
        val scanLineRenderer = when (gbType) {
            GameboyType.GAMEBOY -> GbRenderer(memLcdControl, display, cpu, vramSwitcher,
                    memMonoPaletteBg, memMonoPaletteSprite0, memMonoPaletteSprite1)
            GameboyType.GAMEBOY_COLOUR -> GbcRenderer(memLcdControl, display, cpu, vramSwitcher,
                    cgbPaletteBg, cgbPaletteSprite)
        }
        ppu = DisplayController(cpu, scanLineRenderer, display, memLcdControl, memLcdStatus, memScanLine)

        // create debug provider
        debug = ArtemisDebugger()
    }

    private fun bootromUnmapped() {
        Loggr.debug("Bootrom unmapped.")
        cpu.backtrace.enabled = true
    }

    override fun onMemMutate(addr: Int, length: Int) {
        _debugSession?.onMemoryMutate(addr, length)
    }

    fun begin() {
        Loggr.engine = this
        try {
            ppu.start()
            if (KoboiConfig.debug) startDebugSession()
            while (cpu.alive.get()) {
                debugTarget?.onBeforeGameLoop()
                gameLoop()
            }
            Loggr.info("Cpu killed; freeing resources...")
            if (debugTarget != null) endDebugSession()
            ppu.kill()
            audio.kill()
            Loggr.info("Exiting!")
        } catch (e: EmulationException) {
            ppu.kill()
            audio.kill()
            e.printStackTrace()
            e.printCpuState()
            if (_debugSession != null && KoboiConfig.debugOnCrash) startDebugSession()
        }
    }

    private fun gameLoop() {
        input.cycle()
        speedDependentCycle()
        if (cpu.doubleClock) speedDependentCycle()
        ppu.cycle()
        audio.cycle()
        dma.cycle()
//        throttleThread(238L) // TODO figure out what's wrong with this
        ++gameTick
    }

    private fun speedDependentCycle() {
        clock.cycle()
        cpu.cycle()
    }

    private fun startDebugSession() {
        Loggr.info("Starting debug session!")
        DebugTarget(this).let {
            debugTarget = it
            debug.startDebugging(it).let { sess ->
                _debugSession = sess
                it.session = sess
            }
        }
    }

    fun endDebugSession() {
        debugTarget?.let {
            Loggr.info("Ending debug session!")
            debugTarget = null
            it.unfreeze()
            _debugSession?.kill()
            _debugSession = null
        }
    }

}
