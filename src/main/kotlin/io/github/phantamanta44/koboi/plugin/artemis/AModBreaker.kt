package io.github.phantamanta44.koboi.plugin.artemis

import io.github.phantamanta44.koboi.plugin.artemis.breakpoint.*
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.FileChooser
import javafx.util.Callback
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable

const val JSON_PROVIDER_ID: String = "prov_id"

class AModBreaker(session: ArtemisDebugSession) : ArtemisModule("Breakpoints", "artemis_breaker", session) {

    private val breakpointProviders: Map<String, BreakpointFactory<*>> = listOf(
            BreakpointFactory(BPTypeRegister8), BreakpointFactory(BPTypeRegister16)
    ).associateBy { it.provider.identifier }

    @FXML
    private lateinit var bpBehaviour: ToggleGroup
    @FXML
    private lateinit var bpList: ListView<BreakpointWrapper<*>>
    @FXML
    private lateinit var bpTypeList: TreeView<BreakpointTypeTreeNode>

    private var behaviour: BreakpointBehaviour = BreakpointBehaviour.BREAK
    private val breakpoints: MutableList<BreakpointWrapper<*>> by lazy { bpList.items }
    private var activeBreakpoint: BreakpointWrapper<*>? = null

    @FXML
    fun initialize() {
        bpBehaviour.selectedToggleProperty().addListener { _, _, newValue ->
            if (newValue != null) {
                val fxid = (newValue as RadioMenuItem).id
                behaviour = when (fxid) {
                    "bpbOff" -> BreakpointBehaviour.OFF
                    "bpbBreak" -> BreakpointBehaviour.BREAK
                    "bpbNotify" -> BreakpointBehaviour.NOTIFY
                    else -> throw IllegalStateException(fxid)
                }
            }
        }

        bpList.cellFactory = Callback { BreakpointListCell() }

        val bpTypes = BreakpointCategory.values().associate { it to TreeItem<BreakpointTypeTreeNode>(BTTNCategory(it)) }
        bpTypeList.root = TreeItem<BreakpointTypeTreeNode>().also { root ->
            root.children.addAll(bpTypes.values)
            breakpointProviders.map { it.value.provider }
                    .forEach { bpTypes[it.category]!!.children += TreeItem<BreakpointTypeTreeNode>(BTTNType(it)) }
            bpTypeList.selectionModel.selectionMode = SelectionMode.SINGLE
        }
        bpTypeList.isShowRoot = false
        bpTypeList.cellFactory = Callback { BreakpointTypeCell().also { cell -> cell.onMouseClicked = EventHandler { e ->
            cell.item?.let { node ->
                if (node is BTTNType && e.clickCount == 2) addBreakpoint()
            }
        } } }
    }

    @Suppress("UNCHECKED_CAST")
    @FXML
    fun addBreakpoint() {
        val node = bpTypeList.selectionModel.selectedItem?.value
        if (node != null && node is BTTNType) {
            breakpointProviders[node.type.identifier]!!.displayGenesisEditor()
        } else {
            Alert(Alert.AlertType.ERROR, "Please select a valid breakpoint type.", ButtonType.OK).showAndWait()
        }
    }

    @FXML
    fun requestClear() {
        Alert(Alert.AlertType.CONFIRMATION, "Delete all breakpoints? This can't be undone!", ButtonType.YES, ButtonType.NO)
                .showAndWait().filter { it == ButtonType.YES }.ifPresent { breakpoints.clear() }
    }

    @Suppress("UNCHECKED_CAST")
    @FXML
    fun <T : IBreakpoint<T>>showImportDialog() {
        val dialog = FileChooser()
        dialog.title = "Import Breakpoints"
        dialog.extensionFilters += FileChooser.ExtensionFilter("Breakpoint List", "*.json")
        val file = dialog.showOpenDialog(stage)
        if (file != null) {
            val bpArr = JSONArray(file.readText())
            breakpoints.clear()
            bpArr.forEach {
                if (it is JSONObject) {
                    breakpointProviders[it.getString(JSON_PROVIDER_ID)]?.let { factory ->
                        factory.provider.deserialize(it)?.let {
                            bp -> breakpoints += BreakpointWrapper(factory.provider as IBreakpointProvider<T>, bp as T)
                        }
                    }
                }
            }
        }
    }

