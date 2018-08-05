package io.github.phantamanta44.koboi.memory

class GbMemory(private val mainMemory: IMemoryArea, private val bootrom: ByteArray) : IMemoryArea {

    override val length: Int
        get() = mainMemory.length

    private var bootromActive: Boolean = true

    override fun read(addr: Int): Byte {
        return if (bootromActive && addr < bootrom.size) bootrom[addr] else mainMemory.read(addr)
    }

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange {
        return GbMemoryRange(firstAddr, lastAddr)
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

    private inner class GbMemoryRange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = read(first + index)

        override fun toArray(): ByteArray = ByteArray(length, { get(it) }) // FIXME god is this inefficient

        override val length: Int
            get() = last - first

    }

}

class GbcMemory(private val mainMemory: IMemoryArea, private val bootrom: ByteArray) : IMemoryArea {

    override val length: Int
        get() = mainMemory.length

    private var bootromActive: Boolean = true

    override fun read(addr: Int): Byte {
        return if (bootromActive && addr < bootrom.size && (addr < 0x100 || addr >= 0x200)) {
            bootrom[addr]
        } else {
            mainMemory.read(addr)
        }
    }

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange {
        return GbcMemoryRange(firstAddr, lastAddr)
    }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        if (bootromActive) {
            // assumes that writes only occur entirely within bootrom or entirely outside
            if (addr < bootrom.size && (addr < 0x100 || addr >= 0x200)) {
                System.arraycopy(values, start, bootrom, addr, length)
                return
            } else if (addr == 0xFF50) {
                // assumes this address is only written to if we're disabling the bootrom
                bootromActive = false
            }
        }
        mainMemory.write(addr, *values, start = start, length = length)
    }

    private inner class GbcMemoryRange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = read(first + index)

        override fun toArray(): ByteArray = ByteArray(length, { get(it) }) // FIXME god is this inefficient

        override val length: Int
            get() = last - first

    }

}