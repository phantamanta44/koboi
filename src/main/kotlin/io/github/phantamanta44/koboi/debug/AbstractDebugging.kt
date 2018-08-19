package io.github.phantamanta44.koboi.debug

import io.github.phantamanta44.koboi.cpu.CpuState
import io.github.phantamanta44.koboi.cpu.InterruptType
import io.github.phantamanta44.koboi.memory.IMemoryArea

interface IDebugProvider {

    fun startDebugging(target: IDebugTarget): IDebugSession

}

interface IDebugSession {

    fun kill()

    fun shouldFreeze(): Boolean = false

    fun onMemoryMutate(addr: Int, length: Int) {
        // NO-OP
    }

    fun onCpuMutate(prop: CpuProperty) {
        // NO-OP
    }

    fun onCpuExecute(opcode: Byte) {
        // NO-OP
    }

    fun onCpuCall(addr: Short) {
        // NO-OP
    }

    fun onCpuReturn() {
        // NO-OP
    }

    fun onInterruptExecuted(interrupt: InterruptType) {
        // NO-OP
    }

    // TODO dma debugging
    // TODO graphics debugging
    // TODO audio debugging
    // TODO timer debugging
    // TODO input debugging (?)

}

interface IDebugTarget {

    val memory: IMemoryArea
    val cpu: ICpuAccess
    val tCycle: Long

    fun endDebugSession()

    fun unfreeze()

}

interface ICpuAccess {

    var regA: Byte
    var regF: IFlagAccess
    var regB: Byte
    var regC: Byte
    var regD: Byte
    var regE: Byte
    var regH: Byte
    var regL: Byte
    var regSP: Short
    var regPC: Short

    var regAF: Short
    var regBC: Short
    var regDE: Short
    var regHL: Short

    val state: CpuState
    val doubleClock: Boolean
    var flagIME: Boolean
    
}

interface IFlagAccess {

    var byteValue: Byte

    var flagZ: Boolean
    var flagN: Boolean
    var flagH: Boolean
    var flagC: Boolean

}

enum class CpuProperty {

    REG_A, REG_F, REG_B, REG_C, REG_D, REG_E, REG_H, REG_L, REG_PC, REG_SP, FLAG_IME, STATE, CLOCK_SPEED

}