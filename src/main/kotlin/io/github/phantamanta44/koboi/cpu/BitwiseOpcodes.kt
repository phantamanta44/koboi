package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.toUnsignedInt

val doCbPrefixedOpcode: (Cpu) -> Unit = {
    it.advance()
    CbPrefixedOpcodes.cbPrefixedOpcodeTable[it.readByteAndAdvance().toUnsignedInt()](it)
}

object CbPrefixedOpcodes {

    private val singleByteRegisters: List<Triple<(Cpu) -> Byte, (Cpu, Byte) -> Unit, Int>> = listOf(
            forRegister(Cpu::regB),
            forRegister(Cpu::regC),
            forRegister(Cpu::regD),
            forRegister(Cpu::regE),
            forRegister(Cpu::regH),
            forRegister(Cpu::regL),
            Triple({ cpu: Cpu -> cpu.memory.read(cpu.regHL.read().toUnsignedInt()) },
                    { cpu, byte -> cpu.memory.write(cpu.regHL.read().toUnsignedInt(), byte) },
                    16),
            forRegister(Cpu::regA)
    )

    val cbPrefixedOpcodeTable: Array<(Cpu) -> Unit> = listOf(
            { get, set, flags ->
                val initial = get().toInt()
                val rotatingBit = initial and 0x80
                set((((initial shl 1) and 0xFE) or (rotatingBit ushr 7)).toByte())
                flags.kZ = get() == 0.toByte()
                flags.kN = false
                flags.kH = false
                flags.kC = rotatingBit != 0
            }, // rlc rotate left
            { get, set, flags ->
                val initial = get().toUnsignedInt()
                val rotatingBit = initial and 1
                set(((initial ushr 1) or (rotatingBit shl 7)).toByte())
                flags.kZ = get() == 0.toByte()
                flags.kN = false
                flags.kH = false
                flags.kC = rotatingBit != 0
            }, // rrc rotate right
            { get, set, flags ->
                val initial = get().toInt()
                if (flags.kC) {
                    set(((initial shl 1) or 1).toByte())
                } else {
                    set(((initial shl 1) and 1.inv()).toByte())
                }
                flags.kZ = get() == 0.toByte()
                flags.kN = false
                flags.kH = false
                flags.kC = initial and 0x80 == 0x80
            }, // rl rotate left through carry
            { get, set, flags ->
                val initial = get().toInt()
                if (flags.kC) {
                    set(((initial ushr 1) or 0x80).toByte())
                } else {
                    set(((initial ushr 1) and 0x80.inv()).toByte())
                }
                flags.kZ = get() == 0.toByte()
                flags.kN = false
                flags.kH = false
                flags.kC = initial and 1 == 1
            }, // rr rotate right through carry
            { get, set, flags ->
                val initial = get().toInt()
                set((initial shl 1).toByte())
                flags.kZ = get() == 0.toByte()
                flags.kN = false
                flags.kH = false
                flags.kC = initial and 0x80 == 0x80
            }, // sla arithmetic shift left
            { get, set, flags ->
                val initial = get().toInt()
                set(((initial ushr 1) or (initial and 0x80)).toByte())
                flags.kZ = get() == 0.toByte()
                flags.kN = false
                flags.kH = false
                flags.kC = initial and 1 == 1
            }, // sra arithmetic shift right
            { get, set, flags ->
                val initial = get().toInt()
                if (initial == 0) {
                    flags.kZ = true
                } else {
                    flags.kZ = false
                    set((((initial and 0x0F) shl 4) or ((initial and 0xF0) ushr 4)).toByte())
                }
                flags.kN = false
                flags.kH = false
                flags.kC = false
            }, // swap
            { get, set, flags ->
                val initial = get().toInt()
                set(((initial ushr 1) and 0x7F).toByte())
                flags.kZ = get() == 0.toByte()
                flags.kN = false
                flags.kH = false
                flags.kC = initial and 1 == 1
            }, // srl logical shift right
            testBit(0), testBit(1), testBit(2), testBit(3), testBit(4), testBit(5), testBit(6), testBit(7),
            unsetBit(0), unsetBit(1), unsetBit(2), unsetBit(3), unsetBit(4), unsetBit(5), unsetBit(6), unsetBit(7),
            setBit(0), setBit(1), setBit(2), setBit(3), setBit(4), setBit(5), setBit(6), setBit(7)
    ).flatMap { op ->
        singleByteRegisters.map {
            { cpu: Cpu -> op({ it.first(cpu) }, { byte -> it.second(cpu, byte) }, cpu.regF) } then idle(it.third)
        }
    }.toTypedArray()

    private fun forRegister(register: (Cpu) -> IRegister<Byte>): Triple<(Cpu) -> Byte, (Cpu, Byte) -> Unit, Int> {
        return Triple({ cpu: Cpu -> register(cpu).read() }, { cpu, byte -> register(cpu).write(byte) }, 8)
    }

    private fun testBit(bit: Int): (() -> Byte, (Byte) -> Unit, FlagRegister) -> Unit = { get, _, flags ->
        flags.kZ = get().toInt() and (1 shl bit) == 0
        flags.kN = false
        flags.kH = true
    }

    private fun unsetBit(bit: Int): (() -> Byte, (Byte) -> Unit, FlagRegister) -> Unit = { get, set, _ ->
        set((get().toInt() and (1 shl bit).inv()).toByte())
    }

    private fun setBit(bit: Int): (() -> Byte, (Byte) -> Unit, FlagRegister) -> Unit = { get, set, _ ->
        set((get().toInt() or (1 shl bit)).toByte())
    }

}