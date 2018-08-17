package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.KoboiConfig
import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.backtrace.Backtrace
import io.github.phantamanta44.koboi.memory.ClockSpeedRegister
import io.github.phantamanta44.koboi.memory.IMemoryArea
import io.github.phantamanta44.koboi.memory.InterruptRegister
import io.github.phantamanta44.koboi.memory.LcdControlRegister
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt
import java.io.PrintStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KMutableProperty1

class Cpu(val memory: IMemoryArea,
          val memIntReq: InterruptRegister, private val memIntEnable: InterruptRegister,
          private val memClockSpeed: ClockSpeedRegister, private val memLcdControl: LcdControlRegister) {

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
    var flagIME: Boolean = false

    val regAF: IRegister<Short> = RegisterPair(regA, regF)
    val regBC: IRegister<Short> = RegisterPair(regB, regC)
    val regDE: IRegister<Short> = RegisterPair(regD, regE)
    val regHL: IRegister<Short> = RegisterPair(regH, regL)

    val backtrace = Backtrace(this)

    var alive: AtomicBoolean = AtomicBoolean(true)
    var state: CpuState = CpuState.NORMAL
    var doubleClock: Boolean = false

    var cycleDuration: Long = 238
    private var idleCycles: Int = 0
    private val opQueue: Queue<Insn> = LinkedList()
    private var opcode: Byte = 0

    var imeChangeNextInsn: ImeChange = ImeChange.NONE
    var imeChangeThisInsn: ImeChange = ImeChange.NONE

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

    fun queue(insn: Insn) = opQueue.offer(insn)

    fun idle(cycles: Int) {
        idleCycles += cycles - 1
    }

    fun cycle() {
        if (state == CpuState.STOPPED) {
            if (memIntReq.joypad) {
                memLcdControl.lcdDisplayEnable = true
                state = CpuState.NORMAL
            }
        } else if (state == CpuState.HALTED) {
            if (memIntReq.value.toInt() and memIntEnable.value.toInt() != 0) {
                state = CpuState.NORMAL
            }
        }
        if (state == CpuState.NORMAL) cycle0()
    }

    private fun cycle0() {
        try {
            if (idleCycles > 0) {
                --idleCycles
            } else {
                val queued = opQueue.poll()
                if (queued != null) {
                    queued(this)
                } else {
                    // check ime flag
                    if (imeChangeThisInsn == ImeChange.ON) {
                        flagIME = true
                    } else if (imeChangeThisInsn == ImeChange.OFF) {
                        flagIME = false
                    }
                    imeChangeThisInsn = imeChangeNextInsn
                    imeChangeNextInsn = ImeChange.NONE

                    // check interrupts
                    var doCycle = true
                    if (flagIME) {
                        for (interrupt in InterruptType.values()) {
                            if (interrupt.flag.get(memIntReq) && interrupt.flag.get(memIntEnable)) {
                                interrupt(interrupt)
                                doCycle = false
                                break
                            }
                        }
                    }

                    // execute an opcode
                    if (doCycle) {
                        opcode = readByte()
                        Opcodes[opcode](this)
                    }
                }
            }
        } catch (e: Exception) {
            except(e)
        }
    }

    fun trace() {
        backtrace.accept(opcode)
    }

    fun stop() {
        if (!doubleClock && memClockSpeed.prepareSpeedSwitch) {
            memClockSpeed.doubleSpeed = true
            memClockSpeed.prepareSpeedSwitch = false
            doubleClock = true
            cycleDuration /= 2
            Loggr.debug("Double speed mode enabled.")
        }
        memLcdControl.lcdDisplayEnable = false
        state = CpuState.STOPPED
    }

    fun halt() {
        state = CpuState.HALTED
    }

    private fun interrupt(interrupt: InterruptType) {
        if (KoboiConfig.logInterrupts) Loggr.trace("Calling interrupt vector for $interrupt")
        interrupt.flag.set(memIntReq, false)
        flagIME = false
        idle(19)
        queue({ interrupt.callVector(this) })
    }

    private fun except(e: Exception) {
        alive.set(false)
        state = CpuState.DEAD
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
        if (KoboiConfig.backtrace.enableBacktrace) {
            out.println("===== BACKTRACE =====")
            out.println(cpu.backtrace.stackFrame.stringify())
        }
        out.println("===== CPU STATE =====")
        out.println("IME flag = ${cpu.flagIME}")
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
    }

}

enum class CpuState {

    NORMAL, STOPPED, HALTED, DEAD

}

enum class InterruptType(val flag: KMutableProperty1<InterruptRegister, Boolean>, val vector: Short) {

    V_BLANK(InterruptRegister::vBlank, 0x40),
    LCD_STAT(InterruptRegister::lcdStat, 0x48),
    TIMER(InterruptRegister::timer, 0x50),
    SERIAL(InterruptRegister::serial, 0x58),
    JOYPAD(InterruptRegister::joypad, 0x60);

    val callVector: Insn = {
        it.backtrace.stackCall(vector)
        it.regSP.decrement(2)
        it.memory.write(it.regSP.read().toUnsignedInt(), it.regPC.read())
        it.regPC.write(vector)
    }

}

enum class ImeChange {

    NONE, ON, OFF

}