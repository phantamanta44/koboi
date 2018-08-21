package io.github.phantamanta44.koboi.plugin.artemis.disassembler

import io.github.phantamanta44.koboi.debug.IDebugTarget
import io.github.phantamanta44.koboi.util.toUnsignedInt

typealias TraceCollector = MutableList<ITraceContext>.(IDebugTarget) -> Unit

infix fun TraceCollector.and(other: TraceCollector): TraceCollector {
    val first = this
    return { first(it); other(it) }
}

val nil: TraceCollector = {
    // NO-OP
}

val regA: TraceCollector = { add(TraceContextRegister8("A", it.cpu.regA)) }
val regB: TraceCollector = { add(TraceContextRegister8("B", it.cpu.regB)) }
val regC: TraceCollector = { add(TraceContextRegister8("C", it.cpu.regC)) }
val regD: TraceCollector = { add(TraceContextRegister8("D", it.cpu.regD)) }
val regE: TraceCollector = { add(TraceContextRegister8("E", it.cpu.regE)) }
val regH: TraceCollector = { add(TraceContextRegister8("H", it.cpu.regH)) }
val regL: TraceCollector = { add(TraceContextRegister8("L", it.cpu.regL)) }

val binA: TraceCollector = { add(TraceContextRegisterBin("A", it.cpu.regA)) }
val binB: TraceCollector = { add(TraceContextRegisterBin("B", it.cpu.regB)) }
val binC: TraceCollector = { add(TraceContextRegisterBin("C", it.cpu.regC)) }
val binD: TraceCollector = { add(TraceContextRegisterBin("D", it.cpu.regD)) }
val binE: TraceCollector = { add(TraceContextRegisterBin("E", it.cpu.regE)) }
val binH: TraceCollector = { add(TraceContextRegisterBin("H", it.cpu.regH)) }
val binL: TraceCollector = { add(TraceContextRegisterBin("L", it.cpu.regL)) }

val regAF: TraceCollector = { add(TraceContextRegister16("AF", it.cpu.regAF)) }
val regBC: TraceCollector = { add(TraceContextRegister16("BC", it.cpu.regBC)) }
val regDE: TraceCollector = { add(TraceContextRegister16("DE", it.cpu.regDE)) }
val regHL: TraceCollector = { add(TraceContextRegister16("HL", it.cpu.regHL)) }
val regPC: TraceCollector = { add(TraceContextRegister16("PC", it.cpu.regPC)) }
val regSP: TraceCollector = { add(TraceContextRegister16("SP", it.cpu.regSP)) }

val ptrC: TraceCollector = {
    val addr = it.cpu.regC.toUnsignedInt() + 0xFF00
    add(TraceContextPointer8("C", addr, it.memory.read(addr, true)))
}
val ptrBC: TraceCollector = {
    val addr = it.cpu.regBC.toUnsignedInt()
    add(TraceContextPointer8("BC", addr, it.memory.read(addr, true)))
}
val ptrDE: TraceCollector = {
    val addr = it.cpu.regDE.toUnsignedInt()
    add(TraceContextPointer8("DE", addr, it.memory.read(addr, true)))
}
val ptrHL: TraceCollector = {
    val addr = it.cpu.regHL.toUnsignedInt()
    add(TraceContextPointer8("HL", addr, it.memory.read(addr, true)))
}
val ptrBinHL: TraceCollector = {
    val addr = it.cpu.regHL.toUnsignedInt()
    add(TraceContextPointerBin("HL", addr, it.memory.read(addr, true)))
}
val stk: TraceCollector = {
    val addr = it.cpu.regSP.toUnsignedInt()
    add(TraceContextPointer16("SP", addr, it.memory.readShort(addr, true)))
}

val imm8: TraceCollector = { add(TraceContextImmediate8(it.memory.read(it.cpu.regPC.toUnsignedInt() + 1, true))) }
val imm16: TraceCollector = { add(TraceContextImmediate16(it.memory.readShort(it.cpu.regPC.toUnsignedInt() + 1, true))) }
val immBin: TraceCollector = { add(TraceContextImmediateBin(it.memory.read(it.cpu.regPC.toUnsignedInt() + 1, true))) }

val immPtr8: TraceCollector = {
    val addr = it.memory.readShort(it.cpu.regPC.toUnsignedInt() + 1, true).toUnsignedInt()
    add(TraceContextImmediatePointer8(addr, it.memory.read(addr, true)))
}
val immPtr16: TraceCollector = {
    val addr = it.memory.readShort(it.cpu.regPC.toUnsignedInt() + 1, true).toUnsignedInt()
    add(TraceContextImmediatePointer16(addr, it.memory.readShort(addr, true)))
}
val immPtrH: TraceCollector = {
    val addr = it.memory.read(it.cpu.regPC.toUnsignedInt() + 1, true).toUnsignedInt() + 0xFF00
    add(TraceContextImmediatePointer8(addr, it.memory.read(addr, true)))
}

