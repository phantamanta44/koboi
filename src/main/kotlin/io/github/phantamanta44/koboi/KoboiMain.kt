package io.github.phantamanta44.koboi

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import io.github.phantamanta44.koboi.backtrace.BacktraceDetail
import java.io.File
import java.time.Instant
import kotlin.properties.Delegates
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

object KoboiConfig {

    var debug: Boolean by Delegates.notNull()

    var debugOnCrash: Boolean by Delegates.notNull()

    var backtrace: BacktraceDetail by Delegates.notNull()

    var fullBacktrace: Boolean by Delegates.notNull()

    var traceBootrom: Boolean by Delegates.notNull()

    var logInterrupts: Boolean by Delegates.notNull()

    var logDmaTransfers: Boolean by Delegates.notNull()

    var logTimer: Boolean by Delegates.notNull()

    var blarggMode: Boolean by Delegates.notNull()

    var ram: String by Delegates.notNull()

    fun <T>set(prop: KProperty1<KoboiConfig, T>, value: T) {
        (prop as KMutableProperty1<KoboiConfig, T>).set(this, value)
    }

}

fun main(args: Array<String>) = mainBody {
    ArgParser(args).parseInto(::ParsedArgs).run {
        val romFile = File(rom)
        if (!romFile.exists()) throw SystemExitException("specified ROM file is nil", 1)
        Loggr.setLevel(logLevel)
        KoboiConfig::class.memberProperties.forEach {
            val prop = ParsedArgs::class.memberProperties.find { p -> p.name == it.name }!!
            KoboiConfig.set(it, prop.get(this))
        }
        GameEngine.tryInit(romFile)
    }
}

class ParsedArgs(parser: ArgParser) {

    val logLevel: Loggr.LogLevel by parser.storing("--log-level", "-l", help = "Logging verbosity",
            transform = { Loggr.LogLevel.valueOf(toUpperCase()) }).default(Loggr.LogLevel.INFO)

    val backtrace: BacktraceDetail by parser.storing("--bt-level", "-t", help = "Backtrace detail",
            transform = { BacktraceDetail.valueOf(toUpperCase()) }).default(BacktraceDetail.NONE)

    val fullBacktrace: Boolean by parser.flagging("--bt-full", "-T", help = "Show calls of all backtrace frames")

    val debug: Boolean by parser.flagging("--debug", "-d", help = "Run emulation with debugger")

    val debugOnCrash: Boolean by parser.flagging("--debug-on-crash", "-D", help = "Begin debugger on crash")

    val traceBootrom: Boolean by parser.flagging("--trace-bootrom", "-B", help = "Trace execution within the bootrom")

    val logInterrupts: Boolean by parser.flagging("--log-ints", "-I", help = "Log interrupts at the TRACE level")

    val logDmaTransfers: Boolean by parser.flagging("--log-dma", "-j", help = "Log DMA transfers at the TRACE level")

    val logTimer: Boolean by parser.flagging("--log-timer", "-C", help = "Log timer at the TRACE level")

    val blarggMode: Boolean by parser.flagging("--blargg", help = "Enable output from serial IO ports")

    val ram: String by parser.storing("--ram", "-r", help = "RAM file").default { "$rom.ram" }

    val rom: String by parser.positional("ROM", help = "Path to a ROM image of a game cartridge")

}

object Loggr {

    var engine: GameEngine? = null
    private var effectiveLevel = LogLevel.INFO

    enum class LogLevel(val prefix: String, val symbol: String) {
        TRACE("TRACE", "."), DEBUG("DEBUG", "$"), INFO("INFO", "@"), WARN("WARNING", "?"), ERROR("ERROR", "!")
    }

    private fun doPrint(level: LogLevel, msg: Any) {
        if (level.ordinal >= effectiveLevel.ordinal) {
            engine.let {
                if (it != null) {
                    println("${Instant.now()}@${it.gameTick} ${level.symbol} ${level.prefix} :: $msg")
                } else {
                    println("${Instant.now()} ${level.symbol} ${level.prefix} :: $msg")
                }
            }
        }
    }
    
    fun trace(msg: Any) = doPrint(LogLevel.TRACE, msg)
    
    fun debug(msg: Any) = doPrint(LogLevel.DEBUG, msg)

    fun info(msg: Any) = doPrint(LogLevel.INFO, msg)

    fun warn(msg: Any) = doPrint(LogLevel.WARN, msg)

    fun error(msg: Any) = doPrint(LogLevel.ERROR, msg)
    
    fun setLevel(level: LogLevel) {
        effectiveLevel = level
    }

}