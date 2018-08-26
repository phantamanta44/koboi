package io.github.phantamanta44.koboi.debug

import io.github.phantamanta44.koboi.GameEngine
import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.cpu.CpuState
import io.github.phantamanta44.koboi.cpu.FlagRegister
import io.github.phantamanta44.koboi.cpu.IRegister
import io.github.phantamanta44.koboi.memory.IMemoryArea
import io.github.phantamanta44.koboi.util.PropDel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DebugTarget(private val engine: GameEngine) : IDebugTarget {

    override val memory: IMemoryArea
        get() = engine.memory
    override val cpu: ICpuAccess = CpuWrapper(engine.cpu)
    override val tCycle: Long
        get() = engine.gameTick

    internal lateinit var session: IDebugSession

    private val frozen: AtomicBoolean = AtomicBoolean(false)
    private val lock: ReentrantLock = ReentrantLock()
    private val lockCondition: Condition = lock.newCondition()

    // listeners

    fun onBeforeGameLoop() {
        if (session.shouldFreeze()) {
            frozen.set(true)
            lock.lockInterruptibly()
            try {
                while (frozen.get()) lockCondition.await()
            } finally {
                lock.unlock()
            }
        } else {
            frozen.set(false)
        }
    }

    // api

    override fun endDebugSession() = engine.endDebugSession()

    override fun unfreeze() {
        frozen.set(false)
        lock.lockInterruptibly()
        try {
            lockCondition.signalAll()
        } finally {
            lock.unlock()
        }
    }

}

class CpuWrapper(cpu: Cpu) : ICpuAccess {

    override var regA: Byte by cpu.regA.delegate()
    override var regF: IFlagAccess = FlagWrapper(cpu.regF)
    override var regB: Byte by cpu.regB.delegate()
    override var regC: Byte by cpu.regC.delegate()
    override var regD: Byte by cpu.regD.delegate()
    override var regE: Byte by cpu.regE.delegate()
    override var regH: Byte by cpu.regH.delegate()
    override var regL: Byte by cpu.regL.delegate()
    override var regSP: Short by cpu.regSP.delegate()
    override var regPC: Short by cpu.regPC.delegate()
    
    override var regAF: Short by cpu.regAF.delegate()
    override var regBC: Short by cpu.regBC.delegate()
    override var regDE: Short by cpu.regDE.delegate()
    override var regHL: Short by cpu.regHL.delegate()

    override val state: CpuState by PropDel.r(cpu::state)
    override val doubleClock: Boolean by PropDel.r(cpu::doubleClock)
    override var flagIME: Boolean by PropDel.rw(cpu::flagIME)

}

class FlagWrapper(flags: FlagRegister) : IFlagAccess {

    override var byteValue: Byte by flags.delegate()

    override var flagZ: Boolean by PropDel.rw(flags::kZ)
    override var flagN: Boolean by PropDel.rw(flags::kN)
    override var flagH: Boolean by PropDel.rw(flags::kH)
    override var flagC: Boolean by PropDel.rw(flags::kC)
    
}

fun <E, T : Number>IRegister<T>.delegate(): ReadWriteProperty<E, T> {
    return object : ReadWriteProperty<E, T> {

        override fun getValue(thisRef: E, property: KProperty<*>): T = read()

        override fun setValue(thisRef: E, property: KProperty<*>, value: T) = write(value)

    }
}