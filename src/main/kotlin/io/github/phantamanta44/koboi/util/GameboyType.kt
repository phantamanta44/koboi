package io.github.phantamanta44.koboi.util

import io.github.phantamanta44.koboi.graphics.IScanLineUploader
import io.github.phantamanta44.koboi.memory.GbMemory
import io.github.phantamanta44.koboi.memory.GbcMemory
import io.github.phantamanta44.koboi.memory.IMemoryArea
import io.github.phantamanta44.koboi.plugin.glfrontend.GlGbRenderer
import io.github.phantamanta44.koboi.plugin.glfrontend.GlGbcRenderer

enum class GameboyType(val typeName: String, val wramBankCount: Int,
                       val createRenderer: () -> IScanLineUploader,
                       val wrapMemory: (IMemoryArea, ByteArray) -> IMemoryArea) {

    GAMEBOY("gb", 1, ::GlGbRenderer, ::GbMemory),
    GAMEBOY_COLOUR("cgb", 7, ::GlGbcRenderer, ::GbcMemory)

}