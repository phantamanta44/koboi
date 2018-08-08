package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.KoboiConfig
import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.util.toShortHex
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt

class Backtrace(private val cpu: Cpu) {

    var enabled: Boolean = false
    var stackFrame: StackFrame = StackFrame(null, 0)

    fun accept(opcode: Byte) {
        val insnPtr = cpu.regPC.read()
        if (enabled && KoboiConfig.backtrace.insnTrace) {
            stackFrame.call(cpu, insnPtr, opcode, if (KoboiConfig.backtrace.fullTrace) CpuFreeze(cpu) else null)
        } else {
            Loggr.trace("$${insnPtr.toUnsignedHex()} - ${mnemonics[opcode.toUnsignedInt()]}")
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
        val opcodeIndex = opcode.toUnsignedInt()
        val args = traceOpcodes[opcodeIndex].stringifyArgs(cpu)
        calls.add(StackCall(insnPtr, opcode, args, freeze))
        Loggr.trace("$${insnPtr.toUnsignedHex()} - ${mnemonics[opcodeIndex]}$args")
    }

    fun stringify(withCalls: Boolean = true): String {
        val sb = StringBuilder("$${callAddress.toUnsignedHex()} Stack frame")
        if (withCalls) {
            for (call in calls.asReversed()) {
                if (call.args.isEmpty()) {
                    sb.append("\n  $${call.insnPtr.toUnsignedHex()} ${mnemonics[call.opcode.toUnsignedInt()]}")
                } else {
                    sb.append("\n  $${call.insnPtr.toUnsignedHex()} ${String.format("%-11s", mnemonics[call.opcode.toUnsignedInt()])}${call.args}")
                }
                call.freeze?.let {
                    sb.append("\n    ${call.freeze}")
                }
            }
        }
        if (parent != null) sb.append("\n${parent.stringify(KoboiConfig.fullBacktrace)}")
        return sb.toString()
    }

}

data class StackCall(val insnPtr: Short, val opcode: Byte, val args: String, val freeze: CpuFreeze?)

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
                "HL=${hl.toUnsignedHex()};fZ=${ocb(flagZ)};fN=${ocb(flagN)};fH=${ocb(flagH)};fC=${ocb(flagC)};" +
                "IME=${ocb(ime)};2x=${ocb(doubleClock)}"
    }
    
}

enum class BacktraceDetail(val enableBacktrace: Boolean = false,
                           val insnTrace: Boolean = false,
                           val fullTrace: Boolean = false) {

    NONE(), STACK(true), INSN(true, true), FULL(true, true, true)

}

