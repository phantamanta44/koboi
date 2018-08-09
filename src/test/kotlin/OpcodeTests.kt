import io.github.phantamanta44.koboi.backtrace.mnemonics
import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.cpu.FlagRegister
import io.github.phantamanta44.koboi.cpu.Opcodes
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.*

class IncDecTests : CpuTests() {

    @TestFactory
    fun testInc8(): List<DynamicTest> = listOf(0x04 to Cpu::regB, 0x0C to Cpu::regC, 0x14 to Cpu::regD,
            0x1C to Cpu::regE, 0x24 to Cpu::regH, 0x2C to Cpu::regL, 0x3C to Cpu::regA)
            .map {
                DynamicTest.dynamicTest(it.second.name) {
                    for (i in 0..255) {
                        cpu.regPC.write(0)
                        it.second.get(cpu).write(i.toByte())
                        Opcodes[it.first.toByte()](cpu)
                        assertRegister(it.second.get(cpu), ((i + 1) % 256).toByte(), "INC $i")
                        assertFlag(cpu, FlagRegister::kZ, i == 255, "Z")
                        assertFlag(cpu, FlagRegister::kN, false, "N")
                        assertRegister(cpu.regPC, 1, "PC $i")
                    }
                }
            }

    @TestFactory
    fun testInc16(): List<DynamicTest> = listOf(0x03 to Cpu::regBC, 0x13 to Cpu::regDE, 0x23 to Cpu::regHL, 0x33 to Cpu::regSP)
            .map {
                DynamicTest.dynamicTest(it.second.name) {
                    for (i in 0..65535) {
                        cpu.regPC.write(0)
                        it.second.get(cpu).write(i.toShort())
                        Opcodes[it.first.toByte()](cpu)
                        assertRegister(it.second.get(cpu), ((i + 1) % 65536).toShort(), "INC $i")
                        assertRegister(cpu.regPC, 1, "PC $i")
                    }
                }
            }

    @Test
    fun testIncHlPointer() {
        val rand = Random()
        for (i in 0..255) {
            cpu.regPC.write(0)
            val addr = rand.nextInt(32768)
            cpu.regHL.write(addr.toShort())
            cpu.memory.write(addr, i.toByte())
            Opcodes[0x34.toByte()](cpu)
            assertMemory(cpu, addr, ((i + 1) % 256).toByte(), "INC $i")
            assertFlag(cpu, FlagRegister::kZ, i == 255, "Z")
            assertFlag(cpu, FlagRegister::kN, false, "N")
            assertRegister(cpu.regPC, 1, "PC $i")
        }
    }

    @TestFactory
    fun testDec8(): List<DynamicTest> = listOf(0x05 to Cpu::regB, 0x0D to Cpu::regC, 0x15 to Cpu::regD,
            0x1D to Cpu::regE, 0x25 to Cpu::regH, 0x2D to Cpu::regL, 0x3D to Cpu::regA)
            .map {
                DynamicTest.dynamicTest(it.second.name) {
                    for (i in 0..255) {
                        cpu.regPC.write(0)
                        it.second.get(cpu).write(i.toByte())
                        Opcodes[it.first.toByte()](cpu)
                        assertRegister(it.second.get(cpu), ((i + 255) % 256).toByte(), "DEC $i")
                        assertFlag(cpu, FlagRegister::kZ, i == 1, "Z")
                        assertFlag(cpu, FlagRegister::kN, true, "N")
                        assertRegister(cpu.regPC, 1, "PC $i")
                    }
                }
            }

    @TestFactory
    fun testDec16(): List<DynamicTest> = listOf(0x0B to Cpu::regBC, 0x1B to Cpu::regDE, 0x2B to Cpu::regHL, 0x3B to Cpu::regSP)
            .map {
                DynamicTest.dynamicTest(it.second.name) {
                    for (i in 0..65535) {
                        cpu.regPC.write(0)
                        it.second.get(cpu).write(i.toShort())
                        Opcodes[it.first.toByte()](cpu)
                        assertRegister(it.second.get(cpu), ((i + 65535) % 65536).toShort(), "DEC $i")
                        assertRegister(cpu.regPC, 1, "PC $i")
                    }
                }
            }

