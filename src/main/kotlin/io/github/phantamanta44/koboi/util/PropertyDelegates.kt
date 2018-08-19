package io.github.phantamanta44.koboi.util

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

object PropDel {

    fun <E, T>r(prop: KProperty0<T>): IPropertyFunctor<E, T> {
        return PropertyWrapper(prop)
    }

    private class PropertyWrapper<E, out T>(private val prop: KProperty0<T>) : IPropertyFunctor<E, T> {

        override fun getValue(thisRef: E, property: KProperty<*>): T = prop.get()

    }

    fun <E, T>rw(prop: KMutableProperty0<T>): IMutablePropertyFunctor<E, T> {
        return MutablePropertyWrapper(prop)
    }

    private open class MutablePropertyWrapper<E, T>(protected val prop: KMutableProperty0<T>) : IMutablePropertyFunctor<E, T> {

        override fun getValue(thisRef: E, property: KProperty<*>): T = prop.get()

        override fun setValue(thisRef: E, property: KProperty<*>, value: T) = prop.set(value)

    }

    fun <E, T>observe(value: T, observer: (T) -> Unit): IMutablePropertyFunctor<E, T> {
        return ObservableProperty(value, observer)
    }

    private class ObservableProperty<E, T>(private var value: T, private val observer: (T) -> Unit) : IMutablePropertyFunctor<E, T> {

        override fun getValue(thisRef: E, property: KProperty<*>): T = value

        override fun setValue(thisRef: E, property: KProperty<*>, value: T) {
            this.value = value
            observer(value)
        }

    }

}

interface IPropertyFunctor<E, out T> : ReadOnlyProperty<E, T> {

    fun <U>map(mapper: (T) -> U): IPropertyFunctor<E, U> = DelegatingMappedProperty(this, mapper)

    class DelegatingMappedProperty<E, T, U>(private val upstream: ReadOnlyProperty<E, T>, private val mapper: (T) -> U) : IPropertyFunctor<E, U> {

        override fun getValue(thisRef: E, property: KProperty<*>): U = mapper(upstream.getValue(thisRef, property))

    }

}

interface IMutablePropertyFunctor<E, T> : ReadWriteProperty<E, T> {

    fun <U>map(forwards: (T) -> U, backwards: (U) -> T): IMutablePropertyFunctor<E, U> = DelegatingMappedMutableProperty(this, forwards, backwards)

    class DelegatingMappedMutableProperty<E, T, U>(private val upstream: ReadWriteProperty<E, T>,
                                                   private val forwards: (T) -> U, private val backwards: (U) -> T) : IMutablePropertyFunctor<E, U> {

        override fun getValue(thisRef: E, property: KProperty<*>): U = forwards(upstream.getValue(thisRef, property))

        override fun setValue(thisRef: E, property: KProperty<*>, value: U) = upstream.setValue(thisRef, property, backwards(value))

    }

}