package io.github.phantamanta44.koboi.plugin.artemis

import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.debug.CpuProperty
import io.github.phantamanta44.koboi.debug.ICpuAccess
import io.github.phantamanta44.koboi.util.toUnsignedHex
import java.awt.Dimension
import java.util.*
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

class AModCpu(session: ArtemisDebugSession) : ArtemisModule("CPU", session) {

    private lateinit var frozenControls: List<JComponent>
    private lateinit var btnFreeze: JButton

    private lateinit var callStack: CallStackTableModel
    private lateinit var cpuState: CpuStateTableModel

    private lateinit var btnDelBreak: JButton

    private var noInstruction: Boolean = false

    init {
        contentPane.box(BoxLayout.Y_AXIS) {
            box(BoxLayout.X_AXIS) { // controls
                btnFreeze = button("freeze", enabled = false) { session.frozen = true }
                frozenControls = listOf(
                    button("unfreeze") { session.unfreeze() },
                    button("step t-cycle") { session.unfreezeOverrideUntil { true } },
                    button("step m-cycle") {
                        val initial = session.target.tCycle
                        session.unfreezeOverrideUntil { session.target.tCycle - initial >= 4 }
                    },
                    button("step insn") {
                        noInstruction = true
                        session.unfreezeOverrideUntil { !noInstruction }
                    },
                    button("step h-blank") { Loggr.warn("No impl!") }, // TODO
                    button("step v-blank") { Loggr.warn("No impl!") }) // TODO
            }
            box(BoxLayout.X_AXIS) {
                box(BoxLayout.X_AXIS) {
                    scroll { // trace disassembly
                        add(JTable(DisassemblyTraceTableModel()))
                    }
                    scroll { // memory disassembly
                        add(JTable(DisassemblyMemoryTableModel()))
                    }
                }
                box(BoxLayout.Y_AXIS) {
                    box(BoxLayout.X_AXIS) {
                        scroll { // call stack
                            callStack = CallStackTableModel(session)
                            add(JTable(callStack))
                        }
                        cpuState = CpuStateTableModel(session) // cpu state
                        add(JTable(cpuState))
                    }
                    box(BoxLayout.Y_AXIS) {
                        // breakpoint list // TODO
                        box(BoxLayout.X_AXIS) { // breakpoint controls // TODO
                            button("+") { Loggr.warn("No impl!") }
                            btnDelBreak = button("-") { Loggr.warn("No impl!") }
                        }
                    }
                }
            }
        }
        minimumSize = Dimension(660, 330)
        size = Dimension(660, 330)
        pack()
    }

    override fun isAtBreakpoint(): Boolean {
        return super.isAtBreakpoint()
    }

    override fun onFrozenState(frozen: Boolean) {
        // change control state
        frozenControls.forEach { it.isEnabled = frozen }
        btnFreeze.isEnabled = !frozen

        // update data tables
        if (frozen) {
            callStack.fireTableDataChanged()
            cpuState.forceRefresh()
        }
    }

    fun onCpuMutate(prop: CpuProperty) = cpuState.onCpuMutate(prop)

    fun onCpuExecute(opcode: Byte) {
        noInstruction = false
    }

    fun onCpuCall(addr: Short) = callStack.push(addr)

    fun onCpuReturn() = callStack.pop()

}

abstract class DisassemblyTableModel : AbstractTableModel() {

    override fun getRowCount(): Int = 51

    override fun getColumnCount(): Int = 3

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = "NOOP"

    override fun getColumnName(column: Int): String = when (column) {
        0 -> "pc"
        1 -> "mnemonic"
        2 -> "parameters"
        else -> throw IllegalStateException(column.toString())
    }
    
}

class DisassemblyTraceTableModel : DisassemblyTableModel() {



}

class DisassemblyMemoryTableModel : DisassemblyTableModel() {



}

class CallStackTableModel(private val session: ArtemisDebugSession) : AbstractTableModel() {

    private val callStack: MutableList<Short> = mutableListOf()

    override fun getRowCount(): Int = callStack.size

    override fun getColumnCount(): Int = 1

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = callStack[rowIndex].toUnsignedHex()

    override fun getColumnName(column: Int): String = "address"