val mnemonics: Array<String> = arrayOf("NOP", "LD BC,d16", "LD (BC),A", "INC BC", "INC B", "DEC B", "LD B,d8", "RLCA",
        "LD (a16),SP", "ADD HL,BC", "LD A,(BC)", "DEC BC", "INC C", "DEC C", "LD C,d8", "RRCA", "STOP", "LD DE,d16",
        "LD (DE),A", "INC DE", "INC D", "DEC D", "LD D,d8", "RLA", "JR r8", "ADD HL,DE", "LD A,(DE)", "DEC DE", "INC E",
        "DEC E", "LD E,d8", "RRA", "JR NZ,r8", "LD HL,d16", "LDI (HL),A", "INC HL", "INC H", "DEC H", "LD H,d8", "DAA",
        "JR Z,r8", "ADD HL,HL", "LDI A,(HL)", "DEC HL", "INC L", "DEC L", "LD L,d8", "CPL", "JR NC,r8", "LD SP,d16",
        "LDD (HL),A", "INC SP", "INC (HL)", "DEC (HL)", "LD (HL),d8", "SCF", "JR C,r8", "ADD HL,SP", "LDD A,(HL)",
        "DEC SP", "INC A", "DEC A", "LD A,d8", "CCF", "LD B,B", "LD B,C", "LD B,D", "LD B,E", "LD B,H", "LD B,L",
        "LD B,(HL)", "LD B,A", "LD C,B", "LD C,C", "LD C,D", "LD C,E", "LD C,H", "LD C,L", "LD C,(HL)", "LD C,A",
        "LD D,B", "LD D,C", "LD D,D", "LD D,E", "LD D,H", "LD D,L", "LD D,(HL)", "LD D,A", "LD E,B", "LD E,C", "LD E,D",
        "LD E,E", "LD E,H", "LD E,L", "LD E,(HL)", "LD E,A", "LD H,B", "LD H,C", "LD H,D", "LD H,E", "LD H,H", "LD H,L",
        "LD H,(HL)", "LD H,A", "LD L,B", "LD L,C", "LD L,D", "LD L,E", "LD L,H", "LD L,L", "LD L,(HL)", "LD L,A",
        "LD (HL),B", "LD (HL),C", "LD (HL),D", "LD (HL),E", "LD (HL),H", "LD (HL),L", "HALT", "LD (HL),A", "LD A,B",
        "LD A,C", "LD A,D", "LD A,E", "LD A,H", "LD A,L", "LD A,(HL)", "LD A,A", "ADD A,B", "ADD A,C", "ADD A,D",
        "ADD A,E", "ADD A,H", "ADD A,L", "ADD A,(HL)", "ADD A,A", "ADC A,B", "ADC A,C", "ADC A,D", "ADC A,E", "ADC A,H",
        "ADC A,L", "ADC A,(HL)", "ADC A,A", "SUB B", "SUB C", "SUB D", "SUB E", "SUB H", "SUB L", "SUB (HL)", "SUB A",
        "SBC A,B", "SBC A,C", "SBC A,D", "SBC A,E", "SBC A,H", "SBC A,L", "SBC A,(HL)", "SBC A,A", "AND B", "AND C",
        "AND D", "AND E", "AND H", "AND L", "AND (HL)", "AND A", "XOR B", "XOR C", "XOR D", "XOR E", "XOR H", "XOR L",
        "XOR (HL)", "XOR A", "OR B", "OR C", "OR D", "OR E", "OR H", "OR L", "OR (HL)", "OR A", "CP B", "CP C", "CP D",
        "CP E", "CP H", "CP L", "CP (HL)", "CP A", "RET NZ", "POP BC", "JP NZ,a16", "JP a16", "CALL NZ,a16", "PUSH BC",
        "ADD A,d8", "RST 00H", "RET Z", "RET", "JP Z,a16", "PREFIX", "CALL Z,a16", "CALL a16", "ADC A,d8", "RST 08H",
        "RET NC", "POP DE", "JP NC,a16", "NIL", "CALL NC,a16", "PUSH DE", "SUB d8", "RST 10H", "RET C", "RETI",
        "JP C,a16", "NIL", "CALL C,a16", "NIL", "SBC A,d8", "RST 18H", "LDH (a8),A", "POP HL", "LD (C),A", "NIL", "NIL",
        "PUSH HL", "AND d8", "RST 20H", "ADD SP,r8", "JP HL", "LD (a16),A", "NIL", "NIL", "NIL", "XOR d8", "RST 28H",
        "LDH A,(a8)", "POP AF", "LD A,(C)", "DI", "NIL", "PUSH AF", "OR d8", "RST 30H", "LD HL,SP+r8", "LD SP,HL",
        "LD A,(a16)", "EI", "NIL", "NIL", "CP d8", "RST 38H")

