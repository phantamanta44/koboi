package io.github.phantamanta44.koboi.plugin.artemis

import io.github.phantamanta44.koboi.util.toShortHex
import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.Property
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.control.TableCell
import java.lang.reflect.Method
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

fun <E> IntegerProperty.delegate(): ReadWriteProperty<E, Int> = JfxIntPropWrapper(this)

class JfxIntPropWrapper<E>(private val prop: IntegerProperty) : ReadWriteProperty<E, Int> {

    override fun getValue(thisRef: E, property: KProperty<*>): Int = prop.value

    override fun setValue(thisRef: E, property: KProperty<*>, value: Int) {
        prop.value = value
    }

}

fun <E, T> Property<T>.delegate(): ReadWriteProperty<E, T> = JfxPropWrapper(this)

class JfxPropWrapper<E, T>(private val prop: Property<T>) : ReadWriteProperty<E, T> {

    override fun getValue(thisRef: E, property: KProperty<*>): T = prop.value

    override fun setValue(thisRef: E, property: KProperty<*>, value: T) {
        prop.value = value
    }

}

class BooleanPropWrapper(private val prop: KMutableProperty<Boolean>) : BooleanProperty() {

    private val changeListeners: MutableSet<ChangeListener<in Boolean>> = mutableSetOf()
    private val bindingListener: BindingListener by lazy(::BindingListener)
    private var binding: ObservableValue<out Boolean>? = null

    override fun getName(): String = prop.name

    override fun addListener(listener: ChangeListener<in Boolean>) {
        changeListeners.add(listener)
    }

    override fun addListener(listener: InvalidationListener?) {
        // NO-OP
    }

    override fun getBean(): Any? = null

    override fun set(value: Boolean) = prop.setter.call(value)

    override fun unbind() {
        if (binding != null) {
            binding?.removeListener(bindingListener)
            binding = null
        }
    }

    override fun removeListener(listener: ChangeListener<in Boolean>?) {
        changeListeners.remove(listener)
    }

    override fun removeListener(listener: InvalidationListener?) {
        // NO-OP
    }

    override fun bind(observable: ObservableValue<out Boolean>) {
        observable.addListener(bindingListener)
        binding = observable
    }

    override fun isBound(): Boolean = binding != null

    override fun get(): Boolean = prop.getter.call()

    fun post(old: Boolean, new: Boolean) = changeListeners.forEach { it.changed(this, old, new) }

    private inner class BindingListener : ChangeListener<Boolean> {

        override fun changed(observable: ObservableValue<out Boolean>?, oldValue: Boolean?, newValue: Boolean?) {
            set(newValue!!)
        }

    }

}

abstract class BaseTableCell<S, T> : TableCell<S, T>() {

    override fun updateItem(item: T?, empty: Boolean) {
        if (item === getItem()) return
        super.updateItem(item, empty)
        setCellContents(item)
    }

    abstract fun setCellContents(item: T?)

}

class AddressCell<S> : BaseTableCell<S, Int>() {

    override fun setCellContents(item: Int?) {
        super.setText(if (item == -1) "----" else item?.toShortHex())
    }

}

open class StringDataCell<S> : BaseTableCell<S, String>() {

    override fun setCellContents(item: String?) {
        super.setText(item)
    }

}

val mEnumConstantFactory: Method by lazy {
    val method = Class::class.java.getDeclaredMethod("enumConstantDirectory")
    method.isAccessible = true
    method
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Enum<T>>parseEnum(name: String): T? {
    return (mEnumConstantFactory.invoke(T::class.java) as Map<String, T>)[name]
}