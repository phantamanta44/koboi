package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.toUnsignedInt

object Opcodes {

    private val opcodeTable: Array<Insn> = arrayOf(
            advance() then idle(3), // 00
            idle(8) then (loadHardShort into writeRegister(Cpu::regBC)) then idle(3), // 01
            idle(4) then (loadRegister(Cpu::regA) into writePointer(Cpu::regBC)) then idle(3), // 02
            idle(4) then increment16(Cpu::regBC) then idle(3), // 03
            increment8(Cpu::regB) then idle(3), // 04
            decrement8(Cpu::regB) then idle(3), // 05
            idle(4) then (loadHardByte into writeRegister(Cpu::regB)) then idle(3), // 06
            rotateLeft(Cpu::regA) then idle(3), // 07
            idle(16) then (loadRegister(Cpu::regSP) into writeHardAddress16) then idle(3), // 08
            idle(4) then (loadRegister16AsInt(Cpu::regBC) into incRegister16(Cpu::regHL)) then idle(3), // 09
            idle(4) then (loadPointer(Cpu::regBC) into writeRegister(Cpu::regA)) then idle(3), // 0a
            idle(4) then decrement16(Cpu::regBC) then idle(3), // 0b
            increment8(Cpu::regC) then idle(3), // 0c
            decrement8(Cpu::regC) then idle(3), // 0d
            idle(4) then (loadHardByte into writeRegister(Cpu::regC)) then idle(3), // 0e
            rotateRight(Cpu::regA) then idle(3), // 0f

            { it.advance(2); it.stop(); it.idle(3); }, // 10
            idle(8) then (loadHardShort into writeRegister(Cpu::regDE)) then idle(3), // 11
            idle(4) then (loadRegister(Cpu::regA) into writePointer(Cpu::regDE)) then idle(3), // 12
            idle(4) then increment16(Cpu::regDE) then idle(3), // 13
            increment8(Cpu::regD) then idle(3), // 14
            decrement8(Cpu::regD) then idle(3), // 15
            idle(4) then (loadHardByte into writeRegister(Cpu::regD)) then idle(3), // 16
            rotateLeftThroughCarry(Cpu::regA) then idle(3), // 17
            idle(8) then jumpRelative then idle(3), // 18
            idle(4) then (loadRegister16AsInt(Cpu::regDE) into incRegister16(Cpu::regHL)) then idle(3), // 19
            idle(4) then (loadPointer(Cpu::regDE) into writeRegister(Cpu::regA)) then idle(3), // 1a
            idle(4) then decrement16(Cpu::regDE) then idle(3), // 1b
            increment8(Cpu::regE) then idle(3), // 1c
            decrement8(Cpu::regE) then idle(3), // 1d
            idle(4) then (loadHardByte into writeRegister(Cpu::regE)) then idle(3), // 1e
            rotateRightThroughCarry(Cpu::regA) then idle(3), // 1f

            predicate(nonZero,
                    idle(8) then jumpRelative then idle(3),
                    idle(4) then advance(2) then idle(3)), // 20
            idle(8) then (loadHardShort into writeRegister(Cpu::regHL)) then idle(3), // 21
            idle(4) then (loadRegister(Cpu::regA) into writeHLPointerThenAdvance) then idle(3), // 22
            idle(4) then increment16(Cpu::regHL) then idle(3), // 23
            increment8(Cpu::regH) then idle(3), // 24
            decrement8(Cpu::regH) then idle(3), // 25
            idle(4) then (loadHardByte into writeRegister(Cpu::regH)) then idle(3), // 26
            daa then idle(3), // 27
            predicate(isZero,
                    idle(8) then jumpRelative then idle(3),
                    idle(4) then advance(2) then idle(3)), // 28
            idle(4) then (loadRegister16AsInt(Cpu::regHL) into incRegister16(Cpu::regHL)) then idle(3), // 29
            idle(4) then (loadHLPointerThenAdvance into writeRegister(Cpu::regA)) then idle(3), // 2a
            idle(4) then decrement16(Cpu::regHL) then idle(3), // 2b
            increment8(Cpu::regL) then idle(3), // 2c
            decrement8(Cpu::regL) then idle(3), // 2d
            idle(4) then (loadHardByte into writeRegister(Cpu::regL)) then idle(3), // 2e
            akkuCpl then idle(3), // 2f

            predicate(nonCarry,
                    idle(8) then jumpRelative then idle(3),
                    idle(4) then advance(2) then idle(3)), // 30
            idle(8) then (loadHardShort into writeRegister(Cpu::regSP)) then idle(3), // 31
            idle(4) then (loadRegister(Cpu::regA) into writeHLPointerThenBacktrack) then idle(3), // 32
            idle(4) then increment16(Cpu::regSP) then idle(3), // 33
            idle(8) then incrementPointer(Cpu::regHL) then idle(3), // 34
            idle(8) then decrementPointer(Cpu::regHL) then idle(3), // 35
            idle(8) then (loadHardByte into writePointer(Cpu::regHL)) then idle(3), // 36
            scf then idle(3), // 37
            predicate(isCarry,
                    idle(8) then jumpRelative then idle(3),
                    idle(4) then advance(2) then idle(3)), // 38
            idle(4) then (loadRegister16AsInt(Cpu::regSP) into incRegister16(Cpu::regHL)) then idle(3), // 39
            idle(4) then (loadHLPointerThenBacktrack into writeRegister(Cpu::regA)) then idle(3), // 3a
            idle(4) then decrement16(Cpu::regSP) then idle(3), // 3b
            increment8(Cpu::regA) then idle(3), // 3c
            decrement8(Cpu::regA) then idle(3), // 3d
            idle(4) then (loadHardByte into writeRegister(Cpu::regA)) then idle(3), // 3e
            ccf then idle(3), // 3f

            (loadRegister(Cpu::regB) into writeRegister(Cpu::regB)) then idle(3), // 40
            (loadRegister(Cpu::regC) into writeRegister(Cpu::regB)) then idle(3), // 41
            (loadRegister(Cpu::regD) into writeRegister(Cpu::regB)) then idle(3), // 42
            (loadRegister(Cpu::regE) into writeRegister(Cpu::regB)) then idle(3), // 43
            (loadRegister(Cpu::regH) into writeRegister(Cpu::regB)) then idle(3), // 44
            (loadRegister(Cpu::regL) into writeRegister(Cpu::regB)) then idle(3), // 45
            idle(4) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regB)) then idle(3), // 46
            (loadRegister(Cpu::regA) into writeRegister(Cpu::regB)) then idle(3), // 47
            (loadRegister(Cpu::regB) into writeRegister(Cpu::regC)) then idle(3), // 48
            (loadRegister(Cpu::regC) into writeRegister(Cpu::regC)) then idle(3), // 49
            (loadRegister(Cpu::regD) into writeRegister(Cpu::regC)) then idle(3), // 4a
            (loadRegister(Cpu::regE) into writeRegister(Cpu::regC)) then idle(3), // 4b
            (loadRegister(Cpu::regH) into writeRegister(Cpu::regC)) then idle(3), // 4c
            (loadRegister(Cpu::regL) into writeRegister(Cpu::regC)) then idle(3), // 4d
            idle(4) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regC)) then idle(3), // 4e
            (loadRegister(Cpu::regA) into writeRegister(Cpu::regC)) then idle(3), // 4f

            (loadRegister(Cpu::regB) into writeRegister(Cpu::regD)) then idle(3), // 50
            (loadRegister(Cpu::regC) into writeRegister(Cpu::regD)) then idle(3), // 51
            (loadRegister(Cpu::regD) into writeRegister(Cpu::regD)) then idle(3), // 52
            (loadRegister(Cpu::regE) into writeRegister(Cpu::regD)) then idle(3), // 53
            (loadRegister(Cpu::regH) into writeRegister(Cpu::regD)) then idle(3), // 54
            (loadRegister(Cpu::regL) into writeRegister(Cpu::regD)) then idle(3), // 55
            idle(4) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regD)) then idle(3), // 56
            (loadRegister(Cpu::regA) into writeRegister(Cpu::regD)) then idle(3), // 57
            (loadRegister(Cpu::regB) into writeRegister(Cpu::regE)) then idle(3), // 58
            (loadRegister(Cpu::regC) into writeRegister(Cpu::regE)) then idle(3), // 59
            (loadRegister(Cpu::regD) into writeRegister(Cpu::regE)) then idle(3), // 5a
            (loadRegister(Cpu::regE) into writeRegister(Cpu::regE)) then idle(3), // 5b
            (loadRegister(Cpu::regH) into writeRegister(Cpu::regE)) then idle(3), // 5c
            (loadRegister(Cpu::regL) into writeRegister(Cpu::regE)) then idle(3), // 5d
            idle(4) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regE)) then idle(3), // 5e
            (loadRegister(Cpu::regA) into writeRegister(Cpu::regE)) then idle(3), // 5f

            (loadRegister(Cpu::regB) into writeRegister(Cpu::regH)) then idle(3), // 60
            (loadRegister(Cpu::regC) into writeRegister(Cpu::regH)) then idle(3), // 61
            (loadRegister(Cpu::regD) into writeRegister(Cpu::regH)) then idle(3), // 62
            (loadRegister(Cpu::regE) into writeRegister(Cpu::regH)) then idle(3), // 63
            (loadRegister(Cpu::regH) into writeRegister(Cpu::regH)) then idle(3), // 64
            (loadRegister(Cpu::regL) into writeRegister(Cpu::regH)) then idle(3), // 65
            idle(4) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regH)) then idle(3), // 66
            (loadRegister(Cpu::regA) into writeRegister(Cpu::regH)) then idle(3), // 67
            (loadRegister(Cpu::regB) into writeRegister(Cpu::regL)) then idle(3), // 68
            (loadRegister(Cpu::regC) into writeRegister(Cpu::regL)) then idle(3), // 69
            (loadRegister(Cpu::regD) into writeRegister(Cpu::regL)) then idle(3), // 6a
            (loadRegister(Cpu::regE) into writeRegister(Cpu::regL)) then idle(3), // 6b
            (loadRegister(Cpu::regH) into writeRegister(Cpu::regL)) then idle(3), // 6c
            (loadRegister(Cpu::regL) into writeRegister(Cpu::regL)) then idle(3), // 6d
            idle(4) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regL)) then idle(3), // 6e
            (loadRegister(Cpu::regA) into writeRegister(Cpu::regL)) then idle(3), // 6f

            idle(4) then (loadRegister(Cpu::regB) into writePointer(Cpu::regHL)) then idle(3), // 70
            idle(4) then (loadRegister(Cpu::regC) into writePointer(Cpu::regHL)) then idle(3), // 71
            idle(4) then (loadRegister(Cpu::regD) into writePointer(Cpu::regHL)) then idle(3), // 72
            idle(4) then (loadRegister(Cpu::regE) into writePointer(Cpu::regHL)) then idle(3), // 73
            idle(4) then (loadRegister(Cpu::regH) into writePointer(Cpu::regHL)) then idle(3), // 74
            idle(4) then (loadRegister(Cpu::regL) into writePointer(Cpu::regHL)) then idle(3), // 75
            { it.advance(); it.halt(); it.idle(3); }, // 76
            idle(4) then (loadRegister(Cpu::regA) into writePointer(Cpu::regHL)) then idle(3), // 77
            (loadRegister(Cpu::regB) into writeRegister(Cpu::regA)) then idle(3), // 78
            (loadRegister(Cpu::regC) into writeRegister(Cpu::regA)) then idle(3), // 79
            (loadRegister(Cpu::regD) into writeRegister(Cpu::regA)) then idle(3), // 7a
            (loadRegister(Cpu::regE) into writeRegister(Cpu::regA)) then idle(3), // 7b
            (loadRegister(Cpu::regH) into writeRegister(Cpu::regA)) then idle(3), // 7c
            (loadRegister(Cpu::regL) into writeRegister(Cpu::regA)) then idle(3), // 7d
            idle(4) then (loadPointer(Cpu::regHL) into writeRegister(Cpu::regA)) then idle(3), // 7e
            (loadRegister(Cpu::regA) into writeRegister(Cpu::regA)) then idle(3), // 7f

            (loadRegister8AsInt(Cpu::regB) into incRegister8(Cpu::regA)) then idle(3), // 80
            (loadRegister8AsInt(Cpu::regC) into incRegister8(Cpu::regA)) then idle(3), // 81
            (loadRegister8AsInt(Cpu::regD) into incRegister8(Cpu::regA)) then idle(3), // 82
            (loadRegister8AsInt(Cpu::regE) into incRegister8(Cpu::regA)) then idle(3), // 83
            (loadRegister8AsInt(Cpu::regH) into incRegister8(Cpu::regA)) then idle(3), // 84
            (loadRegister8AsInt(Cpu::regL) into incRegister8(Cpu::regA)) then idle(3), // 85
            idle(4) then (loadPointerAsInt(Cpu::regHL) into incRegister8(Cpu::regA)) then idle(3), // 86
            (loadRegister8AsInt(Cpu::regA) into incRegister8(Cpu::regA)) then idle(3), // 87
            (loadRegister8AsInt(Cpu::regB) into adcRegister8(Cpu::regA)) then idle(3), // 88
            (loadRegister8AsInt(Cpu::regC) into adcRegister8(Cpu::regA)) then idle(3), // 89
            (loadRegister8AsInt(Cpu::regD) into adcRegister8(Cpu::regA)) then idle(3), // 8a
            (loadRegister8AsInt(Cpu::regE) into adcRegister8(Cpu::regA)) then idle(3), // 8b
            (loadRegister8AsInt(Cpu::regH) into adcRegister8(Cpu::regA)) then idle(3), // 8c
            (loadRegister8AsInt(Cpu::regL) into adcRegister8(Cpu::regA)) then idle(3), // 8d
            idle(4) then (loadPointerAsInt(Cpu::regHL) into adcRegister8(Cpu::regA)) then idle(3), // 8e
            (loadRegister8AsInt(Cpu::regA) into adcRegister8(Cpu::regA)) then idle(3), // 8f

            (loadRegister8AsInt(Cpu::regB) into decRegister8(Cpu::regA)) then idle(3), // 90
            (loadRegister8AsInt(Cpu::regC) into decRegister8(Cpu::regA)) then idle(3), // 91
            (loadRegister8AsInt(Cpu::regD) into decRegister8(Cpu::regA)) then idle(3), // 92
            (loadRegister8AsInt(Cpu::regE) into decRegister8(Cpu::regA)) then idle(3), // 93
            (loadRegister8AsInt(Cpu::regH) into decRegister8(Cpu::regA)) then idle(3), // 94
            (loadRegister8AsInt(Cpu::regL) into decRegister8(Cpu::regA)) then idle(3), // 95
            idle(4) then (loadPointerAsInt(Cpu::regHL) into decRegister8(Cpu::regA)) then idle(3), // 96
            (loadRegister8AsInt(Cpu::regA) into decRegister8(Cpu::regA)) then idle(3), // 97
            (loadRegister8AsInt(Cpu::regB) into sbcRegister8(Cpu::regA)) then idle(3), // 98
            (loadRegister8AsInt(Cpu::regC) into sbcRegister8(Cpu::regA)) then idle(3), // 99
            (loadRegister8AsInt(Cpu::regD) into sbcRegister8(Cpu::regA)) then idle(3), // 9a
            (loadRegister8AsInt(Cpu::regE) into sbcRegister8(Cpu::regA)) then idle(3), // 9b
            (loadRegister8AsInt(Cpu::regH) into sbcRegister8(Cpu::regA)) then idle(3), // 9c
            (loadRegister8AsInt(Cpu::regL) into sbcRegister8(Cpu::regA)) then idle(3), // 9d
            idle(4) then (loadPointerAsInt(Cpu::regHL) into sbcRegister8(Cpu::regA)) then idle(3), // 9e
            (loadRegister8AsInt(Cpu::regA) into sbcRegister8(Cpu::regA)) then idle(3), // 9f

            akkuAnd(loadRegister(Cpu::regB)) then idle(3), // a0
            akkuAnd(loadRegister(Cpu::regC)) then idle(3), // a1
            akkuAnd(loadRegister(Cpu::regD)) then idle(3), // a2
            akkuAnd(loadRegister(Cpu::regE)) then idle(3), // a3
            akkuAnd(loadRegister(Cpu::regH)) then idle(3), // a4
            akkuAnd(loadRegister(Cpu::regL)) then idle(3), // a5
            idle(4) then akkuAnd(loadPointer(Cpu::regHL)) then idle(3), // a6
            akkuAnd(loadRegister(Cpu::regA)) then idle(3), // a7
            akkuXor(loadRegister(Cpu::regB)) then idle(3), // a8
            akkuXor(loadRegister(Cpu::regC)) then idle(3), // a9
            akkuXor(loadRegister(Cpu::regD)) then idle(3), // aa
            akkuXor(loadRegister(Cpu::regE)) then idle(3), // ab
            akkuXor(loadRegister(Cpu::regH)) then idle(3), // ac
            akkuXor(loadRegister(Cpu::regL)) then idle(3), // ad
            idle(4) then akkuXor(loadPointer(Cpu::regHL)) then idle(3), // ae
            akkuXor(loadRegister(Cpu::regA)) then idle(3), // af

            akkuOr(loadRegister(Cpu::regB)) then idle(3), // b0
            akkuOr(loadRegister(Cpu::regC)) then idle(3), // b1
            akkuOr(loadRegister(Cpu::regD)) then idle(3), // b2
            akkuOr(loadRegister(Cpu::regE)) then idle(3), // b3
            akkuOr(loadRegister(Cpu::regH)) then idle(3), // b4
            akkuOr(loadRegister(Cpu::regL)) then idle(3), // b5
            idle(4) then akkuOr(loadPointer(Cpu::regHL)) then idle(3), // b6
            akkuOr(loadRegister(Cpu::regA)) then idle(3), // b7
            akkuCp(loadRegister(Cpu::regB)) then idle(3), // b8
            akkuCp(loadRegister(Cpu::regC)) then idle(3), // b9
            akkuCp(loadRegister(Cpu::regD)) then idle(3), // ba
            akkuCp(loadRegister(Cpu::regE)) then idle(3), // bb
            akkuCp(loadRegister(Cpu::regH)) then idle(3), // bc
            akkuCp(loadRegister(Cpu::regL)) then idle(3), // bd
            idle(4) then akkuCp(loadPointer(Cpu::regHL)) then idle(3), // be
            akkuCp(loadRegister(Cpu::regA)) then idle(3), // bf

            predicate(nonZero,
                    idle(16) then stackReturn then idle(3),
                    idle(4) then advance()) then idle(3), // c0
            idle(8) then stackPop(Cpu::regBC) then idle(3), // c1
            predicate(nonZero,
                    idle(12) then jumpAbsolute(loadHardShort) then idle(3),
                    idle(8) then advance(3) then idle(3)), // c2
            idle(12) then jumpAbsolute(loadHardShort) then idle(3), // c3
            predicate(nonZero,
                    idle(20) then stackCall(loadHardShort) then idle(3),
                    idle(8) then advance(3) then idle(3)), // c4
            idle(12) then stackPush(Cpu::regBC) then idle(3), // c5
            idle(4) then (loadHardByteAsInt into incRegister8(Cpu::regA)) then idle(3), // c6
            idle(12) then stackCall({ 0x00 }) then idle(3), // c7
            predicate(isZero,
                    idle(16) then stackReturn then idle(3),
                    idle(4) then advance()) then idle(3), // c8
            idle(12) then stackReturn then idle(3), // c9
            predicate(isZero,
                    idle(12) then jumpAbsolute(loadHardShort) then idle(3),
                    idle(8) then advance(3) then idle(3)), // ca
            doCbPrefixedOpcode then idle(3), // cb
            predicate(isZero,
                    idle(20) then stackCall(loadHardShort) then idle(3),
                    idle(8) then advance(3) then idle(3)), // cc
            idle(20) then stackCall(loadHardShort) then idle(3), // cd
            idle(4) then (loadHardByteAsInt into adcRegister8(Cpu::regA)) then idle(3), // ce
            idle(12) then stackCall({ 0x08 }) then idle(3), // cf

            predicate(nonCarry,
                    idle(16) then stackReturn then idle(3),
                    idle(4) then advance()) then idle(3), // d0
            idle(8) then stackPop(Cpu::regDE) then idle(3), // d1
            predicate(nonCarry,
                    idle(12) then jumpAbsolute(loadHardShort) then idle(3),
                    idle(8) then advance(3) then idle(3)), // d2
            unknownOpcode(0xD3), // d3
            predicate(nonCarry,
                    idle(20) then stackCall(loadHardShort) then idle(3),
                    idle(8) then advance(3) then idle(3)), // d4
            idle(12) then stackPush(Cpu::regDE) then idle(3), // d5
            idle(4) then (loadHardByteAsInt into decRegister8(Cpu::regA)) then idle(3), // d6
            idle(12) then stackCall({ 0x10 }) then idle(3), // d7
            predicate(isCarry,
                    idle(16) then stackReturn then idle(3),
                    idle(4) then advance()) then idle(3), // d8
            idle(12) then stackReturn then { it.flagIME = true; } then idle(3), // d9
            predicate(isCarry,
                    idle(12) then jumpAbsolute(loadHardShort) then idle(3),
                    idle(8) then advance(3) then idle(3)), // da
            unknownOpcode(0xDB), // db
            predicate(isCarry,
                    idle(20) then stackCall(loadHardShort) then idle(3),
                    idle(8) then advance(3) then idle(3)), // dc
            unknownOpcode(0xDD), // dd
            idle(4) then (loadHardByteAsInt into sbcRegister8(Cpu::regA)) then idle(3), // de
            idle(12) then stackCall({ 0x18 }) then idle(3), // df

            idle(8) then (loadRegister(Cpu::regA) into writeHardHighAddress) then idle(3), // e0
            idle(8) then stackPop(Cpu::regHL) then idle(3), // e1
            idle(4) then (loadRegister(Cpu::regA) into writeHighPointer(Cpu::regC)) then idle(3), // e2
            unknownOpcode(0xE3), // e3
            unknownOpcode(0xE4), // e4
            idle(12) then stackPush(Cpu::regHL) then idle(3), // e5
            idle(4) then akkuAnd(loadHardByte) then idle(3), // e6
            idle(12) then stackCall({ 0x20 }) then idle(3), // e7
            idle(12) then offsetStackPointer then idle(3), // e8
            jumpAbsolute(loadRegister(Cpu::regHL)) then idle(3), // e9
            idle(12) then (loadRegister(Cpu::regA) into writeHardAddress8) then idle(3), // ea
            unknownOpcode(0xEB), // eb
            unknownOpcode(0xEC), // ec
            unknownOpcode(0xED), // ed
            idle(4) then akkuXor(loadHardByte) then idle(3), // ee
            idle(12) then stackCall({ 0x28 }) then idle(3), // ef

            idle(8) then (loadHardHighAddress into writeRegister(Cpu::regA)) then idle(3), // f0
            idle(8) then stackPop(Cpu::regAF) then idle(3), // f1
            idle(4) then (loadHighPointer(Cpu::regC) into writeRegister(Cpu::regA)) then idle(3), // f2
            imeOff then idle(3), // f3
            unknownOpcode(0xF4), // f4
            idle(12) then stackPush(Cpu::regAF) then idle(3), // f5
            idle(4) then akkuOr(loadHardByte) then idle(3), // f6
            idle(12) then stackCall({ 0x30 }) then idle(3), // f7
            idle(8) then loadHlWithOffsetStackPointer then idle(3), // f8
            idle(4) then (loadRegister(Cpu::regHL) into writeRegister(Cpu::regSP)) then idle(3), // f9
            idle(12) then (loadHardAddress into writeRegister(Cpu::regA)) then idle(3), // fa
            imeOn then idle(3), // fb
            unknownOpcode(0xFC), // fc
            unknownOpcode(0xFD), // fd
            idle(4) then akkuCp(loadHardByte) then idle(3), // fe
            idle(15) then stackCall({ 0x38 }) // ff
    )

    operator fun get(opcode: Byte): Insn = opcodeTable[opcode.toUnsignedInt()]

}