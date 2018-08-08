package io.github.phantamanta44.koboi

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import io.github.phantamanta44.koboi.cpu.BacktraceDetail
import java.io.File
import java.time.Instant
import kotlin.properties.Delegates

object KoboiConfig {

    var debugShellOnCrash: Boolean by Delegates.notNull()

    var backtrace: BacktraceDetail by Delegates.notNull()

    var fullBacktrace: Boolean by Delegates.notNull()

    var traceBootrom: Boolean by Delegates.notNull()

}

fun main(args: Array<String>) = mainBody {
    ArgParser(args).parseInto(::ParsedArgs).run {
        val romFile = File(rom)
        if (!romFile.exists()) throw SystemExitException("specified ROM file is nil", 1)
        Loggr.setLevel(logLevel)
        KoboiConfig.backtrace = backtrace
        KoboiConfig.fullBacktrace = fullBacktrace
        KoboiConfig.debugShellOnCrash = debugShellOnCrash
        KoboiConfig.traceBootrom = traceBootrom
        GameEngine.tryInit(romFile)
    }
}

class ParsedArgs(parser: ArgParser) {

    val logLevel: Loggr.LogLevel by parser.storing("--log-level", "-l", help = "Logging verbosity",
            transform = { Loggr.LogLevel.valueOf(toUpperCase()) }).default(Loggr.LogLevel.INFO)

    val backtrace: BacktraceDetail by parser.storing("--bt-level", "-t", help = "Backtrace detail",
            transform = { BacktraceDetail.valueOf(toUpperCase()) }).default(BacktraceDetail.NONE)

    val fullBacktrace: Boolean by parser.flagging("--bt-full", "-T", help = "Show calls of all backtrace frames")

    val debugShellOnCrash: Boolean by parser.flagging("--debug-shell-on-crash", "-d", help = "Drop to a debug shell on crash")

    val traceBootrom: Boolean by parser.flagging("--trace-bootrom", "-B", help = "Trace execution within the bootrom")

    val rom: String by parser.positional("ROM", help = "Path to a ROM image of a game cartridge")

}

object Loggr {

    private var effectiveLevel = LogLevel.INFO

    enum class LogLevel(val prefix: String, val symbol: String) {
        TRACE("TRACE", "."), DEBUG("DEBUG", "$"), INFO("INFO", "@"), WARN("WARNING", "?"), ERROR("ERROR", "!")
    }

    private fun doPrint(level: LogLevel, msg: Any) {
        if (level.ordinal >= effectiveLevel.ordinal) {
            println("${Instant.now()} ${level.symbol} ${level.prefix} :: $msg")
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