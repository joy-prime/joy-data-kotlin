package me.joypri

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.primaryConstructor

/**
 * An immutable heterogeneous map from `Role<V>` to `V`.
 *
 * Subclasses can have `val` properties that are delegated to `Role<V>`, in which case those properties
 * will take on the values of the corresponding roles.
 */
open class Mix(vararg parts: Part) {

    val valueByQualifiedName: Map<String, Any> =
        parts.associate { it.keyName to it.value }

    val foo = parts.hashCode()

    inline operator fun <reified V> get(role: Role<V>): V? {
        return when (val value = this.valueByQualifiedName[role.qualifiedName]) {
            null -> null
            is V -> value
            else -> throw IllegalStateException(
                "Expected data[${role.qualifiedName}] to have a ${V::class}, but there's a ${value::class}"
            )
        }
    }

    operator fun <V> Role<V>.unaryPlus() = OptionalRoleMixDelegateProvider<V>(qualifiedName)

    override fun equals(other: Any?): Boolean {
        return other is Mix && other.javaClass == javaClass
                && other.valueByQualifiedName == valueByQualifiedName
    }

    override fun hashCode(): Int {
        return 31 * valueByQualifiedName.hashCode() + javaClass.hashCode()
    }
}

class OptionalRoleMixDelegateProvider<V>(val qualifiedName: String) {
    operator fun provideDelegate(thisRef: Mix, prop: KProperty<*>): RoleMixDelegate<V?> {
        val anyValue = thisRef.valueByQualifiedName[qualifiedName]
        @Suppress("UNCHECKED_CAST")
        return RoleMixDelegate(anyValue as V?)
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
            parts.associateTo(mutableMapOf()) { it.keyName to it.value }

    val parts get() = valueByQualifiedName.map { Part(it.key, it.value) }

    inline operator fun <reified V> get(role: Role<V>): V? {
        return when (val value = valueByQualifiedName[role.qualifiedName]) {
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
            valueByQualifiedName[role.qualifiedName] = value as Any
        }
    }

    protected fun mixParts(): Array<Part> {
        return parts.map { part ->
            if (part.value is Remix) {
                Part(part.keyName, part.value.toMix())
            } else {
                part
            }
        }.toTypedArray()
    }

    open fun toMix() = Mix(*mixParts())

    operator fun <V> Role<V>.unaryPlus() = RoleRemixDelegateProvider<V?>(qualifiedName)

    operator fun <M : Any, R : Any> MixRole<M, R>.not() = MixRoleRemixDelegateProvider<R>(qualifiedName)

    override fun equals(other: Any?): Boolean {
        return other is Remix && other.javaClass == javaClass
                && other.valueByQualifiedName == valueByQualifiedName
    }

    override fun hashCode(): Int {
        return 31 * valueByQualifiedName.hashCode() + javaClass.hashCode()
    }
}

class RoleRemixDelegateProvider<VN>(private val qualifiedName: String) {
    operator fun provideDelegate(thisRef: Remix, prop: KProperty<*>): RoleRemixDelegate<VN> {
        return RoleRemixDelegate(qualifiedName)
    }
}

class RoleRemixDelegate<VN>(private val qualifiedName: String) : ReadWriteProperty<Remix, VN> {
    override operator fun getValue(thisRef: Remix, property: KProperty<*>): VN {
        @Suppress("UNCHECKED_CAST")
        return thisRef.valueByQualifiedName[qualifiedName] as VN
    }

    override operator fun setValue(thisRef: Remix, property: KProperty<*>, value: VN) {
        if (value == null) {
            thisRef.valueByQualifiedName.remove(qualifiedName)
        } else {
            thisRef.valueByQualifiedName[qualifiedName] = value as Any
        }
    }
}

@Suppress("unused") // This is just a lie; R is used in the upcoming extension function.
class MixRoleRemixDelegateProvider<R>(val qualifiedName: String)

inline operator fun <reified R : Any> MixRoleRemixDelegateProvider<R>.provideDelegate(
    thisRef: Remix,
    prop: KProperty<*>
): MixRoleRemixDelegate<R> {
    val ctor = R::class.primaryConstructor
        ?: throw IllegalArgumentException("${R::class} has no primary constructor")
    @Suppress("UNCHECKED_CAST")
    return MixRoleRemixDelegate(qualifiedName) { ctor.call(arrayOf<Part>()) }
}

class MixRoleRemixDelegate<R : Any>(
    private val qualifiedName: String,
    private val cons: () -> R
) : ReadWriteProperty<Remix, R> {
    override operator fun getValue(thisRef: Remix, property: KProperty<*>): R {
        @Suppress("UNCHECKED_CAST")
        return thisRef.valueByQualifiedName.computeIfAbsent(qualifiedName) { cons() } as R
    }

    override operator fun setValue(thisRef: Remix, property: KProperty<*>, value: R) {
        thisRef.valueByQualifiedName[qualifiedName] = value
    }
}

abstract class Named {
    val qualifiedName: String =
        (this::class.qualifiedName
            ?: throw IllegalStateException("Named subclass must have a qualifiedName; it is null'"))
}

abstract class Role<V> : Named() {
    operator fun provideDelegate(thisRef: Mix, prop: KProperty<*>): RoleMixDelegate<V> {
        val anyValue = thisRef.valueByQualifiedName[qualifiedName]
            ?: throw IllegalArgumentException("missing key $qualifiedName")
        @Suppress("UNCHECKED_CAST")
        return RoleMixDelegate(anyValue as V)
    }
}

class RoleMixDelegate<V>(private val value: V) : ReadOnlyProperty<Mix, V> {
    override operator fun getValue(thisRef: Mix, property: KProperty<*>) = value
}

data class Part internal constructor(val keyName: String, val value: Any)

infix fun <V : Any> Role<V>.to(value: V): Part {
    return Part(qualifiedName, value)
}

abstract class MixRole<M : Any, R : Any> : Role<M>()
