package io.github.phantamanta44.koboi.game

import io.github.phantamanta44.koboi.KoboiConfig
import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.cpu.EmulationException
import io.github.phantamanta44.koboi.memory.*
import io.github.phantamanta44.koboi.util.DebugShell
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

    init { // TODO distinguish gb, cgb, whatever else
        val bootromName = if (rom[0x0143] == 0.toByte()) "gb" else "cgb"
        val bootrom = javaClass.getResource("/boot_$bootromName.bin").readBytes()
        val mbc = IMemoryBankController.createController(rom)
        val wram = MappedMemoryArea(SimpleMemoryArea(4096), SimpleMemoryArea(4096))
        memory = MasterMemoryArea(MappedMemoryArea(
                mbc.romArea, // 0000-7FFF cart rom

                SimpleMemoryArea(8192), // 8000-9FFF vram // TODO vram

                mbc.ramArea, // A000-BFFF cart ram

                wram, // C000-DFFF wram // TODO switchable wram
                EchoMemoryArea(wram, 7680), // E000-FDFF echo of C000-DDFF

                SimpleMemoryArea(160), // FE00-FE9F oam // TODO oam

                UnusableMemoryArea(96), // FEA0-FEFF unusable

                SimpleMemoryArea(128), // FF00-FF7F io ports // TODO io

                SimpleMemoryArea(127), // FF80-FFFE hram
                SimpleMemoryArea(1) // FFFF interrupts-enabled flag
        ), bootrom) // TODO impl
        cpu = Cpu(memory)
    }

    fun begin() {
        try {
            while (cpu.alive) cpu.cycle()
        } catch (e: EmulationException) {
            e.printStackTrace()
            e.printCpuState()
            if (KoboiConfig.debugShellOnCrash) {
                DebugShell(this).begin()
            }
        }
    }

}
