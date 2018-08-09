package io.github.phantamanta44.koboi.backtrace

import io.github.phantamanta44.koboi.cpu.Cpu
import io.github.phantamanta44.koboi.cpu.FlagRegister
import io.github.phantamanta44.koboi.cpu.IRegister
import io.github.phantamanta44.koboi.util.toShortHex
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt

fun Boolean.ocb(): String = if (this) "T" else "F"

interface IArgument {

    fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int

}

open class ArgumentRegister8(registerName: String) : IArgument {

    protected val register: (Cpu) -> IRegister<Byte> = when (registerName) {
        "A" -> Cpu::regA
        "F" -> Cpu::regF
        "B" -> Cpu::regB
        "C" -> Cpu::regC
        "D" -> Cpu::regD
        "E" -> Cpu::regE
        "H" -> Cpu::regH
        "L" -> Cpu::regL
        else -> throw IllegalArgumentException(registerName)
    }

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        args.add(ArgValueByte(register(cpu).read()))
        return pc
    }

}

open class ArgumentRegister16(registerName: String) : IArgument {

    protected val register: (Cpu) -> IRegister<Short> = when (registerName) {
        "AF" -> Cpu::regAF
        "BC" -> Cpu::regBC
        "DE" -> Cpu::regDE
        "HL" -> Cpu::regHL
        "PC" -> Cpu::regPC
        "SP" -> Cpu::regSP
        else -> throw IllegalArgumentException(registerName)
    }

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        args.add(ArgValueShort(register(cpu).read()))
        return pc
    }

}

class ArgumentRegisterPointer(registerName: String) : ArgumentRegister16(registerName) {

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        val addr = register(cpu).read().toUnsignedInt()
        args.add(ArgValuePointer(addr, cpu.memory.read(addr, true)))
        return pc
    }

}

class ArgumentRegisterHighPointer(registerName: String) : ArgumentRegister8(registerName) {

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        val addr = register(cpu).read().toUnsignedInt() + 0xFF00
        args.add(ArgValuePointer(addr, cpu.memory.read(addr, true)))
        return pc
    }

}

class ArgumentImmediatePointer : IArgument {

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        val addr = cpu.memory.readShort(pc, true).toUnsignedInt()
        args.add(ArgValuePointer(addr, cpu.memory.read(addr, true)))
        return pc + 2
    }

}

class ArgumentImmediateHighPointer : IArgument {

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        val addr = (cpu.memory.read(pc, true).toUnsignedInt() + 0xFF00)
        args.add(ArgValuePointer(addr, cpu.memory.read(addr, true)))
        return pc + 1
    }

}


class ArgumentImmediateU8 : IArgument {

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        args.add(ArgValueByte(cpu.memory.read(pc, true)))
        return pc + 1
    }

}

class ArgumentImmediateU16 : IArgument {

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        args.add(ArgValueShort(cpu.memory.readShort(pc, true)))
        return pc + 2
    }

}

class ArgumentImmediateI8: IArgument {

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        args.add(ArgValueSigned(cpu.memory.read(pc, true)))
        return pc + 1
    }

}

class ArgumentFlag(flagName: String) : IArgument {

    private val flag: (FlagRegister) -> Boolean = when (flagName) {
        "C" -> FlagRegister::kC
        "NC" -> { flags -> !flags.kC }
        "Z" -> FlagRegister::kZ
        "NZ" -> { flags -> !flags.kZ }
        else -> throw IllegalArgumentException(flagName)
    }

    override fun read(cpu: Cpu, pc: Int, args: MutableList<IArgumentValue>): Int {
        args.add(ArgValueBoolean(flag(cpu.regF)))
        return pc
    }

}

interface IArgumentValue {

    fun stringify(): String

}

fun List<IArgumentValue>.stringify(): String = joinToString(" ", transform = IArgumentValue::stringify)

class ArgValueByte(private val data: Byte) : IArgumentValue {

    override fun stringify(): String = data.toUnsignedHex()
    
}

class ArgValueShort(private val data: Short) : IArgumentValue {
    
    override fun stringify(): String = data.toUnsignedHex()
    
}

class ArgValueSigned(private val data: Byte) : IArgumentValue {
    
    override fun stringify(): String = data.toString()
    
}

class ArgValueBoolean(private val data: Boolean) : IArgumentValue {

    override fun stringify(): String = data.ocb()

}

class ArgValuePointer(private val addr: Int, private val data: Byte) : IArgumentValue {
    
    override fun stringify(): String = "${addr.toShortHex()}=${data.toUnsignedHex()}"
    
}