package io.github.phantamanta44.koboi.memory

import io.github.phantamanta44.koboi.util.PropDel
import kotlin.math.min
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class SimpleMemoryArea(final override val length: Int) : DirectObservableMemoryArea() {

    protected val memory: ByteArray = ByteArray(length)

    override fun read(addr: Int, direct: Boolean): Byte = memory[addr]

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = SimpleMemoryRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        System.arraycopy(values, start, memory, addr, length)
        directObserver.onMemMutate(addr, length)
    }

    override fun typeAt(addr: Int): String = "Simple[$length]"

    private inner class SimpleMemoryRange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = memory[first + index]

        override fun toArray(): ByteArray = memory.copyOfRange(first, last)

        override val length: Int
            get() = last - first

    }

}

open class ControlMemoryArea(override val length: Int, private val callback: (Byte) -> Unit) : DirectObservableMemoryArea() {

    override fun read(addr: Int, direct: Boolean): Byte = 0xFF.toByte()

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = NoopMemoryRange(lastAddr - firstAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        callback(values[0])
    }

    override fun typeAt(addr: Int): String = "Control[$length]"

}

class ToggleableMemoryArea(val backing: DirectObservableMemoryArea, var state: Boolean) : DirectObservableMemoryArea() {

    override var directObserver: IDirectMemoryObserver by PropDel.rw(backing::directObserver)

    override val length: Int
        get() = backing.length

    override fun read(addr: Int, direct: Boolean): Byte = if (state || direct) backing.read(addr, direct) else 0xFF.toByte()

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = ToggleableMemoryRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        if (state || direct) backing.write(addr, *values, start = start, length = length, direct = direct)
    }

    override fun typeAt(addr: Int): String = "Toggle{ ${backing.typeAt(addr)} }"

    private inner class ToggleableMemoryRange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = if (state) backing.read(first + index) else 0xFF.toByte()

        override fun toArray(): ByteArray {
            return if (state) backing.readRange(first, last).toArray() else ByteArray(length, { 0xFF.toByte() })
        }

        override val length: Int
            get() = last - first

    }

}

class ToggleableWriteMemoryArea(private val backing: DirectObservableMemoryArea, var state: Boolean) : DirectObservableMemoryArea() {

    override var directObserver: IDirectMemoryObserver by PropDel.rw(backing::directObserver)

    override val length: Int
        get() = backing.length

    override fun read(addr: Int, direct: Boolean): Byte = backing.read(addr, direct)

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = backing.readRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        if (state || direct) backing.write(addr, *values, start = start, length = length, direct = direct)
    }

    override fun typeAt(addr: Int): String = "WriteToggle{ ${backing.typeAt(addr)} }"

}

class EchoMemoryArea(private val delegate: DirectObservableMemoryArea, override val length: Int) : DirectObservableMemoryArea() {

    override var directObserver: IDirectMemoryObserver by PropDel.rw(delegate::directObserver)

    override fun read(addr: Int, direct: Boolean): Byte = delegate.read(addr, direct)

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = delegate.readRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        delegate.write(addr, *values, start = start, length = length, direct = direct)
    }

    override fun typeAt(addr: Int): String = "Echo[$length]"

}

class DisjointMemoryArea(private val readDelegate: DirectObservableMemoryArea, private val writeDelegate: IMemoryArea) : DirectObservableMemoryArea() {

    override var directObserver: IDirectMemoryObserver by PropDel.rw(readDelegate::directObserver)

    override val length: Int
        get() = readDelegate.length

    init {
        if (readDelegate.length != writeDelegate.length) {
            throw IllegalArgumentException("Mismatched lengths: r ${readDelegate.length} vs w ${writeDelegate.length}")
        }
    }

    override fun read(addr: Int, direct: Boolean): Byte = readDelegate.read(addr, direct)

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = readDelegate.readRange(firstAddr, lastAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        writeDelegate.write(addr, *values, start = start, length = length, direct = direct)
    }

    override fun typeAt(addr: Int): String = "Disjoint{ ${readDelegate.typeAt(addr)}, ${writeDelegate.typeAt(addr)} }"

}

class UnusableMemoryArea(override val length: Int) : DirectObservableMemoryArea() {

    override fun read(addr: Int, direct: Boolean): Byte = 0xFF.toByte()

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = NoopMemoryRange(lastAddr - firstAddr)

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        // NO-OP
    }

    override fun typeAt(addr: Int): String = "Unusable[$length]"

}

class MappedMemoryArea(vararg memSegments: DirectObservableMemoryArea) : DirectObservableMemoryArea() {

    private val segments: List<Pair<Int, DirectObservableMemoryArea>>
    private val indexMappings: IntArray = memSegments.withIndex()
            .flatMap { seg -> (1..seg.value.length).map { seg.index } }
            .toIntArray()
    override val length: Int

    init {
        val segList = mutableListOf<Pair<Int, DirectObservableMemoryArea>>()
        var lenAgg = 0
        for (segment in memSegments) {
            segment.directObserver = MappedSegmentObserver(lenAgg)
            segList.add(lenAgg to segment)
            lenAgg += segment.length
        }
        segments = segList
        length = lenAgg
    }

