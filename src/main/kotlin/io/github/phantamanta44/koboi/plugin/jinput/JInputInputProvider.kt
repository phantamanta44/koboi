package io.github.phantamanta44.koboi.plugin.jinput

import io.github.phantamanta44.koboi.input.ButtonType
import io.github.phantamanta44.koboi.input.IInputProvider
import io.github.phantamanta44.koboi.input.JoypadDir
import net.java.games.input.Controller
import net.java.games.input.ControllerEnvironment
import java.io.File
import java.nio.file.Files

class JInputInputProvider : IInputProvider {

    companion object {

        private fun unpackNatives() {
            val os = System.getProperty("os.name").toLowerCase()
            val natives = when {
                os.contains("windows") -> listOf(
                        "jinput-dx8" to "jinput-dx8.dll",
                        "jinput-dx8_64" to "jinput-dx8_64.dll",
                        "jinput-raw" to "jinput-raw.dll",
                        "jinput-raw_64" to "jinput-raw_64.dll",
                        "jinput-wintab" to "jinput-wintab.dll")
                os.contains("osx") || os.contains("macos") -> listOf(
                        "jinput-osx" to "libjinput-osx.jnilib")
                else -> listOf(
                        "libjinput-linux" to "libjinput-linux.so",
                        "libjinput-linux64" to "libjinput-linux64.so") // eh might work
            }
            val cl = Thread.currentThread().contextClassLoader
            for (native in natives) {
                cl.getResourceAsStream(native.second).use {
                    val dest = File(native.second)
                    if (!dest.exists()) Files.copy(it, dest.toPath())
                    dest.deleteOnExit()
                }
            }
        }

    }

    private val controllerDelegate: ControllerProvider

    init {
        // TODO configurability
        unpackNatives()
        try {
            controllerDelegate = checkNotNull(ControllerEnvironment.getDefaultEnvironment().controllers.map {
                when (it.type) {
                    Controller.Type.GAMEPAD -> JoypadControllerProvider(it)
                    else -> null
                }
            }.first { it != null })
        } catch (e: NoSuchElementException) {
            throw NoSuchElementException("No suitable controllers found!")
        }
    }

    override fun readButton(button: ButtonType): Boolean = controllerDelegate.readButton(button)

    override fun readJoypad(dir: JoypadDir): Boolean = controllerDelegate.readJoypad(dir)

}