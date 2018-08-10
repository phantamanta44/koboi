package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.GameEngine
import io.github.phantamanta44.koboi.KoboiConfig
import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.util.toShortHex

class DmaTransferHandler(private val gameEngine: GameEngine) {

    private var transfer: DmaTransfer? = null

    fun cycle() {
        transfer?.cycle()
    }

    fun isDmaTransferActive(): Boolean {
        return transfer != null
    }

    fun performDmaTransfer(mode: DmaTransferMode, srcAddr: Int, length: Int, destAddr: Int) {
        if (KoboiConfig.logDmaTransfers) {
            Loggr.trace("Starting ${mode.name} DMA transfer: $length bytes from ${srcAddr.toShortHex()} to ${destAddr.toShortHex()}")
        }
        when {
            mode == DmaTransferMode.VRAM_ATOMIC -> gameEngine.memory.write(destAddr, gameEngine.memory.readLength(srcAddr, length))
            transfer != null -> throw IllegalStateException("DMA transfer already in progress!")
            else -> transfer = DmaTransfer(mode, srcAddr, length, destAddr)
        }
    }

    fun cancelTransfer() {
        transfer = null
    }

    private inner class DmaTransfer(private val mode: DmaTransferMode,
                                    private var srcAddr: Int, private var length: Int, private var destAddr: Int) {

        private val transferStart: Long = gameEngine.clock.globalTimer

        fun cycle() {
            val toTransfer = mode.getTransferLength(gameEngine, length, transferStart)
            if (toTransfer > 0) {
                gameEngine.memory.write(destAddr, gameEngine.memory.readLength(srcAddr, toTransfer))
                if (toTransfer == length) {
                    cancelTransfer()
                } else {
                    srcAddr += toTransfer
                    destAddr += toTransfer
                    length -= toTransfer
                }
            }
        }

    }

}

enum class DmaTransferMode(val getTransferLength: (GameEngine, Int, Long) -> Int) {

    VRAM_ATOMIC({ _, _, _ ->
        throw IllegalStateException()
    }),
    OAM({ engine, length, transferStart ->
        if (engine.cpu.doubleClock) {
            if (engine.clock.globalTimer - transferStart == 320L) length else 0
        } else if (engine.clock.globalTimer - transferStart == 640L) {
            length
        } else {
            0
        }
    }),
    VRAM_H_BLANK({ engine, length, _ ->
        if (engine.ppu.dmaHBlankFrame) Math.min(0x10, length) else 0
    })

}