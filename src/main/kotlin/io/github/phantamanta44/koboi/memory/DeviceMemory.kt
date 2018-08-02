package io.github.phantamanta44.koboi.memory

class MasterMemoryArea(private val mainMemory: IMemoryArea, private val bootrom: ByteArray) : IMemoryArea {

    override val length: Int
        get() = mainMemory.length

    private var bootromActive: Boolean = true

    override fun read(addr: Int): Byte {
        return if (bootromActive && addr < bootrom.size) bootrom[addr] else mainMemory.read(addr)
    }

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange {
        return MMARange(firstAddr, lastAddr)
    }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        if (bootromActive) {
            // assumes that writes only occur entirely within bootrom or entirely outside
            if (addr < bootrom.size) {
                System.arraycopy(values, start, bootrom, addr, length)
                return
            } else if (addr == 0xFF50) {
                // assumes this address is only written to if we're disabling the bootrom
                bootromActive = false
            }
        }
        mainMemory.write(addr, *values, start = start, length = length)
    }

    private inner class MMARange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = read(first + index)

        override fun toArray(): ByteArray = ByteArray(length, { get(it) }) // FIXME god is this inefficient

        override val length: Int
            get() = last - first

    }

}

class StaticRomArea(private val rom: ByteArray, private val start: Int = 0, override val length: Int = rom.size) : IMemoryArea {

    override fun read(addr: Int): Byte = rom[start + addr]

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = StaticRomRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        throw IllegalWriteException()
    }

    private inner class StaticRomRange(val first: Int, val last: Int) : IMemoryRange {

        override val length: Int = last - first

        override fun get(index: Int): Byte = rom[start + first + index]

        override fun toArray(): ByteArray = rom.copyOfRange(start + first, start + last)

    }

}