package io.github.phantamanta44.koboi.util

import io.github.phantamanta44.koboi.memory.GbMemory
import io.github.phantamanta44.koboi.memory.GbcMemory
import io.github.phantamanta44.koboi.memory.IMemoryArea

enum class GameboyType(val typeName: String, val wramBankCount: Int,
                       val wrapMemory: (IMemoryArea, ByteArray, () -> Unit) -> IMemoryArea) {

    GAMEBOY("gb", 1, ::GbMemory),
    GAMEBOY_COLOUR("cgb", 7, ::GbcMemory)

}