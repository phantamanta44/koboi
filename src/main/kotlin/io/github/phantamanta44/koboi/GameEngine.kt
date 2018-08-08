package io.github.phantamanta44.koboi

import io.github.phantamanta44.koboi.cpu.*
import io.github.phantamanta44.koboi.graphics.DisplayController
import io.github.phantamanta44.koboi.graphics.GbRenderer
import io.github.phantamanta44.koboi.graphics.GbcRenderer
import io.github.phantamanta44.koboi.input.InputManager
import io.github.phantamanta44.koboi.memory.*
import io.github.phantamanta44.koboi.plugin.glfrontend.GlDisplay
import io.github.phantamanta44.koboi.plugin.jinput.JInputInputProvider
import io.github.phantamanta44.koboi.util.DebugShell
import io.github.phantamanta44.koboi.util.GameboyType
import io.github.phantamanta44.koboi.util.toUnsignedInt
import java.io.File

class GameEngine(rom: ByteArray) {

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

            if (KoboiConfig.debugShell) {
                DebugShell(engine).begin()
            } else {
                Loggr.info("Beginning emulation...")
                engine.begin()
            }
        }

    }

    val memory: IMemoryArea
    val cpu: Cpu
    val dma: DmaTransferHandler
    val ppu: DisplayController
    val clock: Timer
    val input: InputManager

    init { // TODO distinguish gb, cgb, whatever else
        // load boot rom
        val gbType = if (rom[0x0143] == 0.toByte()) GameboyType.GAMEBOY else GameboyType.GAMEBOY_COLOUR
        Loggr.debug("Detected ${gbType.typeName} rom.")
        val bootrom = javaClass.getResource("/boot/boot_${gbType.typeName}.bin").readBytes()

        // init cart rom
        val mbc = IMemoryBankController.createController(rom) // 0000-7FFF cart rom

        // init wram
        val wramSwitcher = MemoryBankSwitcher(gbType.wramBankCount, 1, { SimpleMemoryArea(4096) })
        val memWram = MappedMemoryArea(SimpleMemoryArea(4096), wramSwitcher.memoryArea) // C000-DFFF wram
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
        val memMonoPalettes = SimpleMemoryArea(3) // FF47-FF49 monochrome palettes
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
        val memDivider = ResettableRegister() // FF04 clock divider
        val memTimerCounter = SingleByteMemoryArea() // FF05 timer counter
        val memTimerModulo = SingleByteMemoryArea() // FF06 timer modulo
        val memTimerControl = TimerControlRegister(this) // FF07 timer control
        clock = Timer(memDivider, memTimerCounter, memTimerModulo, memTimerControl, memIntReq)

        // init input and associated memory
        val memInput = JoypadRegister(memIntReq) // FF00 input register
        input = InputManager(memInput, memIntReq, JInputInputProvider())

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

                SimpleMemoryArea(2), // FF01-FF02 serial io ports // TODO serial io

                SimpleMemoryArea(1), // FF03 unused

                // timer stuff
                memDivider, // FF04 clock divider
                memTimerCounter, // FF05 timer counter
                memTimerModulo, // FF06 timer modulo
                memTimerControl, // FF07 time control

                SimpleMemoryArea(7), // FF08-FF0E unused

                memIntReq, // FF0F interrupt request

                // sound stuff
                SimpleMemoryArea(48), // FF10-FF3F sound io ports // TODO sound

                // display stuff
                memLcdControl, // FF40 lcd control
                memLcdStatus, // FF41 lcd status
                memDisplayScroll, // FF42-FF43 display scroll y, x
                memScanLine, // FF44 scan line y
                memLyCompare, // FF45 scan line comparison
                memDmaOam, // FF46 oam dma
                memMonoPalettes, // FF47-FF49 monochrome palettes
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
        ), bootrom)

        // create cpu
        cpu = Cpu(memory, memIntReq, memIntEnable, memClockSpeed, memLcdControl)

        // create ppu
        val scanLineRenderer = when (gbType) {
            GameboyType.GAMEBOY -> GbRenderer(memLcdControl, display, cpu, vramSwitcher)
            GameboyType.GAMEBOY_COLOUR -> GbcRenderer(memLcdControl, display, cpu, vramSwitcher,
                    cgbPaletteBg, cgbPaletteSprite)
        }
        ppu = DisplayController(cpu, scanLineRenderer, display, memLcdControl, memLcdStatus, memScanLine)
    }

    fun begin() {
        try {
            ppu.start()
            while (cpu.alive.get()) {
                input.cycle()
                cpu.cycle()
                if (cpu.doubleClock) cpu.cycle()
                ppu.cycle()
                dma.cycle()
                clock.cycle()
            }
            throw EmulationException(cpu, IllegalStateException("CPU operation abruptly halted!"))
        } catch (e: EmulationException) {
            ppu.kill()
            e.printStackTrace()
            e.printCpuState()
            if (KoboiConfig.debugShellOnCrash) {
                DebugShell(this).begin()
            }
        }
    }

}
