package io.github.phantamanta44.koboi.plugin.jinput

import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.input.ButtonType
import io.github.phantamanta44.koboi.input.IInputProvider
import io.github.phantamanta44.koboi.input.JoypadDir
import net.java.games.input.Component
import net.java.games.input.Controller
import net.java.games.input.Event
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

abstract class ControllerProvider(private val controller: Controller) : IInputProvider {
    
    private val buttons: EnumMap<ButtonType, AtomicBoolean> = EnumMap(ButtonType::class.java)
    private val dirs: EnumMap<JoypadDir, AtomicBoolean> = EnumMap(JoypadDir::class.java)

    private val event: Event = Event()

    init {
        for (button in ButtonType.values()) buttons[button] = AtomicBoolean(false)
        for (dir in JoypadDir.values()) dirs[dir] = AtomicBoolean(false)
        thread(isDaemon = true, name = "JInput thread") {
            while (true) {
                controller.poll()
                while (controller.eventQueue.getNextEvent(event)) {
                    consume(event)
                }
            }
        }
        Loggr.debug("Wrapped controller ${controller.name}.")
    }

    protected abstract fun consume(event: Event)

    protected fun setState(button: ButtonType, state: Boolean) {
        buttons[button]!!.set(state)
    }

    protected fun setState(dir: JoypadDir, state: Boolean) {
        dirs[dir]!!.set(state)
    }

    override fun readButton(button: ButtonType): Boolean = buttons[button]!!.get()

    override fun readJoypad(dir: JoypadDir): Boolean = dirs[dir]!!.get()

}

class JoypadControllerProvider(controller: Controller) : ControllerProvider(controller) {

    override fun consume(event: Event) {
        when (event.component.identifier) {
            Component.Identifier.Button.A, Component.Identifier.Button._0 ->
                setState(ButtonType.A, event.value == 1F)
            Component.Identifier.Button.B, Component.Identifier.Button._1 ->
                setState(ButtonType.B, event.value == 1F)
            Component.Identifier.Button._7 ->
                setState(ButtonType.START, event.value == 1F)
            Component.Identifier.Button.SELECT, Component.Identifier.Button._6 ->
                setState(ButtonType.SELECT, event.value == 1F)
            Component.Identifier.Axis.POV -> when (event.value) {
                Component.POV.OFF -> {
                    setState(JoypadDir.UP, false)
                    setState(JoypadDir.LEFT, false)
                    setState(JoypadDir.DOWN, false)
                    setState(JoypadDir.RIGHT, false)
                }
                Component.POV.UP -> {
                    setState(JoypadDir.UP, true)
                    setState(JoypadDir.LEFT, false)
                    setState(JoypadDir.DOWN, false)
                    setState(JoypadDir.RIGHT, false)
                }
                Component.POV.UP_RIGHT -> {
                    setState(JoypadDir.UP, true)
                    setState(JoypadDir.LEFT, false)
                    setState(JoypadDir.DOWN, false)
                    setState(JoypadDir.RIGHT, true)
                }
                Component.POV.RIGHT -> {
                    setState(JoypadDir.UP, false)
                    setState(JoypadDir.LEFT, false)
                    setState(JoypadDir.DOWN, false)
                    setState(JoypadDir.RIGHT, true)
                }
                Component.POV.DOWN_RIGHT -> {
                    setState(JoypadDir.UP, false)
                    setState(JoypadDir.LEFT, false)
                    setState(JoypadDir.DOWN, true)
                    setState(JoypadDir.RIGHT, true)
                }
                Component.POV.DOWN -> {
                    setState(JoypadDir.UP, false)
                    setState(JoypadDir.LEFT, false)
                    setState(JoypadDir.DOWN, true)
                    setState(JoypadDir.RIGHT, false)
                }
                Component.POV.DOWN_LEFT -> {
                    setState(JoypadDir.UP, false)
                    setState(JoypadDir.LEFT, true)
                    setState(JoypadDir.DOWN, true)
                    setState(JoypadDir.RIGHT, false)
                }
                Component.POV.LEFT -> {
                    setState(JoypadDir.UP, false)
                    setState(JoypadDir.LEFT, true)
                    setState(JoypadDir.DOWN, false)
                    setState(JoypadDir.RIGHT, false)
                }
                Component.POV.UP_LEFT -> {
                    setState(JoypadDir.UP, true)
                    setState(JoypadDir.LEFT, true)
                    setState(JoypadDir.DOWN, false)
                    setState(JoypadDir.RIGHT, false)
                }
            }
        }
    }

}