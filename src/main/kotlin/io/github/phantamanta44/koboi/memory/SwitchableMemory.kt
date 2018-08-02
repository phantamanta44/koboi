package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.util.toUnsignedInt

interface IMemoryBankController {

    companion object {

        fun createController(rom: ByteArray): IMemoryBankController {
            return when (rom[0x147].toUnsignedInt()) { // TODO implement
                0x00 -> MbcRomOnly(rom)
                0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E -> Mbc5(rom)
                else -> throw IllegalArgumentException("Unsupported MBC type")
            }
        }

    }

    val romArea: IMemoryArea

    val ramArea: IMemoryArea

}

class MemoryBankSwitcher(bankCount: Int, var active: Int, factory: (Int) -> IMemoryArea) {

    private val banks: Array<IMemoryArea> = Array(bankCount, factory)

    val memoryArea: IMemoryArea = SwitchableMemoryArea(banks[0].length)

    private inner class SwitchableMemoryArea(override val length: Int) : IMemoryArea {

        override fun read(addr: Int): Byte = banks[active].read(addr)

        override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = banks[active].readRange(firstAddr, lastAddr)

        override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
            banks[active].write(addr, *values, start = start, length = length)
        }

    }

}

class MbcRomOnly(rom: ByteArray) : IMemoryBankController {

    override val romArea: IMemoryArea = StaticRomArea(rom, 0, 32768)

    override val ramArea: IMemoryArea = SimpleMemoryArea(8192)

}

class Mbc5(rom: ByteArray) : IMemoryBankController {

    private val romCtrl: MemoryBankSwitcher = MemoryBankSwitcher(512, 1) { StaticRomArea(rom, 16384 * it, 16384) }

    private val ramCtrl: MemoryBankSwitcher = MemoryBankSwitcher(16, 0) { SimpleMemoryArea(8192) } // TODO impl

    override val ramArea: ToggleableMemoryArea = ToggleableMemoryArea(ramCtrl.memoryArea, false)

    override val romArea: IMemoryArea = DisjointMemoryArea(
            MappedMemoryArea(
                    StaticRomArea(rom, 0, 16384), // 0000-3FFF
                    romCtrl.memoryArea // 4000-7FFF
            ),
            MappedMemoryArea(
                    ControlMemoryArea(8192) { // 0000-1FFF
                        ramArea.state = it.toInt() and 0x0F == 0x0A
                    },
                    ControlMemoryArea(4096) { // 2000-2FFF
                        romCtrl.active = (romCtrl.active and 0x100) or it.toUnsignedInt()
                    },
                    ControlMemoryArea(4096) { // 3000-3FFF
                        romCtrl.active = (romCtrl.active and 0xFF) or ((it.toInt() and 1) shl 8)
                    },
                    ControlMemoryArea(8192) { // 4000-5FFF
                        ramCtrl.active = it.toUnsignedInt()
                    },
                    UnusableMemoryArea(8192) // 6000-7FFF
            )
    )

}