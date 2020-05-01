# joy-data-kotlin

Runtime types for "modeling data as data" in Kotlin™ (as they say in the Clojure world).

The idea behind Joy Data is to float a more dynamic data model gently above Kotlin's class model.
This is inspired by Clojure best practice, which in turn is captured nicely in the [motivational
documentation](https://clojure.org/about/spec) for clojure.spec. Here, from that document, is
the heart of the argument:

> We routinely deal with optional and partial data, data produced by unreliable external sources, 
> dynamic queries etc. These maps represent various sets, subsets, intersections and unions 
> of the same keys, and in general ought to have the same semantic for the same key 
> wherever it is used. Defining specifications of every subset/union/intersection, and then 
> redundantly stating the semantic of each key is both an antipattern and unworkable in the most
> dynamic cases.

To get a tasty blend of this more dynamic data model with Kotlin's type system and idioms, Joy 
Data has the following goals:

* Provide convenient syntax and clean semantics for a heterogeneous map, where the set of 
  keys is arbitrary but where any given key has a type-safe value. (Joy Data supports this 
  with an immutable `Mix` type and a mutable `Remix` counterpart.)
  
* Support conveniently and cleanly declaring Kotlin classes that extend the heterogeneous 
  map type by declaring certain keys that are required or optional and can be accessed as 
  properties.
  
* Automatically support Kotlin DSL syntax for such classes.

Here's how this looks in Joy Data:

```kotlin
object FirstName : Role<String>()
object MiddleName : Role<String>()
object Age : Role<Int>()

open class Person(vararg parts: Part) : Mix(*parts) {
    val firstName by FirstName
    val middleName by +MiddleName
    val age by Age
}
class MyTests {
    @Test
    fun `constructs and gets`() {
        val fred = Mix(FirstName to "Fred", Age to 12)
        assertEquals("Fred", fred[FirstName])
        assertNull(fred[MiddleName])
        assertEquals(12, fred[Age])
    }

    @Test
    fun `subclass can delegate properties to roles`() {
        val fred = Person(FirstName to "Fred", Age to 12)
        assertEquals("Fred", fred.firstName)
        assertNull(fred.middleName)
        assertEquals(12, fred.age)

        val jane = Person(FirstName to "Jane", MiddleName to "Ann", Age to 14)
        assertEquals("Jane", jane.firstName)
        assertEquals("Ann", jane.middleName)
        assertEquals(14, jane.age)
    }
}
```

We expect this to be a pleasant and powerful abstraction for many tasks in which data is
inherently dynamic. Examples might be JSON parsing, database binding, and Jupyter-notebook
data wrangling.

  

