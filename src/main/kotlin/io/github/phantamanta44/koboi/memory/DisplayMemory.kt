package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.util.PropDel

class LcdControlRegister : BitwiseRegister() {

    var lcdDisplayEnable: Boolean by delegateBit(7)

    var windowTileMapDisplaySelect: Boolean by delegateBit(6)

    var windowDisplayEnable: Boolean by delegateBit(5)

    var tileDataSelect: Boolean by delegateBit(4)

    var bgTileMapDisplaySelect: Boolean by delegateBit(3)

    var spriteSize: Boolean by delegateBit(2)

    var spriteDisplayEnable: Boolean by delegateBit(1)

    var bgState: Boolean by delegateBit(0)

    override fun typeAt(addr: Int): String = "LcdControl"

}

class LcdStatusRegister : BitwiseRegister(0b01111100) {

    var intLyCompare: Boolean by delegateBit(6)

    var intOam: Boolean by delegateBit(5)

    var intVBlank: Boolean by delegateBit(4)

    var intHBlank: Boolean by delegateBit(3)

    var lyComparison: Boolean by delegateBit(2)

    var modeUpper: Boolean by delegateBit(1)

    var modeLower: Boolean by delegateBit(0)

    override fun typeAt(addr: Int): String = "LcdStatus"

}

class ColourPaletteSwicher {

    private val memory: ByteArray = ByteArray(64)
    private var active: Int by PropDel.observe(0) { accessMemory.directObserver.onMemMutate(0, 1) }

    val controlMemory: PaletteControlRegister = PaletteControlRegister()
    val accessMemory: PaletteAccessRegister = PaletteAccessRegister()

    fun getColours(palette: Int, colour: Int): Triple<Int, Int, Int> {
        val index = palette * 8 + colour * 2
        val low = memory[index].toInt()
        val high = memory[index + 1].toInt()
        return Triple(low and 0b00011111,
                ((low and 0b11100000) ushr 5) or ((high and 0b00000011) shl 3),
                (high and 0b01111100) ushr 2)
    }

    inner class PaletteControlRegister : BitwiseRegister() {

        var autoIncrement: Boolean by delegateBit(7)

        override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
            value = values[0]
            active = value.toInt() and 0b00011111
        }

        override fun typeAt(addr: Int): String = "RPalCtrl"

    }

    inner class PaletteAccessRegister : DirectObservableMemoryArea() {

        override val length: Int
            get() = 1

        override fun read(addr: Int, direct: Boolean): Byte = memory[active]

        override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = PaletteAccessMemoryRange()

        override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
            memory[active] = values[0]
            directObserver.onMemMutate(0, 1)
            if (controlMemory.autoIncrement && !direct) {
                active = (active + 1) % 64
                controlMemory.value = ((controlMemory.value.toInt() and 0b11000000) or active).toByte()
            }
        }

        override fun typeAt(addr: Int): String = "RPalAccess"

        private inner class PaletteAccessMemoryRange : IMemoryRange {

            override val length: Int
                get() = 1

            override fun get(index: Int): Byte = memory[active]

            override fun toArray(): ByteArray = byteArrayOf(memory[active])

        }

    }

}

val MONO_COLOURS: List<Triple<Int, Int, Int>> = listOf(
        Triple(0x1F, 0x1F, 0x1F), Triple(0x15, 0x15, 0x15), Triple(0x0B, 0x0B, 0x0B), Triple(0, 0, 0))

class MonoPaletteRegister : BitwiseRegister() {

    fun getColour(colour: Int): Triple<Int, Int, Int> {
        val shiftFactor = colour * 2
        return MONO_COLOURS[readMaskedInt(0b11 shl shiftFactor, shiftFactor)]
    }

}