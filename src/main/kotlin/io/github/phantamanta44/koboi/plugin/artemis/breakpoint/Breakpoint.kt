package io.github.phantamanta44.koboi.plugin.artemis.breakpoint

import io.github.phantamanta44.koboi.debug.IDebugTarget
import io.github.phantamanta44.koboi.util.toUnsignedHex
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.util.Callback
import javafx.util.StringConverter
import org.json.JSONObject
import java.util.function.UnaryOperator
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

interface IBreakpointProvider<T : IBreakpoint<T>> {

    val identifier: String

    val name: String

    val category: BreakpointCategory

    fun create(): T

    fun displayEditor(breakpoint: T): T? {
        val bpData = breakpoint.duplicate()
        return Dialog<T>().also { dialog ->
            dialog.title = "Edit Breakpoint"
            dialog.headerText = "Editing Breakpoint: $name"
            dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            dialog.dialogPane.content = VBox(8.0).apply { children.addAll(genConfigurators(bpData)) }
            dialog.resultConverter = Callback { if (it == ButtonType.OK) bpData else null }
        }.showAndWait().orElse(null)
    }

    fun deserialize(dto: JSONObject): T?

}

enum class BreakpointCategory(val desc: String) {

    CPU("CPU"), MEMORY("Memory"), PPU("PPU"), TIMER("Timer"), INPUT("Input")

}

interface IBreakpoint<T : IBreakpoint<T>> {

    fun isMet(target: IDebugTarget): Boolean

    val asString: String

    val serialized: JSONObject

    fun duplicate(): T

    fun assimilate(o: T)

}

enum class ComparisonType(val desc: String, val symbol: String, val comparator: (Int, Int) -> Boolean) {

    EQ("Equal", "=", Int::equals),
    NEQ("Not Equals", "!=", { a, b -> a != b }),
    LT("Less", "<", { a, b -> a < b }),
    LTE("Less or equal", "<=", { a, b -> a <= b }),
    GT("Greater", ">", { a, b -> a > b }),
    GTE("Greater or equal", ">=", { a, b -> a >= b })

}

@Target(AnnotationTarget.PROPERTY)
@Retention
annotation class BPParam(val name: String)

@Suppress("UNCHECKED_CAST")
internal fun <T : Any>genConfigurators(obj: T): List<Node> = obj::class.memberProperties
        .filterIsInstance<KMutableProperty1<T, *>>()
        .mapNotNull { member ->
            member.isAccessible = true
            when (member.returnType.jvmErasure) {
                ComparisonType::class -> {
                    val typedProp = (member as KMutableProperty1<T, ComparisonType>)
                    labelComp("Comparison", dropDown(ComparisonType.values(), typedProp.get(obj), ComparisonType::desc) {
                        typedProp.set(obj, it)
                    })
                }
                CpuRegister8::class -> enumConfig<T, CpuRegister8>("Register", member, obj)
                CpuRegister16::class -> enumConfig<T, CpuRegister16>("Register", member, obj)
                Byte::class -> {
                    val typedProp = (member as KMutableProperty1<T, Byte>)
                    labelComp(member.findAnnotation<BPParam>()?.name ?: member.name,
                            mappingTextBox(typedProp.get(obj), Byte::toUnsignedHex, { it.toInt(16).toByte() }) {
                                typedProp.set(obj, it)
                            })
                }
                Short::class -> {
                    val typedProp = (member as KMutableProperty1<T, Short>)
                    labelComp(member.findAnnotation<BPParam>()?.name ?: member.name,
                            mappingTextBox(typedProp.get(obj), Short::toUnsignedHex, { it.toInt(16).toShort() }) {
                                typedProp.set(obj, it)
                            })
                }
                else -> null
            }
        }

@Suppress("UNCHECKED_CAST")
internal inline fun <E, reified T : Enum<T>>enumConfig(label: String, prop: KMutableProperty1<E, *>, receiver: E): Node {
    val typedProp = (prop as KMutableProperty1<E, T>)
    return labelComp(label, dropDown(enumValues(), typedProp.get(receiver)) { typedProp.set(receiver, it) })
}

internal fun labelComp(label: String, comp: Node): Node = BorderPane().apply {
    left = Label(label).also {
        BorderPane.setAlignment(it, Pos.CENTER_LEFT)
        it.padding = Insets(0.0, 8.0, 0.0, 0.0)
    }
    center = comp.also {
        BorderPane.setAlignment(it, Pos.CENTER_RIGHT)
    }
}

internal fun <T : Any>dropDown(choices: Array<T>, selected: T?  = null, mapper: ((T) -> String)? = null, callback: (T) -> Unit): ChoiceBox<T> {
    return dropDown(choices.asList(), selected, mapper, callback)
}

internal fun <T : Any>dropDown(choices: List<T>, selected: T? = null, mapper: ((T) -> String)? = null, callback: (T) -> Unit): ChoiceBox<T> {
    return ChoiceBox(FXCollections.observableList(choices)).apply {
        selected?.let { value = it }
        mapper?.let {
            val reverseMap = choices.associateBy { choice -> it(choice) }
            converter = object : StringConverter<T>() {
                override fun toString(`object`: T): String = it(`object`)
                override fun fromString(string: String?): T? = reverseMap[string]
            }
        }
        valueProperty().addListener { _, _, newValue -> callback(newValue) }
    }
}

internal fun <T : Any>mappingTextBox(initialValue: T, mapper: (T) -> String, reverse: (String) -> T?, callback: (T) -> Unit): TextField {
    return TextField().apply {
        textFormatter = TextFormatter(object : StringConverter<T>() {
            override fun toString(`object`: T): String = mapper(`object`)
            override fun fromString(string: String): T? = reverse(string)
        }, initialValue, UnaryOperator {
            try {
                if (reverse(it.controlNewText) != null) it else null
            } catch (e: Exception) {
                null
            }
        }).apply {
            valueProperty().addListener { _, _, newValue -> callback(newValue) }
        }
    }
}