package io.github.phantamanta44.koboi.graphics

import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.memory.ColourPaletteSwicher
import io.github.phantamanta44.koboi.memory.IMemoryRange
import io.github.phantamanta44.koboi.memory.LcdControlRegister
import io.github.phantamanta44.koboi.memory.MemoryBankSwitcher
import io.github.phantamanta44.koboi.util.and
import io.github.phantamanta44.koboi.util.or
import io.github.phantamanta44.koboi.util.toUnsignedInt
import kotlin.math.floor

fun getTileMapAddress(tileSelect: Boolean): Int = if (tileSelect) 0x1C00 else 0x1800

abstract class ScanLineRenderer(protected val ctrl: LcdControlRegister, protected val display: IDisplay,
                                protected val cpu: Cpu, protected val vram: MemoryBankSwitcher) : IScanLineUploader {

    fun getTileRow(tileSelect: Boolean, absY: Int, bank: Int): IMemoryRange {
        return vram.banks[bank].readLength(getTileMapAddress(tileSelect) + 32 * floor(absY / 8.0).toInt(), 32)
    }

    fun getTileData(tileNum: Byte, addressingMethod: Boolean, bank: Int): IMemoryRange {
        return if (addressingMethod) {
            vram.banks[bank].readLength(tileNum.toUnsignedInt() * 16, 16)
        } else {
            vram.banks[bank].readLength(0x1000 + tileNum.toInt() * 16, 16)
        }
    }

}

class GbRenderer(ctrl: LcdControlRegister, display: IDisplay, cpu: Cpu, vram: MemoryBankSwitcher) : ScanLineRenderer(ctrl, display, cpu, vram) {

    override fun renderScanLine(y: Int) {
        TODO("non-gbc renderer")
    }

}

