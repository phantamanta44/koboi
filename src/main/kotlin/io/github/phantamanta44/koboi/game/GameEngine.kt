package io.github.phantamanta44.koboi.game

import io.github.phantamanta44.koboi.KoboiConfig
import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.cpu.EmulationException
import io.github.phantamanta44.koboi.cpu.Timer
import io.github.phantamanta44.koboi.graphics.DisplayController
import io.github.phantamanta44.koboi.memory.*
import io.github.phantamanta44.koboi.plugin.glfrontend.GlDisplay
import io.github.phantamanta44.koboi.util.DebugShell
import io.github.phantamanta44.koboi.util.GameboyType
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
    val ppu: DisplayController
    val clock: Timer

    init { // TODO distinguish gb, cgb, whatever else
        // load boot rom
        val gbType = if (rom[0x0143] == 0.toByte()) GameboyType.GAMEBOY else GameboyType.GAMEBOY_COLOUR
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
        val memVram = SimpleMemoryArea(8192) // 8000-9FFF vram // TODO switchable vram
        val memLcdControl = LcdControlRegister() // FF40 lcd control
        val memLcdStatus = LcdStatusRegister() // FF41 lcd status
        val memDisplayScroll = SimpleMemoryArea(2) // FF42-FF43 display scroll y, x
        val memScanLine = ResettableRegister() // FF44 scan line y
        val memLyCompare = SingleByteMemoryArea() // FF45 scan line comparison
        val memMonoPalettes = SimpleMemoryArea(3) // FF47-FF49 monochrome palettes
        val memWindowPosition = SimpleMemoryArea(2) // FF4A-FF4B window y, (x-7)

        // init timer and associated memory
        val memDivider = ResettableRegister() // FF04 clock divider
        val memTimerCounter = SingleByteMemoryArea() // FF05 timer counter
        val memTimerModulo = SingleByteMemoryArea() // FF06 timer modulo
        val memTimerControl = TimerControlRegister(this) // FF07 timer control
        clock = Timer(memDivider, memTimerCounter, memTimerModulo, memTimerControl, memIntReq)

        // other random registers
        val memClockSpeed = ClockSpeedRegister() // FF4D clock speed control

        // build main memory region
        memory = gbType.wrapMemory(MappedMemoryArea(
                mbc.romArea, // 0000-7FFF cart rom

                memVram, // 8000-9FFF vram // TODO vram

                mbc.ramArea, // A000-BFFF cart ram

                // wram stuff
                memWram, // C000-DFFF wram
                EchoMemoryArea(memWram, 7680), // E000-FDFF echo of C000-DDFF

                // oam stuff
                SimpleMemoryArea(160), // FE00-FE9F oam // TODO oam

                UnusableMemoryArea(96), // FEA0-FEFF unusable

                SimpleMemoryArea(1), // FF00 joystick io // TODO joystick

                SimpleMemoryArea(2), // FF01-FF02 serial io ports // TODO serial io

                UnusableMemoryArea(1), // FF03 unused

                // timer stuff
                memDivider, // FF04 clock divider
                memTimerCounter, // FF05 timer counter
                memTimerModulo, // FF06 timer modulo
                memTimerControl, // FF07 time control

                UnusableMemoryArea(7), // FF08-FF0E unused

                memIntReq, // FF0F interrupt request

                // sound stuff
                SimpleMemoryArea(48), // FF10-FF3F sound io ports // TODO sound

                // display stuff
                memLcdControl, // FF40 lcd control
                memLcdStatus, // FF41 lcd status
                memDisplayScroll, // FF42-FF43 display scroll y, x
                memScanLine, // FF44 scan line y
                memLyCompare, // FF45 scan line comparison
                SingleByteMemoryArea(), // FF46 oam dma // TODO dma
                memMonoPalettes, // FF47-FF49 monochrome palettes
                memWindowPosition, // FF4A-FF4B window y, (x-7)

                SingleByteMemoryArea(), // FF4C is-cgb-cart flag

                memClockSpeed, // FF4D clock speed

                UnusableMemoryArea(1), // FF4E unused

                SingleByteMemoryArea(), // FF4F vram bank switch // TODO vram bank switch

                UnusableMemoryArea(1), // FF50 disable boot rom

                SimpleMemoryArea(5), // FF51-FF55 cgb vram dma // TODO dma

                SingleByteMemoryArea(), // FF56 cgb ir comms // TODO ir comms

                UnusableMemoryArea(17), // FF57-FF67 unused

                SimpleMemoryArea(4), // FF68-FF6B cgb colour palettes // TODO colour palettes

                SingleByteMemoryArea(), // FF6C dmg-cart-on-cgb-hardware flag

                UnusableMemoryArea(25), // FF6D-FF6F unused

                memWramControl, // FF70 wram bank switch

                // area 51
                UnusableMemoryArea(1), // FF71 unused
                SimpleMemoryArea(7), // FF71-FF77 some unknown stuff
                UnusableMemoryArea(7), // FF78-FF7F unused

                SimpleMemoryArea(127), // FF80-FFFE hram
                
                memIntEnable // FFFF interrupt enable
        ), bootrom)

        // create cpu
        cpu = Cpu(memory, memIntReq, memIntEnable, memClockSpeed)
        ppu = DisplayController(cpu, gbType.createRenderer(), display, memLcdControl, memLcdStatus, memScanLine)
    }

    fun begin() {
        try {
            ppu.start()
            while (cpu.alive.get()) {
                cpu.cycle()
                ppu.cycle()
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
