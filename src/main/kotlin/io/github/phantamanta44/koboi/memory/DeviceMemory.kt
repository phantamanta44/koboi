package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.cpu.Timer
import io.github.phantamanta44.koboi.game.GameEngine

class StaticRomArea(private val rom: ByteArray, private val start: Int = 0, override val length: Int = rom.size) : IMemoryArea {

    override fun read(addr: Int): Byte = rom[start + addr]

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = StaticRomRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        throw IllegalWriteException()
    }

    private inner class StaticRomRange(val first: Int, val last: Int) : IMemoryRange {

        override val length: Int = last - first

        override fun get(index: Int): Byte = rom[start + first + index]

        override fun toArray(): ByteArray = rom.copyOfRange(start + first, start + last)

    }

}

class InterruptRegister : BitwiseRegister() {

    var vBlank: Boolean by delegateBit(0)

    var lcdStat: Boolean by delegateBit(1)

    var timer: Boolean by delegateBit(2)

    var serial: Boolean by delegateBit(3)

    var joypad: Boolean by delegateBit(4)

}

class ClockSpeedRegister : BitwiseRegister(0b00000001) {

    var doubleSpeed: Boolean by delegateBit(7)

    var prepareSpeedSwitch: Boolean by delegateBit(0)

}

class TimerControlRegister(private val gameEngine: GameEngine) : BitwiseRegister() {

    var timerEnabled: Boolean by delegateBit(2)

    var clockUpper: Boolean by delegateBit(1)

    var clockLower: Boolean by delegateBit(0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int) {
        super.write(addr, *values, start = start, length = length)
        gameEngine.clock.tickRate = when (clockUpper) {
            false -> when (clockLower) {
                false -> Timer.TimerTickRate.R_4096_HZ
                true -> Timer.TimerTickRate.R_262144_HZ
            }
            true -> when (clockLower) {
                false -> Timer.TimerTickRate.R_65536_HZ
                true -> Timer.TimerTickRate.R_16384_HZ
            }
        }
    }

}