package io.github.phantamanta44.koboi.plugin.artemis

import io.github.phantamanta44.koboi.debug.ICpuAccess
import io.github.phantamanta44.koboi.plugin.artemis.disassembler.*
import io.github.phantamanta44.koboi.util.toShortHex
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt
import javafx.fxml.FXML
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.stage.FileChooser
import javafx.util.Callback
import java.io.PrintStream
import java.util.*

class AModDisassembler(session: ArtemisDebugSession) : ArtemisModule("Disassembler", "artemis_dis", session) {
    
    private val cpu: ICpuAccess by lazy { session.target.cpu }
    private val tracer: Tracer = Tracer(session.target)
    private val disassembler: DisassembledMemory = DisassembledMemory(session.target.memory)
    private val callStack: LinkedList<Short> = LinkedList()

    @FXML
    private lateinit var tblTrace: TableView<ITracedOp>
    @FXML
    private lateinit var tblMem: TableView<IDisassembledOp>
    @FXML
    private lateinit var elemCallStack: ListView<Short>

    @FXML
    fun initialize() {
        tblTrace.columns[0].cellValueFactory = Callback { it.value.propAddr }
        tblTrace.columns[0].cellFactory = Callback { AddressCell() }
        tblTrace.columns[1].cellValueFactory = Callback { it.value.propMnemonic }
        tblTrace.columns[1].cellFactory = Callback { StringDataCell() }
        tblTrace.columns[2].cellValueFactory = Callback { it.value.propContext }
        tblTrace.columns[2].cellFactory = Callback { TraceContextCell() }
        for (i in 1..25) tblTrace.items.add(EmptyTrace)

        tblMem.columns[0].cellValueFactory = Callback { it.value.propAddr }
        tblMem.columns[0].cellFactory = Callback { AddressCell() }
        tblMem.columns[1].cellValueFactory = Callback { it.value.propStringValue }
        tblMem.columns[1].cellFactory = Callback { StringDataCell() }
        tblMem.rowFactory = Callback { DisassemblyTableRow(cpu) }
        for (i in 1..25) tblMem.items.add(EmptyOp)

        elemCallStack.cellFactory = Callback { AddressListCell() }
    }

    @FXML
    fun dumpToFile() {
        val dialog = FileChooser()
        dialog.title = "Dump Known Code"
        dialog.extensionFilters += FileChooser.ExtensionFilter("Disassembly Dump", "*.*")
        val file = dialog.showSaveDialog(stage)
        if (file != null) {
            PrintStream(file).use {
                var op: Map.Entry<Int, DisassembledOp>? = disassembler.firstKnownOp
                if (op != null) {
                    do {
                        if (disassembler.isKnown(op!!.value)) {
                            it.println("${op.value.addr.toShortHex()}: ${op.value.propStringValue.value}")
                        }
                        op = disassembler.getNextOperation(op.key)
                    } while (op != null)
                }
            }
        }
    }

    private fun recalculateDisassembly() {
        disassembler.speculateFrom(cpu.regPC.toUnsignedInt())
        var opAddr = cpu.regPC.toUnsignedInt()
        tblMem.items[13] = disassembler.getOperation(opAddr).value
        var good = true
        for (i in 12 downTo 0) {
            if (good) {
                val prevOp = disassembler.getPreviousOperation(opAddr)
                if (prevOp != null) {
                    opAddr = prevOp.key
                    tblMem.items[i] = prevOp.value
                    continue
                } else {
                    good = false
                }
            }
            tblMem.items[i] = EmptyOp
        }
        opAddr = cpu.regPC.toUnsignedInt()
        good = true
        for (i in 14..24) {
            if (good) {
                val nextOp = disassembler.getNextOperation(opAddr)
                if (nextOp != null) {
                    opAddr = nextOp.key
                    tblMem.items[i] = nextOp.value
                    continue
                } else {
                    good = false
                }
            }
            tblMem.items[i] = EmptyOp
        }
        tblMem.refresh()
    }

    fun onMemoryMutate(addr: Int, length: Int) {
        disassembler.markDirty(addr, length)
    }

    fun onCpuExecute(opcode: Byte) {
        tracer.trace(opcode)
        disassembler.resolve(cpu.regPC.toUnsignedInt())
    }

    fun onCpuCall(addr: Short) {
        callStack.add(addr)
    }

    fun onCpuReturn() {
        callStack.removeLast()
    }

    override fun refresh() {
        tracer.dump(tblTrace.items)
        recalculateDisassembly()
        elemCallStack.items.clear()
        elemCallStack.items.addAll(callStack)
    }

}

class DisassemblyTableRow(private val cpu: ICpuAccess) : TableRow<IDisassembledOp>() {

    override fun updateItem(item: IDisassembledOp?, empty: Boolean) {
        super.updateItem(item, empty)
        if (item?.propAddr?.value == cpu.regPC.toUnsignedInt()) {
            style = "-fx-background-color: #F8BBD0;"
        }
    }

}

class TraceContextCell<S> : BaseTableCell<S, List<ITraceContext>>() {

    override fun setCellContents(item: List<ITraceContext>?) {
        super.setText(item?.joinToString(" ", transform = ITraceContext::stringify))
    }

}

class AddressListCell : ListCell<Short>() {

    override fun updateItem(item: Short?, empty: Boolean) {
        if (item === getItem()) return
        super.updateItem(item, empty)
        super.setText(item?.toUnsignedHex())
    }

}