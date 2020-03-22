package me.joypri

import kotlin.reflect.KClass

open class JoyData private constructor(val data: Map<Key<*>, Any>) {

}

open class Key<V : Any>(val valueClass: KClass<V>) {
    val qualifiedName: String =
        (this::class.qualifiedName
            ?: throw IllegalStateException("DataKey reiclass must have a qualifiedName: ${this::class.qualifiedName}"))

    fun getFrom(data: Map<String, Any>): V? {
        val anyValue = data[qualifiedName]
        if (anyValue == null) {
            return null
        } else if (!valueClass.isInstance(anyValue)) {
            throw IllegalStateException(
                "Data type for $qualifiedName should be $valueClass; instead is ${anyValue::class}"
            )
        } else {
            @Suppress("UNCHECKED_CAST")
            return anyValue as V
        }
    }
}
