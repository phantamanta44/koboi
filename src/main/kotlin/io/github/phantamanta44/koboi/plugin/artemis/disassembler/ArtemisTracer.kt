package io.github.phantamanta44.koboi.plugin.artemis.disassembler

import io.github.phantamanta44.koboi.backtrace.cbMnemonics
import io.github.phantamanta44.koboi.backtrace.mnemonics
import io.github.phantamanta44.koboi.debug.IDebugTarget
import io.github.phantamanta44.koboi.util.toBin
import io.github.phantamanta44.koboi.util.toShortHex
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import java.util.*

class Tracer(private val target: IDebugTarget) {

    private val buffer: Deque<ITracedOp> = LinkedList()

    init {
        for (i in 1..25) buffer.add(EmptyTrace)
    }

    fun trace(opcode: Byte) {
        buffer.pollLast()
        buffer.offerFirst(if (opcode == 0xCB.toByte()) takeCbOp() else takeOp(opcode.toUnsignedInt()))
    }

    private fun takeOp(opcode: Int): TracedOp {
        val pc = target.cpu.regPC.toUnsignedInt()
        val collect = traceableOpcodes[opcode]
        val list = FXCollections.observableArrayList<ITraceContext>()
        list.collect(target)
        return TracedOp(pc, mnemonics[opcode], list)
    }

    private fun takeCbOp(): TracedOp {
        val pc = target.cpu.regPC.toUnsignedInt() + 1
        val opcode = target.memory.read(pc, true).toUnsignedInt()
        val collect = traceableCbOpcodes[opcode]
        val list = FXCollections.observableArrayList<ITraceContext>()
        list.collect(target)
        return TracedOp(pc - 1, cbMnemonics[opcode], list)
    }

    fun dump(dest: MutableList<ITracedOp>) {
        buffer.withIndex().forEach { dest[it.index] = it.value }
    }

}

interface ITracedOp {

    val propAddr: IntegerProperty

    val propMnemonic: StringProperty

    val propContext: ListProperty<ITraceContext>

}

class TracedOp(addr: Int, mnemonic: String, context: ObservableList<ITraceContext>) : ITracedOp {

    override val propAddr: IntegerProperty = SimpleIntegerProperty(addr)
    override val propMnemonic: StringProperty = SimpleStringProperty(mnemonic)
    override val propContext: ListProperty<ITraceContext> = SimpleListProperty(context)

}

object EmptyTrace : ITracedOp {

    override val propAddr: IntegerProperty = SimpleIntegerProperty(-1)
    override val propMnemonic: StringProperty = SimpleStringProperty("--")
    override val propContext: ListProperty<ITraceContext> = SimpleListProperty()

}

interface ITraceContext {

    fun stringify(): String
    
}

class TraceContextRegister8(private val name: String, private val value: Byte) : ITraceContext {

    override fun stringify(): String = "$name=${value.toUnsignedHex()}"

}

class TraceContextRegister16(private val name: String, private val value: Short) : ITraceContext {

    override fun stringify(): String = "$name=${value.toUnsignedHex()}"

}

class TraceContextRegisterBin(private val name: String, private val value: Byte) : ITraceContext {

    override fun stringify(): String = "$name=${value.toBin()}"

}

class TraceContextPointer8(private val name: String, private val addr: Int, private val value: Byte) : ITraceContext {

    override fun stringify(): String = "($name:${addr.toShortHex()})=${value.toUnsignedHex()}"

}

class TraceContextPointer16(private val name: String, private val addr: Int, private val value: Short) : ITraceContext {

    override fun stringify(): String = "($name:${addr.toShortHex()})=${value.toUnsignedHex()}"

}

class TraceContextPointerBin(private val name: String, private val addr: Int, private val value: Byte) : ITraceContext {

    override fun stringify(): String = "($name:${addr.toShortHex()})=${value.toBin()}"

}

class TraceContextImmediate8(private val value: Byte) : ITraceContext {

    override fun stringify(): String = "${value.toUnsignedHex()}h"

}

class TraceContextImmediate16(private val value: Short) : ITraceContext {

    override fun stringify(): String = "${value.toUnsignedHex()}h"

}

class TraceContextImmediateBin(private val value: Byte) : ITraceContext {

    override fun stringify(): String = "${value.toBin()}b"

}

class TraceContextImmediatePointer8(private val addr: Int, private val value: Byte) : ITraceContext {

    override fun stringify(): String = "(${addr.toShortHex()})=${value.toUnsignedHex()}"

}

class TraceContextImmediatePointer16(private val addr: Int, private val value: Short) : ITraceContext {

    override fun stringify(): String = "(${addr.toShortHex()})=${value.toUnsignedHex()}"

}

class TraceContextImmediateSigned(private val value: Byte) : ITraceContext {

    override fun stringify(): String = value.toString()

}

class TraceContextFlag(private val name: String, private val value: Boolean) : ITraceContext {

    override fun stringify(): String = "$name=${if (value) "T" else "F"}"

}

class TraceF8Param(private val stkAddr: Short, private val offset: Byte) : ITraceContext {

    override fun stringify(): String = "SP=${stkAddr.toUnsignedHex()}(+$offset)"

}