import io.github.phantamanta44.koboi.backtrace.mnemonics
import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.cpu.FlagRegister
import io.github.phantamanta44.koboi.cpu.IRegister
import io.github.phantamanta44.koboi.cpu.Opcodes
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt
import org.junit.jupiter.api.Assertions
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

class LoadTests : CpuTests() {

    private fun writeRegister8(register: (Cpu) -> IRegister<Byte>): (Cpu, Byte) -> Unit = { cpu, data ->
        register(cpu).write(data)
    }

    private fun writePointer8(register: (Cpu) -> IRegister<Short>): (Cpu, Byte) -> Unit = { cpu, data ->
        cpu.memory.write(register(cpu).read().toUnsignedInt(), data, direct = true)
    }

    private fun writeLocation8(identifier: String): (Cpu, Byte) -> Unit = when (identifier) {
        "(BC)" -> writePointer8(Cpu::regBC)
        "(DE)" -> writePointer8(Cpu::regDE)
        "(HL)" -> writePointer8(Cpu::regHL)
        "B" -> writeRegister8(Cpu::regB)
        "C" -> writeRegister8(Cpu::regC)
        "D" -> writeRegister8(Cpu::regD)
        "E" -> writeRegister8(Cpu::regE)
        "H" -> writeRegister8(Cpu::regH)
        "L" -> writeRegister8(Cpu::regL)
        "A" -> writeRegister8(Cpu::regA)
        "d8" -> { cpu, byte ->
            cpu.memory.write(0x0007, byte, direct = true)
            cpu.regPC.write(0x0006)
        }
        "(a16)" -> { cpu, byte ->
            cpu.memory.write(0x0707, byte, direct = true)
            cpu.memory.write(0x0007, 0x0707, true)
            cpu.regPC.write(0x0006)
        }
        "(a8)" -> { cpu, byte ->
            cpu.memory.write(0xFF07, byte, direct = true)
            cpu.memory.write(0x0007, 0x07.toByte(), direct = true)
            cpu.regPC.write(0x0006)
        }
        "(C)" -> { cpu, byte ->
            cpu.memory.write(0xFF07, byte, direct = true)
            cpu.regC.write(0x07)
        }
        else -> throw IllegalArgumentException(identifier)
    }

    private fun readRegister8(register: (Cpu) -> IRegister<Byte>): (Cpu) -> Byte = { register(it).read() }

    private fun readPointer8(register: (Cpu) -> IRegister<Short>): (Cpu) -> Byte = {
        it.memory.read(register(it).read().toUnsignedInt(), true)
    }

    private fun readLocation8(identifier: String): (Cpu) -> Byte = when (identifier) {
        "(BC)" -> readPointer8(Cpu::regBC)
        "(DE)" -> readPointer8(Cpu::regDE)
        "(HL)" -> readPointer8(Cpu::regHL)
        "B" -> readRegister8(Cpu::regB)
        "C" -> readRegister8(Cpu::regC)
        "D" -> readRegister8(Cpu::regD)
        "E" -> readRegister8(Cpu::regE)
        "H" -> readRegister8(Cpu::regH)
        "L" -> readRegister8(Cpu::regL)
        "A" -> readRegister8(Cpu::regA)
        "(a16)" -> { cpu -> cpu.memory.read(0x7070, true) }
        "(a8)" -> { cpu -> cpu.memory.read(0xFF07, true) }
        "(C)" -> { cpu -> cpu.memory.read(0xFF07, true) }
        else -> throw IllegalArgumentException(identifier)
    }

    private fun setupRead8(identifier: String, cpu: Cpu) {
        when (identifier) {
            "(a16)" -> {
                cpu.memory.write(0x0007, 0x7070, true)
                cpu.regPC.write(0x0006)
            }
            "(a8)" -> {
                cpu.memory.write(0x0007, 0x07.toByte(), direct = true)
                cpu.regPC.write(0x0006)
            }
            "(C)" -> { cpu.regC.write(0x07) }
        }
    }

