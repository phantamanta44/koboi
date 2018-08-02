package io.github.phantamanta44.koboi.util

// Unsigned

fun Short.toUnsignedInt(): Int = (toInt() and 0xFFFF)

fun Short.toUnsignedHex(): String = "%04X".format(toUnsignedInt())

fun Byte.toUnsignedInt(): Int = (toInt() and 0xFF)

fun Byte.toUnsignedHex(): String = "%02X".format(toUnsignedInt())

// Bitwise ops

infix fun Byte.and(o: Byte): Byte = (toInt() and o.toInt()).toByte()

infix fun Byte.or(o: Byte): Byte = (toInt() or o.toInt()).toByte()

infix fun Byte.xor(o: Byte): Byte = (toInt() xor o.toInt()).toByte()

infix fun Byte.shl(o: Int): Byte = (toInt() shl o).toByte()

infix fun Byte.shr(o: Int): Byte = (toInt() shr o).toByte()

infix fun Byte.ushr(o: Int): Byte = (toInt() ushr o).toByte()

fun Byte.inv(): Byte = (toInt() xor 0xFF).toByte()

fun Byte.rotl(): Byte {
    val intValue = toInt()
    return ((intValue shl 1) or ((intValue and 0x80) ushr 7)).toByte()
}

fun Byte.rotr(): Byte {
    val intValue = toInt()
    return ((intValue ushr 1) or ((intValue and 1) shl 7)).toByte()
}

// Byte cons/decons

fun Short.getHighByte(): Byte = ((toInt() and 0xFF00) ushr 8).toByte()

fun Short.getLowByte(): Byte = (toInt() and 0xFF).toByte()

fun Short.Companion.cons(high: Byte, low: Byte): Short = ((high.toUnsignedInt() shl 8) or low.toUnsignedInt()).toShort()