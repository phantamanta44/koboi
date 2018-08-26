package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.GameEngine
import io.github.phantamanta44.koboi.KoboiConfig
import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.memory.InterruptRegister
import io.github.phantamanta44.koboi.memory.SingleByteMemoryArea
import io.github.phantamanta44.koboi.memory.TimerDividerRegister
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt

class Timer(private val memDivider: TimerDividerRegister, private val memTimerCounter: SingleByteMemoryArea,
            private val memTimerModulo: SingleByteMemoryArea, private val memIntReq: InterruptRegister,
            engine: GameEngine) {

    private val cpu: Cpu by lazy { engine.cpu }

    var enabled: Boolean = false
    var tickRate: TimerTickRate = TimerTickRate.R_4096_HZ
    var globalTimer: Long = 0

    var timerReset: Int = -1

    private fun setTickRate(clock: Int) {
        tickRate = when (clock) {
            0b00 -> Timer.TimerTickRate.R_4096_HZ
            0b01 -> Timer.TimerTickRate.R_262144_HZ
            0b10 -> Timer.TimerTickRate.R_65536_HZ
            0b11 -> Timer.TimerTickRate.R_16384_HZ
            else -> throw IllegalStateException(clock.toString())
        }
    }

    fun testGlobalTimer(bit: Long): Boolean = globalTimer and bit != 0L

    fun testBitRisingEdge(bit: Long): Boolean = (globalTimer - 1) and bit < globalTimer and bit

    fun testBitFallingEdge(bit: Long): Boolean = (globalTimer - 1) and bit > globalTimer and bit

    fun cycle() {
        checkDivider()
        if (timerReset > -1) {
            if (--timerReset == 0) {
                memTimerCounter.value = memTimerModulo.value
                if (KoboiConfig.logTimer) {
                    Loggr.trace("Timer reloaded with ${memTimerCounter.value.toUnsignedHex()}")
                }
            }
        }
        if (enabled && testGlobalTimer(tickRate.timerBit)) {
            ++globalTimer
            if (!testGlobalTimer(tickRate.timerBit)) incTimer()
        } else {
            ++globalTimer
        }
    }

    private fun checkDivider() {
        if (testGlobalTimer(0x40)) ++memDivider.value
    }

    private fun incTimer() {
        if (memTimerCounter.value.toUnsignedInt() == 0xFF) {
            memTimerCounter.value = 0
            memIntReq.timer = true // I have no idea why this isn't in the timerReset check
            timerReset = 4
            if (KoboiConfig.logTimer) {
                Loggr.trace("Timer overflowed to 0.")
            }
        } else {
            ++memTimerCounter.value
            if (KoboiConfig.logTimer) {
                Loggr.trace("Timer incremented to ${memTimerCounter.value.toUnsignedHex()}.")
            }
        }
    }

    fun update(clock: Int, newState: Boolean) {
        if (enabled && testGlobalTimer(tickRate.timerBit)) {
            setTickRate(clock)
            enabled = newState
            if (!enabled || !testGlobalTimer(tickRate.timerBit)) incTimer()
        } else {
            setTickRate(clock)
            enabled = newState
        }
    }

    fun clearCounters() {
        if (enabled && testGlobalTimer(tickRate.timerBit)) incTimer()
        globalTimer = 0
    }

    enum class TimerTickRate(val timerBit: Long) {

        R_4096_HZ(1 shl 9),
        R_262144_HZ(1 shl 3),
        R_65536_HZ(1 shl 5),
        R_16384_HZ(1 shl 7)

    }

}