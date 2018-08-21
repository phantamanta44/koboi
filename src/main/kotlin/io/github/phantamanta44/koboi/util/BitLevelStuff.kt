package io.github.phantamanta44.koboi.util

// Unsigned

fun Short.toUnsignedInt(): Int = (toInt() and 0xFFFF)

fun Short.toUnsignedHex(): String = toUnsignedInt().formatPadded(4, 16)

fun Byte.toUnsignedInt(): Int = (toInt() and 0xFF)

fun Byte.toUnsignedHex(): String = toUnsignedInt().formatPadded(2, 16)

fun Byte.toBin(): String = toUnsignedInt().formatPadded(8, 2)

fun Int.toShortHex(): String = (this and 0xFFFF).formatPadded(4, 16)

fun Int.toByteHex(): String = (this and 0xFF).formatPadded(2, 16)

fun Int.formatPadded(length: Int, radix: Int): String {
    val asString = toString(radix).toUpperCase()
    val unpaddedLength = asString.length
    return if (unpaddedLength >= length) asString else ("0".repeat(length - unpaddedLength) + asString)
}

// Bitwise ops

infix fun Byte.and(o: Byte): Byte = (toInt() and o.toInt()).toByte()

infix fun Byte.or(o: Byte): Byte = (toInt() or o.toInt()).toByte()

infix fun Byte.xor(o: Byte): Byte = (toInt() xor o.toInt()).toByte()

fun Byte.inv(): Byte = (toInt() xor 0xFF).toByte()

// Byte cons/decons

fun Short.getHighByte(): Byte = ((toInt() and 0xFF00) ushr 8).toByte()

fun Short.getLowByte(): Byte = (toInt() and 0xFF).toByte()

fun Short.Companion.cons(high: Byte, low: Byte): Short = ((high.toUnsignedInt() shl 8) or low.toUnsignedInt()).toShort()