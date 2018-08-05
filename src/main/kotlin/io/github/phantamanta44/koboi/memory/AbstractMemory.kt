package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.util.cons
import io.github.phantamanta44.koboi.util.getHighByte
import io.github.phantamanta44.koboi.util.getLowByte

interface IMemoryArea {

    val length: Int

    fun read(addr: Int): Byte

    fun readShort(addr: Int): Short = Short.cons(read(addr + 1), read(addr))

    fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange

    fun readLength(firstAddr: Int, length: Int) = readRange(firstAddr, firstAddr + length)

    fun write(addr: Int, vararg values: Byte, start: Int = 0, length: Int = values.size)

    fun write(addr: Int, values: IMemoryRange, start: Int = 0, length: Int = values.length) {
        write(addr, *values.toArray(), start = start, length = length)
    }

    fun write(addr: Int, value: Short) {
        write(addr, value.getLowByte(), value.getHighByte())
    }

}

interface IMemoryRange {

    val length: Int

    operator fun get(index: Int): Byte

    fun toArray(): ByteArray

}

class IllegalReadException : Exception("Cannot read from write-only memory!")

class IllegalWriteException : Exception("Cannot write to read-only memory!")