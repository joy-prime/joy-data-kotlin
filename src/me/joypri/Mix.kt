package me.joypri

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * An immutable heterogeneous map from `Role<V>` to `V`.
 *
 * Subclasses can have `val` properties that are delegated to `Role<V>`, in which case those properties
 * will take on the values of the corresponding roles.
 */
open class Mix(vararg parts: Part) {
    val valueByQualifiedName: Map<String, Any> =
        parts.map { Pair(it.keyName, it.value) }.toMap()

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

@DslMarker
annotation class RemixMarker

/**
 * A mutable heterogeneous map from `Role<V>` to `V`, designed as a builder for a `Mix`, and with DSL support.
 *
 * A `var` property in a subclass of `Remix` can be delegated to `Role<V>`, providing read-write access
 * to the value associated with that `Role`. Such a property has type `V?`, since the value may or may not
 * be present.
 *
 * Alternatively, a `val` property in a subclass of `Remix` can be delegated to `dsl(Role<V>)`, in which
 * case the property is declared as a DSL-suitable function. The property's type is then `(V.() -> ()) -> Unit`.
 * The property's value is a function that constructs a `V` and provides it as the receiver of
 * the property's function argument.
 */
@RemixMarker
open class Remix(vararg parts: Part) {
    val valueByQualifiedName: MutableMap<String, Any> =
        HashMap<String, Any>(parts.size).apply {
            for (entry in parts) {
                this[entry.keyName] = entry.value
            }
        }

    inline operator fun <reified V> get(role: Role<V>): V? {
        return when (val value = this.valueByQualifiedName[role.qualifiedName]) {
            null -> null
            is V -> value
            else -> throw IllegalStateException(
                "Expected data[${role.qualifiedName}] to have a ${V::class}, but there's a ${value::class}"
            )
        }
    }

    operator fun <V> set(role: Role<V>, value: V) {
        if (value == null) {
            valueByQualifiedName.remove(role.qualifiedName)
        } else {
            @Suppress("RemoveExplicitTypeArguments") // necessary to make compiler happy
            valueByQualifiedName.set<String, Any>(role.qualifiedName, value)
        }
    }
}


open class Role<V> {
    val qualifiedName: String =
        (this::class.qualifiedName
            ?: throw IllegalStateException("Key class must have a qualifiedName; it is null'"))

    operator fun provideDelegate(thisRef: Mix, prop: KProperty<*>): RoleMixDelegate<V> {
        val anyValue = thisRef.valueByQualifiedName[qualifiedName]
            ?: throw IllegalArgumentException("missing key $qualifiedName")
        @Suppress("UNCHECKED_CAST")
        return RoleMixDelegate(anyValue as V)
    }

    operator fun provideDelegate(thisRef: Remix, prop: KProperty<*>): RoleRemixDelegate<V?> {
        @Suppress("UNCHECKED_CAST")
        return RoleRemixDelegate(qualifiedName)
    }
}

class RoleMixDelegate<V>(private val value: V) : ReadOnlyProperty<Mix, V> {
    override operator fun getValue(thisRef: Mix, property: KProperty<*>) = value
}

class RoleRemixDelegate<VQ>(private val qualifiedName: String) : ReadWriteProperty<Remix, VQ> {
    override operator fun getValue(thisRef: Remix, property: KProperty<*>): VQ {
        @Suppress("UNCHECKED_CAST")
        return thisRef.valueByQualifiedName[qualifiedName] as VQ
    }

    override operator fun setValue(thisRef: Remix, property: KProperty<*>, value: VQ) {
        if (value == null) {
            thisRef.valueByQualifiedName.remove(qualifiedName)
        } else {
            @Suppress("RemoveExplicitTypeArguments") // necessary to satisfy compiler
            thisRef.valueByQualifiedName.set<String, Any>(qualifiedName, value)
        }
    }
}

data class Part internal constructor(val keyName: String, val value: Any)

infix fun <V : Any> Role<V>.to(value: V): Part {
    return Part(qualifiedName, value)
}