package io.github.phantamanta44.koboi.plugin.debugshell

import io.github.phantamanta44.koboi.backtrace.mnemonics
import io.github.phantamanta44.koboi.debug.IDebugProvider
import io.github.phantamanta44.koboi.debug.IDebugSession
import io.github.phantamanta44.koboi.debug.IDebugTarget
import io.github.phantamanta44.koboi.memory.IMemoryRange
import io.github.phantamanta44.koboi.util.toShortHex
import io.github.phantamanta44.koboi.util.toUnsignedHex
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class DebugShellProvider : IDebugProvider {

    override fun startDebugging(target: IDebugTarget): IDebugSession = DebugShell(target)

}

class DebugShell(private val target: IDebugTarget) : IDebugSession {

    private val alive: AtomicBoolean = AtomicBoolean(true)

    init {
        thread(isDaemon = true, name = "Debug shell") {
            while (alive.get()) {
                try {
                    print("$ ")
                    val line = readLine()
                    if (line == null) {
                        println("! eof reached; exiting shell")
                        break
                    } else {
                        val cmd = line.split(Regex("\\s+"))
                        when (cmd[0].toLowerCase()) {
                            "rd" -> {
                                when (cmd.size) {
                                    2 -> {
                                        try {
                                            println(target.memory.read(cmd[1].toInt(16), true).toUnsignedHex())
                                        } catch (e: NumberFormatException) {
                                            println("? hex format")
                                        }
                                    }
                                    3 -> {
                                        try {
                                            val rangeStart = cmd[1].toInt(16)
                                            hexdump(rangeStart, target.memory.readRangeDirect(rangeStart, cmd[2].toInt(16) + 1))
                                        } catch (e: NumberFormatException) {
                                            println("? hex format")
                                        }
                                    }
                                    else -> println("? arg count")
                                }
                            }

                            "ld" -> {
                                if (cmd.size == 3) {
                                    try {
                                        val addr = cmd[1].toInt(16)
                                        val value = cmd[2].toInt(16).toByte()
                                        target.memory.write(addr, value, direct = true)
                                        println("${addr.toShortHex()} = ${target.memory.read(addr, true).toUnsignedHex()}")
                                    } catch (e: NumberFormatException) {
                                        println("? hex format")
                                    }
                                } else {
                                    println("? arg count")
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

                            "memtype" -> {
                                if (cmd.size == 2) {
                                    try {
                                        println(target.memory.typeAt(cmd[1].toInt(16)))
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

    override fun kill() {
        alive.set(false)
    }

}

fun hexdump(firstIndex: Int, mem: IMemoryRange, out: PrintStream = System.out) {
    var addr = 0
    while (addr < mem.length) {
        out.print("${(addr + firstIndex).toShortHex()} |")
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