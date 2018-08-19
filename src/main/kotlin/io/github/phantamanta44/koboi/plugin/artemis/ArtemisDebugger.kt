package io.github.phantamanta44.koboi.plugin.artemis

import io.github.phantamanta44.koboi.cpu.InterruptType
import io.github.phantamanta44.koboi.debug.CpuProperty
import io.github.phantamanta44.koboi.debug.IDebugProvider
import io.github.phantamanta44.koboi.debug.IDebugSession
import io.github.phantamanta44.koboi.debug.IDebugTarget
import io.github.phantamanta44.koboi.util.PropDel
import java.awt.EventQueue
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource

class ArtemisDebugger : IDebugProvider {

    override fun startDebugging(target: IDebugTarget): IDebugSession = ArtemisDebugSession(target)

}

class ArtemisDebugSession(val target: IDebugTarget) : IDebugSession {

//    private val modMemory: AModMemory = AModMemory(this)
    private val modCpu: AModCpu = AModCpu(this)
    private val modules: List<ArtemisModule> = listOf(/*modMemory, */modCpu)

    var frozen: Boolean by PropDel.observe(true, { modules.forEach { mod -> mod.onFrozenState(it) } })
    private var unfreezeOverride: (() -> Boolean)? = null

    init {
        for (key in UIManager.getDefaults().keys()) {
            val elem = UIManager.get(key)
            if (elem is FontUIResource) UIManager.put(key, FontUIResource("monospace", elem.style, elem.size))
        }
        modules.forEach { it.isVisible = true }
    }

    override fun kill() {
        modules.forEach(ArtemisModule::dispose)
    }

    override fun shouldFreeze(): Boolean {
        unfreezeOverride?.let {
            if (it()) {
                unfreezeOverride = null
            } else {
                return false
            }
        }
        return frozen || if (modules.any(ArtemisModule::isAtBreakpoint)) {
            frozen = true
            true
        } else {
            false
        }
    }

    override fun onMemoryMutate(addr: Int, length: Int) {
        // NO-OP
    }

    override fun onCpuMutate(prop: CpuProperty) {
        modCpu.onCpuMutate(prop)
    }

    override fun onCpuExecute(opcode: Byte) {
        modCpu.onCpuExecute(opcode)
    }

    override fun onCpuCall(addr: Short) {
        modCpu.onCpuCall(addr)
    }

    override fun onCpuReturn() {
        modCpu.onCpuReturn()
    }

    override fun onInterruptExecuted(interrupt: InterruptType) {
        // NO-OP
    }

    fun unfreeze() {
        frozen = false
        target.unfreeze()
    }

    fun unfreezeOverrideUntil(predicate: () -> Boolean) {
        unfreezeOverride = predicate
        target.unfreeze()
    }

    fun fpExec(notFrozen: (() -> Unit)?, ifFrozen: () -> Unit) {
        if (frozen) {
            EventQueue.invokeLater(ifFrozen)
        } else {
            notFrozen?.invoke()
        }
    }

}

@Suppress("LeakingThis")
abstract class ArtemisModule(title: String, protected val session: ArtemisDebugSession) : JFrame("Arty $title") {

    init {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        isResizable = false
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                session.target.endDebugSession()
            }
        })
    }

    open fun isAtBreakpoint(): Boolean = false

    open fun onFrozenState(frozen: Boolean) {
        // NO-OP
    }

}