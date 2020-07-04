package me.joypri

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf
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

inline fun <M : MixParts, reified V : Any> M.mapAt(role: Role<V>, f: (V) -> V): M {
    val oldValue = get(role)
    require(oldValue != null) {
        "We only support mapping an existing role value, but $this[$role] is null."
    }
    return with(role to f(oldValue))
}

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
fun <M : MixParts, V : Any> M.mapAt(path: RolePath<V>, f: (V) -> V): M =
    path.mapHere(this, f)

fun <M : MixParts, V : Any> M.with(path: RolePath<V>, v: V): M =
    mapAt(path) { v }

operator fun <M : MixParts, V : Any> M.get(path: RolePath<V>): V? =
    path.getHere(this)

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

    override fun toString(): String {
        return qualifiedName
    }
}

abstract class Role<V> : Named() {
    operator fun provideDelegate(thisRef: Mix, prop: KProperty<*>): RoleMixDelegate<V> {
        val anyValue = thisRef.valueByQualifiedName[qualifiedName]
            ?: throw IllegalArgumentException("missing key $qualifiedName")
        @Suppress("UNCHECKED_CAST")
        return RoleMixDelegate(anyValue as V)
    }
}

sealed class RolePathSegment

data class AtRole(val role: Role<*>) : RolePathSegment() {
    override fun toString(): String = role.toString()
}

data class AtIndex(val index: Int) : RolePathSegment() {
    override fun toString(): String = "[$index]"
}

data class RolePath<V : Any> internal constructor(
    val segments: List<RolePathSegment>
) {
    constructor (role: Role<V>) :
            this(listOf(AtRole(role)))

    constructor (other: RolePath<List<V>>, index: Int) :
            this(other.segments + AtIndex(index))

    @Suppress("UNCHECKED_CAST")
    fun <M : MixParts> mapHere(mix: M, f: (V) -> V): M =
        mapAt(mix, segments, f, listOf()) as M

    @Suppress("UNCHECKED_CAST")
    fun <M : MixParts> getHere(mix: M): V? =
        getAt(mix, segments, listOf()) as V?

    /**
     * Public only for use by internal inline functions; use `plus(other)` externally.
     */
    fun <R : Any> internalConcat(thisClass: KClass<V>, other: RolePath<R>): RolePath<R> {
        require(thisClass.isSubclassOf(MixParts::class) || thisClass.isSubclassOf(List::class)) {
            "left-hand-side value type must be MixParts or List but is $thisClass"
        }
        if (other.segments.isNotEmpty()) {
            val first = other.segments.first()
            if (thisClass.isSubclassOf(MixParts::class)) {
                require(first is AtRole) {
                    "for concatenating onto RolePath<MixParts>, other must start with a Role but is $other"
                }
            } else {
                require(first is AtIndex) {
                    "for concatenating onto RolePath<List<*>>, other must start with an index but is $other"
                }
            }
        }
        return RolePath(this.segments + other.segments)
    }

    override fun toString(): String {
        return segmentsToString(segments)
    }


    companion object {
        fun <M : MixParts, V : Any> make(mixPath: RolePath<M>, role: Role<V>): RolePath<V> =
            RolePath(mixPath.segments + AtRole(role))

        fun <V : Any> empty(): RolePath<V> =
            RolePath(listOf())
    }
}

private fun segmentsToString(segments: List<RolePathSegment>): String {
    val builder = StringBuilder()
    var isFirst = true
    for (e in segments) {
        when (e) {
            is AtRole -> {
                if (!isFirst) {
                    builder.append(" + ")
                }
                builder.append(e.role.toString())
            }
            is AtIndex ->
                builder.append("[${e.index}]")
        }
        isFirst = false
    }
    return builder.toString()
}

fun <V : Any> Role<V>.toPath(): RolePath<V> = RolePath(this)

inline operator fun <reified L : Any, R: Any> Role<L>.plus(other: RolePath<R>): RolePath<R> =
    toPath().internalConcat(L::class, other)

inline operator fun <reified L: Any, R: Any> RolePath<L>.plus(other: RolePath<R>): RolePath<R> =
    this.internalConcat(L::class, other)

@Suppress("UNCHECKED_CAST")
private fun <V : Any> mapAt(
    from: Any,
    segments: List<RolePathSegment>,
    f: (V) -> V,
    context: List<RolePathSegment>
): Any {
    if (segments.isEmpty()) {
        return f(from as V)
    } else {
        val s = segments.first()
        val contextStr by lazy { segmentsToString(context + s) }

        fun recur(oldValue: Any?): Any {
            require(oldValue != null) {
                "required non-null value at $contextStr"
            }
            return mapAt(
                oldValue,
                segments.drop(1),
                f,
                context + s
            )
        }
        when (s) {
            is AtRole -> {
                require(from is MixParts) {
                    "required MixParts at $contextStr but found $from"
                }
                val oldValue = from.valueByQualifiedName[s.role.qualifiedName]
                return from.with(
                    Part(
                        s.role.qualifiedName,
                        recur(oldValue)
                    )
                )
            }
            is AtIndex -> {
                require(from is List<*>) {
                    "required List at $contextStr but found $from"
                }
                require(s.index in 0 until from.size) {
                    "outside list range (0 until ${from.size}) at $contextStr"
                }
                val oldValue = from[s.index]
                return (from as List<Any>).mapIndexed { i: Int, v: Any ->
                    if (i == s.index) {
                        recur(oldValue)
                    } else {
                        v
                    }
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun getAt(
    from: Any,
    segments: List<RolePathSegment>,
    context: List<RolePathSegment>
): Any? {
    if (segments.isEmpty()) {
        return from
    } else {
        val s = segments.first()
        val contextStr by lazy { segmentsToString(context + s) }

        fun recur(newFrom: Any?): Any? {
            require(newFrom != null) {
                "required non-null value at $contextStr"
            }
            return getAt(
                newFrom,
                segments.drop(1),
                context + s
            )
        }
        when (s) {
            is AtRole -> {
                require(from is MixParts) {
                    "required MixParts at $contextStr but found $from"
                }
                val v = from.valueByQualifiedName[s.role.qualifiedName]
                return if (segments.size == 1) v else recur(v)
            }
            is AtIndex -> {
                require(from is List<*>) {
                    "required List at $contextStr but found $from"
                }
                require(s.index in 0 until from.size) {
                    "outside list range (0 until ${from.size}) at $contextStr"
                }
                return recur(from[s.index])
            }
        }
    }
}

operator fun <M : MixParts, V : Any> RolePath<M>.plus(role: Role<V>): RolePath<V> =
    RolePath.make(this, role)

operator fun <M : MixParts, V : Any> Role<M>.plus(role: Role<V>): RolePath<V> =
    this.toPath() + role

operator fun <V : Any> Role<List<V>>.get(index: Int): RolePath<V> =
    RolePath(this.toPath(), index)

operator fun <V : Any> RolePath<List<V>>.get(index: Int): RolePath<V> =
    RolePath(this, index)

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
