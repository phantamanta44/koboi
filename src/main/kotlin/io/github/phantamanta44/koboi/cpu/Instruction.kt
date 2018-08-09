package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.*

infix fun ((Cpu) -> Unit).then(o: (Cpu) -> Unit): (Cpu) -> Unit = { this(it); o(it) }

fun advance(count: Int = 1): (Cpu) -> Unit = { it.advance(count) }

fun idle(cycles: Int): (Cpu) -> Unit = { it.idle(cycles) }

infix fun <T : Number> ((Cpu) -> T).into(dest: (Cpu) -> (T) -> Unit): (Cpu) -> Unit = {
    it.advance()
    dest(it)(this(it))
}

// Predicates

fun predicate(condition: (Cpu) -> Boolean, ifTrue: (Cpu) -> Unit, ifFalse: (Cpu) -> Unit): (Cpu) -> Unit = {
    (if (condition(it)) ifTrue else ifFalse)(it)
}

val isZero: (Cpu) -> Boolean = { it.regF.kZ }

val nonZero: (Cpu) -> Boolean = { !it.regF.kZ }

val isCarry: (Cpu) -> Boolean = { it.regF.kC }

val nonCarry: (Cpu) -> Boolean = { !it.regF.kC }

// Load sources

val loadHardByte: (Cpu) -> Byte = Cpu::readByteAndAdvance

val loadHardByteAsInt: (Cpu) -> Int = { it.readByteAndAdvance().toUnsignedInt() }

val loadHardShort: (Cpu) -> Short = Cpu::readShortAndAdvance

val loadHardAddress: (Cpu) -> Byte = { it.memory.read(it.readShortAndAdvance().toUnsignedInt()) }

fun <T : Number> loadRegister(register: (Cpu) -> IRegister<T>): (Cpu) -> T = { register(it).read() }