    @Test
    fun testDecHlPointer() {
        val rand = Random()
        for (i in 0..255) {
            cpu.regPC.write(0)
            val addr = rand.nextInt(32768)
            cpu.regHL.write(addr.toShort())
            cpu.memory.write(addr, i.toByte())
            Opcodes[0x35.toByte()](cpu)
            assertMemory(cpu, addr, ((i + 255) % 256).toByte(), "DEC $i")
            assertFlag(cpu, FlagRegister::kZ, i == 1, "Z")
            assertFlag(cpu, FlagRegister::kN, true, "N")
            assertRegister(cpu.regPC, 1, "PC $i")
        }
    }

}

class JumpTests : CpuTests() {

    @TestFactory
    fun testRelativeJumps(): List<DynamicTest> = listOf<Triple<Int, (Cpu) -> Unit, ((Cpu) -> Unit)?>>(
            Triple(0x18, { _ -> }, null),
            Triple(0x20, { cpu -> cpu.regF.kZ = false }, { cpu -> cpu.regF.kZ = true }),
            Triple(0x28, { cpu -> cpu.regF.kZ = true }, { cpu -> cpu.regF.kZ = false }),
            Triple(0x30, { cpu -> cpu.regF.kC = false }, { cpu -> cpu.regF.kC = true }),
            Triple(0x38, { cpu -> cpu.regF.kC = true }, { cpu -> cpu.regF.kC = false }))
            .map {
                DynamicTest.dynamicTest(mnemonics[it.first]) {
                    val rand = Random()
                    for (i in -128..127) {
                        val baseAddr = rand.nextInt(8192) + 16384
                        cpu.regPC.write(baseAddr.toShort())
                        cpu.memory.write(baseAddr, it.first.toByte())
                        cpu.memory.write(baseAddr + 1, i.toByte())
                        it.second(cpu)
                        Opcodes[it.first.toByte()](cpu)
                        assertRegister(cpu.regPC, (baseAddr + i + 2).toShort(), "${mnemonics[it.first]} $i succeeding")
                    }
                    it.third?.let { killer ->
                        for (i in -128..127) {
                            val baseAddr = rand.nextInt(8192) + 16384
                            cpu.regPC.write(baseAddr.toShort())
                            cpu.memory.write(baseAddr, it.first.toByte())
                            cpu.memory.write(baseAddr + 1, i.toByte())
                            killer(cpu)
                            Opcodes[it.first.toByte()](cpu)
                            assertRegister(cpu.regPC, (baseAddr + 2).toShort(), "${mnemonics[it.first]} $i failing")
                        }
                    }
                }
            }

    @TestFactory
    fun testAbsoluteJumps(): List<DynamicTest> = listOf<Triple<Int, (Cpu) -> Unit, ((Cpu) -> Unit)?>>(
            Triple(0xC3, { _ -> }, null),
            Triple(0xC2, { cpu -> cpu.regF.kZ = false }, { cpu -> cpu.regF.kZ = true }),
            Triple(0xCA, { cpu -> cpu.regF.kZ = true }, { cpu -> cpu.regF.kZ = false }),
            Triple(0xD2, { cpu -> cpu.regF.kC = false }, { cpu -> cpu.regF.kC = true }),
            Triple(0xDA, { cpu -> cpu.regF.kC = true }, { cpu -> cpu.regF.kC = false }))
            .map {
                DynamicTest.dynamicTest(mnemonics[it.first]) {
                    val rand = Random()
                    for (i in 0..255) {
                        val baseAddr = rand.nextInt(8192) + 16384
                        val tgtAddr = (rand.nextInt(8192) + 16384).toShort()
                        cpu.regPC.write(baseAddr.toShort())
                        cpu.memory.write(baseAddr, it.first.toByte())
                        cpu.memory.write(baseAddr + 1, tgtAddr)
                        it.second(cpu)
                        Opcodes[it.first.toByte()](cpu)
                        assertRegister(cpu.regPC, tgtAddr, "${mnemonics[it.first]} $tgtAddr succeeding")
                    }
                    it.third?.let { killer ->
                        for (i in 0..255) {
                            val baseAddr = rand.nextInt(8192) + 16384
                            val tgtAddr = (rand.nextInt(8192) + 16384).toShort()
                            cpu.regPC.write(baseAddr.toShort())
                            cpu.memory.write(baseAddr, it.first.toByte())
                            cpu.memory.write(baseAddr + 1, tgtAddr)
                            killer(cpu)
                            Opcodes[it.first.toByte()](cpu)
                            assertRegister(cpu.regPC, (baseAddr + 3).toShort(), "${mnemonics[it.first]} $tgtAddr failing")
                        }
                    }
                }
            }

