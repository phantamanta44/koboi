package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.util.cons
import io.github.phantamanta44.koboi.util.getHighByte
import io.github.phantamanta44.koboi.util.getLowByte

interface IMemoryArea {

    val length: Int

    fun read(addr: Int, direct: Boolean = false): Byte

    fun readShort(addr: Int, direct: Boolean = false): Short = Short.cons(read(addr + 1, direct), read(addr, direct))

    fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange

    fun readLength(firstAddr: Int, length: Int) = readRange(firstAddr, firstAddr + length)

    fun write(addr: Int, vararg values: Byte, start: Int = 0, length: Int = values.size, direct: Boolean = false)

    fun write(addr: Int, values: IMemoryRange, start: Int = 0, length: Int = values.length, direct: Boolean = false) {
        write(addr, *values.toArray(), start = start, length = length, direct = direct)
    }

    fun write(addr: Int, value: Short, direct: Boolean = false) {
        write(addr, value.getLowByte(), value.getHighByte(), direct = direct)
    }

    fun typeAt(addr: Int): String

    fun readRangeDirect(firstAddr: Int, lastAddr: Int): IMemoryRange = DirectMemoryRange(this, firstAddr, lastAddr)

}

abstract class DirectObservableMemoryArea : IMemoryArea {

    open var directObserver: IDirectMemoryObserver = IDirectMemoryObserver.Noop()

}

class DirectMemoryRange(val memory: IMemoryArea, val firstAddr: Int, lastAddr: Int) : IMemoryRange {

    override val length: Int = lastAddr - firstAddr

    override fun get(index: Int): Byte = memory.read(firstAddr + index, direct = true)

    override fun toArray(): ByteArray = ByteArray(length, ::get)

}

interface IMemoryRange : Iterable<Byte> {

    val length: Int

    operator fun get(index: Int): Byte

    fun toArray(): ByteArray

    override fun iterator(): ByteIterator {
        return MemoryRangeIterator(this)
    }

}

class MemoryRangeIterator(private val range: IMemoryRange) : ByteIterator() {

    private var index: Int = 0

    override fun hasNext(): Boolean = index < range.length

    override fun nextByte(): Byte = range[index++]

}

interface IDirectMemoryObserver {

    fun onMemMutate(addr: Int, length: Int)

    class Noop : IDirectMemoryObserver {

        override fun onMemMutate(addr: Int, length: Int) {
            // NO-OP
        }

    }

}