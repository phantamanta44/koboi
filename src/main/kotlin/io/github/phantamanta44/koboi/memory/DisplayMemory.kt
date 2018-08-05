package io.github.phantamanta44.koboi.memory

class LcdControlRegister : BitwiseRegister() {

    var lcdDisplayEnable: Boolean by delegateBit(7)

    var windowTileMapDisplaySelect: Boolean by delegateBit(6)

    var windowDisplayEnable: Boolean by delegateBit(5)

    var tileDataSelect: Boolean by delegateBit(4)

    var bgTileMapDisplaySelect: Boolean by delegateBit(3)

    var spriteSize: Boolean by delegateBit(2)

    var spriteDisplayEnable: Boolean by delegateBit(1)

    var bgState: Boolean by delegateBit(0)

}

class LcdStatusRegister : BitwiseRegister(0b01111100) {

    var intLyCompare: Boolean by delegateBit(6)

    var intOam: Boolean by delegateBit(5)

    var intVBlank: Boolean by delegateBit(4)

    var intHBlank: Boolean by delegateBit(3)

    var lyComparison: Boolean by delegateBit(2)

    var modeUpper: Boolean by delegateBit(1)

    var modeLower: Boolean by delegateBit(0)

}