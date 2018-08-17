package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.*

typealias Insn = (Cpu) -> Unit
typealias InsnCond = (Cpu) -> Boolean

typealias CpuOut8 = (Cpu) -> Byte
typealias CpuOut16 = (Cpu) -> Short
typealias CpuOutInt = (Cpu) -> Int

typealias CpuReg8 = (Cpu) -> IRegister<Byte>
typealias CpuReg16 = (Cpu) -> IRegister<Short>

typealias CpuIn8 = (Cpu) -> (Byte) -> Unit
typealias CpuIn16 = (Cpu) -> (Short) -> Unit
typealias CpuInInt = (Cpu) -> (Int) -> Unit

infix fun (Insn).then(o: Insn): Insn = { this(it); it.queue(o) }

infix fun (Insn).also(o: Insn): Insn = { this(it); o(it) }

fun advance(count: Int = 1): Insn = { it.advance(count) }

val trace: Insn = { it.trace() }

fun idle(cycles: Int): Insn = { it.idle(cycles) }

infix fun <T : Number> ((Cpu) -> T).into(dest: (Cpu) -> (T) -> Unit): Insn = {
    it.trace()
    it.advance()
    dest(it)(this(it))
}

// Predicates

fun predicate(condition: InsnCond, ifTrue: Insn, ifFalse: Insn): Insn = {
    (if (condition(it)) ifTrue else ifFalse)(it)
}

val isZero: InsnCond = { it.regF.kZ }

val nonZero: InsnCond = { !it.regF.kZ }

val isCarry: InsnCond = { it.regF.kC }

val nonCarry: InsnCond = { !it.regF.kC }

// Load sources

val loadHardByte: CpuOut8 = Cpu::readByteAndAdvance

val loadHardByteAsInt: CpuOutInt = { it.readByteAndAdvance().toUnsignedInt() }

val loadHardShort: CpuOut16 = Cpu::readShortAndAdvance

val loadHardAddress: CpuOut8 = { it.memory.read(it.readShortAndAdvance().toUnsignedInt()) }

fun <T : Number> loadRegister(register: (Cpu) -> IRegister<T>): (Cpu) -> T = { register(it).read() }

fun loadRegister8AsInt(register: CpuReg8): CpuOutInt = { register(it).read().toUnsignedInt() }

fun loadRegister16AsInt(register: CpuReg16): CpuOutInt = { register(it).read().toUnsignedInt() }

val loadHardHighAddress: CpuOut8 = {
    it.memory.read(it.readByteAndAdvance().toUnsignedInt() + 0xFF00)
}

fun loadPointer(register: CpuReg16): CpuOut8 = {
    it.memory.read(register(it).read().toUnsignedInt())
}

fun loadPointerAsInt(register: CpuReg16): CpuOutInt = {
    it.memory.read(register(it).read().toUnsignedInt()).toUnsignedInt()
}

fun loadHighPointer(register: CpuReg8): CpuOut8 = {
    it.memory.read(register(it).read().toUnsignedInt() + 0xFF00)
}

val loadHLPointerThenAdvance: CpuOut8 = {
    val result = it.memory.read(it.regHL.read().toUnsignedInt())
    it.regHL.increment(1)
    result
}

val loadHLPointerThenBacktrack: CpuOut8 = {
    val result = it.memory.read(it.regHL.read().toUnsignedInt())
    it.regHL.decrement(1)
    result
}

// Load destinations

fun <T : Number> writeRegister(register: (Cpu) -> IRegister<T>): (Cpu) -> (T) -> Unit = { register(it)::write }

val writeHardAddress8: CpuIn8 = {
    { byte -> it.memory.write(it.readShortAndAdvance().toUnsignedInt(), byte) }
}

val writeHardAddress16: CpuIn16 = {
    { short -> it.memory.write(it.readShortAndAdvance().toUnsignedInt(), short) }
}

val writeHardHighAddress: CpuIn8 = {
    { byte -> it.memory.write(it.readByteAndAdvance().toUnsignedInt() + 0xFF00, byte) }
}

fun writePointer(pointer: CpuReg16): CpuIn8 = {
    { byte -> it.memory.write(pointer(it).read().toUnsignedInt(), byte) }
}

fun writeHighPointer(pointer: CpuReg8): CpuIn8 = {
    { byte -> it.memory.write(pointer(it).read().toUnsignedInt() + 0xFF00, byte) }
}

val writeHLPointerThenAdvance: CpuIn8 = {
    { byte ->
        it.memory.write(it.regHL.read().toUnsignedInt(), byte)
        it.regHL.increment(1)
    }
}

val writeHLPointerThenBacktrack: CpuIn8 = {
    { byte ->
        it.memory.write(it.regHL.read().toUnsignedInt(), byte)
        it.regHL.decrement(1)
    }
}

// Math destinations