    @TestFactory
    fun testSimpleByteLoads(): List<DynamicTest> = listOf(Triple(0x02, "(BC)", "A"), Triple(0x06, "B", "d8"),
            Triple(0x0A, "A", "(BC)"), Triple(0x0E, "C", "d8"), Triple(0x12, "(DE)", "A"), Triple(0x16, "D", "d8"),
            Triple(0x1A, "A", "(DE)"), Triple(0x1E, "E", "d8"), Triple(0x26, "H", "d8"), Triple(0x2E, "L", "d8"),
            Triple(0x36, "(HL)", "d8"), Triple(0x3E, "A", "d8"), Triple(0x40, "B", "B"), Triple(0x41, "B", "C"),
            Triple(0x42, "B", "D"), Triple(0x43, "B", "E"), Triple(0x44, "B", "H"), Triple(0x45, "B", "L"),
            Triple(0x46, "B", "(HL)"), Triple(0x47, "B", "A"), Triple(0x48, "C", "B"), Triple(0x49, "C", "C"),
            Triple(0x4A, "C", "D"), Triple(0x4B, "C", "E"), Triple(0x4C, "C", "H"), Triple(0x4D, "C", "L"),
            Triple(0x4E, "C", "(HL)"), Triple(0x4F, "C", "A"), Triple(0x50, "D", "B"), Triple(0x51, "D", "C"),
            Triple(0x52, "D", "D"), Triple(0x53, "D", "E"), Triple(0x54, "D", "H"), Triple(0x55, "D", "L"),
            Triple(0x56, "D", "(HL)"), Triple(0x57, "D", "A"), Triple(0x58, "E", "B"), Triple(0x59, "E", "C"),
            Triple(0x5A, "E", "D"), Triple(0x5B, "E", "E"), Triple(0x5C, "E", "H"), Triple(0x5D, "E", "L"),
            Triple(0x5E, "E", "(HL)"), Triple(0x5F, "E", "A"), Triple(0x60, "H", "B"), Triple(0x61, "H", "C"),
            Triple(0x62, "H", "D"), Triple(0x63, "H", "E"), Triple(0x64, "H", "H"), Triple(0x65, "H", "L"),
            Triple(0x66, "H", "(HL)"), Triple(0x67, "H", "A"), Triple(0x68, "L", "B"), Triple(0x69, "L", "C"),
            Triple(0x6A, "L", "D"), Triple(0x6B, "L", "E"), Triple(0x6C, "L", "H"), Triple(0x6D, "L", "L"),
            Triple(0x6E, "L", "(HL)"), Triple(0x6F, "L", "A"), Triple(0x70, "(HL)", "B"), Triple(0x71, "(HL)", "C"),
            Triple(0x72, "(HL)", "D"), Triple(0x73, "(HL)", "E"), Triple(0x74, "(HL)", "H"), Triple(0x75, "(HL)", "L"),
            Triple(0x77, "(HL)", "A"), Triple(0x78, "A", "B"), Triple(0x79, "A", "C"), Triple(0x7A, "A", "D"),
            Triple(0x7B, "A", "E"), Triple(0x7C, "A", "H"), Triple(0x7D, "A", "L"), Triple(0x7E, "A", "(HL)"),
            Triple(0x7F, "A", "A"), Triple(0xE0, "(a8)", "A"), Triple(0xE2, "(C)", "A"), Triple(0xEA, "(a16)", "A"),
            Triple(0xF0, "A", "(a8)"), Triple(0xF2, "A", "(C)"), Triple(0xFA, "A", "(a16)"))
            .map {
                DynamicTest.dynamicTest(mnemonics[it.first]) {
                    val rand = Random()
                    for (i in 0..511) {
                        val target = rand.nextInt(256).toByte()
                        writeLocation8(it.third)(cpu, target)
                        setupRead8(it.second, cpu)
                        Opcodes[it.first.toByte()](cpu)
                        Assertions.assertEquals(target, readLocation8(it.second)(cpu), "${it.second}=${target.toUnsignedHex()}")
                    }
                }
            }

    @TestFactory
    fun testShortRegisterLoads(): List<DynamicTest> = listOf(0x01 to Cpu::regBC, 0x11 to Cpu::regDE, 0x21 to Cpu::regHL, 0x31 to Cpu::regSP)
            .map {
                DynamicTest.dynamicTest(mnemonics[it.first]) {
                    val rand = Random()
                    for (i in 0..511) {
                        val target = rand.nextInt(65536).toShort()
                        cpu.memory.write(0x7071, target, direct = true)
                        cpu.regPC.write(0x7070)
                        Opcodes[it.first.toByte()](cpu)
                        assertRegister(it.second.get(cpu), target, "${it.second.name}=${target.toUnsignedHex()}")
                    }
                }
            }

}