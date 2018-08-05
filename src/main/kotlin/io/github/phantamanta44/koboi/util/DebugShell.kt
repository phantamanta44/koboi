package io.github.phantamanta44.koboi.util

import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.cpu.mnemonics
import io.github.phantamanta44.koboi.game.GameEngine
import io.github.phantamanta44.koboi.memory.IMemoryRange
import java.io.PrintStream

class DebugShell(private val engine: GameEngine) {

    fun begin() {
        Thread.sleep(500) // deals with annoying issue where stderr stack trace covers stuff up
        Loggr.warn("Entered debug shell.")
        while (true) {
            try {
                print("$ ")
                val line = readLine()
                if (line == null) {
                    print("! eof reached; exiting shell")
                    break
                } else {
                    val cmd = line.split(Regex("\\s+"))
                    when (cmd[0].toLowerCase()) {
                        "mem" -> {
                            when (cmd.size) {
                                2 -> {
                                    try {
                                        println(engine.memory.read(cmd[1].toInt(16)).toUnsignedHex())
                                    } catch (e: NumberFormatException) {
                                        println("? hex format")
                                    }
                                }
                                3 -> {
                                    try {
                                        val rangeStart = cmd[1].toInt(16)
                                        hexdump(rangeStart, engine.memory.readRange(rangeStart, cmd[2].toInt(16) + 1))
                                    } catch (e: NumberFormatException) {
                                        println("? hex format")
                                    }
                                }
                                else -> println("? arg count")
                            }
                        }
                        "mne" -> {
                            if (cmd.size == 2) {
                                try {
                                    println(mnemonics[cmd[1].toInt(16)])
                                } catch (e: NumberFormatException) {
                                    println("? hex format")
                                }
                            } else {
                                println("? arg count")
                            }
                        }
                        "h2b" -> {
                            if (cmd.size == 2) {
                                try {
                                    println(cmd[1].toInt(16).toString(2))
                                } catch (e: NumberFormatException) {
                                    println("? hex format")
                                }
                            } else {
                                println("? arg count")
                            }
                        }
                        else -> println("? unknown command")
                    }
                }
            } catch (e: Exception) {
                println("! exception raised")
                e.printStackTrace()
            }
        }
    }

}

fun hexdump(firstIndex: Int, mem: IMemoryRange, out: PrintStream = System.out) {
    var addr = 0
    while (addr < mem.length) {
        out.print("${(addr + firstIndex).toShort().toUnsignedHex()} |")
        for (i in addr..(addr + 15)) {
            if (i < mem.length) {
                out.print(" ${mem[i].toUnsignedHex()}")
            } else {
                break
            }
        }
        addr += 16
        out.println()
    }
}