class GbcRenderer(ctrl: LcdControlRegister, display: IDisplay, cpu: Cpu, vram: MemoryBankSwitcher,
                  private val palBg: ColourPaletteSwicher, private val palSprite: ColourPaletteSwicher) : ScanLineRenderer(ctrl, display, cpu, vram) {

    private val pixels = IntArray(160 * 3)
    private val bgPriorities = IntArray(160)

    override fun renderScanLine(y: Int) {
        // draw bg/window
        bgPriorities.fill(if (ctrl.bgState) 2 else 1)
        val tileAbsY = (cpu.memory.read(0xFF42).toUnsignedInt() + y) % 256
        val tileOffsetY = tileAbsY % 8
        val bgTileMapType = ctrl.bgTileMapDisplaySelect
        val tileDataType = ctrl.tileDataSelect
        val rowBgMeta = getTileRow(bgTileMapType, tileAbsY, 1)
        val rowBgTiles = getTileRow(bgTileMapType, tileAbsY, 0)
                .zip(rowBgMeta) { tile, meta -> getTileData(tile, tileDataType, (meta.toInt() and 0x8) ushr 3) }
        val scrollX = cpu.memory.read(0xFF43).toUnsignedInt()
        if (ctrl.windowDisplayEnable) {
            val windowY = cpu.memory.read(0xFF4A).toUnsignedInt()
            if (y >= windowY) {
                val winTileMapType = ctrl.windowTileMapDisplaySelect
                val windowX = cpu.memory.read(0xFF4B).toUnsignedInt() - 7
                val winAbsY = y - windowY
                val rowWinMeta = getTileRow(winTileMapType, winAbsY, 1)
                val rowWinTiles = getTileRow(winTileMapType, winAbsY, 0)
                        .zip(rowBgMeta) { tile, meta -> getTileData(tile, tileDataType, (meta.toInt() and 0x8) ushr 3) }
                val windowXRange = windowX..(windowX + 159)
                val winTileOffsetY = winAbsY % 8
                for (x in 0..159) {
                    if (x in windowXRange) {
                        val winAbsX = x - windowX
                        val rowIndex = floor(winAbsX / 8.0).toInt()
                        val tileMeta = rowWinMeta[rowIndex].toInt()
                        val tileData = rowWinTiles[rowIndex]
                        val index = if (tileMeta and 0x40 != 0) ((7 - winTileOffsetY) * 2) else (winTileOffsetY * 2)
                        val pixLower = tileData[index]
                        val pixUpper = tileData[index + 1]
                        val xShiftFactor = if (tileMeta and 0x20 != 0) (winAbsX % 8) else (7 - (winAbsX % 8))
                        val mask = 1 shl xShiftFactor
                        val colIndex = ((pixLower.toInt() and mask) ushr xShiftFactor) or
                                (((pixUpper.toInt() and mask) ushr xShiftFactor) shl 1)
                        val col = palBg.getColours(tileMeta and 0b00000111, colIndex)
                        if (tileMeta and 0x80 == 0) {
                            bgPriorities[x] = if (colIndex == 0) 0 else 1
                        }
                        writePixel(x, col)
                    } else {
                        drawBgPixel(x, tileOffsetY, scrollX, rowBgMeta, rowBgTiles)
                    }
                }
            } else {
                for (x in 0..159) drawBgPixel(x, tileOffsetY, scrollX, rowBgMeta, rowBgTiles)
            }
        } else {
            for (x in 0..159) drawBgPixel(x, tileOffsetY, scrollX, rowBgMeta, rowBgTiles)
        }

        // draw sprites
        if (ctrl.spriteDisplayEnable) {
            val doubleHeight = ctrl.spriteSize
            val spriteHeight = if (doubleHeight) 16 else 8
            for (sprite in 39 downTo 0) {
                val index = 0xFE00 + sprite * 4
                val sY = cpu.memory.read(index).toUnsignedInt() - 16
                if (y >= sY && y < sY + spriteHeight) {
                    val sX = cpu.memory.read(index + 1).toUnsignedInt() - 8
                    if (sX > -8 && sX < 160) {
                        val tileIndex = cpu.memory.read(index + 2)
                        val meta = cpu.memory.read(index + 3).toInt()
                        var spriteOffsetY = if (meta and 0x40 != 0) (spriteHeight - 1 + sY - y) else (y - sY)
                        val tileData = if (doubleHeight) {
                            if (spriteOffsetY >= 8) {
                                spriteOffsetY %= 8
                                getTileData(tileIndex or 1, true, (meta and 0x8) ushr 3)
                            } else {
                                getTileData(tileIndex and 0b11111110.toByte(), true, (meta and 0x8) ushr 3)
                            }
                        } else {
                            getTileData(tileIndex, true, (meta and 0x8) ushr 3)
                        }
                        for (x in sX..(sX + 7)) {
                            if (x in 0..159) {
                                val bgPriority = bgPriorities[x]
                                if (bgPriority == 0 || (bgPriority == 1 && meta and 0x80 == 0)) {
                                    val pixLower = tileData[spriteOffsetY * 2]
                                    val pixUpper = tileData[spriteOffsetY * 2 + 1]
                                    val xShiftFactor = if (meta and 0x20 != 0) (x - sX) else (7 + sX - x)
                                    val mask = 1 shl xShiftFactor
                                    val colIndex = ((pixLower.toInt() and mask) ushr xShiftFactor) or
                                            (((pixUpper.toInt() and mask) ushr xShiftFactor) shl 1)
                                    if (colIndex != 0) {
                                        val col = palSprite.getColours(meta and 0b00000111, colIndex)
                                        writePixel(x, col)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (x in 0..159) {
            val index = x * 3
            display.writePixel(x, y, pixels[index], pixels[index + 1], pixels[index + 2])
        }
    }

    private fun drawBgPixel(x: Int, tileOffsetY: Int, scrollX: Int, rowBgMeta: IMemoryRange, rowBgTiles: List<IMemoryRange>) {
        val tileAbsX = (scrollX + x) % 256
        val rowIndex = floor(tileAbsX / 8.0).toInt()
        val tileMeta = rowBgMeta[rowIndex].toInt()
        val tileData = rowBgTiles[rowIndex]
        val index = if (tileMeta and 0x40 != 0) ((7 - tileOffsetY) * 2) else (tileOffsetY * 2)
        val pixLower = tileData[index]
        val pixUpper = tileData[index + 1]
        val xShiftFactor = if (tileMeta and 0x20 != 0) (tileAbsX % 8) else (7 - (tileAbsX % 8))
        val mask = 1 shl xShiftFactor
        val colIndex = ((pixLower.toInt() and mask) ushr xShiftFactor) or
                (((pixUpper.toInt() and mask) ushr xShiftFactor) shl 1)
        val col = palBg.getColours(tileMeta and 0b00000111, colIndex)
        if (tileMeta and 0x80 == 0) {
            bgPriorities[x] = if (colIndex == 0) 0 else 1
        }
        writePixel(x, col)
    }

    private fun writePixel(x: Int, col: Triple<Int, Int, Int>) {
        val index = x * 3
        pixels[index] = col.first
        pixels[index + 1] = col.second
        pixels[index + 2] = col.third
    }

}