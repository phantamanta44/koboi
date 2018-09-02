package io.github.phantamanta44.koboi.memory

import java.io.RandomAccessFile

class PersistentMemoryArea(length: Int, private var offset: Int = 0, fileName: String? = null) : SimpleMemoryArea(length) {

    private val file: RandomAccessFile? = fileName?.let { RandomAccessFile(it, "rw") }

    init {
        file?.let {
            it.seek(offset.toLong())
            it.read(memory)
        }
    }

    override fun write(addr: Int, vararg values: Byte, start: Int, length: Int, direct: Boolean) {
        super.write(addr, *values, start = start, length = length, direct = direct)
        file?.let {
            it.seek((offset + addr).toLong())
            it.write(memory, addr, length)
        }
    }

}