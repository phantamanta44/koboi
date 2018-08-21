package io.github.phantamanta44.koboi.input

interface IInputProvider {

    fun readButton(button: ButtonType): Boolean

    fun readJoypad(dir: JoypadDir): Boolean

    fun kill()

}

enum class ButtonType(val bit: Int) {

    A(0),
    B(1),
    START(3),
    SELECT(2)

}

enum class JoypadDir(val bit: Int) {

    UP(2),
    LEFT(1),
    DOWN(3),
    RIGHT(0)

}