fun incRegister8(register: CpuReg8): CpuInInt = {
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

fun adcRegister8(register: CpuReg8): CpuInInt = {
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

fun incRegister16(register: CpuReg16): CpuInInt = {
    { offset ->
        val regInstance = register(it)
        val initial = regInstance.read().toUnsignedInt()
        regInstance.increment(offset)
        it.regF.kN = false
        it.regF.kH = (initial and 0x0FFF) + (offset and 0x0FFF) > 0x0FFF
        it.regF.kC = initial + offset > 0xFFFF
    }
}

fun decRegister8(register: CpuReg8): CpuInInt = {
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

fun sbcRegister8(register: CpuReg8): CpuInInt = {
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

fun increment8(register: CpuReg8): Insn = {
    it.trace()
    it.advance()
    val regInstance = register(it)
    it.regF.kH = regInstance.read().toInt() and 0xF == 0xF
    regInstance.increment(1)
    it.regF.kZ = regInstance.read() == 0.toByte()
    it.regF.kN = false
}

fun increment16(register: CpuReg16): Insn = {
    it.trace()
    it.advance()
    register(it).increment(1)
}

fun decrement8(register: CpuReg8): Insn = {
    it.trace()
    it.advance()
    val regInstance = register(it)
    it.regF.kH = regInstance.read().toInt() and 0x0F == 0
    regInstance.decrement(1)
    it.regF.kZ = regInstance.read() == 0.toByte()
    it.regF.kN = true
}

fun decrement16(register: CpuReg16): Insn = {
    it.trace()
    it.advance()
    register(it).decrement(1)
}

fun incrementPointer(addr: CpuReg16): Insn = {
    it.trace()
    it.advance()
    val addrInstance = addr(it).read().toUnsignedInt()
    val initialValue = it.memory.read(addrInstance)
    it.regF.kH = initialValue.toInt() and 0xF == 0xF
    it.memory.write(addrInstance, initialValue.inc())
    it.regF.kZ = it.memory.read(addrInstance) == 0.toByte()
    it.regF.kN = false
}

fun decrementPointer(addr: CpuReg16): Insn = {
    it.trace()
    it.advance()
    val addrInstance = addr(it).read().toUnsignedInt()
    val initialValue = it.memory.read(addrInstance)
    it.regF.kH = initialValue.toInt() and 0x0F == 0
    it.memory.write(addrInstance, initialValue.dec())
    it.regF.kZ = it.memory.read(addrInstance) == 0.toByte()
    it.regF.kN = true
}

fun rotateLeft(register: CpuReg8): Insn = {
    it.trace()
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

fun rotateLeftThroughCarry(register: CpuReg8): Insn = {
    it.trace()
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

fun rotateRight(register: CpuReg8): Insn = {
    it.trace()
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

fun rotateRightThroughCarry(register: CpuReg8): Insn = {
    it.trace()
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
val daa: Insn = {
    it.trace()
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

fun akkuAnd(operand: CpuOut8): Insn = {
    it.trace()
    it.advance()
    it.regA.write(it.regA.read() and operand(it))
    it.regF.kZ = it.regA.read() == 0.toByte()
    it.regF.kN = false
    it.regF.kH = true
    it.regF.kC = false
}

fun akkuXor(operand: CpuOut8): Insn = {
    it.trace()
    it.advance()
    it.regA.write(it.regA.read() xor operand(it))
    it.regF.kZ = it.regA.read() == 0.toByte()
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = false
}

fun akkuOr(operand: CpuOut8): Insn = {
    it.trace()
    it.advance()
    it.regA.write(it.regA.read() or operand(it))
    it.regF.kZ = it.regA.read() == 0.toByte()
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = false
}

fun akkuCp(operand: CpuOut8): Insn = {
    it.trace()
    it.advance()
    val offset = operand(it).toUnsignedInt()
    val initial = it.regA.read().toUnsignedInt()
    it.regF.kZ = initial == offset
    it.regF.kN = true
    it.regF.kH = (initial and 0x0F) < (offset and 0x0F)
    it.regF.kC = initial < offset
}

val akkuCpl: Insn = {
    it.trace()
    it.advance()
    it.regA.write(it.regA.read().inv())
    it.regF.kN = true
    it.regF.kH = true
}

// Flags

val scf: Insn = {
    it.trace()
    it.advance()
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = true
}

val ccf: Insn = {
    it.trace()
    it.advance()
    it.regF.kN = false
    it.regF.kH = false
    it.regF.kC = !it.regF.kC
}

val imeOn: Insn = {
    it.trace()
    it.advance()
    it.imeChangeNextInsn = ImeChange.ON
}

val imeOff: Insn = {
    it.trace()
    it.advance()
    it.imeChangeThisInsn = ImeChange.OFF
}

// Jumps

fun jumpAbsolute(addr: CpuOut16): Insn = {
    it.trace()
    it.advance()
    it.regPC.write(addr(it))
}

val jumpRelative: Insn = {
    it.trace()
    it.regPC.offset(it.memory.read(it.regPC.read().toUnsignedInt() + 1).toInt() + 2)
}

// Call stack

fun stackPush(register: CpuReg16): Insn = {
    it.trace()
    it.advance()
    it.regSP.decrement(2)
    it.memory.write(it.regSP.read().toUnsignedInt(), register(it).read())
}

fun stackPop(register: CpuReg16): Insn = {
    it.trace()
    it.advance()
    register(it).write(it.memory.readShort(it.regSP.read().toUnsignedInt()))
    it.regSP.increment(2)
}

fun stackCall(addr: CpuOut16): Insn = {
    it.trace()
    it.advance()
    val target = addr(it)
    it.backtrace.stackCall(target)
    it.regSP.decrement(2)
    it.memory.write(it.regSP.read().toUnsignedInt(), it.regPC.read())
    it.regPC.write(target)
}

val stackReturn: Insn = {
    it.trace()
    it.backtrace.stackReturn()
    it.regPC.write(it.memory.readShort(it.regSP.read().toUnsignedInt()))
    it.regSP.increment(2)
}

// Weird stuff

val offsetStackPointer: Insn = {
    it.trace()
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

val loadHlWithOffsetStackPointer: Insn = {
    it.trace()
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

fun unknownOpcode(opcode: Int): Insn = {
    throw UnknownOpcodeException(opcode.toByte())
}