package me.joypri

open class JoyData private constructor(val valueByQualifiedName: Map<String, Any>) {

    constructor(vararg entries: KeyValue) :
            this(HashMap<String, Any>().apply {
                for (entry in entries) {
                    this[entry.keyName] = entry.value
                }
            })

    inline operator fun <reified V> get(key: Key<V>): V? {
        return when(val value = this.valueByQualifiedName[key.qualifiedName]) {
            null -> null
            is V -> value
            else -> throw IllegalStateException(
                "Expected data[${key.qualifiedName}] to have a ${V::class}, but there's a ${value::class}")
        }
    }
}

open class Key<V> {
    val qualifiedName: String =
        (this::class.qualifiedName
            ?: throw IllegalStateException("DataKey class must have a qualifiedName; it is null'"))
}

data class KeyValue internal constructor(val keyName: String, val value: Any)

infix fun <V: Any> Key<V>.to(value: V): KeyValue {
    return KeyValue(qualifiedName, value)
}