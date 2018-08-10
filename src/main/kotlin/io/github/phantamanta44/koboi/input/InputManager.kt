package io.github.phantamanta44.koboi.input

import io.github.phantamanta44.koboi.memory.JoypadRegister

class InputManager(private val memInput: JoypadRegister, private val input: IInputProvider) {

    private val newState: BooleanArray = BooleanArray(4, { false })

    fun cycle() {
        newState.fill(false)
        if (!memInput.enableButtons) {
            for (button in ButtonType.values()) {
                newState[button.bit] = newState[button.bit] || input.readButton(button)
            }
        }
        if (!memInput.enableDPad) {
            for (dir in JoypadDir.values()) {
                newState[dir.bit] = newState[dir.bit] || input.readJoypad(dir)
            }
        }
        for (bit in 0..3) memInput.writeBit(bit, !newState[bit])
    }

}