fun loadRegister8AsInt(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Int = { register(it).read().toUnsignedInt() }

fun loadRegister16AsInt(register: (Cpu) -> IRegister<Short>): (Cpu) -> Int = { register(it).read().toUnsignedInt() }

val loadHardHighAddress: (Cpu) -> Byte = {
    it.memory.read(it.readByteAndAdvance().toUnsignedInt() + 0xFF00)
}

fun loadPointer(register: (Cpu) -> IRegister<Short>): (Cpu) -> Byte = {
    it.memory.read(register(it).read().toUnsignedInt())
}

fun loadPointerAsInt(register: (Cpu) -> IRegister<Short>): (Cpu) -> Int = {
    it.memory.read(register(it).read().toUnsignedInt()).toUnsignedInt()
}

fun loadHighPointer(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Byte = {
    it.memory.read(register(it).read().toUnsignedInt() + 0xFF00)
}

val loadHLPointerThenAdvance: (Cpu) -> Byte = {
    val result = it.memory.read(it.regHL.read().toUnsignedInt())
    it.regHL.increment(1)
    result
}

val loadHLPointerThenBacktrack: (Cpu) -> Byte = {
    val result = it.memory.read(it.regHL.read().toUnsignedInt())
    it.regHL.decrement(1)
    result
}

// Load destinations

fun <T : Number> writeRegister(register: (Cpu) -> IRegister<T>): (Cpu) -> (T) -> Unit = { register(it)::write }

val writeHardAddress8: (Cpu) -> (Byte) -> Unit = {
    { byte -> it.memory.write(it.readShortAndAdvance().toUnsignedInt(), byte) }
}

val writeHardAddress16: (Cpu) -> (Short) -> Unit = {
    { short -> it.memory.write(it.readShortAndAdvance().toUnsignedInt(), short) }
}

val writeHardHighAddress: (Cpu) -> (Byte) -> Unit = {
    { byte -> it.memory.write(it.readByteAndAdvance().toUnsignedInt() + 0xFF00, byte) }
}

fun writePointer(pointer: (Cpu) -> IRegister<Short>): (Cpu) -> (Byte) -> Unit = {
    { byte -> it.memory.write(pointer(it).read().toUnsignedInt(), byte) }
}

fun writeHighPointer(pointer: (Cpu) -> IRegister<Byte>): (Cpu) -> (Byte) -> Unit = {
    { byte -> it.memory.write(pointer(it).read().toUnsignedInt() + 0xFF00, byte) }
}

val writeHLPointerThenAdvance: (Cpu) -> (Byte) -> Unit = {
    { byte ->
        it.memory.write(it.regHL.read().toUnsignedInt(), byte)
        it.regHL.increment(1)
    }
}

val writeHLPointerThenBacktrack: (Cpu) -> (Byte) -> Unit = {
    { byte ->
        it.memory.write(it.regHL.read().toUnsignedInt(), byte)
        it.regHL.decrement(1)
    }
}

// Math destinations

fun incRegister8(register: (Cpu) -> IRegister<Byte>): (Cpu) -> (Int) -> Unit = {
    { offset ->
        val regInstance = register(it)
        val initial = regInstance.read().toUnsignedInt()
        regInstance.increment(offset)
        it.regF.kZ = regInstance.read() == 0.toByte()
        it.regF.kN = false
        it.regF.kH = (initial and 0x0F) + (offset and 0x0F) > 0x0F
        it.regF.kC = initial + offset > 0xFF
    }
}

fun adcRegister8(register: (Cpu) -> IRegister<Byte>): (Cpu) -> (Int) -> Unit = {
    { offset ->
        val regInstance = register(it)
        val initial = regInstance.read().toUnsignedInt()
        if (it.regF.kC) {
            regInstance.increment(offset + 1)
            it.regF.kH = (initial and 0x0F) + (offset and 0x0F) + 1 > 0x0F
            it.regF.kC = initial + offset + 1 > 0xFF
        } else {
            regInstance.increment(offset)
            it.regF.kH = (initial and 0x0F) + (offset and 0x0F) > 0x0F
            it.regF.kC = initial + offset > 0xFF
        }
        it.regF.kZ = regInstance.read() == 0.toByte()
        it.regF.kN = false
    }
}

fun incRegister16(register: (Cpu) -> IRegister<Short>): (Cpu) -> (Int) -> Unit = {
    { offset ->
        val regInstance = register(it)
        val initial = regInstance.read().toUnsignedInt()
        regInstance.increment(offset)
        it.regF.kN = false
        it.regF.kH = (initial and 0x0FFF) + (offset and 0x0FFF) > 0x0FFF
        it.regF.kC = initial + offset > 0xFFFF
    }
}

fun decRegister8(register: (Cpu) -> IRegister<Byte>): (Cpu) -> (Int) -> Unit = {
    { offset ->
        val regInstance = register(it)
        val initial = regInstance.read().toUnsignedInt()
        regInstance.decrement(offset)
        it.regF.kZ = regInstance.read() == 0.toByte()
        it.regF.kN = true
        it.regF.kH = (initial and 0x0F) < (offset and 0x0F)
        it.regF.kC = initial < offset
    }
}

fun sbcRegister8(register: (Cpu) -> IRegister<Byte>): (Cpu) -> (Int) -> Unit = {
    { offset ->
        val regInstance = register(it)
        val initial = regInstance.read().toUnsignedInt()
        if (it.regF.kC) {
            regInstance.decrement(offset + 1)
            it.regF.kH = (initial and 0x0F) < (offset and 0x0F) + 1
            it.regF.kC = initial < offset + 1
        } else {
            regInstance.decrement(offset)
            it.regF.kH = (initial and 0x0F) < (offset and 0x0F)
            it.regF.kC = initial < offset
        }
        it.regF.kZ = regInstance.read() == 0.toByte()
        it.regF.kN = true
    }
}

// Unary math

fun increment8(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Unit = {
    it.advance()
    val regInstance = register(it)
    it.regF.kH = regInstance.read().toInt() and 0xF == 0xF
    regInstance.increment(1)
    it.regF.kZ = regInstance.read() == 0.toByte()
    it.regF.kN = false
}

fun increment16(register: (Cpu) -> IRegister<Short>): (Cpu) -> Unit = {
    it.advance()
    register(it).increment(1)
}

fun decrement8(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Unit = {
    it.advance()
    val regInstance = register(it)
    it.regF.kH = regInstance.read().toInt() and 0x0F == 0
    regInstance.decrement(1)
    it.regF.kZ = regInstance.read() == 0.toByte()
    it.regF.kN = true
}

fun decrement16(register: (Cpu) -> IRegister<Short>): (Cpu) -> Unit = {
    it.advance()
    register(it).decrement(1)
}

fun incrementPointer(addr: (Cpu) -> IRegister<Short>): (Cpu) -> Unit = {
    it.advance()
    val addrInstance = addr(it).read().toUnsignedInt()
    val initialValue = it.memory.read(addrInstance)
    it.regF.kH = initialValue.toInt() and 0xF == 0xF
    it.memory.write(addrInstance, initialValue.inc())
    it.regF.kZ = it.memory.read(addrInstance) == 0.toByte()
    it.regF.kN = false
}

fun decrementPointer(addr: (Cpu) -> IRegister<Short>): (Cpu) -> Unit = {
    it.advance()
    val addrInstance = addr(it).read().toUnsignedInt()
    val initialValue = it.memory.read(addrInstance)
    it.regF.kH = initialValue.toInt() and 0x0F == 0
    it.memory.write(addrInstance, initialValue.dec())
    it.regF.kZ = it.memory.read(addrInstance) == 0.toByte()
    it.regF.kN = true
}

fun rotateLeft(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Unit = {
    it.advance()
    val regInstance = register(it)
    val initial = regInstance.read().toInt()
    val rotatingBit = initial and 0x80
    regInstance.write((((initial shl 1) and 0xFE) or (rotatingBit ushr 7)).toByte())
    it.regF.kZ = false
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = rotatingBit != 0
}

fun rotateLeftThroughCarry(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Unit = {
    it.advance()
    val regInstance = register(it)
    val initial = regInstance.read().toInt()
    if (it.regF.kC) {
        regInstance.write(((initial shl 1) or 1).toByte())
    } else {
        regInstance.write(((initial shl 1) and 1.inv()).toByte())
    }
    it.regF.kZ = false
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = initial and 0x80 != 0
}

fun rotateRight(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Unit = {
    it.advance()
    val regInstance = register(it)
    val initial = regInstance.read().toUnsignedInt()
    val rotatingBit = initial and 1
    regInstance.write(((initial ushr 1) or (rotatingBit shl 7)).toByte())
    it.regF.kZ = false
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = rotatingBit != 0
}

fun rotateRightThroughCarry(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Unit = {
    it.advance()
    val regInstance = register(it)
    val initial = regInstance.read().toInt()
    if (it.regF.kC) {
        regInstance.write(((initial ushr 1) or 0x80).toByte())
    } else {
        regInstance.write(((initial ushr 1) and 0x80.inv()).toByte())
    }
    it.regF.kZ = false
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = initial and 1 != 0
}

/*
 * adapted from trekawek's coffee-gb, which is under the mit license
 * https://github.com/trekawek/coffee-gb/blob/master/src/main/java/eu/rekawek/coffeegb/cpu/AluFunctions.java
 */
val daa: (Cpu) -> Unit = {
    it.advance()
    var result = it.regA.read().toUnsignedInt()
    if (it.regF.kN) {
        if (it.regF.kH) result = (result - 0x06) and 0xFF
        if (it.regF.kC) result = (result - 0x60) and 0xFF
    } else {
        if (it.regF.kH || (result and 0x0F) > 9) result += 0x06
        if (it.regF.kC || result > 0x9F) result += 0x60
    }
    it.regF.kH = false
    if (result > 0xFF) it.regF.kC = true
    val asByte = result.toByte()
    it.regF.kZ = asByte == 0.toByte()
    it.regA.write(asByte)
}

// Akku ops

fun akkuAnd(operand: (Cpu) -> Byte): (Cpu) -> Unit = {
    it.advance()
    it.regA.write(it.regA.read() and operand(it))
    it.regF.kZ = it.regA.read() == 0.toByte()
    it.regF.kN = false
    it.regF.kH = true
    it.regF.kC = false
}

fun akkuXor(operand: (Cpu) -> Byte): (Cpu) -> Unit = {
    it.advance()
    it.regA.write(it.regA.read() xor operand(it))
    it.regF.kZ = it.regA.read() == 0.toByte()
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = false
}

fun akkuOr(operand: (Cpu) -> Byte): (Cpu) -> Unit = {
    it.advance()
    it.regA.write(it.regA.read() or operand(it))
    it.regF.kZ = it.regA.read() == 0.toByte()
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = false
}

fun akkuCp(operand: (Cpu) -> Byte): (Cpu) -> Unit = {
    it.advance()
    val offset = operand(it).toUnsignedInt()
    val initial = it.regA.read().toUnsignedInt()
    it.regF.kZ = initial == offset
    it.regF.kN = true
    it.regF.kH = (initial and 0x0F) < (offset and 0x0F)
    it.regF.kC = initial < offset
}

val akkuCpl: (Cpu) -> Unit = {
    it.advance()
    it.regA.write(it.regA.read().inv())
    it.regF.kN = true
    it.regF.kH = true
}

// Flags

val scf: (Cpu) -> Unit = {
    it.advance()
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = true
}

val ccf: (Cpu) -> Unit = {
    it.advance()
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = !it.regF.kC
}

val imeOn: (Cpu) -> Unit = {
    it.advance()
    it.imeChangeNextCycle = ImeChange.ON
}

val imeOff: (Cpu) -> Unit = {
    it.advance()
    it.imeChangeThisCycle = ImeChange.OFF
}

// Jumps

fun jumpAbsolute(addr: (Cpu) -> Short): (Cpu) -> Unit = {
    it.advance()
    it.regPC.write(addr(it))
}

val jumpRelative: (Cpu) -> Unit = {
    it.regPC.offset(it.memory.read(it.regPC.read().toUnsignedInt() + 1).toInt() + 2)
}

// Call stack

fun stackPush(register: (Cpu) -> IRegister<Short>): (Cpu) -> Unit = {
    it.advance()
    it.regSP.decrement(2)
    it.memory.write(it.regSP.read().toUnsignedInt(), register(it).read())
}

fun stackPop(register: (Cpu) -> IRegister<Short>): (Cpu) -> Unit = {
    it.advance()
    register(it).write(it.memory.readShort(it.regSP.read().toUnsignedInt()))
    it.regSP.increment(2)
}

fun stackCall(addr: (Cpu) -> Short): (Cpu) -> Unit = {
    it.advance()
    val target = addr(it)
    it.backtrace.stackCall(target)
    it.regSP.decrement(2)
    it.memory.write(it.regSP.read().toUnsignedInt(), it.regPC.read())
    it.regPC.write(target)
}

val stackReturn: (Cpu) -> Unit = {
    it.backtrace.stackReturn()
    it.regPC.write(it.memory.readShort(it.regSP.read().toUnsignedInt()))
    it.regSP.increment(2)
}

// Weird stuff

val offsetStackPointer: (Cpu) -> Unit = {
    it.advance()
    val offset = it.readByteAndAdvance().toInt()
    val initial = it.regSP.read().toUnsignedInt()
    it.regSP.offset(offset)
    val op = it.regSP.read().toUnsignedInt() xor offset xor initial
    it.regF.kZ = false
    it.regF.kN = false
    it.regF.kH = op and 0x10 != 0
    it.regF.kC = op and 0x100 != 0
}

val loadHlWithOffsetStackPointer: (Cpu) -> Unit = {
    it.advance()
    val offset = it.readByteAndAdvance().toInt()
    val initial = it.regSP.read().toUnsignedInt()
    val final = (initial + offset) and 0xFFFF
    it.regHL.write(final.toShort())
    val op = offset xor initial xor final
    it.regF.kZ = false
    it.regF.kN = false
    it.regF.kH = op and 0x10 != 0
    it.regF.kC = op and 0x100 != 0
}

fun unknownOpcode(opcode: Int): (Cpu) -> Unit = {
    throw UnknownOpcodeException(opcode.toByte())
}