    override fun read(addr: Int, direct: Boolean): Byte {
        val segment = segments[indexMappings[addr]]
        return segment.second.read(addr - segment.first)
    }

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange {
        return MappedMemoryRange(firstAddr, lastAddr)
    }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        var toWrite = length
        var index = indexMappings[addr]
        val firstSegment = segments[index]
        val firstSegmentMaxWrite = min(firstSegment.second.length - (addr - firstSegment.first), toWrite)
        firstSegment.second.write(addr - firstSegment.first, *values, start = start, length = firstSegmentMaxWrite, direct = direct)
        if (firstSegmentMaxWrite != toWrite) {
            toWrite -= firstSegmentMaxWrite
            while (toWrite > 0) {
                val segment = segments[++index]
                val segmentMaxWrite = min(segment.second.length, toWrite)
                segment.second.write(0, *values, start = length - toWrite, length = segmentMaxWrite, direct = direct)
                toWrite -= segmentMaxWrite
            }
        }
    }

    override fun typeAt(addr: Int): String {
        val segment = segments[indexMappings[addr]]
        return "Mapped{ ${segment.second.typeAt(addr - segment.first)} }"
    }

    private inner class MappedMemoryRange(val first: Int, val last: Int) : IMemoryRange {

        override fun get(index: Int): Byte = read(first + index)

        override fun toArray(): ByteArray = ByteArray(length, ::get) // FIXME god is this inefficient

        override val length: Int
            get() = last - first

    }

    private inner class MappedSegmentObserver(private val addrOffset: Int) : IDirectMemoryObserver {

        override fun onMemMutate(addr: Int, length: Int) {
            directObserver.onMemMutate(addr + addrOffset, length)
        }

    }

}

open class SingleByteMemoryArea : DirectObservableMemoryArea() {

    override val length: Int
        get() = 1

    private val range: IMemoryRange = SingleByteMemoryRange()

    var value: Byte by PropDel.observe(0.toByte()) { directObserver.onMemMutate(0, 1) }

    override fun read(addr: Int, direct: Boolean): Byte = value // don't even bother validating args

    override fun readRange(firstAddr: Int, lastAddr: Int): IMemoryRange = range

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        value = values[0]
    }

    override fun typeAt(addr: Int): String = "RSingle"

    inner class SingleByteMemoryRange : IMemoryRange {

        override val length: Int
            get() = 1

        override fun get(index: Int): Byte = value

        override fun toArray(): ByteArray = byteArrayOf(value)

    }

}

open class BitwiseRegister(private val writableMask: Int = 0xFF) : SingleByteMemoryArea() {

    private val nonWritableMask = writableMask.inv() and 0xFF

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        value = if (direct) {
            values[0]
        } else {
            val mask = values[0].toInt()
            ((value.toInt() or (mask and writableMask)) and (mask or nonWritableMask)).toByte()
        }
    }

    override fun typeAt(addr: Int): String = "RBitwise"

    fun readBit(bit: Int): Boolean = (value.toInt() and (1 shl bit)) != 0

    open fun writeBit(bit: Int, flag: Boolean) {
        value = if (flag) {
            (value.toInt() or (1 shl bit)).toByte()
        } else {
            (value.toInt() and (1 shl bit).inv()).toByte()
        }
    }

    fun delegateBit(bit: Int): ReadWriteProperty<BitwiseRegister, Boolean> = BitDelegate(bit)

    inner class BitDelegate(private val bit: Int) : ReadWriteProperty<BitwiseRegister, Boolean> {

        override fun getValue(thisRef: BitwiseRegister, property: KProperty<*>): Boolean = readBit(bit)

        override fun setValue(thisRef: BitwiseRegister, property: KProperty<*>, value: Boolean) = writeBit(bit, value)

    }

    fun readMaskedInt(mask: Int, shift: Int): Int = (value.toInt() and mask) ushr shift

    fun writeMaskedInt(mask: Int, shift: Int, num: Int) {
        value = ((value.toInt() and mask.inv()) or (num shl shift)).toByte()
    }

    fun delegateMaskedInt(mask: Int, shift: Int) : ReadWriteProperty<BitwiseRegister, Int> = BitmaskDelegate(mask, shift)

    inner class BitmaskDelegate(private val mask: Int, private val shift: Int) : ReadWriteProperty<BitwiseRegister, Int> {

        override fun getValue(thisRef: BitwiseRegister, property: KProperty<*>): Int = readMaskedInt(mask, shift)

        override fun setValue(thisRef: BitwiseRegister, property: KProperty<*>, value: Int) = writeMaskedInt(mask, shift, value)

    }

}

open class BiDiBitwiseRegister(writableMask: Int = 0xFF, readableMask: Int) : BitwiseRegister(writableMask) {

    private val unreadableMask: Int = readableMask.inv() and 0xFF

    override fun read(addr: Int, direct: Boolean): Byte {
        return if (direct) {
            super.read(addr, direct)
        } else {
            (super.read(addr, direct).toInt() or unreadableMask).toByte()
        }
    }
}

open class ResettableRegister : SingleByteMemoryArea() {

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        value = if (direct) values[0] else 0
    }

    override fun typeAt(addr: Int): String = "RReset"

}

open class ObservableRegister(private val callback: (Byte) -> Unit) : SingleByteMemoryArea() {

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        value = values[0]
        callback(values[0])
    }

    override fun typeAt(addr: Int): String = "RObserve"

}

class MaskedObservableRegister(readableMask: Int = 0xFF, callback: (Byte) -> Unit)
    : ObservableRegister(callback) {

    private val unreadableMask: Int = readableMask.inv() and 0xFF

    override fun read(addr: Int, direct: Boolean): Byte {
        return if (direct) {
            super.read(addr, direct)
        } else {
            (super.read(addr, direct).toInt() or unreadableMask).toByte()
        }
    }

    override fun typeAt(addr: Int): String = "RMaskObserve"

}

class NoopMemoryRange(override val length: Int) : IMemoryRange {

    override fun get(index: Int): Byte = 0xFF.toByte()

    override fun toArray(): ByteArray {
        return ByteArray(length, { 0xFF.toByte() })
    }

}