val immOff: TraceCollector = { add(TraceContextImmediateSigned(it.memory.read(it.cpu.regPC.toUnsignedInt() + 1, true))) }

val flagZ: TraceCollector = { add(TraceContextFlag("Z", it.cpu.regF.flagZ)) }
val flagC: TraceCollector = { add(TraceContextFlag("C", it.cpu.regF.flagC)) }

val f8Param: TraceCollector = { add(TraceF8Param(it.cpu.regSP, it.memory.read(it.cpu.regPC.toUnsignedInt() + 1, true))) }

val traceableOpcodes: List<TraceCollector> = listOf(
        nil, regBC and imm16, ptrBC and regA, regBC, regB, regB, regB and imm8, binA, immPtr16 and regSP, regHL and regBC, regA and ptrBC, regBC, regC, regC, regC and imm8, binA,
        nil, regDE and imm16, ptrDE and regA, regDE, regD, regD, regD and imm8, binA and flagC, immOff, regHL and regDE, regA and ptrDE, regDE, regE, regE, regE and imm8, binA and flagC,
        immOff and flagZ, regHL and imm16, ptrHL and regA, regHL, regH, regH, regH and imm8, regA, immOff and flagZ, regHL, regA and ptrHL, regHL, regL, regL, regL and imm8, binA,
        immOff and flagC, regSP and imm16, ptrHL and regA, regSP, ptrHL, ptrHL, ptrHL and imm8, flagC, immOff and flagC, regHL and regSP, regA and ptrHL, regSP, regA, regA, regA and imm8, flagC,
        regB, regB and regC, regB and regD, regB and regE, regB and regH, regB and regL, regB and ptrHL, regB and regA, regC and regB, regC, regC and regD, regC and regE, regC and regH, regC and regL, regC and ptrHL, regC and regA,
        regD and regB, regD and regC, regD, regD and regE, regD and regH, regD and regL, regD and ptrHL, regD and regA, regE and regB, regE and regC, regE and regD, regE, regE and regH, regE and regL, regE and ptrHL, regE and regA,
        regH and regB, regH and regC, regH and regD, regH and regE, regH, regH and regL, regH and ptrHL, regH and regA, regL and regB, regL and regC, regL and regD, regL and regE, regL and regH, regL, regL and ptrHL, regL and regA,
        ptrHL and regB, ptrHL and regC, ptrHL and regD, ptrHL and regE, ptrHL and regH, ptrHL and regL, nil, ptrHL and regA, regA and regB, regA and regC, regA and regD, regA and regE, regA and regH, regA and regL, regA and ptrHL, regA,
        regA and regB, regA and regC, regA and regD, regA and regE, regA and regH, regA and regL, regA and ptrHL, regA, regA and regB and flagC, regA and regC and flagC, regA and regD and flagC, regA and regE and flagC, regA and regH and flagC, regA and regL and flagC, regA and ptrHL and flagC, regA,
        regA and regB, regA and regC, regA and regD, regA and regE, regA and regH, regA and regL, regA and ptrHL, regA, regA and regB and flagC, regA and regC and flagC, regA and regD and flagC, regA and regE and flagC, regA and regH and flagC, regA and regL and flagC, regA and ptrHL and flagC, regA,
        binA and binB, binA and binC, binA and binD, binA and binE, binA and binH, binA and binL, binA and ptrBinHL, binA, binA and binB, binA and binC, binA and binD, binA and binE, binA and binH, binA and binL, binA and ptrBinHL, binA,
        binA and binB, binA and binC, binA and binD, binA and binE, binA and binH, binA and binL, binA and ptrBinHL, binA, binA and binB, binA and binC, binA and binD, binA and binE, binA and binH, binA and binL, binA and ptrBinHL, binA,
        stk and flagZ, regBC and stk, imm16 and flagZ, imm16, imm16 and stk and flagZ, regBC and stk, regA and imm8, stk, stk and flagZ, stk, imm16 and flagZ, nil, imm16 and stk and flagZ, imm16 and stk, regA and imm8 and flagC, stk,
        stk and flagC, regDE and stk, imm16 and flagC, nil, imm16 and stk and flagC, regDE and stk, regA and imm8, stk, stk and flagC, stk, imm16 and flagC, nil, imm16 and stk and flagC, nil, regA and imm8 and flagC, stk,
        immPtrH and regA, regHL and stk, ptrC and regA, nil, nil, regHL and stk, binA and immBin, stk, stk and imm8, regHL, immPtr8 and regA, nil, nil, nil, binA and immBin, stk,
        regA and immPtrH, regAF and stk, regA and ptrC, nil, nil, regAF and stk, binA and immBin, stk, regHL and f8Param, regSP and regHL, regA and immPtr8, nil, nil, nil, binA and immBin, stk)

val traceableCbOpcodes: List<TraceCollector> = listOf(
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        regB, regC, regD, regE, regH, regL, ptrHL, regA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA,
        binB, binC, binD, binE, binH, binL, ptrBinHL, binA, binB, binC, binD, binE, binH, binL, ptrBinHL, binA)