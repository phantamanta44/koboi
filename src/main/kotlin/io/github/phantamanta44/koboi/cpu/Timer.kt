package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.GameEngine
import io.github.phantamanta44.koboi.memory.InterruptRegister
import io.github.phantamanta44.koboi.memory.ResettableRegister
import io.github.phantamanta44.koboi.memory.SingleByteMemoryArea
import io.github.phantamanta44.koboi.util.toUnsignedInt

class Timer(private val memDivider: ResettableRegister, private val memTimerCounter: SingleByteMemoryArea,
            private val memTimerModulo: SingleByteMemoryArea, private val memIntReq: InterruptRegister,
            engine: GameEngine) {

    private val cpu: Cpu by lazy { engine.cpu }

    var enabled: Boolean = false
    var tickRate: TimerTickRate = TimerTickRate.R_4096_HZ
    var globalTimer: Long = 0

    private var timerCounter: Int = 0
    private var timerReset: Boolean = false

    fun cycle() {
        if (cpu.doubleClock) {
            if (globalTimer % 128 == 0L) ++memDivider.value
        } else if (globalTimer % 256 == 0L) {
            ++memDivider.value
        }
        if (enabled) {
            if (timerReset) {
                timerReset = false
                memTimerCounter.value = memTimerModulo.value
                memIntReq.timer = true
            }
            if (++timerCounter >= tickRate.cyclesPerTimerTick) {
                timerCounter = 0
                if (memTimerCounter.value.toUnsignedInt() == 0xFF) {
                    memTimerCounter.value = 0
                    timerReset = true
                } else {
                    ++memTimerCounter.value
                }
            }
        }
        ++globalTimer
    }

    enum class TimerTickRate(val cyclesPerTimerTick: Int) {

        R_4096_HZ(1024),
        R_262144_HZ(16),
        R_65536_HZ(64),
        R_16384_HZ(256)

    }

}