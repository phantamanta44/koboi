package io.github.phantamanta44.koboi.cpu

import io.github.phantamanta44.koboi.util.inv
import io.github.phantamanta44.koboi.util.toUnsignedInt

object Opcodes {

    private val opcodeTable: Array<(Cpu) -> Unit> = arrayOf(
            advance() then idle(4), // 00
            loadHardShort into writeRegister(Cpu::regBC) then idle(12), // 01
            loadRegister(Cpu::regA) into writePointer(Cpu::regBC) then idle(8), // 02
            increment16(Cpu::regBC) then idle(8), // 03
            increment8(Cpu::regB) then idle(4), // 04
            decrement8(Cpu::regB) then idle(4), // 05
            loadHardByte into writeRegister(Cpu::regB) then idle(8), // 06
            rotateLeft(Cpu::regA) then idle(4), // 07
            loadRegister(Cpu::regSP) into writeHardAddress16 then idle(20), // 08
            loadRegister16AsInt(Cpu::regBC) into incRegister16(Cpu::regHL) then idle(8), // 09
            loadPointer(Cpu::regBC) into writeRegister(Cpu::regA) then idle(8), // 0a
            decrement16(Cpu::regBC) then idle(8), // 0b
            increment8(Cpu::regC) then idle(4), // 0c
            decrement8(Cpu::regC) then idle(4), // 0d
            loadHardByte into writeRegister(Cpu::regC) then idle(8), // 0e
            rotateRight(Cpu::regA) then idle(4), // 0f

            { it.advance(2); it.stop(); it.idle(4); }, // 10
            loadHardShort into writeRegister(Cpu::regDE) then idle(12), // 11
            loadRegister(Cpu::regA) into writePointer(Cpu::regDE) then idle(8), // 12
            increment16(Cpu::regDE) then idle(8), // 13
            increment8(Cpu::regD) then idle(4), // 14
            decrement8(Cpu::regD) then idle(4), // 15
            loadHardByte into writeRegister(Cpu::regD) then idle(8), // 16
            rotateLeftThroughCarry(Cpu::regA) then idle(4), // 17
            jumpRelative then idle(12), // 18
            loadRegister16AsInt(Cpu::regDE) into incRegister16(Cpu::regHL) then idle(8), // 19
            loadPointer(Cpu::regDE) into writeRegister(Cpu::regA) then idle(8), // 1a
            decrement16(Cpu::regDE) then idle(8), // 1b
            increment8(Cpu::regE) then idle(4), // 1c
            decrement8(Cpu::regE) then idle(4), // 1d
            loadHardByte into writeRegister(Cpu::regE) then idle(8), // 1e
            rotateRightThroughCarry(Cpu::regA) then idle(4), // 1f

            predicate(nonZero,
                    jumpRelative then idle(12),
                    advance(2) then idle(8)), // 20
            loadHardShort into writeRegister(Cpu::regHL) then idle(12), // 21
            loadRegister(Cpu::regA) into writeHLPointerThenAdvance then idle(8), // 22
            increment16(Cpu::regHL) then idle(8), // 23
            increment8(Cpu::regH) then idle(4), // 24
            decrement8(Cpu::regH) then idle(4), // 25
            loadHardByte into writeRegister(Cpu::regH) then idle(8), // 26
            daa then idle(4), // 27
            predicate(isZero,
                    jumpRelative then idle(12),
                    advance(2) then idle(8)), // 28
            loadRegister16AsInt(Cpu::regHL) into incRegister16(Cpu::regHL) then idle(8), // 29
            loadHLPointerThenAdvance into writeRegister(Cpu::regA) then idle(8), // 2a
            decrement16(Cpu::regHL) then idle(8), // 2b
            increment8(Cpu::regL) then idle(4), // 2c
            decrement8(Cpu::regL) then idle(4), // 2d
            loadHardByte into writeRegister(Cpu::regL) then idle(8), // 2e
            loadRegister(Cpu::regA).map(Byte::inv) into writeRegister(Cpu::regA) then idle(4), // 2f

            predicate(nonCarry,
                    jumpRelative then idle(12),
                    advance(2) then idle(8)), // 30
            loadHardShort into writeRegister(Cpu::regSP) then idle(12), // 31
            loadRegister(Cpu::regA) into writeHLPointerThenBacktrack then idle(8), // 32
            increment16(Cpu::regSP) then idle(8), // 33
            incrementPointer(Cpu::regHL) then idle(12), // 34
            decrementPointer(Cpu::regHL) then idle(12), // 35
            loadHardByte into writePointer(Cpu::regHL) then idle(8), // 36
            flagOn(FlagRegister::kC) then idle(4), // 37
            predicate(isCarry,
                    jumpRelative then idle(12),
                    advance(2) then idle(8)),// 38
            loadRegister16AsInt(Cpu::regHL) into incRegister16(Cpu::regSP) then idle(8), // 39
            loadHLPointerThenBacktrack into writeRegister(Cpu::regA) then idle(8), // 3a
            decrement16(Cpu::regSP) then idle(8), // 3b
            increment8(Cpu::regA) then idle(4), // 3c
            decrement8(Cpu::regA) then idle(4), // 3d
            loadHardByte into writeRegister(Cpu::regA) then idle(8), // 3e
            flagFlip(FlagRegister::kC) then idle(4), // 3f

            loadRegister(Cpu::regB) into writeRegister(Cpu::regB) then idle(4), // 40
            loadRegister(Cpu::regC) into writeRegister(Cpu::regB) then idle(4), // 41
            loadRegister(Cpu::regD) into writeRegister(Cpu::regB) then idle(4), // 42
            loadRegister(Cpu::regE) into writeRegister(Cpu::regB) then idle(4), // 43
            loadRegister(Cpu::regH) into writeRegister(Cpu::regB) then idle(4), // 44
            loadRegister(Cpu::regL) into writeRegister(Cpu::regB) then idle(4), // 45
            loadPointer(Cpu::regHL) into writeRegister(Cpu::regB) then idle(8), // 46
            loadRegister(Cpu::regA) into writeRegister(Cpu::regB) then idle(4), // 47
            loadRegister(Cpu::regB) into writeRegister(Cpu::regC) then idle(4), // 48
            loadRegister(Cpu::regC) into writeRegister(Cpu::regC) then idle(4), // 49
            loadRegister(Cpu::regD) into writeRegister(Cpu::regC) then idle(4), // 4a
            loadRegister(Cpu::regE) into writeRegister(Cpu::regC) then idle(4), // 4b
            loadRegister(Cpu::regH) into writeRegister(Cpu::regC) then idle(4), // 4c
            loadRegister(Cpu::regL) into writeRegister(Cpu::regC) then idle(4), // 4d
            loadPointer(Cpu::regHL) into writeRegister(Cpu::regC) then idle(8), // 4e
            loadRegister(Cpu::regA) into writeRegister(Cpu::regC) then idle(4), // 4f

            loadRegister(Cpu::regB) into writeRegister(Cpu::regD) then idle(4), // 50
            loadRegister(Cpu::regC) into writeRegister(Cpu::regD) then idle(4), // 51
            loadRegister(Cpu::regD) into writeRegister(Cpu::regD) then idle(4), // 52
            loadRegister(Cpu::regE) into writeRegister(Cpu::regD) then idle(4), // 53
            loadRegister(Cpu::regH) into writeRegister(Cpu::regD) then idle(4), // 54
            loadRegister(Cpu::regL) into writeRegister(Cpu::regD) then idle(4), // 55
            loadPointer(Cpu::regHL) into writeRegister(Cpu::regD) then idle(8), // 56
            loadRegister(Cpu::regA) into writeRegister(Cpu::regD) then idle(4), // 57
            loadRegister(Cpu::regB) into writeRegister(Cpu::regE) then idle(4), // 58
            loadRegister(Cpu::regC) into writeRegister(Cpu::regE) then idle(4), // 59
            loadRegister(Cpu::regD) into writeRegister(Cpu::regE) then idle(4), // 5a
            loadRegister(Cpu::regE) into writeRegister(Cpu::regE) then idle(4), // 5b
            loadRegister(Cpu::regH) into writeRegister(Cpu::regE) then idle(4), // 5c
            loadRegister(Cpu::regL) into writeRegister(Cpu::regE) then idle(4), // 5d
            loadPointer(Cpu::regHL) into writeRegister(Cpu::regE) then idle(8), // 5e
            loadRegister(Cpu::regA) into writeRegister(Cpu::regE) then idle(4), // 5f

            loadRegister(Cpu::regB) into writeRegister(Cpu::regH) then idle(4), // 60
            loadRegister(Cpu::regC) into writeRegister(Cpu::regH) then idle(4), // 61
            loadRegister(Cpu::regD) into writeRegister(Cpu::regH) then idle(4), // 62
            loadRegister(Cpu::regE) into writeRegister(Cpu::regH) then idle(4), // 63
            loadRegister(Cpu::regH) into writeRegister(Cpu::regH) then idle(4), // 64
            loadRegister(Cpu::regL) into writeRegister(Cpu::regH) then idle(4), // 65
            loadPointer(Cpu::regHL) into writeRegister(Cpu::regH) then idle(8), // 66
            loadRegister(Cpu::regA) into writeRegister(Cpu::regH) then idle(4), // 67
            loadRegister(Cpu::regB) into writeRegister(Cpu::regL) then idle(4), // 68
            loadRegister(Cpu::regC) into writeRegister(Cpu::regL) then idle(4), // 69
            loadRegister(Cpu::regD) into writeRegister(Cpu::regL) then idle(4), // 6a
            loadRegister(Cpu::regE) into writeRegister(Cpu::regL) then idle(4), // 6b
            loadRegister(Cpu::regH) into writeRegister(Cpu::regL) then idle(4), // 6c
            loadRegister(Cpu::regL) into writeRegister(Cpu::regL) then idle(4), // 6d
            loadPointer(Cpu::regHL) into writeRegister(Cpu::regL) then idle(8), // 6e
            loadRegister(Cpu::regA) into writeRegister(Cpu::regL) then idle(4), // 6f

            loadRegister(Cpu::regB) into writePointer(Cpu::regHL) then idle(8), // 70
            loadRegister(Cpu::regC) into writePointer(Cpu::regHL) then idle(8), // 71
            loadRegister(Cpu::regD) into writePointer(Cpu::regHL) then idle(8), // 72
            loadRegister(Cpu::regE) into writePointer(Cpu::regHL) then idle(8), // 73
            loadRegister(Cpu::regH) into writePointer(Cpu::regHL) then idle(8), // 74
            loadRegister(Cpu::regL) into writePointer(Cpu::regHL) then idle(8), // 75
            { it.advance(); it.halt(); it.idle(4); }, // 76
            loadRegister(Cpu::regA) into writePointer(Cpu::regHL) then idle(8), // 77
            loadRegister(Cpu::regB) into writeRegister(Cpu::regA) then idle(4), // 78
            loadRegister(Cpu::regC) into writeRegister(Cpu::regA) then idle(4), // 79
            loadRegister(Cpu::regD) into writeRegister(Cpu::regA) then idle(4), // 7a
            loadRegister(Cpu::regE) into writeRegister(Cpu::regA) then idle(4), // 7b
            loadRegister(Cpu::regH) into writeRegister(Cpu::regA) then idle(4), // 7c
            loadRegister(Cpu::regL) into writeRegister(Cpu::regA) then idle(4), // 7d
            loadPointer(Cpu::regHL) into writeRegister(Cpu::regA) then idle(8), // 7e
            loadRegister(Cpu::regA) into writeRegister(Cpu::regA) then idle(4), // 7f

            loadRegister8AsInt(Cpu::regB) into incRegister8(Cpu::regA) then idle(4), // 80
            loadRegister8AsInt(Cpu::regC) into incRegister8(Cpu::regA) then idle(4), // 81
            loadRegister8AsInt(Cpu::regD) into incRegister8(Cpu::regA) then idle(4), // 82
            loadRegister8AsInt(Cpu::regE) into incRegister8(Cpu::regA) then idle(4), // 83
            loadRegister8AsInt(Cpu::regH) into incRegister8(Cpu::regA) then idle(4), // 84
            loadRegister8AsInt(Cpu::regL) into incRegister8(Cpu::regA) then idle(4), // 85
            loadPointerAsInt(Cpu::regHL) into incRegister8(Cpu::regA) then idle(8), // 86
            loadRegister8AsInt(Cpu::regA) into incRegister8(Cpu::regA) then idle(4), // 87
            loadRegister8AsInt(Cpu::regB).plusCarry() into incRegister8(Cpu::regA) then idle(4), // 88
            loadRegister8AsInt(Cpu::regC).plusCarry() into incRegister8(Cpu::regA) then idle(4), // 89
            loadRegister8AsInt(Cpu::regD).plusCarry() into incRegister8(Cpu::regA) then idle(4), // 8a
            loadRegister8AsInt(Cpu::regE).plusCarry() into incRegister8(Cpu::regA) then idle(4), // 8b
            loadRegister8AsInt(Cpu::regH).plusCarry() into incRegister8(Cpu::regA) then idle(4), // 8c
            loadRegister8AsInt(Cpu::regL).plusCarry() into incRegister8(Cpu::regA) then idle(4), // 8d
            loadPointerAsInt(Cpu::regHL).plusCarry() into incRegister8(Cpu::regA) then idle(8), // 8e
            loadRegister8AsInt(Cpu::regA).plusCarry() into incRegister8(Cpu::regA) then idle(4), // 8f

            loadRegister8AsInt(Cpu::regB) into decRegister8(Cpu::regA) then idle(4), // 90
            loadRegister8AsInt(Cpu::regC) into decRegister8(Cpu::regA) then idle(4), // 91
            loadRegister8AsInt(Cpu::regD) into decRegister8(Cpu::regA) then idle(4), // 92
            loadRegister8AsInt(Cpu::regE) into decRegister8(Cpu::regA) then idle(4), // 93
            loadRegister8AsInt(Cpu::regH) into decRegister8(Cpu::regA) then idle(4), // 94
            loadRegister8AsInt(Cpu::regL) into decRegister8(Cpu::regA) then idle(4), // 95
            loadPointerAsInt(Cpu::regHL) into decRegister8(Cpu::regA) then idle(8), // 96
            loadRegister8AsInt(Cpu::regA) into decRegister8(Cpu::regA) then idle(4), // 97
            loadRegister8AsInt(Cpu::regB).minusCarry() into decRegister8(Cpu::regA) then idle(4), // 98
            loadRegister8AsInt(Cpu::regC).minusCarry() into decRegister8(Cpu::regA) then idle(4), // 99
            loadRegister8AsInt(Cpu::regD).minusCarry() into decRegister8(Cpu::regA) then idle(4), // 9a
            loadRegister8AsInt(Cpu::regE).minusCarry() into decRegister8(Cpu::regA) then idle(4), // 9b
            loadRegister8AsInt(Cpu::regH).minusCarry() into decRegister8(Cpu::regA) then idle(4), // 9c
            loadRegister8AsInt(Cpu::regL).minusCarry() into decRegister8(Cpu::regA) then idle(4), // 9d
            loadPointerAsInt(Cpu::regHL).minusCarry() into decRegister8(Cpu::regA) then idle(8), // 9e
            loadRegister8AsInt(Cpu::regA).minusCarry() into decRegister8(Cpu::regA) then idle(4), // 9f

            akkuAnd(loadRegister(Cpu::regB)) then idle(4), // a0
            akkuAnd(loadRegister(Cpu::regC)) then idle(4), // a1
            akkuAnd(loadRegister(Cpu::regD)) then idle(4), // a2
            akkuAnd(loadRegister(Cpu::regE)) then idle(4), // a3
            akkuAnd(loadRegister(Cpu::regH)) then idle(4), // a4
            akkuAnd(loadRegister(Cpu::regL)) then idle(4), // a5
            akkuAnd(loadPointer(Cpu::regHL)) then idle(8), // a6
            akkuAnd(loadRegister(Cpu::regA)) then idle(4), // a7
            akkuXor(loadRegister(Cpu::regB)) then idle(4), // a8
            akkuXor(loadRegister(Cpu::regC)) then idle(4), // a9
            akkuXor(loadRegister(Cpu::regD)) then idle(4), // aa
            akkuXor(loadRegister(Cpu::regE)) then idle(4), // ab
            akkuXor(loadRegister(Cpu::regH)) then idle(4), // ac
            akkuXor(loadRegister(Cpu::regL)) then idle(4), // ad
            akkuXor(loadPointer(Cpu::regHL)) then idle(8), // ae
            akkuXor(loadRegister(Cpu::regA)) then idle(4), // af

            akkuOr(loadRegister(Cpu::regB)) then idle(4), // b0
            akkuOr(loadRegister(Cpu::regC)) then idle(4), // b1
            akkuOr(loadRegister(Cpu::regD)) then idle(4), // b2
            akkuOr(loadRegister(Cpu::regE)) then idle(4), // b3
            akkuOr(loadRegister(Cpu::regH)) then idle(4), // b4
            akkuOr(loadRegister(Cpu::regL)) then idle(4), // b5
            akkuOr(loadPointer(Cpu::regHL)) then idle(8), // b6
            akkuOr(loadRegister(Cpu::regA)) then idle(4), // b7
            akkuCp(loadRegister(Cpu::regB)) then idle(4), // b8
            akkuCp(loadRegister(Cpu::regC)) then idle(4), // b9
            akkuCp(loadRegister(Cpu::regD)) then idle(4), // ba
            akkuCp(loadRegister(Cpu::regE)) then idle(4), // bb
            akkuCp(loadRegister(Cpu::regH)) then idle(4), // bc
            akkuCp(loadRegister(Cpu::regL)) then idle(4), // bd
            akkuCp(loadPointer(Cpu::regHL)) then idle(8), // be
            akkuCp(loadRegister(Cpu::regA)) then idle(4), // bf

            predicate(nonZero,
                    stackReturn then idle(20),
                    advance() then idle(8)), // c0
            stackPop(Cpu::regBC) then idle(12), // c1
            predicate(nonZero,
                    jumpAbsolute(loadHardShort) then idle(16),
                    advance(2) then idle(12)), // c2
            jumpAbsolute(loadHardShort) then idle(16), // c3
            predicate(nonZero,
                    stackCall(loadHardShort) then idle(24),
                    advance(2) then idle(12)), // c4
            stackPush(Cpu::regBC) then idle(16), // c5
            loadHardByteAsInt into incRegister8(Cpu::regA) then idle(8), // c6
            stackCall({ 0x00 }) then idle(16), // c7
            predicate(isZero,
                    stackReturn then idle(20),
                    advance() then idle(8)), // c8
            stackReturn then idle(16), // c9
            predicate(isZero,
                    jumpAbsolute(loadHardShort) then idle(16),
                    advance(2) then idle(12)), // ca
            doCbPrefixedOpcode then idle(4), // cb
            predicate(isZero,
                    stackCall(loadHardShort) then idle(24),
                    advance(2) then idle(12)), // cc
            stackCall(loadHardShort) then idle(24), // cd
            loadHardByteAsInt.plusCarry() into incRegister8(Cpu::regA) then idle(8), // ce
            stackCall({ 0x08 }) then idle(16), // cf

            predicate(nonCarry,
                    stackReturn then idle(20),
                    advance() then idle(8)), // d0
            stackPop(Cpu::regDE) then idle(12), // d1
            predicate(nonCarry,
                    jumpAbsolute(loadHardShort) then idle(16),
                    advance(2) then idle(12)), // d2
            unknownOpcode(0xD3), // d3
            predicate(nonCarry,
                    stackCall(loadHardShort) then idle(24),
                    advance(2) then idle(12)), // d4
            stackPush(Cpu::regDE) then idle(16), // d5
            loadHardByteAsInt into decRegister8(Cpu::regA) then idle(8), // d6
            stackCall({ 0x10 }) then idle(16), // d7
            predicate(isCarry,
                    stackReturn then idle(20),
                    advance() then idle(8)), // d8
            stackReturn then imeOn then idle(16), // d9
            predicate(isCarry,
                    jumpAbsolute(loadHardShort) then idle(16),
                    advance(2) then idle(12)), // da
            unknownOpcode(0xDB), // db
            predicate(isCarry,
                    stackCall(loadHardShort) then idle(24),
                    advance(2) then idle(12)), // dc
            unknownOpcode(0xDD), // dd
            loadHardByteAsInt.minusCarry() into decRegister8(Cpu::regA) then idle(8), // de
            stackCall({ 0x18 }) then idle(16), // df

            loadRegister(Cpu::regA) into writeHardHighAddress then idle(12), // e0
            stackPop(Cpu::regHL) then idle(12), // e1
            loadRegister(Cpu::regA) into writeHighPointer(Cpu::regC) then idle(8), // e2
            unknownOpcode(0xE3), // e3
            unknownOpcode(0xE4), // e4
            stackPush(Cpu::regHL) then idle(16), // e5
            akkuAnd(loadHardByte) then idle(8), // e6
            stackCall({ 0x20 }) then idle(16), // e7
            offsetStackPointer then idle(16), // e8
            jumpAbsolute(loadRegister(Cpu::regHL)) then idle(4), // e9
            loadRegister(Cpu::regA) into writeHardAddress8 then idle(16), // ea
            unknownOpcode(0xEB), // eb
            unknownOpcode(0xEC), // ec
            unknownOpcode(0xED), // ed
            akkuXor(loadHardByte) then idle(8), // ee
            stackCall({ 0x28 }) then idle(16), // ef

            loadHardHighAddress into writeRegister(Cpu::regA) then idle(12), // f0
            stackPop(Cpu::regAF) then idle(12), // f1
            loadHighPointer(Cpu::regC) into writeRegister(Cpu::regA) then idle(8), // f2
            imeOff then idle(4), // f3
            unknownOpcode(0xF4), // f4
            stackPush(Cpu::regAF) then idle(16), // f5
            akkuOr(loadHardByte) then idle(8), // f6
            stackCall({ 0x30 }) then idle(16), // f7
            offsetHLWithStackPointer then idle(12), // f8
            loadRegister(Cpu::regHL) into writeRegister(Cpu::regSP) then idle(8), // f9
            loadHardAddress into writeRegister(Cpu::regA) then idle(16), // fa
            imeOn then idle(4), // fb
            unknownOpcode(0xFC), // fc
            unknownOpcode(0xFD), // fd
            akkuCp(loadHardByte) then idle(8), // fe
            stackCall({ 0x38 }) then idle(16) // ff
    )

    operator fun get(opcode: Byte): (Cpu) -> Unit = opcodeTable[opcode.toUnsignedInt()]

}