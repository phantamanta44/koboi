package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.*
import kotlin.reflect.KMutableProperty1

fun <T, V> ((Cpu) -> T).map(mapper: (T) -> V): (Cpu) -> V = { mapper(this(it)) }

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

fun loadPointer8(register: (Cpu) -> IRegister<Short>): (Cpu) -> Byte = {
    it.memory.read(register(it).read().toUnsignedInt())
}

fun loadPointer8AsInt(register: (Cpu) -> IRegister<Short>): (Cpu) -> Int = {
    it.memory.read(register(it).read().toUnsignedInt()).toUnsignedInt()
}

fun loadPointer16(register: (Cpu) -> IRegister<Short>): (Cpu) -> Short = {
    it.memory.readShort(register(it).read().toUnsignedInt())
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

fun ((Cpu) -> Int).plusCarry(): (Cpu) -> Int = {
    if (it.regF.kC) (this(it) + 1) else this(it)
}

fun ((Cpu) -> Int).minusCarry(): (Cpu) -> Int = {
    if (it.regF.kC) (this(it) - 1) else this(it)
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

fun writePointer8(pointer: (Cpu) -> IRegister<Short>): (Cpu) -> (Byte) -> Unit = {
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
        it.regF.kH = initial and 0xF0 != regInstance.read().toInt() and 0xF0
        it.regF.kC = initial + offset > 0xFF
    }
}

fun incRegister16(register: (Cpu) -> IRegister<Short>): (Cpu) -> (Int) -> Unit = {
    { offset ->
        val regInstance = register(it)
        val initial = regInstance.read().toUnsignedInt()
        regInstance.increment(offset)
        it.regF.kN = false
        it.regF.kH = initial and 0xF0 != regInstance.read().toInt() and 0xF0
        it.regF.kC = initial + offset > 0xFFFF
    }
}

fun decRegister8(register: (Cpu) -> IRegister<Byte>): (Cpu) -> (Int) -> Unit = {
    { offset ->
        val regInstance = register(it)
        val initial = regInstance.read().toUnsignedInt()
        regInstance.decrement(offset)
        val final = regInstance.read().toInt()
        it.regF.kZ = final == 0
        it.regF.kN = true
        it.regF.kH = initial and 0xF0 == final and 0xF0
        it.regF.kC = initial - offset >= 0
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
    it.regF.kH = regInstance.read().toInt() and 0xF == 0
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
    it.regF.kH = initialValue.toInt() and 0xF == 0
    it.memory.write(addrInstance, initialValue.dec())
    it.regF.kZ = it.memory.read(addrInstance) == 0.toByte()
    it.regF.kN = true
}

fun rotateLeft(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Unit = {
    it.advance()
    val regInstance = register(it)
    val initial = regInstance.read()
    regInstance.write(initial.rotl())
    it.regF.kC = initial.toInt() and 0x80 == 0x80
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
    it.regF.kC = initial and 0x80 == 0x80
}

fun rotateRight(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Unit = {
    it.advance()
    val regInstance = register(it)
    val initial = regInstance.read()
    regInstance.write(initial.rotr())
    it.regF.kC = initial.toInt() and 1 == 1
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
    it.regF.kC = initial and 1 == 1
}

val daa: (Cpu) -> Unit = {
    // TODO check if this impl is correct
    it.advance()
    if (it.regA.read().toInt() and 0x0F > 9 || it.regF.kH) it.regA.increment(6)
    if (it.regA.read().toUnsignedInt() > 0x9F || it.regF.kC) {
        it.regA.increment(0x60)
        it.regF.kC = true
    } else {
        it.regF.kC = false
    }
    it.regF.kZ = it.regA.read().toUnsignedInt() == 0
    it.regF.kH = false
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
    var final = initial - offset
    while (final < 0) final += 256
    it.regF.kZ = final == 0
    it.regF.kN = true
    it.regF.kH = initial and 0xF0 == final and 0xF0
    it.regF.kC = initial - offset >= 0
}

// Flags

fun flagOn(flag: KMutableProperty1<FlagRegister, Boolean>): (Cpu) -> Unit = {
    it.advance()
    flag.set(it.regF, true)
}

fun flagFlip(flag: KMutableProperty1<FlagRegister, Boolean>): (Cpu) -> Unit = {
    it.advance()
    flag.set(it.regF, !flag.get(it.regF))
}

val imeOn: (Cpu) -> Unit = {
    it.advance()
    it.flagIME = true
}

val imeOff: (Cpu) -> Unit = {
    it.advance()
    it.flagIME = false
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
    it.regSP.decrement(2)
    it.memory.write(it.regSP.read().toUnsignedInt(), it.regPC.read())
    it.regPC.write(target)
}

val stackReturn: (Cpu) -> Unit = {
    it.regPC.write(it.memory.readShort(it.regSP.read().toUnsignedInt()))
    it.regSP.increment(2)
}

// Weird stuff

val offsetStackPointer: (Cpu) -> Unit = {
    val offset = it.readByteAndAdvance().toInt()
    val initial = it.regSP.read().toUnsignedInt()
    val finalUnbounded = initial + offset
    it.regSP.offset(offset)
    it.regF.kZ = false
    it.regF.kN = false
    it.regF.kH = initial and 0xF0 != it.regSP.read().toInt() and 0xF0
    it.regF.kC = finalUnbounded < 0 || finalUnbounded > 0xFFFF
}

val offsetHLWithStackPointer: (Cpu) -> Unit = {
    val offset = it.readByteAndAdvance().toInt()
    val initial = it.regSP.read().toUnsignedInt()
    val finalUnbounded = initial + offset
    val result = finalUnbounded % 65536
    it.regHL.write((if (result < 0) (result + 65536) else result).toShort())
    it.regF.kZ = false
    it.regF.kN = false
    it.regF.kH = initial and 0xF0 != it.regSP.read().toInt() and 0xF0
    it.regF.kC = finalUnbounded < 0 || finalUnbounded > 0xFFFF
}

fun unknownOpcode(opcode: Int): (Cpu) -> Unit = {
    throw UnknownOpcodeException(opcode.toByte())
}