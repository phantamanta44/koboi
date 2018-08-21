package io.github.phantamanta44.koboi.plugin.artemis

import com.sun.javafx.application.LauncherImpl
import com.sun.javafx.application.PlatformImpl
import io.github.phantamanta44.koboi.cpu.InterruptType
import io.github.phantamanta44.koboi.debug.CpuProperty
import io.github.phantamanta44.koboi.debug.IDebugProvider
import io.github.phantamanta44.koboi.debug.IDebugSession
import io.github.phantamanta44.koboi.debug.IDebugTarget
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage
import kotlin.properties.Delegates

class ArtemisDebugger : IDebugProvider {

    private var firstTime: Boolean = true

    override fun startDebugging(target: IDebugTarget): IDebugSession {
        if (firstTime) {
            firstTime = false
            val tkInit = LauncherImpl::class.java.getDeclaredMethod("startToolkit")
            tkInit.isAccessible = true
            tkInit.invoke(null)
        }
        var session: ArtemisDebugSession? = null
        PlatformImpl.runAndWait {
            session = ArtemisDebugSession(target)
        }
        return session!!
    }

}

class ArtemisDebugSession(val target: IDebugTarget) : IDebugSession {

    val propFrozen: BooleanProperty = SimpleBooleanProperty(true)
    var frozen: Boolean by propFrozen.delegate()

    private val mainWindow: ArtemisMainWindow = ArtemisMainWindow(this)

    val modCpu: AModCpu = AModCpu(this)
    val modDis: AModDisassembler = AModDisassembler(this)
    private val modules: List<ArtemisModule> = listOf(modCpu, modDis)

    init {
        mainWindow.finishLoad()
        modules.forEach(ArtemisModule::finishLoad)
        mainWindow.show()
    }

    override fun kill() = Platform.runLater {
        modules.forEach(ArtemisModule::dispose)
        mainWindow.dispose()
    }

    override fun shouldFreeze(): Boolean {
        return if (mainWindow.checkStep() && frozen) {
            Platform.runLater { modules.forEach { it.refresh() } }
            true
        } else {
            false
        }
    }

    override fun onMemoryMutate(addr: Int, length: Int) {
        modDis.onMemoryMutate(addr, length)
    }

    override fun onCpuMutate(prop: CpuProperty) {
        // NO-OP
    }

    override fun onCpuExecute(opcode: Byte) {
        mainWindow.onCpuExecute()
        modDis.onCpuExecute(opcode)
    }

    override fun onCpuCall(addr: Short) {
        modDis.onCpuCall(addr)
    }

    override fun onCpuReturn() {
        modDis.onCpuReturn()
    }

    override fun onInterruptExecuted(interrupt: InterruptType) {
        // NO-OP
    }

}

abstract class ArtemisStageWrapper(title: String, private val fxml: String) {

    protected val stage: Stage = Stage()

    init {
        stage.title = title
        stage.isResizable = false
    }

    open fun finishLoad() {
        val loader = FXMLLoader(javaClass.getResource("/artemis/$fxml.fxml"))
        loader.setController(this)
        stage.scene = Scene(loader.load())
        stage.sizeToScene()
    }

    fun dispose() {
        stage.close()
    }

}

abstract class ArtemisModule(title: String, fxml: String, protected val session: ArtemisDebugSession) : ArtemisStageWrapper("Arty $title", fxml) {

    var enabled: Boolean by Delegates.observable(false) { _, old, new ->
        if (new) stage.show() else stage.hide()
        propEnabled.post(old, new)
    }
    val propEnabled: BooleanPropWrapper = BooleanPropWrapper(::enabled)

    init {
        stage.setOnCloseRequest {
            it.consume()
            enabled = false
        }
    }

    override fun finishLoad() {
        super.finishLoad()
        stage.scene.root.disableProperty().bind(session.propFrozen.not())
    }

    abstract fun refresh()

}