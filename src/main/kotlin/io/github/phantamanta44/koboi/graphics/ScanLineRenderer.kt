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

    override fun renderScanLine(y: Int) {
        // draw bg/window
        val bgDefaultPriority = if (ctrl.bgState) 2 else 1
        val bgPriorities = IntArray(160, { bgDefaultPriority })
        val tileAbsY = cpu.memory.read(0xFF42).toUnsignedInt() + y
        val tileOffsetY = tileAbsY % 8
        val bgTileMapType = ctrl.bgTileMapDisplaySelect
        val tileDataType = ctrl.tileDataSelect
        val rowBgMeta = getTileRow(bgTileMapType, tileAbsY, 1)
        val rowBgTiles = getTileRow(bgTileMapType, tileAbsY, 0)
                .zip(rowBgMeta) { tile, meta -> getTileData(tile, tileDataType, (meta.toInt() and 0x8) ushr 3) }
        val scrollX = cpu.memory.read(0xFF43).toUnsignedInt()
        if (ctrl.windowDisplayEnable && y > cpu.memory.read(0xFF4A).toUnsignedInt()) {
            val winTileMapType = ctrl.windowTileMapDisplaySelect
            val windowX = cpu.memory.read(0xFF4B).toUnsignedInt() - 7
            val rowWinMeta = getTileRow(winTileMapType, y, 1)
            val rowWinTiles = getTileRow(winTileMapType, y, 0)
                        .zip(rowBgMeta) { tile, meta -> getTileData(tile, tileDataType, (meta.toInt() and 0x8) ushr 3) }
            for (x in 0..(windowX - 1)) {
                val tileAbsX = scrollX + x
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
                display.writePixel(x, y, col.first, col.second, col.third)
            }
            for (x in windowX..159) {
                val winTileOffsetY = y % 8
                val rowIndex = floor(x / 8.0).toInt()
                val tileMeta = rowWinMeta[rowIndex].toInt()
                val tileData = rowWinTiles[rowIndex]
                val index = if (tileMeta and 0x40 != 0) ((7 - winTileOffsetY) * 2) else (winTileOffsetY * 2)
                val pixLower = tileData[index]
                val pixUpper = tileData[index + 1]
                val xShiftFactor = if (tileMeta and 0x20 != 0) (x % 8) else (7 - (x % 8))
                val mask = 1 shl xShiftFactor
                val colIndex = ((pixLower.toInt() and mask) ushr xShiftFactor) or
                        (((pixUpper.toInt() and mask) ushr xShiftFactor) shl 1)
                val col = palBg.getColours(tileMeta and 0b00000111, colIndex)
                if (tileMeta and 0x80 == 0) {
                    bgPriorities[x] = if (colIndex == 0) 0 else 1
                }
                display.writePixel(x, y, col.first, col.second, col.third)
            }
        } else {
            for (x in 0..159) {
                val tileAbsX = scrollX + x
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
                display.writePixel(x, y, col.first, col.second, col.third)
            }
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
                        var spriteOffsetY = if (meta and 0x40 != 0) (spriteHeight + sY - y) else (y - sY)
                        val tileData = if (doubleHeight) {
                            if (spriteOffsetY >= 8) {
                                getTileData(tileIndex or 1, false, (meta and 0x8) ushr 3)
                            } else {
                                spriteOffsetY %= 8
                                getTileData(tileIndex and 0b11111110.toByte(), false, (meta and 0x8) ushr 3)
                            }
                        } else {
                            getTileData(tileIndex, false, (meta and 0x8) ushr 3)
                        }
                        for (x in sY..(sY + 8)) {
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
                                        val col = palBg.getColours(meta and 0b00000111, colIndex)
                                        display.writePixel(x, y, col.first, col.second, col.third)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}