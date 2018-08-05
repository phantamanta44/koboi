package io.github.phantamanta44.koboi.graphics

import io.github.phantamanta44.koboi.cpu.Cpu

interface IDisplay {

    fun show(deathCallback: () -> Unit)

    fun kill()

    fun writePixel(x: Int, y: Int, colour: Int)

}

interface IScanLineUploader {

    fun renderScanLine(scanLine: Int, display: IDisplay, cpu: Cpu)

}