    @Test
    fun testJumpHl() {
        val rand = Random()
        for (i in 0..255) {
            val baseAddr = rand.nextInt(8192) + 16384
            val tgtAddr = (rand.nextInt(8192) + 16384).toShort()
            cpu.regPC.write(baseAddr.toShort())
            cpu.memory.write(baseAddr, 0xE9.toByte())
            cpu.regHL.write(tgtAddr)
            Opcodes[0xE9.toByte()](cpu)
            assertRegister(cpu.regPC, tgtAddr, "JP HL $tgtAddr")
        }
    }

}

class ArithmeticTests : CpuTests() {

    @TestFactory
    fun testAkkuRegisterAdd(): List<DynamicTest> = listOf(0x80 to Cpu::regB, 0x81 to Cpu::regC, 0x82 to Cpu::regD,
            0x83 to Cpu::regE, 0x84 to Cpu::regH, 0x85 to Cpu::regL)
            .map {
                DynamicTest.dynamicTest(it.second.name) {
                    val rand = Random()
                    for (i in 0..511) {
                        val a = rand.nextInt(256)
                        val o = rand.nextInt(256)
                        cpu.regA.write(a.toByte())
                        it.second.get(cpu).write(o.toByte())
                        Opcodes[it.first.toByte()](cpu)
                        val result = (a + o) % 256
                        assertRegister(cpu.regA, result.toByte(), "$a + $o")
                        assertFlag(cpu, FlagRegister::kZ, result == 0, "Z")
                        assertFlag(cpu, FlagRegister::kN, false, "N")
                        assertFlag(cpu, FlagRegister::kH, result and 0xF0 != a and 0xF0, "H")
                        assertFlag(cpu, FlagRegister::kC, a + o > 255, "C")
                    }
                }
            }

    @TestFactory
    fun testAkkuRegisterSub(): List<DynamicTest> = listOf(0x90 to Cpu::regB, 0x91 to Cpu::regC, 0x92 to Cpu::regD,
            0x93 to Cpu::regE, 0x94 to Cpu::regH, 0x95 to Cpu::regL)
            .map {
                DynamicTest.dynamicTest(it.second.name) {
                    val rand = Random()
                    for (i in 0..511) {
                        val a = rand.nextInt(256)
                        val o = rand.nextInt(256)
                        cpu.regA.write(a.toByte())
                        it.second.get(cpu).write(o.toByte())
                        Opcodes[it.first.toByte()](cpu)
                        val result = (a + (256 - o)) % 256
                        assertRegister(cpu.regA, result.toByte(), "$a - $o")
                        assertFlag(cpu, FlagRegister::kZ, result == 0, "Z")
                        assertFlag(cpu, FlagRegister::kN, true, "N")
                        assertFlag(cpu, FlagRegister::kH, result and 0xF0 == a and 0xF0, "H")
                        assertFlag(cpu, FlagRegister::kC, o <= a, "C")
                    }
                }
            }

}