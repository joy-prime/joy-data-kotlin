package me.joypri

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.IllegalArgumentException

object FirstName: Role<String>()
object Age: Role<Int>()

class Person(vararg entries: Part): Mix(*entries) {
    val firstName by FirstName
    val age by Age
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataTest {

    @Test
    fun `Key has the right fully qualified name`() {
        assertThat(FirstName.qualifiedName).isEqualTo("me.joypri.FirstName")
    }

    @Test
    fun `Data constructs and gets`() {
        val data = Mix(FirstName to "Fred", Age to 12)
        assertThat(data[FirstName]).isEqualTo("Fred")
        assertThat(data[Age]).isEqualTo(12)
    }

    @Test
    fun `Data subclass can delegate properties to keys`() {
        val person = Person(FirstName to "Fred", Age to 12)
        assertThat(person.firstName).isEqualTo("Fred")
        assertThat(person.age).isEqualTo(12)
    }

    @Test
    fun `Constructing Data subclass with missing delegated property throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            Person(FirstName to "Fred")
        }
    }
}