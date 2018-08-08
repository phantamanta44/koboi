package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.util.toShortHex
import io.github.phantamanta44.koboi.util.toUnsignedInt

interface IMemoryBankController {

    companion object {

        fun createController(rom: ByteArray): IMemoryBankController {
            val mbcType = rom[0x147].toUnsignedInt()
            return when (mbcType) { // TODO implement the rest of the MBCs
                0x00 -> MbcRomOnly(rom)
                0x01, 0x02, 0x03 -> Mbc1(rom)
                0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E -> Mbc5(rom)
                else -> throw IllegalArgumentException("Unsupported MBC type ${mbcType.toShortHex()}")
            }
        }

    }

    val romArea: IMemoryArea

    val ramArea: IMemoryArea

}

class MemoryBankSwitcher(bankCount: Int, var active: Int, factory: (Int) -> IMemoryArea) {

    val banks: Array<IMemoryArea> = Array(bankCount, factory)

    val memoryArea: IMemoryArea = SwitchableMemoryArea(banks[0].length)

    private inner class SwitchableMemoryArea(override val length: Int) : IMemoryArea {

        override fun read(addr: Int, direct: Boolean): Byte = banks[active].read(addr, direct)

        override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = banks[active].readRange(firstAddr, lastAddr)

        override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
            banks[active].write(addr, *values, start = start, length = length, direct = direct)
        }

        override fun typeAt(addr: Int): String = "Switch{ ${banks[active].typeAt(addr)} }"

    }

}

class MbcRomOnly(rom: ByteArray) : IMemoryBankController {

    override val romArea: IMemoryArea = StaticRomArea(rom, 0, 32768)

    override val ramArea: IMemoryArea = SimpleMemoryArea(8192)

}

class Mbc1(rom: ByteArray) : IMemoryBankController {

    private val romCtrl: MemoryBankSwitcher = MemoryBankSwitcher(128, 1) {
        if (it == 0x00 || it == 0x20 || it == 0x40 || it == 0x60) {
            UnusableMemoryArea(16384)
        } else {
            StaticRomArea(rom, 16384 * it, 16384)
        }
    }

    private val ramCtrl: MemoryBankSwitcher = MemoryBankSwitcher(4, 0) { SimpleMemoryArea(8192) }

    override val ramArea: ToggleableMemoryArea = ToggleableMemoryArea(ramCtrl.memoryArea, false)

    private var writeType: Boolean = false

    override val romArea: IMemoryArea = DisjointMemoryArea(
            MappedMemoryArea(
                    StaticRomArea(rom, 0, 16384), // 0000-3FFF
                    romCtrl.memoryArea // 4000-7FFF
            ),
            MappedMemoryArea(
                    ControlMemoryArea(8192) { // 0000-1FFF
                        ramArea.state = it.toInt() and 0x0F == 0x0A
                    },
                    ControlMemoryArea(8192) { // 2000-3FFF
                        romCtrl.active = (romCtrl.active and 0b01100000) or (it.toInt() and 0b00011111)
                        fixRomBank()
                    },
                    ControlMemoryArea(8192) { // 4000-5FFF
                        if (writeType) {
                            romCtrl.active = (romCtrl.active and 0b00011111) or ((it.toInt() and 0b00000011) shl 5)
                            fixRomBank()
                        } else {
                            ramCtrl.active = it.toInt() and 0b00000011
                        }
                    },
                    ControlMemoryArea(8192) { // 6000-7FFF
                        if (it.toInt() and 1 == 0) {
                            ramCtrl.active = 0
                            writeType = false
                        } else {
                            romCtrl.active = romCtrl.active and 0b00011111
                            fixRomBank()
                            writeType = true
                        }
                    }
            )
    )

    private fun fixRomBank() {
        when (romCtrl.active) {
            0x00 -> romCtrl.active = 0x01
            0x20 -> romCtrl.active = 0x21
            0x40 -> romCtrl.active = 0x41
            0x60 -> romCtrl.active = 0x61
        }
    }

}

class Mbc5(rom: ByteArray) : IMemoryBankController {

    private val romCtrl: MemoryBankSwitcher = MemoryBankSwitcher(512, 1) { StaticRomArea(rom, 16384 * it, 16384) }

    private val ramCtrl: MemoryBankSwitcher = MemoryBankSwitcher(16, 0) { SimpleMemoryArea(8192) }

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