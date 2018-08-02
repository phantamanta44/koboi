package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.KoboiConfig
import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.memory.IMemoryArea
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt
import java.io.PrintStream
import kotlin.system.measureNanoTime

class Cpu(val memory: IMemoryArea) {

    val regA: IRegister<Byte> = SingleByteRegister()
    val regF: FlagRegister = FlagRegister()
    val regB: IRegister<Byte> = SingleByteRegister()
    val regC: IRegister<Byte> = SingleByteRegister()
    val regD: IRegister<Byte> = SingleByteRegister()
    val regE: IRegister<Byte> = SingleByteRegister()
    val regH: IRegister<Byte> = SingleByteRegister()
    val regL: IRegister<Byte> = SingleByteRegister()
    val regSP: IRegister<Short> = SingleShortRegister()
    val regPC: IRegister<Short> = SingleShortRegister()
    val regIME: MemoryBitRegister = MemoryBitRegister(memory, 0xFFFF)

    val regAF: IRegister<Short> = RegisterPair(regA, regF)
    val regBC: IRegister<Short> = RegisterPair(regB, regC)
    val regDE: IRegister<Short> = RegisterPair(regD, regE)
    val regHL: IRegister<Short> = RegisterPair(regH, regL)

    var alive: Boolean = true

    private var cycleDuration: Long = 238
    private var cycleTime: Long = 0
    private var stackCounter: Int = 0

    fun readByte(): Byte = memory.read(regPC.read().toUnsignedInt())

    fun readByteAndAdvance(): Byte {
        val byte = readByte()
        advance()
        return byte
    }

    fun readShort(): Short = memory.readShort(regPC.read().toUnsignedInt())

    fun readShortAndAdvance(): Short {
        val short = readShort()
        advance(2)
        return short
    }

    fun advance(offset: Int = 1) = regPC.increment(offset)

    fun idle(cycles: Int) {
        cycleTime = cycles * cycleDuration
    }

    fun stackPush() {
        ++stackCounter
    }

    fun stackPop() {
        if (--stackCounter < 0) error(IllegalStateException("Illogical stack pointer state!"))
    }

    fun cycle() {
        try {
            val sleepNanos = cycleTime - measureNanoTime {
                val opcode = readByte()
                Loggr.trace("$${regPC.read().toUnsignedHex()} - ${opcode.toUnsignedHex()} :: ${mnemonics[opcode.toUnsignedInt()]}")
                val op = Opcodes[opcode]
                if (op == null) {
                    throw UnknownOpcodeException(opcode)
                } else {
                    op(this)
                }
            }
            if (sleepNanos > 0) {
                val sleepMillis = Math.floor(sleepNanos / 1000000.0).toLong()
                Thread.sleep(sleepMillis, (sleepNanos - sleepMillis * 1000000).toInt())
            } else {
//            Loggr.trace("Going too slow! $sleepNanos ns cycle duration difference")
            }
        } catch (e: Exception) {
            except(e)
        }
    }

    fun stop() {

        // TODO wait for joypad input
    }

    fun halt() {
        TODO("not implemented")
    }

    private fun except(e: Exception) {
        alive = false
        throw EmulationException(this, e)
    }

}

class UnknownOpcodeException(opcode: Byte) : Exception("Unknown opcode ${opcode.toUnsignedHex()}!")

class EmulationException(val cpu: Cpu, cause: Exception) : Exception("Exception raised in emulation!", cause) {

    companion object {

        fun printRegisterState8(out: PrintStream, register: IRegister<Byte>, name: String) {
            out.println("Reg $name = ${register.read().toUnsignedHex()}")
        }

        fun printRegisterState16(out: PrintStream, register: IRegister<Short>, name: String) {
            out.println("Reg $name = ${register.read().toUnsignedHex()}")
        }

    }

    fun printCpuState(out: PrintStream = System.err) {
        out.println("===== REGISTER STATES =====")
        printRegisterState8(out, cpu.regA, "A")
        printRegisterState8(out, cpu.regF, "F")
        printRegisterState8(out, cpu.regB, "B")
        printRegisterState8(out, cpu.regC, "C")
        printRegisterState8(out, cpu.regD, "D")
        printRegisterState8(out, cpu.regE, "E")
        printRegisterState8(out, cpu.regH, "H")
        printRegisterState8(out, cpu.regL, "L")
        printRegisterState16(out, cpu.regSP, "SP")
        printRegisterState16(out, cpu.regPC, "PC")
        out.println("===== REGISTER PAIR STATES =====")
        printRegisterState16(out, cpu.regAF, "AF")
        printRegisterState16(out, cpu.regBC, "BC")
        printRegisterState16(out, cpu.regDE, "DE")
        printRegisterState16(out, cpu.regHL, "HL")
        if (KoboiConfig.memTrace) {
            out.println("===== MEMORY MAP =====")

        }
    }

}

val mnemonics: Array<String> = arrayOf("NOP", "LD BC,d16", "LD (BC),A", "INC BC", "INC B", "DEC B", "LD B,d8", "RLCA",
        "LD (a16),SP", "ADD HL,BC", "LD A,(BC)", "DEC BC", "INC C", "DEC C", "LD C,d8", "RRCA", "STOP", "LD DE,d16",
        "LD (DE),A", "INC DE", "INC D", "DEC D", "LD D,d8", "RLA", "JR r8", "ADD HL,DE", "LD A,(DE)", "DEC DE", "INC E",
        "DEC E", "LD E,d8", "RRA", "JR NZ,r8", "LD HL,d16", "LD (HL),A", "INC HL", "INC H", "DEC H", "LD H,d8", "DAA",
        "JR Z,r8", "ADD HL,HL", "LD A,(HL)", "DEC HL", "INC L", "DEC L", "LD L,d8", "CPL", "JR NC,r8", "LD SP,d16",
        "LD (HL),A", "INC SP", "INC (HL)", "DEC (HL)", "LD (HL),d8", "SCF", "JR C,r8", "ADD HL,SP", "LD A,(HL)",
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
        "RET NC", "POP DE", "JP NC,a16", "", "CALL NC,a16", "PUSH DE", "SUB d8", "RST 10H", "RET C", "RETI", "JP C,a16",
        "", "CALL C,a16", "", "SBC A,d8", "RST 18H", "LDH (a8),A", "POP HL", "LD (C),A", "", "", "PUSH HL", "AND d8",
        "RST 20H", "ADD SP,r8", "JP HL", "LD (a16),A", "", "", "", "XOR d8", "RST 28H", "LDH A,(a8)", "POP AF",
        "LD A,(C)", "DI", "", "PUSH AF", "OR d8", "RST 30H", "LD HL,SP", "LD SP,HL", "LD A,(a16)", "EI", "", "",
        "CP d8", "RST 38H")