    @FXML
    fun showExportDialog() {
        val dialog = FileChooser()
        dialog.title = "Export Breakpoints"
        dialog.extensionFilters += FileChooser.ExtensionFilter("Breakpoint List", "*.json")
        val file = dialog.showSaveDialog(stage)
        if (file != null) {
            val bpArr = JSONArray()
            breakpoints.forEach { bpArr.put(it.serialized) }
            file.writeText(bpArr.toString(0))
        }
    }

    val atBreakpoint: Boolean
        get() = behaviour.shouldBreak && activeBreakpoint != null

    fun checkBreakpoints() {
        if (behaviour != BreakpointBehaviour.OFF) {
            activeBreakpoint?.propMet?.value = false
            activeBreakpoint = breakpoints.firstOrNull(BreakpointWrapper<*>::check)
            if (behaviour.shouldNotify && activeBreakpoint != null) {
                Alert(Alert.AlertType.INFORMATION, "Breakpoint: $activeBreakpoint", ButtonType.OK).showAndWait()
            }
        }
    }

    override fun refresh() {
        // NO-OP
    }

    enum class BreakpointBehaviour(val shouldBreak: Boolean, val shouldNotify: Boolean) {

        OFF(false, false), BREAK(true, false), NOTIFY(false, true)

    }

    private inner class BreakpointFactory<T : IBreakpoint<T>>(var provider: IBreakpointProvider<T>) {

        private var prototype: T = provider.create()

        fun displayGenesisEditor() {
            provider.displayEditor(prototype)?.let {
                breakpoints += BreakpointWrapper(provider, it)
            }
        }

    }

    internal inner class BreakpointWrapper<T : IBreakpoint<T>>(private val provider: IBreakpointProvider<T>, private val breakpoint: T) {

        var propMet: BooleanProperty = SimpleBooleanProperty(false)

        fun check(): Boolean {
            return if (breakpoint.isMet(session.target)) {
                propMet.value = true
                true
            } else {
                false
            }
        }

        fun displayEditor(callback: () -> Unit) {
            provider.displayEditor(breakpoint)?.let {
                breakpoint.assimilate(it)
                callback()
            }
        }

        val serialized: JSONObject
            get() = breakpoint.serialized.put(JSON_PROVIDER_ID, provider.identifier)

        override fun toString(): String = breakpoint.asString

    }

}

internal class BreakpointListCell : ListCell<AModBreaker.BreakpointWrapper<*>>() {

    private var notInitialized: Boolean = true
    private lateinit var cont: HBox
    private lateinit var label: Label

    override fun updateItem(item: AModBreaker.BreakpointWrapper<*>?, empty: Boolean) {
        super.updateItem(item, empty)
        if (notInitialized && item != null) {
            notInitialized = false
            text = null
            cont = HBox(
                    Label().also { label = it },
                    Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
                    Button("Edit").also { it.onAction = EventHandler { _ -> this.item?.displayEditor(listView::refresh) } },
                    Button("Delete").also { it.onAction = EventHandler { _ -> listView.items.remove(this.item) } }
            )
        }
        if (item != null) {
            graphic = cont
            label.text = item.toString()
            styleProperty().bind(Bindings.createStringBinding(Callable {
                if (item.propMet.value) "-fx-background-color: #F8BBD0;" else null
            }, item.propMet))
        } else {
            graphic = null
            styleProperty().unbind()
        }
    }

}

internal sealed class BreakpointTypeTreeNode

internal class BTTNCategory(val category: BreakpointCategory) : BreakpointTypeTreeNode()

internal class BTTNType(val type: IBreakpointProvider<*>) : BreakpointTypeTreeNode()

internal class BreakpointTypeCell : TreeCell<BreakpointTypeTreeNode>() {

    override fun updateItem(item: BreakpointTypeTreeNode?, empty: Boolean) {
        super.updateItem(item, empty)
        text = when (item) {
            is BTTNCategory -> item.category.desc
            is BTTNType -> item.type.name
            else -> null
        }
    }

}