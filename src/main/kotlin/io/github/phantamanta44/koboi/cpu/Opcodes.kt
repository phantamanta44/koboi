package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.toUnsignedInt

object Opcodes {
    // TODO handle each read of memory on a different M-cycle (maybe implement an internal stack in Cpu?)
    private val opcodeTable: Array<Insn> = arrayOf(
            idle(3) then (trace also advance()), // 00
            idle(11) then (loadHardShort into writeRegister(Cpu::regBC)), // 01
            idle(7) then (loadRegister(Cpu::regA) into writePointer(Cpu::regBC)), // 02
            idle(7) then increment16(Cpu::regBC), // 03
            idle(3) then increment8(Cpu::regB), // 04
            idle(3) then decrement8(Cpu::regB), // 05
            idle(7) then (loadHardByte into writeRegister(Cpu::regB)), // 06
            idle(3) then rotateLeft(Cpu::regA), // 07
            idle(19) then (loadRegister(Cpu::regSP) into writeHardAddress16), // 08
            idle(7) then (loadRegister16AsInt(Cpu::regBC) into incRegister16(Cpu::regHL)), // 09
            idle(7) then (loadPointer(Cpu::regBC) into writeRegister(Cpu::regA)), // 0a
            idle(7) then decrement16(Cpu::regBC), // 0b
            idle(3) then increment8(Cpu::regC), // 0c
            idle(3) then decrement8(Cpu::regC), // 0d
            idle(7) then (loadHardByte into writeRegister(Cpu::regC)), // 0e
            idle(3) then rotateRight(Cpu::regA), // 0f

            { it.trace(); it.advance(2); it.stop(); it.idle(3); }, // 10
            idle(11) then (loadHardShort into writeRegister(Cpu::regDE)), // 11
            idle(7) then (loadRegister(Cpu::regA) into writePointer(Cpu::regDE)), // 12
            idle(7) then increment16(Cpu::regDE), // 13
            idle(3) then increment8(Cpu::regD), // 14
            idle(3) then decrement8(Cpu::regD), // 15
            idle(7) then (loadHardByte into writeRegister(Cpu::regD)), // 16
            idle(3) then rotateLeftThroughCarry(Cpu::regA), // 17
            idle(11) then jumpRelative, // 18
            idle(7) then (loadRegister16AsInt(Cpu::regDE) into incRegister16(Cpu::regHL)), // 19
            idle(7) then (loadPointer(Cpu::regDE) into writeRegister(Cpu::regA)), // 1a
            idle(7) then decrement16(Cpu::regDE), // 1b
            idle(3) then increment8(Cpu::regE), // 1c
            idle(3) then decrement8(Cpu::regE), // 1d
            idle(7) then (loadHardByte into writeRegister(Cpu::regE)), // 1e
            idle(3) then rotateRightThroughCarry(Cpu::regA), // 1f

            predicate(nonZero,
                    idle(11) then jumpRelative,
                    idle(7) then (trace also advance(2))), // 20
            idle(11) then (loadHardShort into writeRegister(Cpu::regHL)), // 21
            idle(7) then (loadRegister(Cpu::regA) into writeHLPointerThenAdvance), // 22
            idle(7) then increment16(Cpu::regHL), // 23
            idle(3) then increment8(Cpu::regH), // 24
            idle(3) then decrement8(Cpu::regH), // 25
            idle(7) then (loadHardByte into writeRegister(Cpu::regH)), // 26
            idle(3) then daa, // 27
            predicate(isZero,
                    idle(11) then jumpRelative,
                    idle(7) then (trace also advance(2))), // 28
            idle(7) then (loadRegister16AsInt(Cpu::regHL) into incRegister16(Cpu::regHL)), // 29
            idle(7) then (loadHLPointerThenAdvance into writeRegister(Cpu::regA)), // 2a
            idle(7) then decrement16(Cpu::regHL), // 2b
            idle(3) then increment8(Cpu::regL), // 2c
            idle(3) then decrement8(Cpu::regL), // 2d
            idle(7) then (loadHardByte into writeRegister(Cpu::regL)), // 2e
            idle(3) then akkuCpl, // 2f

            predicate(nonCarry,
                    idle(11) then jumpRelative,
                    idle(7) then (trace also advance(2))), // 30
            idle(11) then (loadHardShort into writeRegister(Cpu::regSP)), // 31
            idle(7) then (loadRegister(Cpu::regA) into writeHLPointerThenBacktrack), // 32
            idle(7) then increment16(Cpu::regSP), // 33
            idle(11) then incrementPointer(Cpu::regHL), // 34
            idle(11) then decrementPointer(Cpu::regHL), // 35
            idle(11) then (loadHardByte into writePointer(Cpu::regHL)), // 36
            idle(3) then scf, // 37
            predicate(isCarry,
                    idle(11) then jumpRelative,
                    idle(7) then (trace also advance(2))), // 38
            idle(7) then (loadRegister16AsInt(Cpu::regSP) into incRegister16(Cpu::regHL)), // 39
            idle(7) then (loadHLPointerThenBacktrack into writeRegister(Cpu::regA)), // 3a
            idle(7) then decrement16(Cpu::regSP), // 3b
            idle(3) then increment8(Cpu::regA), // 3c
            idle(3) then decrement8(Cpu::regA), // 3d
            idle(7) then (loadHardByte into writeRegister(Cpu::regA)), // 3e
            idle(3) then ccf, // 3f

            idle(3) then (loadRegister(Cpu::regB) into writeRegister(Cpu::regB)), // 40
            idle(3) then (loadRegister(Cpu::regC) into writeRegister(Cpu::regB)), // 41
            idle(3) then (loadRegister(Cpu::regD) into writeRegister(Cpu::regB)), // 42
            idle(3) then (loadRegister(Cpu::regE) into writeRegister(Cpu::regB)), // 43
            idle(3) then (loadRegister(Cpu::regH) into writeRegister(Cpu::regB)), // 44
            idle(3) then (loadRegister(Cpu::regL) into writeRegister(Cpu::regB)), // 45
            idle(7) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regB)), // 46
            idle(3) then (loadRegister(Cpu::regA) into writeRegister(Cpu::regB)), // 47
            idle(3) then (loadRegister(Cpu::regB) into writeRegister(Cpu::regC)), // 48
            idle(3) then (loadRegister(Cpu::regC) into writeRegister(Cpu::regC)), // 49
            idle(3) then (loadRegister(Cpu::regD) into writeRegister(Cpu::regC)), // 4a
            idle(3) then (loadRegister(Cpu::regE) into writeRegister(Cpu::regC)), // 4b
            idle(3) then (loadRegister(Cpu::regH) into writeRegister(Cpu::regC)), // 4c
            idle(3) then (loadRegister(Cpu::regL) into writeRegister(Cpu::regC)), // 4d
            idle(7) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regC)), // 4e
            idle(3) then (loadRegister(Cpu::regA) into writeRegister(Cpu::regC)), // 4f

            idle(3) then (loadRegister(Cpu::regB) into writeRegister(Cpu::regD)), // 50
            idle(3) then (loadRegister(Cpu::regC) into writeRegister(Cpu::regD)), // 51
            idle(3) then (loadRegister(Cpu::regD) into writeRegister(Cpu::regD)), // 52
            idle(3) then (loadRegister(Cpu::regE) into writeRegister(Cpu::regD)), // 53
            idle(3) then (loadRegister(Cpu::regH) into writeRegister(Cpu::regD)), // 54
            idle(3) then (loadRegister(Cpu::regL) into writeRegister(Cpu::regD)), // 55
            idle(7) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regD)), // 56
            idle(3) then (loadRegister(Cpu::regA) into writeRegister(Cpu::regD)), // 57
            idle(3) then (loadRegister(Cpu::regB) into writeRegister(Cpu::regE)), // 58
            idle(3) then (loadRegister(Cpu::regC) into writeRegister(Cpu::regE)), // 59
            idle(3) then (loadRegister(Cpu::regD) into writeRegister(Cpu::regE)), // 5a
            idle(3) then (loadRegister(Cpu::regE) into writeRegister(Cpu::regE)), // 5b
            idle(3) then (loadRegister(Cpu::regH) into writeRegister(Cpu::regE)), // 5c
            idle(3) then (loadRegister(Cpu::regL) into writeRegister(Cpu::regE)), // 5d
            idle(7) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regE)), // 5e
            idle(3) then (loadRegister(Cpu::regA) into writeRegister(Cpu::regE)), // 5f

            idle(3) then (loadRegister(Cpu::regB) into writeRegister(Cpu::regH)), // 60
            idle(3) then (loadRegister(Cpu::regC) into writeRegister(Cpu::regH)), // 61
            idle(3) then (loadRegister(Cpu::regD) into writeRegister(Cpu::regH)), // 62
            idle(3) then (loadRegister(Cpu::regE) into writeRegister(Cpu::regH)), // 63
            idle(3) then (loadRegister(Cpu::regH) into writeRegister(Cpu::regH)), // 64
            idle(3) then (loadRegister(Cpu::regL) into writeRegister(Cpu::regH)), // 65
            idle(7) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regH)), // 66
            idle(3) then (loadRegister(Cpu::regA) into writeRegister(Cpu::regH)), // 67
            idle(3) then (loadRegister(Cpu::regB) into writeRegister(Cpu::regL)), // 68
            idle(3) then (loadRegister(Cpu::regC) into writeRegister(Cpu::regL)), // 69
            idle(3) then (loadRegister(Cpu::regD) into writeRegister(Cpu::regL)), // 6a
            idle(3) then (loadRegister(Cpu::regE) into writeRegister(Cpu::regL)), // 6b
            idle(3) then (loadRegister(Cpu::regH) into writeRegister(Cpu::regL)), // 6c
            idle(3) then (loadRegister(Cpu::regL) into writeRegister(Cpu::regL)), // 6d
            idle(7) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regL)), // 6e
            idle(3) then (loadRegister(Cpu::regA) into writeRegister(Cpu::regL)), // 6f

            idle(7) then (loadRegister(Cpu::regB) into writePointer(Cpu::regHL)), // 70
            idle(7) then (loadRegister(Cpu::regC) into writePointer(Cpu::regHL)), // 71
            idle(7) then (loadRegister(Cpu::regD) into writePointer(Cpu::regHL)), // 72
            idle(7) then (loadRegister(Cpu::regE) into writePointer(Cpu::regHL)), // 73
            idle(7) then (loadRegister(Cpu::regH) into writePointer(Cpu::regHL)), // 74
            idle(7) then (loadRegister(Cpu::regL) into writePointer(Cpu::regHL)), // 75
            { it.trace(); it.advance(); it.halt(); it.idle(3); }, // 76
            idle(7) then (loadRegister(Cpu::regA) into writePointer(Cpu::regHL)), // 77
            idle(3) then (loadRegister(Cpu::regB) into writeRegister(Cpu::regA)), // 78
            idle(3) then (loadRegister(Cpu::regC) into writeRegister(Cpu::regA)), // 79
            idle(3) then (loadRegister(Cpu::regD) into writeRegister(Cpu::regA)), // 7a
            idle(3) then (loadRegister(Cpu::regE) into writeRegister(Cpu::regA)), // 7b
            idle(3) then (loadRegister(Cpu::regH) into writeRegister(Cpu::regA)), // 7c
            idle(3) then (loadRegister(Cpu::regL) into writeRegister(Cpu::regA)), // 7d
            idle(7) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regA)), // 7e
            idle(3) then (loadRegister(Cpu::regA) into writeRegister(Cpu::regA)), // 7f

            idle(3) then (loadRegister8AsInt(Cpu::regB) into incRegister8(Cpu::regA)), // 80
            idle(3) then (loadRegister8AsInt(Cpu::regC) into incRegister8(Cpu::regA)), // 81
            idle(3) then (loadRegister8AsInt(Cpu::regD) into incRegister8(Cpu::regA)), // 82
            idle(3) then (loadRegister8AsInt(Cpu::regE) into incRegister8(Cpu::regA)), // 83
            idle(3) then (loadRegister8AsInt(Cpu::regH) into incRegister8(Cpu::regA)), // 84
            idle(3) then (loadRegister8AsInt(Cpu::regL) into incRegister8(Cpu::regA)), // 85
            idle(7) then (loadPointerAsInt(Cpu::regHL) into incRegister8(Cpu::regA)), // 86
            idle(3) then (loadRegister8AsInt(Cpu::regA) into incRegister8(Cpu::regA)), // 87
            idle(3) then (loadRegister8AsInt(Cpu::regB) into adcRegister8(Cpu::regA)), // 88
            idle(3) then (loadRegister8AsInt(Cpu::regC) into adcRegister8(Cpu::regA)), // 89
            idle(3) then (loadRegister8AsInt(Cpu::regD) into adcRegister8(Cpu::regA)), // 8a
            idle(3) then (loadRegister8AsInt(Cpu::regE) into adcRegister8(Cpu::regA)), // 8b
            idle(3) then (loadRegister8AsInt(Cpu::regH) into adcRegister8(Cpu::regA)), // 8c
            idle(3) then (loadRegister8AsInt(Cpu::regL) into adcRegister8(Cpu::regA)), // 8d
            idle(7) then (loadPointerAsInt(Cpu::regHL) into adcRegister8(Cpu::regA)), // 8e
            idle(3) then (loadRegister8AsInt(Cpu::regA) into adcRegister8(Cpu::regA)), // 8f

            idle(3) then (loadRegister8AsInt(Cpu::regB) into decRegister8(Cpu::regA)), // 90
            idle(3) then (loadRegister8AsInt(Cpu::regC) into decRegister8(Cpu::regA)), // 91
            idle(3) then (loadRegister8AsInt(Cpu::regD) into decRegister8(Cpu::regA)), // 92
            idle(3) then (loadRegister8AsInt(Cpu::regE) into decRegister8(Cpu::regA)), // 93
            idle(3) then (loadRegister8AsInt(Cpu::regH) into decRegister8(Cpu::regA)), // 94
            idle(3) then (loadRegister8AsInt(Cpu::regL) into decRegister8(Cpu::regA)), // 95
            idle(7) then (loadPointerAsInt(Cpu::regHL) into decRegister8(Cpu::regA)), // 96
            idle(3) then (loadRegister8AsInt(Cpu::regA) into decRegister8(Cpu::regA)), // 97
            idle(3) then (loadRegister8AsInt(Cpu::regB) into sbcRegister8(Cpu::regA)), // 98
            idle(3) then (loadRegister8AsInt(Cpu::regC) into sbcRegister8(Cpu::regA)), // 99
            idle(3) then (loadRegister8AsInt(Cpu::regD) into sbcRegister8(Cpu::regA)), // 9a
            idle(3) then (loadRegister8AsInt(Cpu::regE) into sbcRegister8(Cpu::regA)), // 9b
            idle(3) then (loadRegister8AsInt(Cpu::regH) into sbcRegister8(Cpu::regA)), // 9c
            idle(3) then (loadRegister8AsInt(Cpu::regL) into sbcRegister8(Cpu::regA)), // 9d
            idle(7) then (loadPointerAsInt(Cpu::regHL) into sbcRegister8(Cpu::regA)), // 9e
            idle(3) then (loadRegister8AsInt(Cpu::regA) into sbcRegister8(Cpu::regA)), // 9f

            idle(3) then akkuAnd(loadRegister(Cpu::regB)), // a0
            idle(3) then akkuAnd(loadRegister(Cpu::regC)), // a1
            idle(3) then akkuAnd(loadRegister(Cpu::regD)), // a2
            idle(3) then akkuAnd(loadRegister(Cpu::regE)), // a3
            idle(3) then akkuAnd(loadRegister(Cpu::regH)), // a4
            idle(3) then akkuAnd(loadRegister(Cpu::regL)), // a5
            idle(7) then akkuAnd(loadPointer(Cpu::regHL)), // a6
            idle(3) then akkuAnd(loadRegister(Cpu::regA)), // a7
            idle(3) then akkuXor(loadRegister(Cpu::regB)), // a8
            idle(3) then akkuXor(loadRegister(Cpu::regC)), // a9
            idle(3) then akkuXor(loadRegister(Cpu::regD)), // aa
            idle(3) then akkuXor(loadRegister(Cpu::regE)), // ab
            idle(3) then akkuXor(loadRegister(Cpu::regH)), // ac
            idle(3) then akkuXor(loadRegister(Cpu::regL)), // ad
            idle(7) then akkuXor(loadPointer(Cpu::regHL)), // ae
            idle(3) then akkuXor(loadRegister(Cpu::regA)), // af

            idle(3) then akkuOr(loadRegister(Cpu::regB)), // b0
            idle(3) then akkuOr(loadRegister(Cpu::regC)), // b1
            idle(3) then akkuOr(loadRegister(Cpu::regD)), // b2
            idle(3) then akkuOr(loadRegister(Cpu::regE)), // b3
            idle(3) then akkuOr(loadRegister(Cpu::regH)), // b4
            idle(3) then akkuOr(loadRegister(Cpu::regL)), // b5
            idle(7) then akkuOr(loadPointer(Cpu::regHL)), // b6
            idle(3) then akkuOr(loadRegister(Cpu::regA)), // b7
            idle(3) then akkuCp(loadRegister(Cpu::regB)), // b8
            idle(3) then akkuCp(loadRegister(Cpu::regC)), // b9
            idle(3) then akkuCp(loadRegister(Cpu::regD)), // ba
            idle(3) then akkuCp(loadRegister(Cpu::regE)), // bb
            idle(3) then akkuCp(loadRegister(Cpu::regH)), // bc
            idle(3) then akkuCp(loadRegister(Cpu::regL)), // bd
            idle(7) then akkuCp(loadPointer(Cpu::regHL)), // be
            idle(3) then akkuCp(loadRegister(Cpu::regA)), // bf

            predicate(nonZero,
                    idle(19) then stackReturn,
                    idle(7) then (trace also advance())), // c0
            idle(11) then stackPop(Cpu::regBC), // c1
            predicate(nonZero,
                    idle(15) then jumpAbsolute(loadHardShort),
                    idle(11) then (trace also advance(3))), // c2
            idle(15) then jumpAbsolute(loadHardShort), // c3
            predicate(nonZero,
                    idle(23) then stackCall(loadHardShort),
                    idle(11) then (trace also advance(3))), // c4
            idle(15) then stackPush(Cpu::regBC), // c5
            idle(7) then (loadHardByteAsInt into incRegister8(Cpu::regA)), // c6
            idle(15) then stackCall({ 0x00 }), // c7
            predicate(isZero,
                    idle(19) then stackReturn,
                    idle(7) then (trace also advance())), // c8
            idle(15) then stackReturn, // c9
            predicate(isZero,
                    idle(15) then jumpAbsolute(loadHardShort),
                    idle(11) then (trace also advance(3))), // ca
            doCbPrefixedOpcode, // cb
            predicate(isZero,
                    idle(23) then stackCall(loadHardShort),
                    idle(11) then (trace also advance(3))), // cc
            idle(23) then stackCall(loadHardShort), // cd
            idle(7) then (loadHardByteAsInt into adcRegister8(Cpu::regA)), // ce
            idle(15) then stackCall({ 0x08 }), // cf

            predicate(nonCarry,
                    idle(19) then stackReturn,
                    idle(7) then (trace also advance())), // d0
            idle(11) then stackPop(Cpu::regDE), // d1
            predicate(nonCarry,
                    idle(15) then jumpAbsolute(loadHardShort),
                    idle(11) then (trace also advance(3))), // d2
            unknownOpcode(0xD3), // d3
            predicate(nonCarry,
                    idle(23) then stackCall(loadHardShort),
                    idle(11) then (trace also advance(3))), // d4
            idle(15) then stackPush(Cpu::regDE), // d5
            idle(7) then (loadHardByteAsInt into decRegister8(Cpu::regA)), // d6
            idle(15) then stackCall({ 0x10 }), // d7
            predicate(isCarry,
                    idle(19) then stackReturn,
                    idle(7) then (trace also advance())), // d8
            idle(15) then (stackReturn also { it.flagIME = true; }), // d9
            predicate(isCarry,
                    idle(15) then jumpAbsolute(loadHardShort),
                    idle(11) then (trace also advance(3))), // da
            unknownOpcode(0xDB), // db
            predicate(isCarry,
                    idle(23) then stackCall(loadHardShort),
                    idle(11) then (trace also advance(3))), // dc
            unknownOpcode(0xDD), // dd
            idle(7) then (loadHardByteAsInt into sbcRegister8(Cpu::regA)), // de
            idle(15) then stackCall({ 0x18 }), // df

            idle(11) then (loadRegister(Cpu::regA) into writeHardHighAddress), // e0
            idle(11) then stackPop(Cpu::regHL), // e1
            idle(7) then (loadRegister(Cpu::regA) into writeHighPointer(Cpu::regC)), // e2
            unknownOpcode(0xE3), // e3
            unknownOpcode(0xE4), // e4
            idle(15) then stackPush(Cpu::regHL), // e5
            idle(7) then akkuAnd(loadHardByte), // e6
            idle(15) then stackCall({ 0x20 }), // e7
            idle(15) then offsetStackPointer, // e8
            idle(3) then jumpAbsolute(loadRegister(Cpu::regHL)), // e9
            idle(15) then (loadRegister(Cpu::regA) into writeHardAddress8), // ea
            unknownOpcode(0xEB), // eb
            unknownOpcode(0xEC), // ec
            unknownOpcode(0xED), // ed
            idle(7) then akkuXor(loadHardByte), // ee
            idle(15) then stackCall({ 0x28 }), // ef

            idle(11) then (loadHardHighAddress into writeRegister(Cpu::regA)), // f0
            idle(11) then stackPop(Cpu::regAF), // f1
            idle(7) then (loadHighPointer(Cpu::regC) into writeRegister(Cpu::regA)), // f2
            idle(3) then imeOff, // f3
            unknownOpcode(0xF4), // f4
            idle(15) then stackPush(Cpu::regAF), // f5
            idle(7) then akkuOr(loadHardByte), // f6
            idle(15) then stackCall({ 0x30 }), // f7
            idle(11) then loadHlWithOffsetStackPointer, // f8
            idle(7) then (loadRegister(Cpu::regHL) into writeRegister(Cpu::regSP)), // f9
            idle(15) then (loadHardAddress into writeRegister(Cpu::regA)), // fa
            idle(3) then imeOn, // fb
            unknownOpcode(0xFC), // fc
            unknownOpcode(0xFD), // fd
            idle(7) then akkuCp(loadHardByte), // fe
            idle(15) then stackCall({ 0x38 }) // ff
    )

    operator fun get(opcode: Byte): Insn = opcodeTable[opcode.toUnsignedInt()]

}