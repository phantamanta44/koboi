package io.github.phantamanta44.koboi.graphics

import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.memory.LcdControlRegister
import io.github.phantamanta44.koboi.memory.LcdStatusRegister
import io.github.phantamanta44.koboi.memory.ResettableRegister
import io.github.phantamanta44.koboi.util.toUnsignedInt

class DisplayController(private val cpu: Cpu, private val renderer: IScanLineUploader, private val display: IDisplay,
                        private val memLcdControl: LcdControlRegister, private val memLcdStatus: LcdStatusRegister,
                        private val memScanLine: ResettableRegister) {

    private var cycleCount: Int = 0

    fun start() {
        display.show { cpu.alive.set(false) }
    }

    fun kill() {
        display.kill()
    }

    fun cycle() {
        if (memLcdControl.lcdDisplayEnable) {
            // deal with scan lines
            val scanLine = memScanLine.value.toUnsignedInt()
            val finalCycle = cycleCount == 455
            var vBlank = true
            when {
                scanLine < 144 -> { // draw visible scan lines
                    vBlank = false
                    when {
                        cycleCount < 80 -> { // mode 2 searching oam
                            memLcdStatus.modeUpper = true
                            memLcdStatus.modeLower = false
                            if (memLcdStatus.intOam) cpu.memIntReq.lcdStat = true
                        }
                        cycleCount == 251 -> { // mode 3 upload to display, final cycle
                            memLcdStatus.modeUpper = true
                            memLcdStatus.modeLower = true
                            uploadScanLine(scanLine)
                        }
                        cycleCount < 252 -> { // mode 3 upload to display
                            memLcdStatus.modeUpper = true
                            memLcdStatus.modeLower = true
                        }
                        finalCycle -> { // mode 0 h-blank, final cycle
                            memLcdStatus.modeUpper = false
                            memLcdStatus.modeLower = false
                            if (memLcdStatus.intHBlank) cpu.memIntReq.lcdStat = true
                            ++memScanLine.value
                        }
                        else -> { // mode 0 h-blank
                            memLcdStatus.modeUpper = false
                            memLcdStatus.modeLower = false
                            if (memLcdStatus.intHBlank) cpu.memIntReq.lcdStat = true
                        }
                    }
                }
                scanLine == 144 -> { // entering v-blank; fire v-blank interrupt
                    if (cycleCount == 0) {
                        memLcdStatus.modeUpper = false
                        memLcdStatus.modeLower = true
                        cpu.memIntReq.vBlank = true
                    } else if (finalCycle) {
                        ++memScanLine.value
                    }
                }
                scanLine == 153 -> { // exiting v-blank; reset scan line
                    if (finalCycle) memScanLine.value = 0
                }
                else -> if (finalCycle) ++memScanLine.value
            }
            if (vBlank && memLcdStatus.intVBlank) cpu.memIntReq.lcdStat = true

            // ly coincidence check
            if (scanLine == cpu.memory.read(0xFF45).toUnsignedInt()) {
                if (memLcdStatus.intLyCompare) cpu.memIntReq.lcdStat = true
                memLcdStatus.lyComparison = true
            } else {
                memLcdStatus.lyComparison = false
            }

            // increment cycle
            cycleCount = if (finalCycle) 0 else (cycleCount + 1)
        } else { // display not enabled; set mode to 1 and reset scan line
            memScanLine.value = 0
            memLcdStatus.modeUpper = false
            memLcdStatus.modeLower = true
        }
    }

    private fun uploadScanLine(scanLine: Int) {
        renderer.renderScanLine(scanLine, display, cpu)
    }

}