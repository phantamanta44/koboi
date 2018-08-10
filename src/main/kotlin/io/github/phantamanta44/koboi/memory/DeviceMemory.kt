package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.GameEngine
import io.github.phantamanta44.koboi.cpu.Timer

class StaticRomArea(private val rom: ByteArray, private val start: Int = 0, override val length: Int = rom.size) : IMemoryArea {

    override fun read(addr: Int, direct: Boolean): Byte = rom[start + addr]

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = StaticRomRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        if (direct) System.arraycopy(values, start, rom, this.start + addr, length)
    }

    override fun typeAt(addr: Int): String = "StaticRom[$length]"

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

    override fun typeAt(addr: Int): String = "Interrupt"

}

class ClockSpeedRegister : BitwiseRegister(0b00000001) {

    var doubleSpeed: Boolean by delegateBit(7)

    var prepareSpeedSwitch: Boolean by delegateBit(0)

    override fun typeAt(addr: Int): String = "ClockSpeed"

}

class TimerControlRegister(private val gameEngine: GameEngine) : BitwiseRegister() {

    var timerEnabled: Boolean by delegateBit(2)

    var clockUpper: Boolean by delegateBit(1)

    var clockLower: Boolean by delegateBit(0)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        value = values[0]
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

    override fun typeAt(addr: Int): String = "TimerCtrl"

}

class JoypadRegister(private val memIntReq: InterruptRegister) : BitwiseRegister(0b00110000) {

    var enableButtons: Boolean by delegateBit(5)

    var enableDPad: Boolean by delegateBit(4)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        if ((value.toInt() and values[0].toInt().inv()) and 0b00001111 != 0) memIntReq.joypad = true
        super.write(addr, *values, start = start, length = length, direct = direct)
    }

    override fun writeBit(bit: Int, flag: Boolean) {
        if (bit <= 3 && !flag && readBit(bit)) memIntReq.joypad = true
        super.writeBit(bit, flag)
    }

}