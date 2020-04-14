package me.joypri

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertNull

object FirstName : Role<String>()
object Age : Role<Int>()

class Person(vararg entries: Part) : Mix(*entries) {
    val firstName by FirstName
    val age by Age
}

class PersonRemix(vararg entries: Part) : Remix(*entries) {
    var firstName by FirstName
    var age by Age
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoyDataTest {
    @Nested
    inner class RoleTest {
        @Test
        fun `has the right fully qualified name`() {
            assertEquals("me.joypri.FirstName", FirstName.qualifiedName)
        }
    }

    @Nested
    inner class MixTest {

        @Test
        fun `constructs and gets`() {
            val data = Mix(FirstName to "Fred", Age to 12)
            assertEquals("Fred",data[FirstName])
            assertEquals(12, data[Age])
        }

        @Test
        fun `subclass can delegate properties to roles`() {
            val person = Person(FirstName to "Fred", Age to 12)
            assertEquals("Fred",person.firstName)
            assertEquals(12,person.age)
        }

        @Test
        fun `Constructing Mix subclass with missing delegated property throws IllegalArgumentException`() {
            assertThrows(IllegalArgumentException::class.java) {
                Person(FirstName to "Fred")
            }
        }
    }

    @Nested
    inner class RemixTest {

        @Test
        fun `constructs, gets, and sets`() {
            val person = Remix(FirstName to "Fred")
            assertEquals("Fred",person[FirstName])
            assertNull(person[Age])
            person[Age] = 12
            assertEquals(12,person[Age])
        }

        @Test
        fun `subclass can delegate properties to roles`() {
            val person = PersonRemix(FirstName to "Fred")
            assertEquals("Fred",person.firstName)
            assertNull(person.age)
            person.age = 12
            assertEquals(12, person.age)
        }
    }
}