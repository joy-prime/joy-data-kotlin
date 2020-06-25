package me.joypri

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.primaryConstructor

interface MixParts {
    val valueByQualifiedName: Map<String, Any>
    val parts: List<Part> get() = valueByQualifiedName.map { Part(it.key, it.value) }
}

inline operator fun <M : MixParts, reified V> M.get(role: Role<V>): V? {
    return when (val value = this.valueByQualifiedName[role.qualifiedName]) {
        null -> null
        is V -> value
        else -> throw IllegalStateException(
            "Expected data[${role.qualifiedName}] to have a ${V::class}, but there's a ${value::class}"
        )
    }
}

/**
 * Constructs a new instance of this same class, with roles overridden by the provided `overrides`.
 * Other than replacing overridden roles, this method is a shallow copy
 * that reuses role values.
 */
fun <M : MixParts> M.with(vararg overrides: Part): M =
    this::class.constructFromParts(parts + overrides)

/**
 * Returns an instance of this class with the value at `path` mapped through `f`.
 * There must be an existing value at `path` (and hence every step along `path`),
 * else this method throws `IllegalArgumentException`.
 *
 * This method constructs new `List` and `MixParts` instances all the way down `path`,
 * preserving `MixParts` runtime classes. In all other respects, it is a shallow copy
 * that reuses role values. Beyond the usual cautions about reuse of mutable
 * values in shallow copies, the complex mix of shared and new `MixParts`
 * instances may be problematic for `Remix`s.
 */
@Suppress("UNCHECKED_CAST")
fun <M : MixParts, V : Any> M.mapAt(path: RolePath, f: (V) -> V): M {
    if (path.isEmpty()) {
        return f(this as V) as M
    } else {
        val pathHead = path.first()

        fun mapValue(v: Any): Any =
            if (path.size == 1) {
                f(v as V)
            } else {
                require(v is MixParts) {
                    "expected MixParts at $pathHead but got $v"
                }
                v.mapAt(path.drop(1), f)
            }

        val oldHeadValue: Any? = this.valueByQualifiedName[pathHead.qualifiedName]
        require(oldHeadValue != null) {
            "no value at $pathHead"
        }
        val newHeadValue: Any =
            if (pathHead is RoleAtIndex<*, *>) {
                require(oldHeadValue is List<*>) {
                    "because the next role in the path is indexed, needed a List at $pathHead but got $oldHeadValue"
                }
                val index = pathHead.index
                require(index in 0 until oldHeadValue.size) {
                    "invalid index $index; existing list at $pathHead has size ${oldHeadValue.size}"
                }
                oldHeadValue.mapIndexed { i, v ->
                    if (i == index) {
                        require(v != null) {
                            "unexpected null at $pathHead"
                        }
                        mapValue(v)
                    } else {
                        v
                    }
                }
            } else {
                mapValue(oldHeadValue)
            }

        val newParts: List<Part> =
            // Transform existing part.
            parts.map {
                if (it.keyName == pathHead.qualifiedName) {
                    Part(it.keyName, newHeadValue)
                } else {
                    it
                }
            }
        return this::class.constructFromParts(newParts)
    }
}

fun <M : MixParts, V : Any> M.mapAt(vararg role: Role<*>, f: (V) -> V): M =
    mapAt(role.toList(), f)

fun <R : Any> KClass<R>.constructFromParts(parts: List<Part>): R {
    val ctor = primaryConstructor
        ?: throw IllegalArgumentException("$this has no primary constructor")
    return ctor.call(parts.toTypedArray())
}

/**
 * An immutable heterogeneous map from `Role<V>` to `V`. If any of the provided `parts` have the same
 * keys, the last occurrence of a key is the one that is used.
 *
 * Subclasses can have `val` properties that are delegated to `Role<V>`, in which case those properties
 * will take on the values of the corresponding roles.
 */
open class Mix(vararg parts: Part) : MixParts {

    override val valueByQualifiedName: Map<String, Any> =
        parts.associate { it.keyName to it.value }