    fun push(addr: Short) {
        session.fpExec({
            callStack.add(addr)
        }, {
            val index = callStack.size
            callStack.add(addr)
            fireTableRowsInserted(index, index)
        })
    }

    fun pop() {
        session.fpExec({
            callStack.removeAt(callStack.lastIndex)
        }, {
            val index = callStack.lastIndex
            callStack.removeAt(index)
            fireTableRowsDeleted(index, index)
        })
    }

}

class CpuStateTableModel(private val session: ArtemisDebugSession) : AbstractTableModel() {

    companion object {

        private val tracking: List<IndexedValue<TrackedCpuState>> = listOf(
                TrackedCpuState("register a", CpuProperty.REG_A) { it.regA.toUnsignedHex() },
                TrackedCpuState("register b", CpuProperty.REG_B) { it.regB.toUnsignedHex() },
                TrackedCpuState("register c", CpuProperty.REG_C) { it.regC.toUnsignedHex() },
                TrackedCpuState("register d", CpuProperty.REG_D) { it.regD.toUnsignedHex() },
                TrackedCpuState("register e", CpuProperty.REG_E) { it.regE.toUnsignedHex() },
                TrackedCpuState("register h", CpuProperty.REG_H) { it.regH.toUnsignedHex() },
                TrackedCpuState("register l", CpuProperty.REG_L) { it.regL.toUnsignedHex() },
                TrackedCpuState("reg pair af", CpuProperty.REG_A, CpuProperty.REG_F) { it.regAF.toUnsignedHex() },
                TrackedCpuState("reg pair bc", CpuProperty.REG_B, CpuProperty.REG_C) { it.regBC.toUnsignedHex() },
                TrackedCpuState("reg pair de", CpuProperty.REG_D, CpuProperty.REG_E) { it.regDE.toUnsignedHex() },
                TrackedCpuState("reg pair hl", CpuProperty.REG_H, CpuProperty.REG_L) { it.regHL.toUnsignedHex() },
                TrackedCpuState("register pc", CpuProperty.REG_PC) { it.regPC.toUnsignedHex() },
                TrackedCpuState("register sp", CpuProperty.REG_SP) { it.regSP.toUnsignedHex() },
                TrackedCpuState("flag z", CpuProperty.REG_F) { it.regF.flagZ.toString() },
                TrackedCpuState("flag n", CpuProperty.REG_F) { it.regF.flagN.toString() },
                TrackedCpuState("flag h", CpuProperty.REG_F) { it.regF.flagH.toString() },
                TrackedCpuState("flag c", CpuProperty.REG_F) { it.regF.flagC.toString() },
                TrackedCpuState("flag ime", CpuProperty.FLAG_IME) { it.flagIME.toString() },
                TrackedCpuState("double clock", CpuProperty.CLOCK_SPEED) { it.doubleClock.toString() },
                TrackedCpuState("cpu state", CpuProperty.STATE) { it.state.name }
        ).withIndex().toList()

    }

    private val storedStates: Array<String> = Array(tracking.size, { tracking[it].value.getter(session.target.cpu) })

    override fun getRowCount(): Int = tracking.size

    override fun getColumnCount(): Int = 2

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
        0 -> tracking[rowIndex].value.key
        1 -> storedStates[rowIndex]
        else -> throw IllegalStateException(rowIndex.toString())
    }

    override fun getColumnName(column: Int): String = when (column) {
        0 -> "property"
        1 -> "value"
        else -> throw IllegalStateException(column.toString())
    }

    fun onCpuMutate(prop: CpuProperty) {
        session.fpExec(null, {
            tracking.filter { it.value.isAffectedBy(prop) }.forEach {
                storedStates[it.index] = it.value.getter(session.target.cpu)
                if (session.frozen) fireTableCellUpdated(it.index, 1)
            }
        })
    }

    fun forceRefresh() {
        tracking.forEach {
            storedStates[it.index] = it.value.getter(session.target.cpu)
            if (session.frozen) fireTableCellUpdated(it.index, 1)
        }
        fireTableDataChanged()
    }

    private class TrackedCpuState(val key: String, vararg props: CpuProperty, val getter: (ICpuAccess) -> String) {

        private val propSet: EnumSet<CpuProperty> = EnumSet.copyOf(props.asList())

        fun isAffectedBy(prop: CpuProperty): Boolean = propSet.contains(prop)

    }

}