package io.github.phantamanta44.koboi.plugin.artemis.breakpoint

import io.github.phantamanta44.koboi.Loggr
import io.github.phantamanta44.koboi.debug.ICpuAccess
import io.github.phantamanta44.koboi.debug.IDebugTarget
import io.github.phantamanta44.koboi.plugin.artemis.parseEnum
import io.github.phantamanta44.koboi.util.toUnsignedHex
import io.github.phantamanta44.koboi.util.toUnsignedInt
import org.json.JSONObject

enum class CpuRegister8(val getter: (ICpuAccess) -> Byte) {

    A(ICpuAccess::regA),
    B(ICpuAccess::regB),
    C(ICpuAccess::regC),
    D(ICpuAccess::regD),
    E(ICpuAccess::regE),
    H(ICpuAccess::regH),
    L(ICpuAccess::regL)

}

enum class CpuRegister16(val getter: (ICpuAccess) -> Short) {

    AF(ICpuAccess::regAF),
    BC(ICpuAccess::regBC),
    DE(ICpuAccess::regDE),
    HL(ICpuAccess::regHL),
    PC(ICpuAccess::regPC),
    SP(ICpuAccess::regSP)

}

const val K_REGISTER = "reg"
const val K_COMPARISON = "comp"
const val K_VALUE = "val"

object BPTypeRegister8 : IBreakpointProvider<BPTypeRegister8.Impl> {

    override val identifier: String
        get() = "cpu_reg8"
    override val name: String
        get() = "8-Bit Register"
    override val category: BreakpointCategory
        get() = BreakpointCategory.CPU

    override fun create(): Impl = Impl()

    override fun deserialize(dto: JSONObject): Impl? {
        return parseEnum<CpuRegister8>(dto.optString(K_REGISTER))?.let { reg ->
            parseEnum<ComparisonType>(dto.optString(K_COMPARISON))?.let { comp ->
                Impl(reg, comp, dto.optInt(K_VALUE).toByte())
            }
        }
    }

    data class Impl(private var register: CpuRegister8 = CpuRegister8.B,
                    private var comparison: ComparisonType = ComparisonType.EQ,
                    @BPParam("Value") private var value: Byte = 0) : IBreakpoint<Impl> {

        override fun isMet(target: IDebugTarget): Boolean {
            return comparison.comparator(register.getter(target.cpu).toUnsignedInt(), value.toUnsignedInt())
        }

        override val asString: String
            get() = "${register.name} ${comparison.symbol} ${value.toUnsignedHex()}h"
        override val serialized: JSONObject
            get() = JSONObject()
                    .put(K_REGISTER, register)
                    .put(K_COMPARISON, comparison)
                    .put(K_VALUE, value.toUnsignedInt())

        override fun duplicate(): Impl = copy()

        override fun assimilate(o: Impl) {
            register = o.register
            comparison = o.comparison
            value = o.value
        }

    }

}

object BPTypeRegister16 : IBreakpointProvider<BPTypeRegister16.Impl> {

    override val identifier: String
        get() = "cpu_reg16"
    override val name: String
        get() = "16-Bit Register"
    override val category: BreakpointCategory
        get() = BreakpointCategory.CPU

    override fun create(): Impl = Impl()

    override fun deserialize(dto: JSONObject): Impl? {
        return parseEnum<CpuRegister16>(dto.optString(K_REGISTER))?.let { reg ->
            parseEnum<ComparisonType>(dto.optString(K_COMPARISON))?.let { comp ->
                Impl(reg, comp, dto.optInt(K_VALUE).toShort())
            }
        }
    }

    data class Impl(private var register: CpuRegister16 = CpuRegister16.PC,
                       private var comparison: ComparisonType = ComparisonType.EQ,
                       @BPParam("Value") private var value: Short = 0) : IBreakpoint<Impl> {

        override fun isMet(target: IDebugTarget): Boolean {
            return comparison.comparator(register.getter(target.cpu).toUnsignedInt(), value.toUnsignedInt())
        }

        override val asString: String
            get() = "${register.name} ${comparison.symbol} ${value.toUnsignedHex()}h"
        override val serialized: JSONObject
            get() = JSONObject()
                    .put(K_REGISTER, register)
                    .put(K_COMPARISON, comparison)
                    .put(K_VALUE, value.toUnsignedInt())

        override fun duplicate(): Impl = copy()

        override fun assimilate(o: Impl) {
            register = o.register
            comparison = o.comparison
            value = o.value
        }

    }

}