package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.memory.IMemoryArea
import io.github.phantamanta44.koboi.util.cons
import io.github.phantamanta44.koboi.util.getHighByte
import io.github.phantamanta44.koboi.util.getLowByte

interface IRegister<T : Number> {

    fun read(): T

    fun write(value: T)

    fun increment(offset: Int)

    fun decrement(offset: Int)

    fun offset(offset: Int) {
        if (offset > 0) {
            increment(offset)
        } else if (offset < 0) {
            decrement(-offset)
        }
    }

}

open class SingleByteRegister(protected var value: Byte = 0) : IRegister<Byte> {

    override fun read(): Byte = value

    override fun write(value: Byte) {
        this.value = value
    }

    override fun increment(offset: Int) {
        write(((value + offset) % 256).toByte())
    }

    override fun decrement(offset: Int) {
        var newValue = value - offset
        while (newValue < 0) newValue += 256
        write(newValue.toByte())
    }

}

class RegisterPair(private val high: IRegister<Byte>, private val low: IRegister<Byte>) : IRegister<Short> {

    override fun read(): Short = Short.cons(high.read(), low.read())

    override fun write(value: Short) {
        low.write(value.getLowByte())
        high.write(value.getHighByte())
    }

    override fun increment(offset: Int) {
        write(((read() + offset) % 65536).toShort())
    }

    override fun decrement(offset: Int) {
        var newValue = read() - offset
        while (newValue < 0) newValue += 65536
        write(newValue.toShort())
    }

}

class SingleShortRegister(private var value: Short = 0) : IRegister<Short> {

    override fun read(): Short = value

    override fun write(value: Short) {
        this.value = value
    }

    override fun increment(offset: Int) {
        write(((value + offset) % 65536).toShort())
    }

    override fun decrement(offset: Int) {
        var newValue = value - offset
        while (newValue < 0) newValue += 65536
        write(newValue.toShort())
    }

}

class MemoryBitRegister(val memory: IMemoryArea, val addr: Int) : IRegister<Byte> {

    var flag: Boolean
        get() = memory.read(0) != 0.toByte()
        set(value) {
            memory.write(addr, (if (value) 1 else 0).toByte())
        }

    override fun read(): Byte {
        return memory.read(addr)
    }

    override fun write(value: Byte) {
        memory.write(addr, value)
    }

    override fun increment(offset: Int) {
        write((read() + offset).toByte())
    }

    override fun decrement(offset: Int) {
        write((read() - offset).toByte())
    }

}

class FlagRegister(initialValue: Byte = 0) : SingleByteRegister(initialValue) {

    var kZ: Boolean
        get() = hasFlag(7)
        set(value) = setFlag(7, value)

    var kN: Boolean
        get() = hasFlag(6)
        set(value) = setFlag(6, value)

    var kH: Boolean
        get() = hasFlag(5)
        set(value) = setFlag(5, value)

    var kC: Boolean
        get() = hasFlag(4)
        set(value) = setFlag(4, value)

    private fun hasFlag(index: Int): Boolean {
        return value.toInt() and (1 shl index) != 0
    }

    private fun setFlag(index: Int, flag: Boolean) {
        write((if (flag) (value.toInt() or (1 shl index)) else (value.toInt() and (1 shl index).inv())).toByte())
    }

}