package me.joypri

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

object FirstName: Key<String>()
object Age: Key<Int>()

object JoyDataSpec : Spek({
    describe("Key") {
        it("has the right fully qualified name") {
            assertEquals("me.joypri.FirstName", FirstName.qualifiedName)
        }
    }
    describe("JoyData") {
        it("constructs and gets") {
            val data = JoyData(FirstName to "Fred", Age to 12)
            assertEquals("Fred", data[FirstName])
            assertEquals(12, data[Age])
        }
    }
})