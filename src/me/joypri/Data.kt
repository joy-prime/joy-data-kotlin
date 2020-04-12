package me.joypri

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A heterogeneous map from `Key<V>` to `V`.
 *
 * Subclasses can have `val` properties that are delegated to `Key<V>`, in which case those properties
 * will take on the values of the corresponding keys.
 */
open class Data(vararg entries: KeyValue) {
    val valueByQualifiedName: Map<String, Any> =
        entries.map { Pair(it.keyName, it.value) }.toMap()

    inline operator fun <reified V> get(key: Key<V>): V? {
        return when (val value = this.valueByQualifiedName[key.qualifiedName]) {
            null -> null
            is V -> value
            else -> throw IllegalStateException(
                "Expected data[${key.qualifiedName}] to have a ${V::class}, but there's a ${value::class}"
            )
        }
    }
}

open class Key<V> {
    val qualifiedName: String =
        (this::class.qualifiedName
            ?: throw IllegalStateException("Key class must have a qualifiedName; it is null'"))

    operator fun provideDelegate(thisRef: Data, prop: KProperty<*>): KeyDelegate<V> {
        val anyValue = thisRef.valueByQualifiedName[qualifiedName]
            ?: throw IllegalArgumentException("missing key $qualifiedName")
        @Suppress("UNCHECKED_CAST")
        return KeyDelegate(anyValue as V)
    }
}

class KeyDelegate<V>(private val value: V) : ReadOnlyProperty<Data, V> {
    override operator fun getValue(thisRef: Data, property: KProperty<*>) = value
}

data class KeyValue internal constructor(val keyName: String, val value: Any)

infix fun <V : Any> Key<V>.to(value: V): KeyValue {
    return KeyValue(qualifiedName, value)
}