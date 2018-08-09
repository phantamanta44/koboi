package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.*

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
        write(((value.toUnsignedInt() + offset) % 256).toByte())
    }

    override fun decrement(offset: Int) {
        write(((value.toUnsignedInt() + (256 - offset)) % 256).toByte())
    }

}

class RegisterPair(private val high: IRegister<Byte>, private val low: IRegister<Byte>) : IRegister<Short> {

    override fun read(): Short = Short.cons(high.read(), low.read())

    override fun write(value: Short) {
        low.write(value.getLowByte())
        high.write(value.getHighByte())
    }

    override fun increment(offset: Int) {
        write(((read().toUnsignedInt() + offset) % 65536).toShort())
    }

    override fun decrement(offset: Int) {
        write(((read().toUnsignedInt() + (65536 - offset)) % 65536).toShort())
    }

}

class SingleShortRegister(private var value: Short = 0) : IRegister<Short> {

    override fun read(): Short = value

    override fun write(value: Short) {
        this.value = value
    }

    override fun increment(offset: Int) {
        write(((value.toUnsignedInt() + offset) % 65536).toShort())
    }

    override fun decrement(offset: Int) {
        write(((read().toUnsignedInt() + (65536 - offset)) % 65536).toShort())
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

    override fun write(value: Byte) {
        super.write(value and 0xF0.toByte())
    }

}