package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.cons
import io.github.phantamanta44.koboi.util.getHighByte
import io.github.phantamanta44.koboi.util.getLowByte

class OpStack {

    private val stack: ByteArray = ByteArray(6) // no op should need more than 6 stack elements
    private var pointer: Int = 0

    fun u8(value: Byte) {
        stack[pointer++] = value
    }

    fun u16(value: Short) {
        stack[pointer] = value.getHighByte()
        stack[pointer + 1] = value.getLowByte()
        pointer += 2
    }

    fun u8(): Byte = stack[--pointer]

    fun u16(): Short {
        pointer -= 2
        return Short.cons(stack[pointer], stack[pointer + 1])
    }

    fun i8(): Int = u8().toInt()

}