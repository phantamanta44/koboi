package io.github.phantamanta44.koboi.backtrace

import io.github.phantamanta44.koboi.KoboiConfig
import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt

class Backtrace(private val cpu: Cpu) {

    var enabled: Boolean = false
    var stackFrame: StackFrame = StackFrame(null, 0)

    fun accept(opcode: Byte) {
        if (enabled) {
            val insnPtr = cpu.regPC.read()
            if (KoboiConfig.backtrace.insnTrace) {
                stackFrame.call(cpu, insnPtr, opcode, if (KoboiConfig.backtrace.fullTrace) CpuFreeze(cpu) else null)
            } else {
                Loggr.trace("$${insnPtr.toUnsignedHex()} - ${mnemonics[opcode.toUnsignedInt()]}")
            }
        }
    }

    fun stackCall(addr: Short) {
        if (KoboiConfig.backtrace.enableBacktrace) {
            stackFrame = StackFrame(stackFrame, addr)
        }
    }

    fun stackReturn() {
        if (KoboiConfig.backtrace.enableBacktrace) {
            if (stackFrame.parent != null) {
                stackFrame = stackFrame.parent!!
            } else {
                Loggr.error("Inconsistent call stack state! Disabling backtrace.")
                KoboiConfig.backtrace = BacktraceDetail.NONE
            }
        }
    }

}

class StackFrame(val parent: StackFrame?, val callAddress: Short) {

    val calls: MutableList<StackCall> = mutableListOf()

    fun call(cpu: Cpu, insnPtr: Short, opcode: Byte, freeze: CpuFreeze?) {
        val call = StackCall(insnPtr, opcode, traceOpcodes[opcode.toUnsignedInt()].collectArgs(cpu), freeze)
        calls.add(call)
        Loggr.trace(call)
    }

    fun stringify(withCalls: Boolean = true): String {
        val sb = StringBuilder("$${callAddress.toUnsignedHex()} Stack frame")
        if (withCalls) {
            for (call in calls.asReversed()) {
                sb.append("\n  $call")
                call.freeze?.let { sb.append("\n    ${call.freeze}") }
            }
        }
        if (parent != null) sb.append("\n${parent.stringify(KoboiConfig.fullBacktrace)}")
        return sb.toString()
    }

}

data class StackCall(val insnPtr: Short, val opcode: Byte, val args: List<IArgumentValue>, val freeze: CpuFreeze?) {

    override fun toString(): String = if (args.isEmpty()) {
        "$${insnPtr.toUnsignedHex()} ${mnemonics[opcode.toUnsignedInt()]}"
    } else {
        "$${insnPtr.toUnsignedHex()} ${String.format("%-11s", mnemonics[opcode.toUnsignedInt()])}${args.stringify()}"
    }

}

class CpuFreeze(cpu: Cpu) {

    val a: Byte = cpu.regA.read()
    val b: Byte = cpu.regB.read()
    val c: Byte = cpu.regC.read()
    val d: Byte = cpu.regD.read()
    val e: Byte = cpu.regE.read()
    val h: Byte = cpu.regH.read()
    val l: Byte = cpu.regL.read()
    val sp: Short = cpu.regSP.read()
    val pc: Short = cpu.regPC.read()
    val ime: Boolean = cpu.flagIME
    val af: Short = cpu.regAF.read()
    val bc: Short = cpu.regBC.read()
    val de: Short = cpu.regDE.read()
    val hl: Short = cpu.regHL.read()
    val flagZ: Boolean = cpu.regF.kZ
    val flagN: Boolean = cpu.regF.kN
    val flagH: Boolean = cpu.regF.kH
    val flagC: Boolean = cpu.regF.kC
    val doubleClock: Boolean = cpu.doubleClock

    override fun toString(): String {
        return "A=${a.toUnsignedHex()};B=${b.toUnsignedHex()};C=${c.toUnsignedHex()};D=${d.toUnsignedHex()};" +
                "E=${e.toUnsignedHex()};H=${h.toUnsignedHex()};L=${l.toUnsignedHex()};SP=${sp.toUnsignedHex()};" +
                "PC=${pc.toUnsignedHex()};AF=${af.toUnsignedHex()};BC=${bc.toUnsignedHex()};DE=${de.toUnsignedHex()};" +
                "HL=${hl.toUnsignedHex()};fZ=${flagZ.ocb()};fN=${flagN.ocb()};fH=${flagH.ocb()};fC=${flagC.ocb()};" +
                "IME=${ime.ocb()};2x=${doubleClock.ocb()}"
    }
    
}

enum class BacktraceDetail(val enableBacktrace: Boolean = false,
                           val insnTrace: Boolean = false,
                           val fullTrace: Boolean = false) {

    NONE(), STACK(true), INSN(true, true), FULL(true, true, true)

}