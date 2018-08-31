package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.toUnsignedInt

val doCbPrefixedOpcode: Insn = {
    it.trace()
    it.advance()
    CbPrefixedOpcodes.cbPrefixedOpcodeTable[it.readByteAndAdvance().toUnsignedInt()](it)
}

typealias BitwiseProvider = Triple<(Insn) -> Insn, Insn, (Insn, Insn) -> Insn>
typealias BitwiseInsn = (BitwiseProvider) -> Insn

fun BitwiseProvider.wrapR(insn: Insn): Insn = first(insn)
fun BitwiseProvider.wrapRW(insn: Insn): Insn = wrapR(third(insn, second))

object CbPrefixedOpcodes {

    private val singleByteRegisters: List<BitwiseProvider> = listOf(
            forRegister(Cpu::regB),
            forRegister(Cpu::regC),
            forRegister(Cpu::regD),
            forRegister(Cpu::regE),
            forRegister(Cpu::regH),
            forRegister(Cpu::regL),
            BitwiseProvider({ insn -> idle(4) then { it.opStk.u8(it.memory.read(it.regHL.read().toUnsignedInt())); insn(it) } },
                    idle(3) then { it.memory.write(it.regHL.read().toUnsignedInt(), it.opStk.u8()) },
                    Insn::then),
            forRegister(Cpu::regA)
    )

    val cbPrefixedOpcodeTable: Array<Insn> = listOf(
            { prov ->
                prov.wrapRW {
                    val initial = it.opStk.i8()
                    val rotatingBit = initial and 0x80
                    val final = (((initial shl 1) and 0xFE) or (rotatingBit ushr 7)).toByte()
                    it.opStk.u8(final)
                    it.regF.kZ = final == 0.toByte()
                    it.regF.kN = false
                    it.regF.kH = false
                    it.regF.kC = rotatingBit != 0
                }
            }, // rlc rotate left
            { prov ->
                prov.wrapRW {
                    val initial = it.opStk.u8().toUnsignedInt()
                    val rotatingBit = initial and 1
                    val final = ((initial ushr 1) or (rotatingBit shl 7)).toByte()
                    it.opStk.u8(final)
                    it.regF.kZ = final == 0.toByte()
                    it.regF.kN = false
                    it.regF.kH = false
                    it.regF.kC = rotatingBit != 0
                }
            }, // rrc rotate right
            { prov ->
                prov.wrapRW {
                    val initial = it.opStk.i8()
                    val final = if (it.regF.kC) {
                        ((initial shl 1) or 1).toByte()
                    } else {
                        ((initial shl 1) and 1.inv()).toByte()
                    }
                    it.opStk.u8(final)
                    it.regF.kZ = final == 0.toByte()
                    it.regF.kN = false
                    it.regF.kH = false
                    it.regF.kC = initial and 0x80 == 0x80
                }
            }, // rl rotate left through carry
            { prov ->
                prov.wrapRW {
                    val initial = it.opStk.i8()
                    val final = if (it.regF.kC) {
                        ((initial ushr 1) or 0x80).toByte()
                    } else {
                        ((initial ushr 1) and 0x80.inv()).toByte()
                    }
                    it.opStk.u8(final)
                    it.regF.kZ = final == 0.toByte()
                    it.regF.kN = false
                    it.regF.kH = false
                    it.regF.kC = initial and 1 == 1
                }
            }, // rr rotate right through carry
            { prov ->
                prov.wrapRW {
                    val initial = it.opStk.i8()
                    val final = (initial shl 1).toByte()
                    it.opStk.u8(final)
                    it.regF.kZ = final == 0.toByte()
                    it.regF.kN = false
                    it.regF.kH = false
                    it.regF.kC = initial and 0x80 == 0x80
                }
            }, // sla arithmetic shift left
            { prov ->
                prov.wrapRW {
                    val initial = it.opStk.i8()
                    val final = ((initial ushr 1) or (initial and 0x80)).toByte()
                    it.opStk.u8(final)
                    it.regF.kZ = final == 0.toByte()
                    it.regF.kN = false
                    it.regF.kH = false
                    it.regF.kC = initial and 1 == 1
                }
            }, // sra arithmetic shift right
            { prov ->
                prov.wrapRW {
                    val initial = it.opStk.u8()
                    it.opStk.u8(if (initial == 0.toByte()) {
                        it.regF.kZ = true
                        initial
                    } else {
                        it.regF.kZ = false
                        initial.toInt().let { asInt ->
                            (((asInt and 0x0F) shl 4) or ((asInt and 0xF0) ushr 4)).toByte()
                        }
                    })
                    it.regF.kN = false
                    it.regF.kH = false
                    it.regF.kC = false
                }
            }, // swap
            { prov ->
                prov.wrapRW {
                    val initial = it.opStk.i8()
                    val final = ((initial ushr 1) and 0x7F).toByte()
                    it.opStk.u8(final)
                    it.regF.kZ = final == 0.toByte()
                    it.regF.kN = false
                    it.regF.kH = false
                    it.regF.kC = initial and 1 == 1
                }
            }, // srl logical shift right
            testBit(0), testBit(1), testBit(2), testBit(3), testBit(4), testBit(5), testBit(6), testBit(7),
            unsetBit(0), unsetBit(1), unsetBit(2), unsetBit(3), unsetBit(4), unsetBit(5), unsetBit(6), unsetBit(7),
            setBit(0), setBit(1), setBit(2), setBit(3), setBit(4), setBit(5), setBit(6), setBit(7)
    ).flatMap { op ->
        singleByteRegisters.map { idle(7) then op(it) }
    }.toTypedArray()

    private fun forRegister(register: CpuReg8): BitwiseProvider {
        return BitwiseProvider({insn -> { it.opStk.u8(register(it).read()); insn(it) } },
                { register(it).write(it.opStk.u8()) },
                Insn::and)
    }

    private fun testBit(bit: Int): BitwiseInsn = { prov ->
        prov.wrapR {
            it.regF.kZ = it.opStk.i8() and (1 shl bit) == 0
            it.regF.kN = false
            it.regF.kH = true
        }
    }

    private fun unsetBit(bit: Int): BitwiseInsn = { prov ->
        prov.wrapRW {
            it.opStk.u8((it.opStk.i8() and (1 shl bit).inv()).toByte())
        }
    }

    private fun setBit(bit: Int): BitwiseInsn = { prov ->
        prov.wrapRW {
            it.opStk.u8((it.opStk.i8() or (1 shl bit)).toByte())
        }
    }

}