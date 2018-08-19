package io.github.phantamanta44.koboi.plugin.artemis

import java.awt.Container
import javax.swing.*

// containers

fun Container.box(axis: Int, constraints: Any? = null, func: Container.() -> Unit) {
    val panel = JPanel(true)
    val layout = BoxLayout(panel, axis)
    panel.layout = layout
    panel.func()
    add(panel, constraints)
}

fun Container.scroll(horizontal: Int = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, vertical: Int = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                     constraints: Any? = null, func: Container.() -> Unit) {
    val viewPane = JPanel(true)
    val pane = JScrollPane(viewPane, vertical, horizontal)
    pane.setViewportView(viewPane)
    viewPane.func()

    add(pane, constraints)
}

// components

// controls

fun Container.button(text: String? = null, icon: Icon? = null, enabled: Boolean = true, constraints: Any? = null, callback: () -> Unit): JButton {
    val button = JButton(text, icon)
    button.isEnabled = enabled
    button.addActionListener { callback() }
    add(button, constraints)
    return button
}