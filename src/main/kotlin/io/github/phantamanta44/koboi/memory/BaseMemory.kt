package io.github.phantamanta44.koboi.memory

import kotlin.math.min

open class SimpleMemoryArea(final override val length: Int) : IMemoryArea {

    private val memory: ByteArray = ByteArray(length)

    override fun read(addr: Int): Byte = memory[addr]

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = SimpleMemoryRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        System.arraycopy(values, start, memory, addr, length)
    }

    private inner class SimpleMemoryRange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = memory[first + index]

        override fun toArray(): ByteArray = memory.copyOfRange(first, last)

        override val length: Int
            get() = last - first

    }

}

class ControlMemoryArea(override val length: Int, private val callback: (Byte) -> Unit) : IMemoryArea {

    override fun read(addr: Int): Byte {
        throw IllegalReadException()
    }

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange {
        throw IllegalReadException()
    }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        callback(values[0])
    }

}

class ToggleableMemoryArea(val backing: IMemoryArea, var state: Boolean) : IMemoryArea {

    override val length: Int
        get() = backing.length

    override fun read(addr: Int): Byte = if (state) backing.read(addr) else 0xFF.toByte()

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = ToggleableMemoryRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        if (state) backing.write(addr, *values, start = start, length = length)
    }

    private inner class ToggleableMemoryRange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = if (state) backing.read(first + index) else 0xFF.toByte()

        override fun toArray(): ByteArray {
            return if (state) backing.readRange(first, last).toArray() else ByteArray(length, { 0xFF.toByte() })
        }

        override val length: Int
            get() = last - first

    }

}

class EchoMemoryArea(private val delegate: IMemoryArea, override val length: Int) : IMemoryArea {

    override fun read(addr: Int): Byte = delegate.read(addr)

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = delegate.readRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        delegate.write(addr, *values, start = start, length = length)
    }

}

class DisjointMemoryArea(private val readDelegate: IMemoryArea, private val writeDelegate: IMemoryArea) : IMemoryArea {

    override val length: Int
        get() = readDelegate.length

    init {
        if (readDelegate.length != writeDelegate.length) {
            throw IllegalArgumentException("Mismatched lengths: r ${readDelegate.length} vs w ${writeDelegate.length}")
        }
    }

    override fun read(addr: Int): Byte = readDelegate.read(addr)

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = readDelegate.readRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        writeDelegate.write(addr, *values, start = start, length = length)
    }

}

class UnusableMemoryArea(override val length: Int) : IMemoryArea {

    override fun read(addr: Int): Byte {
        throw UnsupportedOperationException()
    }

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange {
        throw UnsupportedOperationException()
    }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        throw UnsupportedOperationException()
    }

}

class MappedMemoryArea(vararg segments: IMemoryArea) : IMemoryArea {

    private val segments: MutableList<Pair<Int, IMemoryArea>> = mutableListOf()
    override val length: Int

    init {
        var length = 0
        for (segment in segments) {
            this.segments.add(Pair(length, segment))
            length += segment.length
        }
        this.length = length
    }

    override fun read(addr: Int): Byte {
        val index = segments.indexOfFirst { it.first > addr }
        val segment = if (index == -1) segments.last() else segments[index - 1]
        return segment.second.read(addr - segment.first)
    }

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange {
        return MappedMemoryRange(firstAddr, lastAddr)
    }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        var toWrite = length
        var index = segments.indexOfFirst { it.first > addr }
        val firstSegment = if (index == -1) segments.last() else segments[index - 1]
        val firstSegmentMaxWrite = min(firstSegment.second.length - (addr - firstSegment.first), toWrite)
        firstSegment.second.write(addr - firstSegment.first, *values, start = start, length = firstSegmentMaxWrite)
        if (firstSegmentMaxWrite != toWrite) {
            toWrite -= firstSegmentMaxWrite
            while (toWrite > 0) {
                val segment = segments[index++]
                val segmentMaxWrite = min(segment.second.length, toWrite)
                segment.second.write(0, *values, start = length - toWrite, length = segmentMaxWrite)
                toWrite -= segmentMaxWrite
            }
        }
    }

    private inner class MappedMemoryRange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = read(first + index)

        override fun toArray(): ByteArray = ByteArray(length, { get(it) }) // FIXME god is this inefficient

        override val length: Int
            get() = last - first

    }

}