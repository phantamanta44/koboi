import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.cpu.FlagRegister
import io.github.phantamanta44.koboi.cpu.IRegister
import io.github.phantamanta44.koboi.memory.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import kotlin.reflect.KProperty1

open class CpuTests {

    protected val cpu: Cpu = createCpu()

    @BeforeEach
    fun wipeRegisters() {
        cpu.regAF.write(0)
        cpu.regBC.write(0)
        cpu.regDE.write(0)
        cpu.regHL.write(0)
        cpu.regSP.write(0xFFFF.toShort())
        cpu.regPC.write(0)
    }

}

fun createCpu(): Cpu {
    val memIntReq = InterruptRegister() // FF0F interrupt request
    val memLcdControl = LcdControlRegister() // FF40 lcd control
    val memClockSpeed = ClockSpeedRegister() // FF4D clock speed control
    val memIntEnable = InterruptRegister() // FFFF interrupt enable
    val memory = MappedMemoryArea(
            SimpleMemoryArea(0xFF0F),
            memIntReq,
            SimpleMemoryArea(0xFF40 - 0xFF10),
            memLcdControl,
            SimpleMemoryArea(0xFF4D - 0xFF41),
            memClockSpeed,
            SimpleMemoryArea(0xFFFF - 0xFF4E),
            memIntEnable
    )
    return Cpu(memory, memIntReq, memIntEnable, memClockSpeed, memLcdControl)
}

fun <T : Number> assertRegister(register: IRegister<T>, expected: T, message: String) {
    Assertions.assertEquals(expected, register.read(), message)
}

fun assertMemory(cpu: Cpu, addr: Int, expected: Byte, message: String) {
    Assertions.assertEquals(expected, cpu.memory.read(addr, true), message)
}

fun assertFlag(cpu: Cpu, flag: KProperty1<FlagRegister, Boolean>, expected: Boolean, message: String) {
    Assertions.assertEquals(expected, flag.get(cpu.regF), message)
}