val traceOpcodes: List<TraceOpcode> = mnemonics.map {
    val parts = it.split(' ')
    if (it == "PREFIX") {
        TraceOpcodeCb()
    } else if (parts.size == 1 || parts[0] == "RST") {
        TraceOpcode(it, null)
    } else {
        TraceOpcode(parts[0], parts[1].split(',', '+').map { arg ->
            if (arg.startsWith('(')) {
                val actualArg = arg.substring(1, arg.length - 1)
                when (actualArg) {
                    "a8" -> TraceOpcode.ArgumentImmediateHighPointer()
                    "a16" -> TraceOpcode.ArgumentImmediatePointer()
                    "C" -> TraceOpcode.ArgumentRegisterHighPointer(actualArg)
                    else -> TraceOpcode.ArgumentRegisterPointer(actualArg)
                }
            } else {
                when (arg) {
                    "C", "NC", "Z", "NZ" -> if (parts[0] == "CALL" || parts[0] == "RET" || parts[0] == "JP" || parts[0] == "JR") {
                        TraceOpcode.ArgumentFlag(arg)
                    } else {
                        TraceOpcode.ArgumentRegister8(arg)
                    }
                    "A", "F", "B", "D", "E", "H", "L" -> TraceOpcode.ArgumentRegister8(arg)
                    "AF", "BC", "DE", "HL", "SP", "PC" -> TraceOpcode.ArgumentRegister16(arg)
                    "d8" -> TraceOpcode.ArgumentImmediateU8()
                    "d16", "a16" -> TraceOpcode.ArgumentImmediateU16()
                    "r8" -> TraceOpcode.ArgumentImmediateI8()
                    else -> throw IllegalStateException(arg)
                }
            }
        })
    }
}

open class TraceOpcode(val name: String, val arguments: List<IArgument>?) {

    open fun stringifyArgs(cpu: Cpu): String {
        if (arguments == null) return ""
        val sb = StringBuilder()
        var pc = cpu.regPC.read().toUnsignedInt() + 1
        for (argument in arguments) {
            sb.append(" ")
            pc = argument.read(cpu, pc, sb)
        }
        return sb.toString()
    }

    interface IArgument {

        fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int

    }

    open class ArgumentRegister8(registerName: String) : IArgument {

        protected val register: (Cpu) -> IRegister<Byte> = when (registerName) {
            "A" -> Cpu::regA
            "F" -> Cpu::regF
            "B" -> Cpu::regB
            "C" -> Cpu::regC
            "D" -> Cpu::regD
            "E" -> Cpu::regE
            "H" -> Cpu::regH
            "L" -> Cpu::regL
            else -> throw IllegalArgumentException(registerName)
        }

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            sb.append(register(cpu).read().toUnsignedHex())
            return pc
        }

    }

    open class ArgumentRegister16(registerName: String) : IArgument {

        protected val register: (Cpu) -> IRegister<Short> = when (registerName) {
            "AF" -> Cpu::regAF
            "BC" -> Cpu::regBC
            "DE" -> Cpu::regDE
            "HL" -> Cpu::regHL
            "PC" -> Cpu::regPC
            "SP" -> Cpu::regSP
            else -> throw IllegalArgumentException(registerName)
        }

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            sb.append(register(cpu).read().toUnsignedHex())
            return pc
        }

    }

    class ArgumentRegisterPointer(registerName: String) : ArgumentRegister16(registerName) {

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            val addr = register(cpu).read()
            sb.append(addr.toUnsignedHex())
                    .append('=')
                    .append(cpu.memory.read(addr.toUnsignedInt(), true).toUnsignedHex())
            return pc
        }

    }

    class ArgumentRegisterHighPointer(registerName: String) : ArgumentRegister8(registerName) {

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            val addr = register(cpu).read().toUnsignedInt() + 0xFF00
            sb.append(addr.toShortHex())
                    .append('=')
                    .append(cpu.memory.read(addr, true).toUnsignedHex())
            return pc
        }

    }

    class ArgumentImmediatePointer : IArgument {

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            val addr = cpu.memory.readShort(pc, true)
            sb.append(addr.toUnsignedHex())
                    .append('=')
                    .append(cpu.memory.read(addr.toUnsignedInt(), true).toUnsignedHex())
            return pc + 2
        }

    }

    class ArgumentImmediateHighPointer : IArgument {

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            val addr = (cpu.memory.read(pc, true).toUnsignedInt() + 0xFF00)
            sb.append(addr.toShortHex())
                    .append('=')
                    .append(cpu.memory.read(addr, true).toUnsignedHex())
            return pc + 1
        }

    }


    class ArgumentImmediateU8 : IArgument {

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            sb.append(cpu.memory.read(pc, true).toUnsignedHex())
            return pc + 1
        }

    }

    class ArgumentImmediateU16 : IArgument {

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            sb.append(cpu.memory.readShort(pc, true).toUnsignedHex())
            return pc + 2
        }

    }

    class ArgumentImmediateI8: IArgument {

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            sb.append(cpu.memory.read(pc, true).toInt())
            return pc + 1
        }

    }

    class ArgumentFlag(flagName: String) : IArgument {

        private val flag: (FlagRegister) -> Boolean = when (flagName) {
            "C" -> FlagRegister::kC
            "NC" -> { flags -> !flags.kC }
            "Z" -> FlagRegister::kZ
            "NZ" -> { flags -> !flags.kZ }
            else -> throw IllegalArgumentException(flagName)
        }

        override fun read(cpu: Cpu, pc: Int, sb: StringBuilder): Int {
            sb.append(ocb(flag(cpu.regF)))
            return pc
        }

    }

}

