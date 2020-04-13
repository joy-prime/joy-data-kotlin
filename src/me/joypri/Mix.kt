package me.joypri

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A heterogeneous map from `Key<V>` to `V`.
 *
 * Subclasses can have `val` properties that are delegated to `Key<V>`, in which case those properties
 * will take on the values of the corresponding keys.
 */
open class Mix(vararg entries: Part) {
    val valueByQualifiedName: Map<String, Any> =
        entries.map { Pair(it.keyName, it.value) }.toMap()

    inline operator fun <reified V> get(role: Role<V>): V? {
        return when (val value = this.valueByQualifiedName[role.qualifiedName]) {
            null -> null
            is V -> value
            else -> throw IllegalStateException(
                "Expected data[${role.qualifiedName}] to have a ${V::class}, but there's a ${value::class}"
            )
        }
    }
}

open class Role<V> {
    val qualifiedName: String =
        (this::class.qualifiedName
            ?: throw IllegalStateException("Key class must have a qualifiedName; it is null'"))

    operator fun provideDelegate(thisRef: Mix, prop: KProperty<*>): RoleDelegate<V> {
        val anyValue = thisRef.valueByQualifiedName[qualifiedName]
            ?: throw IllegalArgumentException("missing key $qualifiedName")
        @Suppress("UNCHECKED_CAST")
        return RoleDelegate(anyValue as V)
    }
}

class RoleDelegate<V>(private val value: V) : ReadOnlyProperty<Mix, V> {
    override operator fun getValue(thisRef: Mix, property: KProperty<*>) = value
}

data class Part internal constructor(val keyName: String, val value: Any)

infix fun <V : Any> Role<V>.to(value: V): Part {
    return Part(qualifiedName, value)
}