package io.github.phantamanta44.koboi.plugin.artemis.disassembler

import io.github.phantamanta44.koboi.backtrace.cbMnemonics
import io.github.phantamanta44.koboi.backtrace.mnemonics
import io.github.phantamanta44.koboi.memory.IMemoryArea
import io.github.phantamanta44.koboi.plugin.artemis.delegate
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt
import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import java.util.*

class DisassembledMemory(private val gbMem: IMemoryArea) {

    private val memory: NavigableMap<Int, DisassembledOp> = TreeMap()
    private val known: BitSet = BitSet(0x10000)

    fun resolve(addr: Int) {
        val opcode = gbMem.read(addr, true).toUnsignedInt()
        if (opcode == 0xCB) {
            putOperation(addr, 2, cbMnemonics[gbMem.read(addr + 1, true).toUnsignedInt()])
        } else {
            val param = Immediate.iOps[opcode]
            if (param != null) {
                putOperation(addr, param.length + 1, "${mnemonics[opcode]} ${param.capture(gbMem, addr + 1)}")
            } else {
                putOperation(addr, 1, mnemonics[opcode])
            }
        }
    }

    fun speculateFrom(startingAddr: Int) {
        var addr = startingAddr
        while (!known[addr] && addr < 0x10000) {
            val insnAddr = addr
            val opcode = gbMem.read(addr++, true).toUnsignedInt()
            if (opcode == 0xCB) {
                putOperation(insnAddr, 2, cbMnemonics[gbMem.read(addr++, true).toUnsignedInt()])
            } else {
                val param = Immediate.iOps[opcode]
                if (param != null) {
                    putOperation(insnAddr, param.length + 1, "${mnemonics[opcode]} ${param.capture(gbMem, addr)}")
                    addr += param.length
                } else {
                    putOperation(insnAddr, 1, mnemonics[opcode])
                }
                if (Immediate.tOps.contains(opcode)) break
            }
        }
    }

    private fun putOperation(addr: Int, length: Int, asString: String) {
        val op = DisassembledOp(addr, length, asString)
        memory[op.addr] = op
        known.set(op.addr, op.addr + op.length)
    }

    fun getOperation(addr: Int): Map.Entry<Int, DisassembledOp> = memory.floorEntry(addr)

    fun getPreviousOperation(addr: Int): Map.Entry<Int, DisassembledOp>? = memory.lowerEntry(addr)

    fun getNextOperation(addr: Int): Map.Entry<Int, DisassembledOp>? = memory.higherEntry(addr)

    fun markDirty(addr: Int, length: Int) = known.clear(addr, addr + length)

}

interface IDisassembledOp {

    val propAddr: IntegerProperty

    val propStringValue: StringProperty

}

class DisassembledOp(addr: Int, val length: Int, asString: String) : IDisassembledOp {

    override val propAddr: IntegerProperty = SimpleIntegerProperty(addr)
    override val propStringValue: StringProperty = SimpleStringProperty(asString)

    val addr: Int by propAddr.delegate()

}

object EmptyOp : IDisassembledOp {

    override val propAddr: IntegerProperty = SimpleIntegerProperty(-1)
    override val propStringValue: StringProperty = SimpleStringProperty("--")

}

abstract class Immediate(val length: Int) {

    companion object {

        private val d8: Immediate = object : Immediate(1) {
            override fun capture(memory: IMemoryArea, addr: Int): String = memory.read(addr, true).toUnsignedHex()
        }

        private val d16: Immediate = object : Immediate(2) {
            override fun capture(memory: IMemoryArea, addr: Int): String = memory.readShort(addr, true).toUnsignedHex()
        }

        private val a8: Immediate = object : Immediate(1) {
            override fun capture(memory: IMemoryArea, addr: Int): String = "\$FF${memory.read(addr, true).toUnsignedHex()}"
        }

        private val a16: Immediate = object : Immediate(2) {
            override fun capture(memory: IMemoryArea, addr: Int): String = "\$${memory.readShort(addr, true).toUnsignedHex()}"
        }

        private val r8: Immediate = object : Immediate(1) {
            override fun capture(memory: IMemoryArea, addr: Int): String = memory.read(addr, true).toString()
        }

        val iOps: Map<Int, Immediate> = mapOf(
                0x01 to d16, 0x06 to d8, 0x08 to a16, 0x0E to d8, 0x11 to d16, 0x16 to d8, 0x18 to r8, 0x1E to d8,
                0x20 to r8, 0x21 to d16, 0x26 to d8, 0x28 to r8, 0x2E to d8, 0x30 to r8, 0x31 to d16, 0x36 to d8,
                0x38 to r8, 0x3E to d8, 0xC2 to a16, 0xC3 to a16, 0xC4 to a16, 0xC6 to d8, 0xCA to a16, 0xCC to a16,
                0xCD to a16, 0xCE to d8, 0xD2 to a16, 0xD4 to a16, 0xD6 to d8, 0xDA to a16, 0xDC to a16, 0xDE to d8,
                0xE0 to a8, 0xE6 to d8, 0xE8 to r8, 0xEA to a16, 0xEE to d8, 0xF0 to a8, 0xF6 to d8, 0xF8 to r8,
                0xFA to a16, 0xFE to d8)

        val tOps: Set<Int> = setOf(0x18, 0xC3, 0xC9, 0xD9, 0xE9)

    }

    abstract fun capture(memory: IMemoryArea, addr: Int): String

}