package io.github.phantamanta44.koboi.util

import io.github.phantamanta44.koboi.memory.GbMemory
import io.github.phantamanta44.koboi.memory.GbcMemory
import io.github.phantamanta44.koboi.memory.DirectObservableMemoryArea

enum class GameboyType(val typeName: String, val wramBankCount: Int,
                       val wrapMemory: (DirectObservableMemoryArea, ByteArray, () -> Unit) -> DirectObservableMemoryArea) {

    GAMEBOY("gb", 2, ::GbMemory),
    GAMEBOY_COLOUR("cgb", 8, ::GbcMemory)

}