    operator fun <V> Role<V>.unaryPlus(): OptionalRoleMixDelegateProvider<V> =
        OptionalRoleMixDelegateProvider(qualifiedName)

    override fun equals(other: Any?): Boolean =
        other is Mix && other.javaClass == javaClass
                && other.valueByQualifiedName == valueByQualifiedName

    override fun hashCode(): Int =
        31 * valueByQualifiedName.hashCode() + javaClass.hashCode()
}

class OptionalRoleMixDelegateProvider<V>(private val qualifiedName: String) {
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
open class Remix(vararg parts: Part) : MixParts {

    override val valueByQualifiedName: MutableMap<String, Any> =
        parts.associateTo(mutableMapOf()) { it.keyName to it.value }

    operator fun <V> set(role: Role<V>, value: V) {
        if (value == null) {
            valueByQualifiedName.remove(role.qualifiedName)
        } else {
            valueByQualifiedName[role.qualifiedName] = value as Any
        }
    }

    /**
     * Returns this `Remix`'s `Part`s but with top-level `Remix` `Part`s converted
     * via `Remix.toMix()`.
     *
     * FIXME: We should convert `Remix` to `Mix` throughout the tree!
     */
    protected fun mixParts(): Array<Part> {
        return parts.map { part ->
            if (part.value is Remix) {
                Part(part.keyName, part.value.toMix())
            } else {
                part
            }
        }.toTypedArray()
    }

    /**
     * Converts this `Remix` and all of its subordinate `Remix`s to `Mix`s.
     */
    open fun toMix(): Mix = Mix(*mixParts())

    operator fun <V> Role<V>.unaryPlus(): RoleRemixDelegateProvider<V?> =
        RoleRemixDelegateProvider(qualifiedName)

    operator fun <M : Any, R : Any> MixRole<M, R>.not(): MixRoleRemixDelegateProvider<R> =
        MixRoleRemixDelegateProvider(qualifiedName)

    override fun equals(other: Any?): Boolean =
        other is Remix && other.javaClass == javaClass
                && other.valueByQualifiedName == valueByQualifiedName

    override fun hashCode(): Int =
        31 * valueByQualifiedName.hashCode() + javaClass.hashCode()
}

// The type name VN means nullable value.

class RoleRemixDelegateProvider<VN>(private val qualifiedName: String) {
    operator fun provideDelegate(thisRef: Remix, prop: KProperty<*>): RoleRemixDelegate<VN> =
        RoleRemixDelegate(qualifiedName)
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

@Suppress("unused") // The unused warning for R is a lie; R is used in the upcoming extension function.
class MixRoleRemixDelegateProvider<R>(val qualifiedName: String)

inline operator fun <reified R : Any> MixRoleRemixDelegateProvider<R>.provideDelegate(
    thisRef: Remix,
    prop: KProperty<*>
): MixRoleRemixDelegate<R> {
    return MixRoleRemixDelegate(qualifiedName) {
        R::class.constructFromParts(listOf())
    }
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
    open val qualifiedName: String =
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

class RoleAtIndex<R : Role<V>, V>(
    val role: R,
    val index: Int
) : Role<V>() {
    override val qualifiedName: String = role.qualifiedName
    override fun toString(): String {
        return "$role[$index]"
    }
}

operator fun <V, R : Role<V>> R.get(i: Int): RoleAtIndex<R, V> =
    RoleAtIndex(this, i)

typealias RolePath = List<Role<*>>

class RoleMixDelegate<V>(private val value: V) : ReadOnlyProperty<Mix, V> {
    override operator fun getValue(thisRef: Mix, property: KProperty<*>): V = value
}

data class Part constructor(val keyName: String, val value: Any)

/**
 * TODO: Change this name to `has`?
 * TODO: Provide a counterpart for `MixRole`.
 */
infix fun <V : Any> Role<V>.to(value: V): Part {
    return Part(qualifiedName, value)
}

abstract class MixRole<M : Any, R : Any> : Role<M>()
