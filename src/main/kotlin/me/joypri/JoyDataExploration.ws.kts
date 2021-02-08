import kotlin.reflect.KClass

inline fun <reified T : Any> theClass(): KClass<T> = T::class

val kClassListInt: KClass<List<Int>> = theClass()

kClassListInt === List::class