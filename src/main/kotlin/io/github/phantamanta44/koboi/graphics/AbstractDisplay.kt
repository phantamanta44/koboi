package io.github.phantamanta44.koboi.graphics

interface IDisplay {

    fun show(deathCallback: () -> Unit)

    fun kill()

    fun writePixel(x: Int, y: Int, r: Int, g: Int, b: Int)

    fun vBlank()

    fun setDisplayEnabled(enabled: Boolean)

}

interface IScanLineUploader {

    fun renderScanLine(y: Int)

}