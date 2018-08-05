package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.memory.InterruptRegister
import io.github.phantamanta44.koboi.memory.ResettableRegister
import io.github.phantamanta44.koboi.memory.SingleByteMemoryArea
import io.github.phantamanta44.koboi.memory.TimerControlRegister
import io.github.phantamanta44.koboi.util.toUnsignedInt

class Timer(private val memDivider: ResettableRegister, private val memTimerCounter: SingleByteMemoryArea,
            private val memTimerModulo: SingleByteMemoryArea, private val memTimerControl: TimerControlRegister,
            private val memIntReq: InterruptRegister) {

    var tickRate: TimerTickRate = TimerTickRate.R_4096_HZ

    private var globalTimer: Long = 0

    fun cycle() {
        if ((globalTimer % 256L) == 0L) ++memDivider.value
        if (memTimerControl.timerEnabled) {
            if ((globalTimer % tickRate.cyclesPerTimerTick) == 0L) {
                if (memTimerCounter.value.toUnsignedInt() == 255) {
                    memTimerCounter.value = memTimerModulo.value
                    memIntReq.timer = true
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