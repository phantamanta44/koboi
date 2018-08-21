package io.github.phantamanta44.koboi.plugin.artemis

import io.github.phantamanta44.koboi.debug.ICpuAccess
import io.github.phantamanta44.koboi.debug.IFlagAccess
import io.github.phantamanta44.koboi.util.toUnsignedHex
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.Cursor
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.TableView
import javafx.scene.control.cell.TextFieldTableCell
import javafx.util.Callback
import javafx.util.converter.DefaultStringConverter
import kotlin.reflect.KMutableProperty1

class AModCpu(session: ArtemisDebugSession) : ArtemisModule("CPU State", "artemis_cpu", session) {

    @FXML
    private lateinit var propTable: TableView<CachedCpuState>

    @FXML
    fun initialize() {
        propTable.columns[0].cellValueFactory = Callback { it.value.propKey }
        propTable.columns[0].cellFactory = Callback { StringDataCell() }
        propTable.columns[1].cellValueFactory = Callback { it.value.propValue }
        propTable.columns[1].cellFactory = Callback { PropValueCell() }
        propTable.columns[1].onEditCommit = EventHandler {
            if (!it.rowValue.prop.setter!!(session.target.cpu, it.newValue as String)) {
                Alert(Alert.AlertType.ERROR, "Invalid property value!", ButtonType.OK).showAndWait()
                it.consume()
                it.tableView.refresh()
            }
        }
        propTable.selectionModel.isCellSelectionEnabled = true
        tracking.forEach { propTable.items.add(CachedCpuState(it.value, it.value.getter(session.target.cpu))) }
    }

    override fun refresh() {
        tracking.forEach { propTable.items[it.index].propValue.value = it.value.getter(session.target.cpu) }
    }

}

fun regSetter16(reg: KMutableProperty1<ICpuAccess, Short>): (ICpuAccess, String) -> Boolean = { cpu, s ->
    try {
        reg.set(cpu, s.toShort(16))
        true
    } catch (e: NumberFormatException) {
        false
    }
}

fun flagSetter(flag: KMutableProperty1<IFlagAccess, Boolean>): (ICpuAccess, String) -> Boolean = { cpu, s ->
    when (s.trim().toLowerCase()) {
        "true" -> {
            flag.set(cpu.regF, true)
            true
        }
        "false" -> {
            flag.set(cpu.regF, false)
            true
        }
        else -> false
    }
}

val tracking: List<IndexedValue<TrackedCpuState>> = listOf(
        TrackedCpuState("reg pair af", regSetter16(ICpuAccess::regAF)) { it.regAF.toUnsignedHex() },
        TrackedCpuState("reg pair bc", regSetter16(ICpuAccess::regBC)) { it.regBC.toUnsignedHex() },
        TrackedCpuState("reg pair de", regSetter16(ICpuAccess::regDE)) { it.regDE.toUnsignedHex() },
        TrackedCpuState("reg pair hl", regSetter16(ICpuAccess::regHL)) { it.regHL.toUnsignedHex() },
        TrackedCpuState("register pc", regSetter16(ICpuAccess::regPC)) { it.regPC.toUnsignedHex() },
        TrackedCpuState("register sp", regSetter16(ICpuAccess::regSP)) { it.regSP.toUnsignedHex() },
        TrackedCpuState("flag z", flagSetter(IFlagAccess::flagZ)) { it.regF.flagZ.toString() },
        TrackedCpuState("flag n", flagSetter(IFlagAccess::flagN)) { it.regF.flagN.toString() },
        TrackedCpuState("flag h", flagSetter(IFlagAccess::flagH)) { it.regF.flagH.toString() },
        TrackedCpuState("flag c", flagSetter(IFlagAccess::flagC)) { it.regF.flagC.toString() },
        TrackedCpuState("flag ime", { cpu, s ->
            when (s.trim().toLowerCase()) {
                "true" -> {
                    cpu.flagIME = true
                    true
                }
                "false" -> {
                    cpu.flagIME = false
                    true
                }
                else -> false
            }
        }) { it.flagIME.toString() },
        TrackedCpuState("double clock") { it.doubleClock.toString() },
        TrackedCpuState("cpu state") { it.state.name }
).withIndex().toList()

data class TrackedCpuState(val key: String, val setter: ((ICpuAccess, String) -> Boolean)? = null, val getter: (ICpuAccess) -> String)

class CachedCpuState(val prop: TrackedCpuState, value: String) {

    val propKey: StringProperty = SimpleStringProperty(prop.key)
    val propValue: StringProperty = SimpleStringProperty(value)

}

class PropValueCell : TextFieldTableCell<CachedCpuState, String>(DefaultStringConverter()) {

    override fun updateItem(item: String?, empty: Boolean) {
        super.updateItem(item, empty)
        tableRow.item?.let {
            if ((it as CachedCpuState).prop.setter != null) {
                cursor = Cursor.TEXT
                isEditable = true
            } else {
                cursor = null
                isEditable = false
            }
        }
    }

}