class TraceOpcodeCb : TraceOpcode("PREFIX CB", listOf()) {

    override fun stringifyArgs(cpu: Cpu): String {
        val opcode = cpu.memory.read(cpu.regPC.read().toUnsignedInt() + 1).toUnsignedInt()
        return " ${cbMnemonics[opcode]}${cbTraceOpcodes[opcode].stringifyArgs(cpu)}"
    }

}

fun ocb(bool: Boolean): Char = if (bool) 'T' else 'F'

val cbMnemonics: Array<String> = arrayOf("RLC B", "RLC C", "RLC D", "RLC E", "RLC H", "RLC L", "RLC (HL)", "RLC A",
        "RRC B", "RRC C", "RRC D", "RRC E", "RRC H", "RRC L", "RRC (HL)", "RRC A", "RL B", "RL C", "RL D", "RL E",
        "RL H", "RL L", "RL (HL)", "RL A", "RR B", "RR C", "RR D", "RR E", "RR H", "RR L", "RR (HL)", "RR A", "SLA B",
        "SLA C", "SLA D", "SLA E", "SLA H", "SLA L", "SLA (HL)", "SLA A", "SRA B", "SRA C", "SRA D", "SRA E", "SRA H",
        "SRA L", "SRA (HL)", "SRA A", "SWAP B", "SWAP C", "SWAP D", "SWAP E", "SWAP H", "SWAP L", "SWAP (HL)", "SWAP A",
        "SRL B", "SRL C", "SRL D", "SRL E", "SRL H", "SRL L", "SRL (HL)", "SRL A", "BIT 0,B", "BIT 0,C", "BIT 0,D",
        "BIT 0,E", "BIT 0,H", "BIT 0,L", "BIT 0,(HL)", "BIT 0,A", "BIT 1,B", "BIT 1,C", "BIT 1,D", "BIT 1,E", "BIT 1,H",
        "BIT 1,L", "BIT 1,(HL)", "BIT 1,A", "BIT 2,B", "BIT 2,C", "BIT 2,D", "BIT 2,E", "BIT 2,H", "BIT 2,L",
        "BIT 2,(HL)", "BIT 2,A", "BIT 3,B", "BIT 3,C", "BIT 3,D", "BIT 3,E", "BIT 3,H", "BIT 3,L", "BIT 3,(HL)",
        "BIT 3,A", "BIT 4,B", "BIT 4,C", "BIT 4,D", "BIT 4,E", "BIT 4,H", "BIT 4,L", "BIT 4,(HL)", "BIT 4,A", "BIT 5,B",
        "BIT 5,C", "BIT 5,D", "BIT 5,E", "BIT 5,H", "BIT 5,L", "BIT 5,(HL)", "BIT 5,A", "BIT 6,B", "BIT 6,C", "BIT 6,D",
        "BIT 6,E", "BIT 6,H", "BIT 6,L", "BIT 6,(HL)", "BIT 6,A", "BIT 7,B", "BIT 7,C", "BIT 7,D", "BIT 7,E", "BIT 7,H",
        "BIT 7,L", "BIT 7,(HL)", "BIT 7,A", "RES 0,B", "RES 0,C", "RES 0,D", "RES 0,E", "RES 0,H", "RES 0,L",
        "RES 0,(HL)", "RES 0,A", "RES 1,B", "RES 1,C", "RES 1,D", "RES 1,E", "RES 1,H", "RES 1,L", "RES 1,(HL)",
        "RES 1,A", "RES 2,B", "RES 2,C", "RES 2,D", "RES 2,E", "RES 2,H", "RES 2,L", "RES 2,(HL)", "RES 2,A", "RES 3,B",
        "RES 3,C", "RES 3,D", "RES 3,E", "RES 3,H", "RES 3,L", "RES 3,(HL)", "RES 3,A", "RES 4,B", "RES 4,C", "RES 4,D",
        "RES 4,E", "RES 4,H", "RES 4,L", "RES 4,(HL)", "RES 4,A", "RES 5,B", "RES 5,C", "RES 5,D", "RES 5,E", "RES 5,H",
        "RES 5,L", "RES 5,(HL)", "RES 5,A", "RES 6,B", "RES 6,C", "RES 6,D", "RES 6,E", "RES 6,H", "RES 6,L",
        "RES 6,(HL)", "RES 6,A", "RES 7,B", "RES 7,C", "RES 7,D", "RES 7,E", "RES 7,H", "RES 7,L", "RES 7,(HL)",
        "RES 7,A", "SET 0,B", "SET 0,C", "SET 0,D", "SET 0,E", "SET 0,H", "SET 0,L", "SET 0,(HL)", "SET 0,A", "SET 1,B",
        "SET 1,C", "SET 1,D", "SET 1,E", "SET 1,H", "SET 1,L", "SET 1,(HL)", "SET 1,A", "SET 2,B", "SET 2,C", "SET 2,D",
        "SET 2,E", "SET 2,H", "SET 2,L", "SET 2,(HL)", "SET 2,A", "SET 3,B", "SET 3,C", "SET 3,D", "SET 3,E", "SET 3,H",
        "SET 3,L", "SET 3,(HL)", "SET 3,A", "SET 4,B", "SET 4,C", "SET 4,D", "SET 4,E", "SET 4,H", "SET 4,L",
        "SET 4,(HL)", "SET 4,A", "SET 5,B", "SET 5,C", "SET 5,D", "SET 5,E", "SET 5,H", "SET 5,L", "SET 5,(HL)",
        "SET 5,A", "SET 6,B", "SET 6,C", "SET 6,D", "SET 6,E", "SET 6,H", "SET 6,L", "SET 6,(HL)", "SET 6,A", "SET 7,B",
        "SET 7,C", "SET 7,D", "SET 7,E", "SET 7,H", "SET 7,L", "SET 7,(HL)", "SET 7,A")

val cbTraceOpcodes: List<TraceOpcode> = cbMnemonics.map {
    val regIndex = if (it.length >= 5 && it[4].isDigit()) 5 else it.indexOf(' ')
    val reg = it.substring(regIndex + 1)
    TraceOpcode(it.substring(0, regIndex), listOf(if (reg == "(HL)") {
        TraceOpcode.ArgumentRegisterPointer("HL")
    } else {
        TraceOpcode.ArgumentRegister8(reg)
    }))
}